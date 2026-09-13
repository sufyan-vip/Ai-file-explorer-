package com.nexarq.app.ui.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.Format
import com.nexarq.app.core.StorageInfo
import com.nexarq.app.core.TimeFormat
import com.nexarq.app.root.RootManager
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.Screen
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.defaultStoragePath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val context = LocalContext.current
    val recents = remember { container.recents.all() }
    val bookmarks = remember { container.bookmarks.all() }
    var rootAvailable by remember { mutableStateOf(RootManager.isRooted()) }
    val primary = defaultStoragePath()

    Scaffold(
        topBar = { TopAppBar(title = { Text("NEXARQ") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                StorageCard(primary)
            }

            item {
                Text("Quick access", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip("Internal", Icons.Default.Storage) { navigator.push(Screen.Browser(primary)) }
                    QuickChip("Downloads", Icons.Default.Folder) { navigator.push(Screen.Browser("$primary/Download")) }
                    QuickChip("SD card", Icons.Default.SdStorage) { navigator.push(Screen.Browser("/storage")) }
                    QuickChip("Search", Icons.Default.Search) { navigator.push(Screen.Search) }
                    if (rootAvailable) {
                        QuickChip("Root", Icons.Default.Terminal) { navigator.push(Screen.RootBrowser) }
                    }
                }
            }

            item {
                Text("Tools", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip("Archive", Icons.Default.Archive) { navigator.push(Screen.Tools) }
                    QuickChip("Analyzer", Icons.Default.Dashboard) { navigator.push(Screen.Analyzer) }
                    QuickChip("Duplicates", Icons.Default.ContentCopy) { navigator.push(Screen.Duplicates) }
                    QuickChip("Hash", Icons.Default.Calculate) { navigator.push(Screen.HashTool) }
                    QuickChip("APK", Icons.Default.Security) { navigator.push(Screen.Tools) }
                }
            }

            if (bookmarks.isNotEmpty()) {
                item { Text("Favorites", style = MaterialTheme.typography.titleMedium) }
                items(bookmarks) { b ->
                    Row(Modifier.fillMaxWidth().clickable { navigator.push(Screen.Browser(b.path)) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(b.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                item { Text("Recent files", style = MaterialTheme.typography.titleMedium) }
                items(recents.take(10)) { r ->
                    Row(Modifier.fillMaxWidth().clickable { navigator.push(Screen.Browser(File_parent(r.path))) }
                        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FindInPage, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(TimeFormat.format(r.accessedAt), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                RootStatusBanner(rootAvailable) {
                    RootManager.refresh()
                    rootAvailable = RootManager.isRooted()
                    if (rootAvailable) navigator.push(Screen.RootBrowser)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun File_parent(path: String): String = path.substringBeforeLast('/').ifEmpty { "/" }

@Composable
private fun StorageCard(path: String) {
    val info = remember { StorageInfo.of(path) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            if (info == null) {
                Text("Storage information unavailable", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(path, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Format.bytes(info.usedBytes)} / ${Format.bytes(info.totalBytes)}", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { if (info.totalBytes > 0) (info.usedBytes.toFloat() / info.totalBytes) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                Text("${Format.bytes(info.availableBytes)} free", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RootStatusBanner(rootAvailable: Boolean, onRefresh: () -> Unit) {
    Card(
        onClick = onRefresh,
        colors = CardDefaults.cardColors(containerColor = if (rootAvailable) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Key, null, tint = if (rootAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(start = 12.dp)) {
                Text(if (rootAvailable) "Root available" else "No root detected", style = MaterialTheme.typography.bodyMedium)
                Text(if (rootAvailable) "Tap to browse the filesystem with elevated access" else "Running in normal (SAF) mode",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
