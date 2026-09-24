package com.nexarq.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexarq.app.tools.HexRow
import com.nexarq.app.tools.HexViewer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.LoadingState
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(path: String, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var offset by remember { mutableStateOf(0L) }
    var rows by remember { mutableStateOf<List<HexRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var totalSize by remember { mutableStateOf(0L) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            rows = HexViewer.readChunk(path, offset, chunkSize = 64 * 1024)
            totalSize = HexViewer.fileSize(path)
            loading = false
        }
    }

    LaunchedEffect(path, offset) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${File(path).name} — 0x${"%08X".format(offset)}") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Default.Search, "Search") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) {
                LoadingState("Reading…")
            } else {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Column {
                        rows.forEach { row ->
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("${"%08X".format(row.offset)}  ", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                Text(hexString(row.bytes) + "  " + row.ascii,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    TextButton(enabled = offset > 0, onClick = { offset = (offset - 64 * 1024).coerceAtLeast(0) }) { Text("Prev") }
                    Text("${offset} / $totalSize", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = offset + 64 * 1024 < totalSize, onClick = { offset += 64 * 1024 }) { Text("Next") }
                }
            }
        }
    }

    if (searchOpen) {
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text("Search bytes / text") },
            text = {
                Column {
                    OutlinedTextField(searchText, { searchText = it }, label = { Text("Pattern (text)") }, singleLine = true)
                    searchResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val pattern = searchText.toByteArray(Charsets.UTF_8)
                        val found = HexViewer.search(path, pattern, offset)
                        searchResult = if (found >= 0) "Found at 0x${"%08X".format(found)}" else "Not found"
                        if (found >= 0) offset = found
                    }
                }) { Text("Find") }
            },
            dismissButton = { TextButton(onClick = { searchOpen = false }) { Text("Close") } },
        )
    }
}

private fun hexString(bytes: ByteArray): String = buildString {
    for (b in bytes) append("%02X ".format(b.toInt() and 0xFF))
    repeat(16 - bytes.size) { append("   ") }
}
