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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.Format
import com.nexarq.app.root.FileStat
import com.nexarq.app.root.MountInfo
import com.nexarq.app.root.RootManager
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.ConfirmDialog
import com.nexarq.app.ui.components.EmptyState
import com.nexarq.app.ui.components.ErrorState
import com.nexarq.app.ui.components.LoadingState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootBrowserScreen(navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("/") }
    var items by remember { mutableStateOf<List<FileStat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rootAvailable by remember { mutableStateOf(RootManager.isRooted()) }
    var menuFor by remember { mutableStateOf<FileStat?>(null) }
    var chmodTarget by remember { mutableStateOf<FileStat?>(null) }
    var chmodValue by remember { mutableStateOf("755") }
    var chownTarget by remember { mutableStateOf<FileStat?>(null) }
    var chownValue by remember { mutableStateOf("") }
    var symlinkTarget by remember { mutableStateOf<FileStat?>(null) }
    var symlinkValue by remember { mutableStateOf("") }
    var showMounts by remember { mutableStateOf(false) }
    var mounts by remember { mutableStateOf<List<MountInfo>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching { RootManager.listDirectoryDetailed(path) }
                .onSuccess { items = it }
                .onFailure { e -> error = e.message ?: "Root access denied" }
            loading = false
        }
    }

    LaunchedEffect(path) {
        if (rootAvailable) load()
    }

    fun toast(msg: String) {
        toast = msg
        scope.launch { kotlinx.coroutines.delay(2000); toast = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Root browser")
                        Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { path = path.substringBeforeLast('/', "/").ifEmpty { "/" } }) { Icon(Icons.Default.ArrowUpward, "Up") }
                    IconButton(onClick = {
                        scope.launch { mounts = RootManager.getMounts(); showMounts = true }
                    }) { Icon(Icons.Default.MoreVert, "Mounts") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!rootAvailable) {
                EmptyState("No root access", "This device is not rooted, or root was denied.")
                TextButton(onClick = {
                    RootManager.refresh()
                    rootAvailable = RootManager.isRooted()
                    if (rootAvailable) load()
                }, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)) { Text("Detect again") }
            } else {
                when {
                    loading -> LoadingState("Reading…")
                    error != null -> ErrorState(error ?: "")
                    items.isEmpty() -> EmptyState("Empty (permission denied?)")
                    else -> LazyColumn {
                        items(items, key = { it.path }) { stat ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(stat.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${stat.permissions}  ${stat.owner}:${stat.group}  ${if (stat.isDirectory) "dir" else Format.bytes(stat.size)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (stat.isSymlink) Text("→ ${stat.linkTarget}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { menuFor = stat }) { Icon(Icons.Default.MoreVert, "Actions") }
                            }
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // per-entry menu
    menuFor?.let { stat ->
        var expanded by remember { mutableStateOf(true) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { menuFor = null }) {
            if (stat.isDirectory) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { path = stat.path; menuFor = null })
            } else {
                DropdownMenuItem(text = { Text("Read (root)") }, onClick = {
                    scope.launch {
                        runCatching { RootManager.readFile(stat.path) }
                            .onSuccess { toast = it.take(200) }
                            .onFailure { toast(it.message ?: "Read failed") }
                    }
                    menuFor = null
                })
            }
            DropdownMenuItem(text = { Text("chmod") }, onClick = { chmodTarget = stat; menuFor = null })
            DropdownMenuItem(text = { Text("chown") }, onClick = { chownTarget = stat; menuFor = null })
            DropdownMenuItem(text = { Text("Create symlink") }, onClick = { symlinkTarget = stat; menuFor = null })
        }
    }

    chmodTarget?.let { stat ->
        AlertDialog(
            onDismissRequest = { chmodTarget = null },
            title = { Text("chmod ${stat.name}") },
            text = {
                Column {
                    Text("Current: ${stat.permissions}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(chmodValue, { chmodValue = it }, label = { Text("Mode (e.g. 755)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val r = RootManager.chmod(stat.path, chmodValue)
                        toast(if (r.success) "Permissions updated" else "Failed: ${r.output.take(120)}")
                        load()
                    }
                    chmodTarget = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { chmodTarget = null }) { Text("Cancel") } },
        )
    }

    chownTarget?.let { stat ->
        AlertDialog(
            onDismissRequest = { chownTarget = null },
            title = { Text("chown ${stat.name}") },
            text = {
                Column {
                    Text("Current owner: ${stat.owner}:${stat.group}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(chownValue, { chownValue = it }, label = { Text("owner[:group]") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val parts = chownValue.split(':', limit = 2)
                        val r = RootManager.chown(stat.path, parts[0], parts.getOrNull(1))
                        toast(if (r.success) "Ownership updated" else "Failed: ${r.output.take(120)}")
                        load()
                    }
                    chownTarget = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { chownTarget = null }) { Text("Cancel") } },
        )
    }

    symlinkTarget?.let { stat ->
        AlertDialog(
            onDismissRequest = { symlinkTarget = null },
            title = { Text("Create symlink") },
            text = {
                Column {
                    Text("Target file: ${stat.path}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(symlinkValue, { symlinkValue = it }, label = { Text("Link path") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val r = RootManager.createSymlink(stat.path, symlinkValue)
                        toast(if (r.success) "Symlink created" else "Failed: ${r.output.take(120)}")
                    }
                    symlinkTarget = null
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { symlinkTarget = null }) { Text("Cancel") } },
        )
    }

    if (showMounts) {
        AlertDialog(
            onDismissRequest = { showMounts = false },
            title = { Text("Mounts (${mounts.size})") },
            text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(mounts.take(100)) { m ->
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Text(m.mountPoint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${m.device} · ${m.fsType} · ${m.options}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMounts = false }) { Text("Close") } },
        )
    }

    toast?.let {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium) {
            Text(it, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(12.dp))
        }
    }
}
