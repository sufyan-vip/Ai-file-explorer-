package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.Format
import com.nexarq.app.tools.CryptoFile
import com.nexarq.app.ui.Navigator
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoScreen(path: String, encrypt: Boolean, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    val src = remember(path) { File(path) }

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var outputName by remember {
        mutableStateOf(if (encrypt) CryptoFile.encryptedName(src) else CryptoFile.decryptedName(src))
    }
    var running by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var donePath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val passwordOk = password.length >= 4 && (!encrypt || password == confirm)
    val canRun = passwordOk && outputName.isNotBlank() && !running && donePath == null

    fun run() {
        scope.launch {
            running = true
            error = null
            fraction = 0f
            try {
                val dst = File(src.parentFile, outputName.trim())
                val pw = password.toCharArray()
                try {
                    val report: (Long, Long) -> Unit = { done, total ->
                        fraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    }
                    if (encrypt) CryptoFile.encryptFile(src, dst, pw, report)
                    else CryptoFile.decryptFile(src, dst, pw, report)
                } finally {
                    pw.fill('\u0000')
                }
                donePath = dst.absolutePath
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Operation failed"
            }
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (encrypt) "Encrypt file" else "Decrypt file") },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                Column(Modifier.padding(16.dp)) {
                    Icon(
                        if (encrypt) Icons.Default.Lock else Icons.Default.LockOpen,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(src.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Format.bytes(src.length())} · ${src.parent}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (encrypt) {
                        Text(
                            "Secured with AES-256-GCM. The password cannot be recovered — keep it safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 4 characters)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !running && donePath == null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (encrypt) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirm.isNotEmpty() && confirm != password,
                    enabled = !running && donePath == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output file name") },
                singleLine = true,
                enabled = !running && donePath == null,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            if (running) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            donePath?.let {
                Text(
                    "Saved successfully:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            Spacer(Modifier.height(8.dp))
            if (donePath == null) {
                Button(onClick = { run() }, enabled = canRun, modifier = Modifier.fillMaxWidth()) {
                    Text(if (running) "Working…" else if (encrypt) "Encrypt" else "Decrypt")
                }
            } else {
                Button(onClick = { navigator.pop() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
                TextButton(onClick = {
                    donePath = null
                    password = ""
                    confirm = ""
                    fraction = 0f
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Process another file")
                }
            }
        }
    }
}
