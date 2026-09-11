package com.nexarq.app.ai

import android.content.Context
import android.util.Log
import com.nexarq.app.core.ChatMessage
import com.nexarq.app.data.JsonStore
import com.nexarq.app.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * AI Center backend. Talks to Google Gemini and OpenRouter using their official REST
 * APIs. Keys are encrypted at rest with [SecureStore] and never logged.
 *
 * Privacy: file contents are only sent when the caller explicitly passes them in a
 * prompt; the repository itself never reads or uploads files.
 */
class AiRepository(
    private val context: Context,
    private val secureStore: SecureStore,
    private val store: JsonStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------- SETTINGS

    private fun loadSettings(): AiSettings =
        store.read("ai_settings.json", AiSettings.serializer(), AiSettings())

    private fun persist(s: AiSettings) {
        store.write("ai_settings.json", AiSettings.serializer(), s)
        _settings.value = s
    }

    fun configFor(provider: AiProvider): AiProviderConfig = when (provider) {
        AiProvider.GEMINI -> _settings.value.gemini
        AiProvider.OPENROUTER -> _settings.value.openrouter
    }

    fun setEnabled(enabled: Boolean) {
        persist(_settings.value.copy(enabled = enabled))
    }

    fun setFallback(enabled: Boolean) {
        persist(_settings.value.copy(fallbackEnabled = enabled))
    }

    fun setMetadataOnly(metadataOnly: Boolean) {
        persist(_settings.value.copy(metadataOnly = metadataOnly))
    }

    fun setPriority(provider: AiProvider) {
        persist(_settings.value.copy(priority = provider.id))
    }

    fun setApiKey(provider: AiProvider, key: String) {
        val encrypted = secureStore.encrypt(key.trim())
        val updated = when (provider) {
            AiProvider.GEMINI -> _settings.value.copy(gemini = configFor(provider).copy(apiKeyEncrypted = encrypted))
            AiProvider.OPENROUTER -> _settings.value.copy(openrouter = configFor(provider).copy(apiKeyEncrypted = encrypted))
        }
        persist(updated)
    }

    fun removeApiKey(provider: AiProvider) {
        val updated = when (provider) {
            AiProvider.GEMINI -> _settings.value.copy(gemini = configFor(provider).copy(apiKeyEncrypted = null))
            AiProvider.OPENROUTER -> _settings.value.copy(openrouter = configFor(provider).copy(apiKeyEncrypted = null))
        }
        persist(updated)
    }

    fun setProviderEnabled(provider: AiProvider, enabled: Boolean) {
        val updated = when (provider) {
            AiProvider.GEMINI -> _settings.value.copy(gemini = configFor(provider).copy(enabled = enabled))
            AiProvider.OPENROUTER -> _settings.value.copy(openrouter = configFor(provider).copy(enabled = enabled))
        }
        persist(updated)
    }

    fun setModel(provider: AiProvider, model: String) {
        val updated = when (provider) {
            AiProvider.GEMINI -> _settings.value.copy(gemini = configFor(provider).copy(model = model.trim()))
            AiProvider.OPENROUTER -> _settings.value.copy(openrouter = configFor(provider).copy(model = model.trim()))
        }
        persist(updated)
    }

    fun apiKey(provider: AiProvider): String? =
        configFor(provider).apiKeyEncrypted?.let { secureStore.decrypt(it) }

    fun enabledProviders(): List<AiProvider> {
        val s = _settings.value
        val list = mutableListOf<AiProvider>()
        val ordered = if (s.priority == "openrouter") listOf(AiProvider.OPENROUTER, AiProvider.GEMINI)
        else listOf(AiProvider.GEMINI, AiProvider.OPENROUTER)
        for (p in ordered) {
            val c = configFor(p)
            if (c.enabled && c.hasKey()) list.add(p)
        }
        return list
    }

    fun defaultModel(provider: AiProvider): String {
        val configured = configFor(provider).model
        if (configured.isNotBlank()) return configured
        return when (provider) {
            AiProvider.GEMINI -> "gemini-1.5-flash"
            AiProvider.OPENROUTER -> "openai/gpt-4o-mini"
        }
    }

    // ------------------------------------------------------------- TEST

    suspend fun testConnection(provider: AiProvider): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey(provider) ?: return@withContext Result.failure(AiError.NoKey(provider))
            val model = defaultModel(provider)
            when (provider) {
                AiProvider.GEMINI -> geminiRequest(key, model, "ping", maxTokens = 4, stream = false)
                AiProvider.OPENROUTER -> openRouterRequest(key, model, "ping", maxTokens = 4, stream = false)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------- CHAT (streaming)

    /**
     * Stream a chat completion. Calls [onDelta] with each text chunk as it arrives and
     * [onDone] once with usage info. Falls back to the secondary provider if enabled
     * and the primary fails.
     */
    suspend fun streamChat(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        onDelta: (String) -> Unit,
        onDone: (AiResult) -> Unit,
        onError: (AiError) -> Unit,
    ) {
        val providers = enabledProviders()
        if (providers.isEmpty()) {
            onError(AiError.NoProvider)
            return
        }
        val fallback = _settings.value.fallbackEnabled && providers.size > 1
        var lastError: AiError? = null
        for (provider in providers) {
            try {
                val result = streamFrom(provider, messages, systemPrompt, onDelta)
                onDone(result)
                return
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                lastError = toAiError(e, provider)
                Log.w("NexarqAI", "Provider ${provider.id} failed: ${e.message}")
                if (!fallback) break
            }
        }
        onError(lastError ?: AiError.NoProvider)
    }

    /** Non-streaming convenience for one-shot tasks (analyze, summarize, explain). */
    suspend fun complete(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
    ): Result<AiResult> = withContext(Dispatchers.IO) {
        val providers = enabledProviders()
        if (providers.isEmpty()) return@withContext Result.failure(AiError.NoProvider)
        val fallback = _settings.value.fallbackEnabled && providers.size > 1
        var lastError: Exception? = null
        for (provider in providers) {
            try {
                val text = streamFrom(provider, messages, systemPrompt) { }
                return@withContext Result.success(text)
            } catch (e: Exception) {
                lastError = e
                if (!fallback) break
            }
        }
        Result.failure(lastError ?: AiError.NoProvider)
    }

    private suspend fun streamFrom(
        provider: AiProvider,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        onDelta: (String) -> Unit,
    ): AiResult = withContext(Dispatchers.IO) {
        val key = apiKey(provider) ?: throw AiError.NoKey(provider)
        val model = defaultModel(provider)
        val providerConfig = configFor(provider)
        val temperature = providerConfig.temperature
        val history = messages.map { it.role.name.lowercase() to it.content }
        val (text, usage) = when (provider) {
            AiProvider.GEMINI -> geminiStream(key, model, history, systemPrompt, temperature, onDelta)
            AiProvider.OPENROUTER -> openRouterStream(key, model, history, systemPrompt, temperature, onDelta)
        }
        AiResult(text, provider, model, usage)
    }

    // ------------------------------------------------------------- HISTORY

    fun loadHistory(): List<ChatMessage> =
        store.read("ai_chat.json", AiChatHistory.serializer(), AiChatHistory()).messages

    fun saveHistory(messages: List<ChatMessage>) {
        store.write("ai_chat.json", AiChatHistory.serializer(), AiChatHistory(messages.takeLast(200)))
    }

    fun clearHistory() = store.write("ai_chat.json", AiChatHistory.serializer(), AiChatHistory())

    // ------------------------------------------------------------- GEMINI

    private suspend fun geminiStream(
        key: String,
        model: String,
        history: List<Pair<String, String>>,
        systemPrompt: String?,
        temperature: Double,
        onDelta: (String) -> Unit,
    ): Pair<String, AiUsage> {
        val body = buildJsonObject {
            if (systemPrompt != null) {
                putJsonObject("systemInstruction") {
                    putJsonObject("parts") {
                        put("text", systemPrompt)
                    }
                }
            }
            putJsonArray("contents") {
                for ((role, content) in history) {
                    addJsonObject {
                        put("role", if (role == "assistant") "model" else "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", content) }
                        }
                    }
                }
                if (history.isEmpty()) {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", "Hello") } }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
            }
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$key"
        val lines = executeSse(url, body.toString(), null)
        val text = StringBuilder()
        var usage = AiUsage()
        for (line in lines) {
            coroutineContext.ensureActive()
            val data = line.removePrefix("data:").trim()
            if (data.isBlank() || data == "[DONE]") continue
            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            val candidates = obj["candidates"]?.jsonArray
            candidates?.forEach { c ->
                val parts = c.jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
                parts?.forEach { p ->
                    p.jsonObject["text"]?.jsonPrimitive?.content?.let { delta ->
                        text.append(delta)
                        onDelta(delta)
                    }
                }
            }
            val meta = obj["usageMetadata"]?.jsonObject
            if (meta != null) {
                usage = AiUsage(
                    promptTokens = meta["promptTokenCount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    completionTokens = meta["candidatesTokenCount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    totalTokens = meta["totalTokenCount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                )
            }
        }
        return text.toString() to usage
    }

    private suspend fun geminiRequest(key: String, model: String, prompt: String, maxTokens: Int, stream: Boolean): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", maxTokens)
            }
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val response = httpPost(url, body.toString(), null)
        val obj = json.parseToJsonElement(response).jsonObject
        val text = obj["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
        return text ?: throw IOException("Unexpected Gemini response")
    }

    // ------------------------------------------------------------- OPENROUTER

    private suspend fun openRouterStream(
        key: String,
        model: String,
        history: List<Pair<String, String>>,
        systemPrompt: String?,
        temperature: Double,
        onDelta: (String) -> Unit,
    ): Pair<String, AiUsage> {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", temperature)
            putJsonArray("messages") {
                if (systemPrompt != null) {
                    addJsonObject { put("role", "system"); put("content", systemPrompt) }
                }
                for ((role, content) in history) {
                    addJsonObject { put("role", role); put("content", content) }
                }
                if (history.isEmpty()) {
                    addJsonObject { put("role", "user"); put("content", "Hello") }
                }
            }
        }
        val headers = mapOf(
            "Authorization" to "Bearer $key",
            "HTTP-Referer" to "https://nexarq.app",
            "X-Title" to "NEXARQ",
        )
        val lines = executeSse("https://openrouter.ai/api/v1/chat/completions", body.toString(), headers)
        val text = StringBuilder()
        var usage = AiUsage()
        for (line in lines) {
            coroutineContext.ensureActive()
            val data = line.removePrefix("data:").trim()
            if (data.isBlank() || data == "[DONE]") continue
            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            obj["choices"]?.jsonArray?.forEach { c ->
                val delta = c.jsonObject["delta"]?.jsonObject
                delta?.get("content")?.jsonPrimitive?.content?.let { d ->
                    text.append(d)
                    onDelta(d)
                }
            }
            val u = obj["usage"]?.jsonObject
            if (u != null) {
                usage = AiUsage(
                    promptTokens = u["prompt_tokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    completionTokens = u["completion_tokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    totalTokens = u["total_tokens"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                )
            }
        }
        return text.toString() to usage
    }

    private suspend fun openRouterRequest(key: String, model: String, prompt: String, maxTokens: Int, stream: Boolean): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                addJsonObject { put("role", "user"); put("content", prompt) }
            }
        }
        val headers = mapOf(
            "Authorization" to "Bearer $key",
            "HTTP-Referer" to "https://nexarq.app",
            "X-Title" to "NEXARQ",
        )
        val response = httpPost("https://openrouter.ai/api/v1/chat/completions", body.toString(), headers)
        val obj = json.parseToJsonElement(response).jsonObject
        val text = obj["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        return text ?: throw IOException("Unexpected OpenRouter response")
    }

    // ------------------------------------------------------------- HTTP

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun httpPost(url: String, body: String, headers: Map<String, String>?): String {
        val builder = Request.Builder().url(url).post(body.toRequestBody(jsonMediaType))
        headers?.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                when (response.code) {
                    429 -> throw AiError.RateLimited
                    401, 403 -> throw AiError.HttpError(response.code, "Invalid API key or access denied")
                    else -> throw AiError.HttpError(response.code, extractError(text))
                }
            }
            return text
        }
    }

    private suspend fun executeSse(url: String, body: String, headers: Map<String, String>?): List<String> =
        withTimeoutOrNull(120_000) {
            val builder = Request.Builder().url(url).post(body.toRequestBody(jsonMediaType))
            headers?.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string() ?: ""
                    when (response.code) {
                        429 -> throw AiError.RateLimited
                        401, 403 -> throw AiError.HttpError(response.code, "Invalid API key or access denied")
                        else -> throw AiError.HttpError(response.code, extractError(text))
                    }
                }
                val lines = mutableListOf<String>()
                response.body?.string()?.let { s ->
                    lines.addAll(s.split('\n'))
                }
                lines
            }
        } ?: throw AiError.Timeout

    private fun extractError(body: String): String {
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: obj["error"]?.jsonPrimitive?.content
                ?: "Request failed"
        }.getOrDefault(body.take(200))
    }

    private fun toAiError(e: Exception, provider: AiProvider): AiError = when (e) {
        is AiError -> e
        is java.net.SocketTimeoutException -> AiError.Timeout
        is IOException -> AiError.HttpError(0, e.message ?: "Network error")
        else -> AiError.HttpError(0, e.message ?: e.javaClass.simpleName)
    }
}
