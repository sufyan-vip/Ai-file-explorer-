package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.OperationStatus
import com.nexarq.app.core.TimeFormat
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.OperationProgressBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(navigator: Navigator) {
    val container = LocalContainer.current ?: return
    val active by container.operations.active.collectAsState()
    var history by remember { mutableStateOf(container.operations.history()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operations") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { container.operations.clearHistory(); history = emptyList() }) {
                        Icon(Icons.Default.ClearAll, "Clear history")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            if (active.isNotEmpty()) {
                item { Text("Active", style = MaterialTheme.typography.titleMedium) }
                items(active, key = { it.operationId }) { op ->
                    OperationProgressBar(op.label, op.currentFile, op.fraction, indeterminate = op.totalBytes == 0L && op.totalFiles == 0L)
                }
            }
            item { Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            if (history.isEmpty()) {
                item { Text("No operations yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(history, key = { it.id }) { op ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(op.label, style = MaterialTheme.typography.bodyMedium)
                        Text("${op.kind.name} · ${op.status.name.lowercase()} · ${TimeFormat.format(op.timestamp)}",
                            style = MaterialTheme.typography.bodySmall, color = when (op.status) {
                                OperationStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                OperationStatus.FAILED -> MaterialTheme.colorScheme.error
                                OperationStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        op.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
