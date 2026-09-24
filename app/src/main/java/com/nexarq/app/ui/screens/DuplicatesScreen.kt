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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.DuplicateGroup
import com.nexarq.app.core.FileSystem
import com.nexarq.app.core.Format
import com.nexarq.app.tools.DuplicateFinder
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.ConfirmDialog
import com.nexarq.app.ui.defaultStoragePath
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf(defaultStoragePath()) }
    var running by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var scanned by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun run() {
        scope.launch {
            running = true
            groups = DuplicateFinder.find(listOf(root), onProgress = { p ->
                scanned = if (p.phase == com.nexarq.app.tools.DuplicateScanProgress.Phase.HASHING)
                    "Hashing ${p.scanned}/${p.total}" else ""
            })
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate finder") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (selection.isNotEmpty()) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete selected") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(root, { root = it }, label = { Text("Path") }, singleLine = true, modifier = Modifier.weight(1f))
                androidx.compose.material3.Button(onClick = { run() }, enabled = !running, modifier = Modifier.padding(start = 8.dp)) { Text("Scan") }
            }
            if (scanned.isNotBlank()) Text(scanned, style = MaterialTheme.typography.bodySmall)
            val wasted = DuplicateFinder.wastedBytes(groups)
            if (groups.isNotEmpty()) {
                Text("${groups.size} groups · ${Format.bytes(wasted)} recoverable",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            when {
                running -> com.nexarq.app.ui.components.LoadingState("Scanning…")
                groups.isEmpty() -> com.nexarq.app.ui.components.EmptyState("No duplicates found (or not scanned yet)")
                else -> LazyColumn {
                    items(groups, key = { it.hash }) { group ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${group.files.size} copies · ${Format.bytes(group.size)} each",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            group.files.forEachIndexed { index, file ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = file.absolutePath in selection,
                                        onCheckedChange = { checked ->
                                            selection = if (checked) selection + file.absolutePath else selection - file.absolutePath
                                        },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(file.absolutePath, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (index == 0) Text("keep", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${selection.size} files?",
            message = "Selected duplicate files will be permanently deleted.",
            confirmLabel = "Delete", destructive = true,
            onConfirm = {
                val targets = selection.toList()
                scope.launch {
                    FileSystem.delete(targets) {}
                    selection = emptySet()
                    confirmDelete = false
                    groups = emptyList()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
