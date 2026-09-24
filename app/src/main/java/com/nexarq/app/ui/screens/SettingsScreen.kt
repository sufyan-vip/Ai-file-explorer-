package com.nexarq.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexarq.app.data.SettingsRepository
import com.nexarq.app.root.RootManager
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val settings by container.settings.settings.collectAsState(initial = SettingsRepository.AppSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionTitle("Appearance") }
            item {
                SegmentedSetting("Theme", listOf("system", "light", "dark"), settings.theme,
                    labels = listOf("System", "Light", "Dark")) { scope.launch { container.settings.setTheme(it) } }
            }
            item { SwitchSetting("Dynamic colors", settings.dynamicColors) { scope.launch { container.settings.setDynamicColors(it) } } }
            item {
                SegmentedSetting("Default view", listOf("list", "grid"), settings.viewMode,
                    labels = listOf("List", "Grid")) { scope.launch { container.settings.setViewMode(it) } }
            }

            item { SectionTitle("File browser") }
            item { SwitchSetting("Show hidden files", settings.showHidden) { scope.launch { container.settings.setShowHidden(it) } } }
            item { SwitchSetting("Show file extensions", settings.showExtensions) { scope.launch { container.settings.setShowExtensions(it) } } }
            item { SwitchSetting("Folders first", settings.folderFirst) { scope.launch { container.settings.setFolderFirst(it) } } }
            item { SwitchSetting("Confirm before delete", settings.confirmDelete) { scope.launch { container.settings.setConfirmDelete(it) } } }
            item { SwitchSetting("Move deleted files to trash", settings.useTrash) { scope.launch { container.settings.setUseTrash(it) } } }
            item {
                SegmentedSetting("Auto-empty trash", listOf("7", "30", "90"), settings.trashRetentionDays.toString(),
                    labels = listOf("7 days", "30 days", "90 days")) { scope.launch { container.settings.setTrashRetentionDays(it.toInt()) } }
            }
            item { SwitchSetting("Confirm before overwrite", settings.confirmOverwrite) { scope.launch { container.settings.setConfirmOverwrite(it) } } }
            item { SwitchSetting("Recent files history", settings.recentEnabled) { scope.launch { container.settings.setRecentEnabled(it) } } }
            item {
                SegmentedSetting("Sort by", listOf("name", "size", "date", "type"), settings.sortMode,
                    labels = listOf("Name", "Size", "Date", "Type")) { scope.launch { container.settings.setSortMode(it) } }
            }

            item { SectionTitle("Archive") }
            item {
                SegmentedSetting("Default format", listOf("zip", "7z", "tar", "tar.gz"), settings.defaultFormat,
                    labels = listOf("ZIP", "7Z", "TAR", "TAR.GZ")) { scope.launch { container.settings.setDefaultFormat(it) } }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Compression level: ${settings.compressionLevel}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = settings.compressionLevel.toFloat(),
                        onValueChange = { scope.launch { container.settings.setCompressionLevel(it.toInt()) } },
                        valueRange = 0f..9f, steps = 8)
                }
            }

            item { SectionTitle("Root") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Root mode", style = MaterialTheme.typography.bodyLarge)
                        Text("Root detected: ${RootManager.isRooted()}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.rootEnabled, onCheckedChange = { scope.launch { container.settings.setRootEnabled(it) } })
                }
            }

            item { SectionTitle("AI") }
            item {
                Row(Modifier.fillMaxWidth().clickable { navigator.push(Screen.AiCenter) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI Center", style = MaterialTheme.typography.bodyLarge)
                        Text("Providers, chat, privacy controls", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }

            item { SectionTitle("About") }
            item {
                Row(Modifier.fillMaxWidth().clickable { navigator.push(Screen.About) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("About NEXARQ", style = MaterialTheme.typography.bodyLarge)
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SegmentedSetting(label: String, values: List<String>, current: String, labels: List<String>, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.padding(top = 4.dp)) {
            values.forEachIndexed { i, v ->
                FilterChip(selected = current == v, onClick = { onSelect(v) },
                    label = { Text(labels.getOrElse(i) { v }) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
    }
}
