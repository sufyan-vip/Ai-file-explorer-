package com.nexarq.app.ai

import com.nexarq.app.core.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

enum class AiProvider(val id: String, val label: String) {
    GEMINI("gemini", "Google Gemini"),
    OPENROUTER("openrouter", "OpenRouter");
}

/** Per-provider configuration. API keys are stored encrypted (never in plaintext). */
@Serializable
data class AiProviderConfig(
    val provider: String,
    val enabled: Boolean = false,
    val apiKeyEncrypted: String? = null,
    val model: String = "",
    val temperature: Double = 0.7,
) {
    fun hasKey(): Boolean = !apiKeyEncrypted.isNullOrBlank()
}

@Serializable
data class AiSettings(
    val gemini: AiProviderConfig = AiProviderConfig("gemini"),
    val openrouter: AiProviderConfig = AiProviderConfig("openrouter"),
    val enabled: Boolean = false,
    val fallbackEnabled: Boolean = false,
    val metadataOnly: Boolean = true,
    val priority: String = "gemini",
)

@Serializable
data class AiChatHistory(val messages: List<ChatMessage> = emptyList())

data class AiUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
)

data class AiResult(
    val text: String,
    val provider: AiProvider,
    val model: String,
    val usage: AiUsage? = null,
)

sealed class AiError(message: String) : Exception(message) {
    class NoKey(provider: AiProvider) : AiError("No API key configured for ${provider.label}")
    class NoProvider : AiError("No AI provider is configured or enabled")
    class HttpError(val code: Int, message: String) : AiError(message)
    class RateLimited : AiError("Rate limit exceeded. Try again later.")
    class Timeout : AiError("The AI request timed out.")
}

val ListSerializerForChatHistory = ListSerializer(ChatMessage.serializer())
