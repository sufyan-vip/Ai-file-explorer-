package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.archive.ArchiveEngine
import com.nexarq.app.archive.ArchiveListing
import com.nexarq.app.core.ArchiveEntry
import com.nexarq.app.core.Format
import com.nexarq.app.core.Intents
import com.nexarq.app.core.TimeFormat
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.Screen
import com.nexarq.app.ui.components.EmptyState
import com.nexarq.app.ui.components.ErrorState
import com.nexarq.app.ui.components.LoadingState
import com.nexarq.app.ui.components.OperationProgressBar
import com.nexarq.app.ui.components.fileIcon
import com.nexarq.app.ui.dialogs.ExtractDialog
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(path: String, navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var listing by remember { mutableStateOf<ArchiveListing?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showExtract by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<com.nexarq.app.core.OperationProgress?>(null) }
    var searchTerm by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching { ArchiveEngine.list(path, password?.toCharArray()) }
                .onSuccess { listing = it; needsPassword = false }
                .onFailure { e ->
                    if (e.message?.contains("password", true) == true || e.message?.contains("encrypted", true) == true) {
                        needsPassword = true
                    }
                    error = e.message
                }
            loading = false
        }
    }

    LaunchedEffect(path) { load() }

    val filtered = remember(listing, searchTerm) {
        val l = listing ?: return@remember emptyList<ArchiveEntry>()
        if (searchTerm.isBlank()) l.entries else l.entries.filter { it.name.contains(searchTerm, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showExtract = true }) { Icon(Icons.Default.Unarchive, "Extract") }
                    IconButton(onClick = {
                        scope.launch {
                            testResult = "Testing…"
                            val r = ArchiveEngine.test(path, password?.toCharArray())
                            testResult = if (r.success) "Archive OK (${r.elapsedMs}ms)" else "Corrupt: ${r.message}"
                        }
                    }) { Icon(Icons.Default.Verified, "Test") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Extract all") }, onClick = { showExtract = true; menuOpen = false })
                        DropdownMenuItem(text = { Text("Add files…") }, onClick = { toast = "Add files works on ZIP archives"; menuOpen = false })
                        if (password != null) {
                            DropdownMenuItem(text = { Text("Clear password") }, onClick = { password = null; load(); menuOpen = false })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // metadata card
            listing?.let { l ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MetaItem("Format", l.format.id.uppercase())
                            MetaItem("Files", l.fileCount.toString())
                            MetaItem("Size", Format.bytes(l.totalUncompressedSize))
                            MetaItem("Ratio", l.compressionRatio)
                        }
                        if (l.encrypted) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 4.dp))
                                Text("Encrypted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchTerm, onValueChange = { searchTerm = it },
                label = { Text("Search entries") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            progress?.let { p ->
                OperationProgressBar(p.label, p.currentFile, p.fraction, indeterminate = p.totalBytes == 0L)
            }

            when {
                loading -> LoadingState("Reading archive…")
                error != null && !needsPassword -> ErrorState(error ?: "")
                filtered.isEmpty() && listing != null -> EmptyState("No entries match")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.path }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(fileIcon(entry.name, entry.isDirectory), null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(entry.path, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    buildString {
                                        if (!entry.isDirectory) {
                                            append(Format.bytes(entry.size))
                                            append(" · ")
                                        }
                                        append(if (entry.modified > 0) TimeFormat.format(entry.modified) else "—")
                                        if (entry.encrypted) append(" · encrypted")
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { preview(entry) }) { Icon(Icons.Default.Visibility, "Preview") }
                            IconButton(onClick = { extractOne(entry) }) { Icon(Icons.Default.Unarchive, "Extract") }
                        }
                    }
                }
            }
        }
    }

    // password dialog
    if (needsPassword) {
        AlertDialog(
            onDismissRequest = { if (error == null) {} },
            title = { Text("Password required") },
            text = {
                Column {
                    Text("This archive is encrypted.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(passwordInput, { passwordInput = it }, label = { Text("Password") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    password = passwordInput
                    load()
                }, enabled = passwordInput.isNotBlank()) { Text("Unlock") }
            },
            dismissButton = { TextButton(onClick = { error = "Password required"; needsPassword = false }) { Text("Cancel") } },
        )
    }

    if (showExtract) {
        ExtractDialog(
            archivePath = path,
            entryNames = null,
            encrypted = listing?.encrypted == true,
            onDone = { showExtract = false },
        )
    }

    previewText?.let { text ->
        AlertDialog(
            onDismissRequest = { previewText = null },
            title = { Text("Preview") },
            text = {
                androidx.compose.foundation.verticalScroll(androidx.compose.foundation.rememberScrollState()) {
                    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { previewText = null }) { Text("Close") } },
        )
    }

    testResult?.let {
        AlertDialog(onDismissRequest = { testResult = null }, title = { Text("Archive test") },
            text = { Text(it) }, confirmButton = { TextButton(onClick = { testResult = null }) { Text("OK") } })
    }

    toast?.let {
        LaunchedEffect(it) { kotlinx.coroutines.delay(2000); toast = null }
        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium) {
            Text(it, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(12.dp))
        }
    }

    fun preview(entry: ArchiveEntry) {
        if (entry.isDirectory) return
        if (entry.size > 256 * 1024) { toast = "Entry too large to preview"; return }
        scope.launch {
            runCatching {
                val p = ArchiveEngine.preview(path, entry.path, password?.toCharArray())
                String(p.bytes, Charsets.UTF_8)
            }.onSuccess { previewText = it }
                .onFailure { toast = it.message }
        }
    }

    fun extractOne(entry: ArchiveEntry) {
        scope.launch {
            val dest = "${File(path).parentFile?.absolutePath}/${File(path).nameWithoutExtension}"
            runCatching {
                ArchiveEngine.extract(path, dest, password?.toCharArray(),
                    entryFilter = { it.path == entry.path },
                    conflict = com.nexarq.app.core.ConflictPolicy.ASK,
                    resolver = { com.nexarq.app.core.ConflictResolution.OVERWRITE }) { progress = it }
            }.onSuccess { toast = "Extracted to $dest" }
                .onFailure { toast = it.message }
        }
    }
}

@Composable
private fun MetaItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
