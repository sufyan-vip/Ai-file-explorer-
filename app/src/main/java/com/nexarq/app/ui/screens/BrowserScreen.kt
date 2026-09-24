package com.nexarq.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.archive.ArchiveEngine
import com.nexarq.app.core.FileItem
import com.nexarq.app.core.FileSystem
import com.nexarq.app.core.FileType
import com.nexarq.app.core.Format
import com.nexarq.app.core.Intents
import com.nexarq.app.core.OperationKind
import com.nexarq.app.core.OperationProgress
import com.nexarq.app.core.OperationStatus
import com.nexarq.app.core.TimeFormat
import com.nexarq.app.data.SettingsRepository
import com.nexarq.app.tools.CryptoFile
import com.nexarq.app.trash.TrashManager
import com.nexarq.app.ui.Clipboard
import com.nexarq.app.ui.ClipboardMode
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.Screen
import com.nexarq.app.ui.components.ConfirmDialog
import com.nexarq.app.ui.components.ErrorState
import com.nexarq.app.ui.components.LoadingState
import com.nexarq.app.ui.components.OperationProgressBar
import com.nexarq.app.ui.components.fileIcon
import com.nexarq.app.ui.dialogs.CompressDialog
import com.nexarq.app.ui.dialogs.ExtractDialog
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(path: String, navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = SettingsRepository.AppSettings())
    val activeOps by container.operations.active.collectAsState()

    var currentPath by remember { mutableStateOf(path) }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var showHidden by remember { mutableStateOf(settings.showHidden) }
    var viewMode by remember { mutableStateOf(settings.viewMode) }
    var sortMode by remember { mutableStateOf(settings.sortMode) }

    var menuOpen by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var showCompress by remember { mutableStateOf(false) }
    var showExtract by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var jumpValue by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var newNameValue by remember { mutableStateOf("") }
    var showProperties by remember { mutableStateOf<FileItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true; error = null
            runCatching { FileSystem.listDirectory(currentPath, showHidden) }
                .onSuccess { items = sortItems(it, sortMode, settings.folderFirst) }
                .onFailure { error = it.message ?: "Cannot read directory" }
            loading = false
        }
    }

    LaunchedEffect(currentPath, showHidden, sortMode, navigator.current) { reload() }

    fun runOp(kind: OperationKind, label: String, block: suspend ((OperationProgress) -> Unit) -> Unit, after: () -> Unit = {}) {
        scope.launch {
            try {
                block { p -> container.operations.report(p) }
                after()
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    toast = e.message ?: "Operation failed"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Files", style = MaterialTheme.typography.titleMedium)
                        Text(currentPath, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { FileSystem.parentOf(currentPath)?.let { currentPath = it; selection = emptySet() } }) {
                        Icon(Icons.Default.ArrowUpward, "Up")
                    }
                },
                actions = {
                    IconButton(onClick = { navigator.push(Screen.Search) }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = { viewMode = if (viewMode == "list") "grid" else "list" }) {
                        Icon(if (viewMode == "list") Icons.Default.GridView else Icons.Default.ViewList, "View")
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
                            onClick = { showHidden = !showHidden; menuOpen = false })
                        DropdownMenuItem(text = { Text("Jump to path…") }, onClick = { showJump = true; menuOpen = false })
                        DropdownMenuItem(text = { Text("New folder") }, onClick = { showNewFolder = true; menuOpen = false })
                        DropdownMenuItem(text = { Text("New file") }, onClick = { showNewFile = true; menuOpen = false })
                        DropdownMenuItem(text = { Text("Sort by name") }, onClick = { sortMode = "name"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Sort by size") }, onClick = { sortMode = "size"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Sort by date") }, onClick = { sortMode = "date"; menuOpen = false })
                        DropdownMenuItem(text = { Text("Sort by type") }, onClick = { sortMode = "type"; menuOpen = false })
                        if (Clipboard.hasContent) {
                            DropdownMenuItem(text = { Text("Paste (${Clipboard.paths.size} items)") }, onClick = { paste(); menuOpen = false })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewFolder = true },
                icon = { Icon(Icons.Default.CreateNewFolder, null) },
                text = { Text("New") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingState("Loading…")
                error != null -> ErrorState(error ?: "")
                else -> {
                    if (viewMode == "grid") {
                        LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(items, key = { it.path }) { item -> GridItem(item, selection, onOpen = { open(item) }, onSelect = { toggleSelect(item.path) }) }
                        }
                    } else {
                        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
                            items(items, key = { it.path }) { item ->
                                FileRow(item, selection.contains(item.path),
                                    onClick = { open(item) },
                                    onLongClick = { toggleSelect(item.path) })
                            }
                        }
                    }
                }
            }

            // Active operations
            activeOps.forEach { op ->
                Column(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    OperationProgressBar(op.label, op.currentFile, op.fraction, indeterminate = op.totalBytes == 0L && op.totalFiles == 0L)
                }
            }

            // Selection action bar
            if (selection.isNotEmpty()) {
                val singleFile = selection.singleOrNull()?.let { File(it) }?.takeIf { it.isFile }
                SelectionBar(
                    count = selection.size,
                    onCancel = { selection = emptySet() },
                    onCopy = { Clipboard.set(selection.toList(), ClipboardMode.COPY); toast = "Copied to clipboard"; selection = emptySet() },
                    onMove = { Clipboard.set(selection.toList(), ClipboardMode.MOVE); toast = "Move queue ready"; selection = emptySet() },
                    onDelete = { confirmDelete = true },
                    onShare = { Intents.shareMultiple(context, selection.toList()); selection = emptySet() },
                    onCompress = { showCompress = true },
                    onEncrypt = if (singleFile != null && !CryptoFile.isEncryptedFile(singleFile.name)) {
                        {
                            navigator.push(Screen.Crypto(singleFile.absolutePath, encrypt = true))
                            selection = emptySet()
                        }
                    } else null,
                    onDecrypt = if (singleFile != null && CryptoFile.isEncryptedFile(singleFile.name)) {
                        {
                            navigator.push(Screen.Crypto(singleFile.absolutePath, encrypt = false))
                            selection = emptySet()
                        }
                    } else null,
                )
            }
        }
    }

    // ---- dialogs ----
    if (showNewFolder) {
        NameDialog(title = "New folder", initial = "", onConfirm = { name ->
            runOp(OperationKind.OTHER, "Create folder") { _ ->
                FileSystem.mkdir(File(currentPath, name).absolutePath)
            }
            showNewFolder = false
        }, onDismiss = { showNewFolder = false })
    }
    if (showNewFile) {
        NameDialog(title = "New file", initial = "", onConfirm = { name ->
            runOp(OperationKind.OTHER, "Create file") { _ ->
                FileSystem.createFile(File(currentPath, name).absolutePath)
            }
            showNewFile = false
        }, onDismiss = { showNewFile = false })
    }
    if (showCompress) {
        CompressDialog(sources = selection.toList().ifEmpty { listOf(currentPath) }, onDone = {
            showCompress = false
            if (it != null) reload()
        })
    }
    if (showExtract) {
        ExtractDialog(archivePath = selection.firstOrNull() ?: currentPath, onDone = {
            showExtract = false
            if (it != null) reload()
        })
    }
    if (showJump) {
        NameDialog(title = "Jump to path", initial = currentPath, onConfirm = { value ->
            val norm = FileSystem.normalizePath(value)
            if (File(norm).isDirectory) { currentPath = norm } else { toast = "Not a directory" }
            showJump = false
        }, onDismiss = { showJump = false })
    }
    if (confirmDelete) {
        val useTrash = settings.useTrash
        ConfirmDialog(
            title = if (useTrash) "Move ${selection.size} item(s) to trash?" else "Delete ${selection.size} item(s)?",
            message = (if (useTrash) "The selected files and folders will be moved to the trash. You can restore them from Tools → Trash bin."
                else "This will permanently delete the selected files and folders.") +
                    "\n\n" + selection.take(5).joinToString("\n") { "• $it" },
            confirmLabel = if (useTrash) "Move to trash" else "Delete", destructive = !useTrash,
            onConfirm = {
                val targets = selection.toList()
                confirmDelete = false
                selection = emptySet()
                runOp(OperationKind.DELETE, if (useTrash) "Moving to trash" else "Deleting") { report ->
                    if (useTrash) TrashManager.moveToTrash(context, container.trash, targets) { report(it) }
                    else FileSystem.delete(targets) { report(it) }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    showRename?.let { target ->
        NameDialog(title = "Rename", initial = File(target).name, onConfirm = { newName ->
            runOp(OperationKind.RENAME, "Rename") { _ ->
                FileSystem.rename(target, File(File(target).parentFile, newName).absolutePath)
            }
            showRename = null
        }, onDismiss = { showRename = null })
    }
    showProperties?.let { item ->
        PropertiesDialog(item) { showProperties = null }
    }
    toast?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(2000)
            toast = null
        }
        Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(16.dp)) {
            Text(it, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(12.dp))
        }
    }

    // ---- helpers ----
    fun toggleSelect(p: String) {
        selection = if (p in selection) selection - p else selection + p
    }

    fun open(item: FileItem) {
        if (selection.isNotEmpty()) { toggleSelect(item.path); return }
        container.recents.record(item.path, item.name)
        when {
            item.isDirectory -> currentPath = item.path
            ArchiveEngine.isArchiveFile(item.name) -> navigator.push(Screen.ArchiveViewer(item.path))
            FileType.isImage(item.extension) -> navigator.push(Screen.ImagePreview(item.path))
            FileType.isAudio(item.extension) -> navigator.push(Screen.AudioPlayer(item.path))
            FileType.isTextLike(item.extension) || item.extension in setOf("txt", "md", "log", "json", "xml", "csv") ->
                navigator.push(Screen.TextEditor(item.path))
            item.extension == "apk" -> navigator.push(Screen.ApkInspector(item.path))
            else -> Intents.openWith(context, item.path)
        }
    }

    fun paste() {
        val mode = Clipboard.mode
        val sources = Clipboard.paths.toList()
        runOp(if (mode == ClipboardMode.MOVE) OperationKind.MOVE else OperationKind.COPY,
            if (mode == ClipboardMode.MOVE) "Moving" else "Copying") { report ->
            if (mode == ClipboardMode.MOVE) {
                FileSystem.move(sources, currentPath, conflict = com.nexarq.app.core.ConflictPolicy.RENAME) { report(it) }
            } else {
                FileSystem.copy(sources, currentPath, conflict = com.nexarq.app.core.ConflictPolicy.RENAME) { report(it) }
            }
            Clipboard.clear()
        }
    }
}

