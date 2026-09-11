package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.FileItem
import com.nexarq.app.core.FileType
import com.nexarq.app.core.Format
import com.nexarq.app.core.TimeFormat
import com.nexarq.app.search.SearchEngine
import com.nexarq.app.search.SearchQuery
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.EmptyState
import com.nexarq.app.ui.components.fileIcon
import com.nexarq.app.ui.defaultStoragePath
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var term by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<FileType.Category?>(null) }
    var results by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf(defaultStoragePath()) }

    fun run() {
        scope.launch {
            searching = true; searched = false
            val q = SearchQuery(
                root = root, term = term,
                extension = extension.ifBlank { null },
                category = category,
            )
            val r = SearchEngine.search(q)
            results = r.items
            truncated = r.truncated
            searched = true
            searching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(term, { term = it }, label = { Text("Name contains") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(extension, { extension = it }, label = { Text("Extension") }, singleLine = true,
                    modifier = Modifier.weight(0.4f))
                OutlinedTextField(root, { root = it }, label = { Text("Root") }, singleLine = true,
                    modifier = Modifier.weight(0.6f))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FileType.Category.entries.filter { it != FileType.Category.FOLDER }.take(6).forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c },
                        label = { Text(c.label) })
                }
            }
            androidx.compose.material3.Button(onClick = { run() }, enabled = !searching, modifier = Modifier.fillMaxWidth()) {
                Text(if (searching) "Searching…" else "Search")
            }

            when {
                searching -> com.nexarq.app.ui.components.LoadingState("Searching…")
                !searched -> EmptyState("Enter a query to search")
                results.isEmpty() -> EmptyState("No results")
                else -> {
                    if (truncated) Text("Showing first ${results.size} results", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn {
                        items(results, key = { it.path }) { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                androidx.compose.material3.Icon(fileIcon(item.name, item.isDirectory), null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${if (item.isDirectory) "Folder" else Format.bytes(item.size)} · ${TimeFormat.format(item.modified)}\n${item.path}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
