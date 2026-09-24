package com.nexarq.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexarq.app.transfer.HttpFileServer
import com.nexarq.app.ui.Navigator
import java.io.File
import java.io.IOException

/**
 * Wi-Fi file transfer: starts the embedded HTTP server and shows the URL the
 * user opens in a PC/phone browser on the same network to browse, download
 * and upload files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(dir: String, navigator: Navigator) {
    val context = LocalContext.current
    val server = remember(dir) { HttpFileServer(File(dir)) }

    var running by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    fun start() {
        error = null
        copied = false
        runCatching {
            server.start()
            url = server.baseUrl()
                ?: throw IOException("No Wi-Fi network address found — connect this phone to Wi-Fi and try again")
            running = true
        }.onFailure {
            server.stop()
            url = null
            error = it.message ?: "Could not start the server"
            running = false
        }
    }

    fun stop() {
        server.stop()
        running = false
    }

    // Auto-start on open; always stop when leaving the screen.
    LaunchedEffect(Unit) { start() }
    DisposableEffect(Unit) { onDispose { server.shutdown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi transfer") },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Wifi,
                null,
                tint = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp).padding(top = 12.dp),
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (running)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (running) Icons.Default.CheckCircle else Icons.Default.Error,
                        null,
                        tint = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            if (running) "Server running" else "Server stopped",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Sharing: $dir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (running && url != null) {
                Text(
                    "Open this address in a browser on the same Wi-Fi network:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            url!!,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("wifi-url", url))
                            copied = true
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy address")
                        }
                    }
                }
                if (copied) {
                    Text("Copied to clipboard", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(4.dp))

            if (running) {
                OutlinedButton(onClick = { stop() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop server")
                }
            } else {
                Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start server")
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("How it works", style = MaterialTheme.typography.titleSmall)
                listOf(
                    "1. Connect this phone and your computer to the same Wi-Fi.",
                    "2. Open the address above in the computer's browser.",
                    "3. Browse folders, download files, or upload new ones.",
                    "4. Stop the server here when you are done.",
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Anyone on the same network can reach the server while it runs — stop it when finished.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
