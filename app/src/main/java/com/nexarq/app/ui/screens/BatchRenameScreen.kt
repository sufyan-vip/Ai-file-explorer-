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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexarq.app.tools.BatchRenamer
import com.nexarq.app.tools.RenameMode
import com.nexarq.app.tools.RenamePlan
import com.nexarq.app.tools.RenameItem
import com.nexarq.app.ui.Navigator
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenameScreen(paths: List<String>, navigator: Navigator) {
    val files = remember(paths) { paths.map { File(it) } }
    var mode by remember { mutableStateOf(RenameMode.PREFIX) }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var startNumber by remember { mutableStateOf("1") }
    var padTo by remember { mutableStateOf("2") }
    var keepExtension by remember { mutableStateOf(true) }
    var applied by remember { mutableStateOf<String?>(null) }

    val plan = RenamePlan(
        mode = mode, prefix = prefix, suffix = suffix, find = find, replace = replace,
        startNumber = startNumber.toIntOrNull() ?: 1, padTo = padTo.toIntOrNull() ?: 2,
        keepExtension = keepExtension,
    )
    val preview = remember(mode, prefix, suffix, find, replace, startNumber, padTo, keepExtension) {
        BatchRenamer.preview(files, plan)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch rename") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                RenameMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            when (mode) {
                RenameMode.PREFIX -> OutlinedTextField(prefix, { prefix = it }, label = { Text("Prefix") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                RenameMode.SUFFIX -> OutlinedTextField(suffix, { suffix = it }, label = { Text("Suffix") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                RenameMode.REPLACE -> {
                    OutlinedTextField(find, { find = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(replace, { replace = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                RenameMode.NUMBERED -> {
                    OutlinedTextField(prefix, { prefix = it }, label = { Text("Prefix (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(suffix, { suffix = it }, label = { Text("Suffix (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(startNumber, { startNumber = it }, label = { Text("Start number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(padTo, { padTo = it }, label = { Text("Pad to digits") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = keepExtension, onCheckedChange = { keepExtension = it })
                Text("Keep extension")
            }

            Text("Preview (${preview.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(preview) { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(item.originalName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("→ ${item.newName}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(onClick = {
                val count = BatchRenamer.apply(preview)
                applied = "Renamed $count file(s)"
            }, modifier = Modifier.fillMaxWidth()) { Text("Apply") }
            applied?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
