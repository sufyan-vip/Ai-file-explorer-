package com.nexarq.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.ChatMessage
import com.nexarq.app.tools.TextFile
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.LoadingState
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(path: String, navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var text by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var readOnly by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lineNumbers by remember { mutableStateOf(true) }
    var aiBusy by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        runCatching { TextFile.read(path) }
            .onSuccess { r -> text = r.text; original = r.text; readOnly = r.readOnly; editing = false }
            .onFailure { error = it.message }
        loading = false
    }

    fun aiAction(prompt: String, apply: Boolean) {
        if (!container.ai.settings.value.enabled) {
            scope.launch { snackbar.showSnackbar("Enable AI in the AI Center first") }
            return
        }
        val selection = text // whole content for v1
        val messages = listOf(
            ChatMessage(System.currentTimeMillis(), ChatMessage.Role.USER,
                "$prompt\n\nFile: ${File(path).name}\n\n```\n${selection.take(8000)}\n```"),
        )
        aiBusy = true
        scope.launch {
            val result = container.ai.complete(messages, "You are a helpful file-editing assistant. Reply with ONLY the transformed text/code when asked to transform; otherwise reply conversationally.")
            aiBusy = false
            result.onSuccess { r ->
                if (apply && r.text.isNotBlank()) {
                    text = r.text.trim('\n')
                    editing = true
                } else {
                    snackbar.showSnackbar(r.text.take(400))
                }
            }.onFailure { e ->
                aiBusy = false
                scope.launch { snackbar.showSnackbar(e.message ?: "AI error") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(File(path).name)
                        Text(if (readOnly) "Read-only (large file)" else if (editing) "Edited" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { lineNumbers = !lineNumbers }) { Text(if (lineNumbers) "¶" else "≡") }
                    IconButton(enabled = !aiBusy, onClick = { aiAction("Format this text/code and return only the formatted result.", apply = true) }) {
                        Icon(Icons.Default.AutoAwesome, "AI format")
                    }
                    IconButton(enabled = editing && !readOnly, onClick = {
                        scope.launch {
                            runCatching { TextFile.write(path, text) }
                                .onSuccess { original = text; editing = false; snackbar.showSnackbar("Saved") }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Save failed") }
                        }
                    }) { Icon(Icons.Default.Save, "Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (loading) {
            LoadingState("Opening file…")
        } else if (error != null) {
            Text(error ?: "", modifier = Modifier.padding(padding).padding(16.dp), color = MaterialTheme.colorScheme.error)
        } else if (readOnly) {
            SelectionContainer {
                Text(
                    text,
                    modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                        .background(Color.Transparent).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; editing = true },
                modifier = Modifier.padding(padding).fillMaxSize().padding(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