private fun sortItems(items: List<FileItem>, mode: String, folderFirst: Boolean): List<FileItem> {
    val comparator: Comparator<FileItem> = when (mode) {
        "size" -> compareBy { it.size }
        "date" -> compareBy { it.modified }
        "type" -> compareBy { it.extension }.thenBy { it.name.lowercase() }
        else -> compareBy { it.name.lowercase() }
    }
    val dirComparator = if (folderFirst) compareByDescending<FileItem> { it.isDirectory } else compareBy { false }
    return items.sortedWith(dirComparator.then(comparator))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(item: FileItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(fileIcon(item.name, item.isDirectory), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(if (item.isDirectory) "Folder" else Format.bytes(item.size))
                            append(" · ")
                            append(TimeFormat.format(item.modified))
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridItem(item: FileItem, selection: Set<String>, onOpen: () -> Unit, onSelect: () -> Unit) {
    val selected = item.path in selection
    Card(
        onClick = onOpen,
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(fileIcon(item.name, item.isDirectory), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int, onCancel: () -> Unit, onCopy: () -> Unit, onMove: () -> Unit,
    onDelete: () -> Unit, onShare: () -> Unit, onCompress: () -> Unit,
    onEncrypt: (() -> Unit)? = null, onDecrypt: (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Check, "Cancel", tint = MaterialTheme.colorScheme.primary) }
            Text("$count selected", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
            IconButton(onClick = onMove) { Icon(Icons.Default.DriveFileMove, "Move") }
            IconButton(onClick = onCompress) { Icon(Icons.Default.Archive, "Compress") }
            onEncrypt?.let { IconButton(onClick = it) { Icon(Icons.Default.Lock, "Encrypt") } }
            onDecrypt?.let { IconButton(onClick = it) { Icon(Icons.Default.LockOpen, "Decrypt") } }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text("OK") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PropertiesDialog(item: FileItem, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                PropRow("Path", item.path)
                PropRow("Type", if (item.isDirectory) "Folder" else FileType.category(item.name, false).label)
                PropRow("Size", if (item.isDirectory) "—" else Format.bytes(item.size))
                PropRow("Modified", TimeFormat.format(item.modified))
                PropRow("MIME", item.mimeType ?: FileType.mimeType(item.name))
                if (item.isSymlink) PropRow("Symlink", "true → ${item.target ?: ""}")
                item.permissions?.let { PropRow("Permissions", it) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
