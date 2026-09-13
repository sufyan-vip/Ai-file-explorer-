package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.Format
import com.nexarq.app.core.TypeBucket
import com.nexarq.app.tools.LargeEntry
import com.nexarq.app.tools.StorageAnalysis
import com.nexarq.app.tools.StorageAnalyzer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.defaultStoragePath
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf(defaultStoragePath()) }
    var running by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<StorageAnalysis?>(null) }

    fun run() {
        scope.launch {
            running = true
            analysis = StorageAnalyzer.analyze(root)
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage analyzer") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(root, { root = it }, label = { Text("Path") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                androidx.compose.material3.Button(onClick = { run() }, enabled = !running,
                    modifier = Modifier.padding(start = 8.dp)) { Text("Scan") }
            }

            val a = analysis
            when {
                running -> com.nexarq.app.ui.components.LoadingState("Scanning…")
                a == null -> com.nexarq.app.ui.components.EmptyState("Choose a path and tap Scan")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text("Total: ${Format.bytes(a.totalBytes)} · ${Format.count(a.fileCount)} files · ${Format.count(a.dirCount)} folders",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    item { Text("By type", style = MaterialTheme.typography.titleMedium) }
                    items(a.buckets) { bucket -> BucketRow(bucket, a.totalBytes) }
                    item { Text("Largest files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
                    items(a.largestFiles.take(20)) { f -> EntryRow(f.name, f.path, f.size) }
                    item { Text("Largest folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
                    items(a.largestDirs.take(20)) { f -> EntryRow(f.name, f.path, f.size) }
                }
            }
        }
    }
}

@Composable
private fun BucketRow(bucket: TypeBucket, total: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(bucket.category, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${Format.bytes(bucket.bytes)} · ${Format.count(bucket.count)}", style = MaterialTheme.typography.bodySmall)
        }
        val frac = if (total > 0) bucket.bytes.toFloat() / total else 0f
        LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EntryRow(name: String, path: String, size: Long) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$path · ${Format.bytes(size)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
