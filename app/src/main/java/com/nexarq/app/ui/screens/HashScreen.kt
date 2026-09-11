package com.nexarq.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexarq.app.tools.HashAlgorithm
import com.nexarq.app.tools.Hashing
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.defaultStoragePath
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf(HashAlgorithm.SHA256) }
    var result by remember { mutableStateOf<String?>(null) }
    var expected by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<String?>(null) }

    fun compute() {
        val file = File(path)
        if (!file.isFile) { result = "Not a file"; return }
        scope.launch {
            running = true
            result = Hashing.hashFile(file, algorithm)
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hash calculator") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            OutlinedTextField(path, { path = it }, label = { Text("File path") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                HashAlgorithm.entries.forEach { a ->
                    androidx.compose.material3.FilterChip(selected = algorithm == a, onClick = { algorithm = a },
                        label = { Text(a.label) })
                }
            }
            Button(onClick = { compute() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) "Hashing…" else "Compute")
            }
            result?.let { hash ->
                Text(algorithm.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(hash, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("hash", hash))
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                }
                OutlinedTextField(expected, { expected = it }, label = { Text("Compare with expected (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                Button(onClick = {
                    verifyResult = if (Hashing.verify(hash, expected)) "MATCH" else "MISMATCH"
                }, enabled = expected.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Verify") }
                verifyResult?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium,
                        color = if (it == "MATCH") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
