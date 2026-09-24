package com.nexarq.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexarq.app.ai.AiProvider
import com.nexarq.app.ai.AiProviderConfig
import com.nexarq.app.core.ChatMessage
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.Navigator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCenterScreen(navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val aiSettings by container.ai.settings.collectAsState()
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Center") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Chat") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Providers") })
            }
            if (tab == 0) ChatTab(container.ai.settings.value.enabled)
            else ProvidersTab()
        }
    }
}

@Composable
private fun ChatTab(aiEnabled: Boolean) {
    val container = LocalContainer.current ?: return
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    var messages by remember { mutableStateOf(container.ai.loadHistory()) }
    var input by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var activeProvider by remember { mutableStateOf<AiProvider?>(null) }
    var usage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        if (input.isBlank() || streaming) return
        val userMsg = ChatMessage(System.currentTimeMillis(), ChatMessage.Role.USER, input.trim())
        val updated = messages + userMsg
        messages = updated
        container.ai.saveHistory(updated)
        input = ""
        streaming = true
        var assistantText = ""
        val assistantMsgId = System.currentTimeMillis() + 1
        messages = updated + ChatMessage(assistantMsgId, ChatMessage.Role.ASSISTANT, "")
        job = scope.launch {
            container.ai.streamChat(
                messages = updated,
                systemPrompt = "You are NEXARQ's file assistant. You can answer questions about files, explain formats, summarize documents, and suggest file operations. Never claim to execute operations directly.",
                onDelta = { delta ->
                    assistantText += delta
                    messages = updated + ChatMessage(assistantMsgId, ChatMessage.Role.ASSISTANT, assistantText)
                },
                onDone = { result ->
                    activeProvider = result.provider
                    usage = result.usage?.let { "↑${it.promptTokens} ↓${it.completionTokens}" }
                    messages = updated + ChatMessage(assistantMsgId, ChatMessage.Role.ASSISTANT, assistantText)
                    container.ai.saveHistory(messages)
                    streaming = false
                },
                onError = { e ->
                    messages = updated + ChatMessage(assistantMsgId, ChatMessage.Role.ERROR, e.message ?: "AI error")
                    container.ai.saveHistory(messages)
                    streaming = false
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!aiEnabled) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("AI is disabled", style = MaterialTheme.typography.titleMedium)
                    Text("Configure a provider and enable AI in the Providers tab to start chatting.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(messages) { msg ->
                when (msg.role) {
                    ChatMessage.Role.USER -> UserBubble(msg.content)
                    ChatMessage.Role.ASSISTANT -> AssistantBubble(msg.content)
                    ChatMessage.Role.ERROR -> ErrorBubble(msg.content)
                    else -> {}
                }
            }
            if (streaming) {
                item { Text("…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Ask about your files…") },
                modifier = Modifier.weight(1f), maxLines = 4,
            )
            if (streaming) {
                IconButton(onClick = { job?.cancel(); streaming = false }) { Icon(Icons.Default.Stop, "Stop") }
            } else {
                IconButton(onClick = { send() }, enabled = input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { messages = emptyList(); container.ai.clearHistory() }) { Text("Clear chat") }
            activeProvider?.let { Text("via ${it.label}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            usage?.let { Text(" · $it", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    val clipboard = LocalClipboardManager.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)) {
            Column(Modifier.padding(12.dp)) {
                MarkdownLite(text)
                IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
            Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }
    }
}

/** Minimal markdown renderer: code fences + bold + paragraphs. */
@Composable
private fun MarkdownLite(text: String) {
    val parts = text.split("```")
    Column {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(part, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp))
                }
            } else if (part.isNotBlank()) {
                Text(part, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProvidersTab() {
    val container = LocalContainer.current ?: return
    val settings by container.ai.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var testStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("AI features", style = MaterialTheme.typography.titleMedium)
                Text("AI usage may incur charges on your provider account.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.enabled, onCheckedChange = { container.ai.setEnabled(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Provider fallback", style = MaterialTheme.typography.bodyMedium)
                Text("Use the next enabled provider on failure.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.fallbackEnabled, onCheckedChange = { container.ai.setFallback(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Metadata-only by default", style = MaterialTheme.typography.bodyMedium)
                Text("Send only summaries, never full file contents.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.metadataOnly, onCheckedChange = { container.ai.setMetadataOnly(it) })
        }

        Spacer(Modifier.height(16.dp))
        ProviderCard(
            provider = AiProvider.GEMINI,
            config = settings.gemini,
            onToggle = { container.ai.setProviderEnabled(AiProvider.GEMINI, it) },
            onSaveKey = { container.ai.setApiKey(AiProvider.GEMINI, it) },
            onRemoveKey = { container.ai.removeApiKey(AiProvider.GEMINI) },
            onModel = { container.ai.setModel(AiProvider.GEMINI, it) },
            onTest = {
                scope.launch {
                    testStatus = testStatus + (AiProvider.GEMINI.id to "Testing…")
                    val r = container.ai.testConnection(AiProvider.GEMINI)
                    testStatus = testStatus + (AiProvider.GEMINI.id to if (r.isSuccess) "Connected ✓" else "Failed: ${r.exceptionOrNull()?.message}")
                }
            },
            testStatus = testStatus[AiProvider.GEMINI.id],
        )
        Spacer(Modifier.height(12.dp))
        ProviderCard(
            provider = AiProvider.OPENROUTER,
            config = settings.openrouter,
            onToggle = { container.ai.setProviderEnabled(AiProvider.OPENROUTER, it) },
            onSaveKey = { container.ai.setApiKey(AiProvider.OPENROUTER, it) },
            onRemoveKey = { container.ai.removeApiKey(AiProvider.OPENROUTER) },
            onModel = { container.ai.setModel(AiProvider.OPENROUTER, it) },
            onTest = {
                scope.launch {
                    testStatus = testStatus + (AiProvider.OPENROUTER.id to "Testing…")
                    val r = container.ai.testConnection(AiProvider.OPENROUTER)
                    testStatus = testStatus + (AiProvider.OPENROUTER.id to if (r.isSuccess) "Connected ✓" else "Failed: ${r.exceptionOrNull()?.message}")
                }
            },
            testStatus = testStatus[AiProvider.OPENROUTER.id],
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    config: AiProviderConfig,
    onToggle: (Boolean) -> Unit,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
    onModel: (String) -> Unit,
    onTest: () -> Unit,
    testStatus: String?,
) {
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf(config.model) }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(provider.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = config.enabled, onCheckedChange = onToggle)
            }
            if (config.hasKey()) {
                Text("API key stored securely (Keystore-encrypted)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onRemoveKey) { Text("Remove credentials", color = MaterialTheme.colorScheme.error) }
            } else {
                OutlinedTextField(keyInput, { keyInput = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle")
                        }
                    },
                    modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onSaveKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) { Text("Save key") }
            }
            OutlinedTextField(modelInput, { modelInput = it }, label = { Text("Model") }, singleLine = true,
                placeholder = { Text(provider.defaultModelHint) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onModel(modelInput) }) { Text("Set model") }
            Row {
                Button(onClick = onTest) { Text("Test connection") }
                testStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp).align(Alignment.CenterVertically)) }
            }
            Text("Usage may incur charges on your ${provider.label} account.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private val AiProvider.defaultModelHint: String
    get() = when (this) {
        AiProvider.GEMINI -> "gemini-2.5-flash"
        AiProvider.OPENROUTER -> "openai/gpt-4o-mini"
    }
