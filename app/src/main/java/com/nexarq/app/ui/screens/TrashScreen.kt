package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.Format
import com.nexarq.app.core.TimeFormat
import com.nexarq.app.core.TrashedItem
import com.nexarq.app.data.SettingsRepository
import com.nexarq.app.trash.TrashManager
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.ConfirmDialog
import com.nexarq.app.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = SettingsRepository.AppSettings())

    var items by remember { mutableStateOf(container.trash.list()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TrashedItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        items = container.trash.list()
    }

    // Auto-purge items older than the retention period whenever the bin is opened.
    LaunchedEffect(Unit) {
        val purged = TrashManager.purgeExpired(context, container.trash, settings.trashRetentionDays)
        if (purged > 0) notice = "Auto-removed $purged expired item(s)"
        refresh()
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(2500)
            notice = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trash")
                        Text(
                            "${items.size} item(s) · ${Format.bytes(items.sumOf { it.size })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                            Text("Empty", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                title = "Trash is empty",
                subtitle = "Deleted files will appear here when the trash is enabled in Settings.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        onRestore = {
                            scope.launch {
                                runCatching { TrashManager.restore(context, container.trash, item.id) }
                                    .onSuccess { path -> notice = "Restored to $path" }
                                    .onFailure { notice = it.message ?: "Restore failed" }
                                refresh()
                            }
                        },
                        onDelete = { pendingDelete = item },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Empty trash?",
            message = "Permanently delete all ${items.size} item(s)? This cannot be undone.",
            confirmLabel = "Empty trash",
            destructive = true,
            onConfirm = {
                confirmEmpty = false
                scope.launch {
                    TrashManager.emptyTrash(context, container.trash)
                    notice = "Trash emptied"
                    refresh()
                }
            },
            onDismiss = { confirmEmpty = false },
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete permanently?",
            message = "Permanently delete \"${item.name}\"? This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    TrashManager.deletePermanently(container.trash, item.id)
                    refresh()
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    notice?.let {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(it, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun TrashRow(item: TrashedItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.originalPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Format.bytes(item.size)} · Trashed ${TimeFormat.format(item.trashedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore) { Icon(Icons.Default.Restore, "Restore") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteForever, "Delete permanently", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
