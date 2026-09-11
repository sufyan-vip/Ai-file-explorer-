package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexarq.app.tools.FileCompare
import com.nexarq.app.tools.FileComparison
import com.nexarq.app.tools.ComparisonLine
import com.nexarq.app.ui.Navigator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCompareScreen(left: String, right: String, navigator: Navigator) {
    val scope = rememberCoroutineScope()
    var leftPath by remember { mutableStateOf(left) }
    var rightPath by remember { mutableStateOf(right) }
    var result by remember { mutableStateOf<FileComparison?>(null) }
    var running by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare files") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            OutlinedTextField(leftPath, { leftPath = it }, label = { Text("First file") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rightPath, { rightPath = it }, label = { Text("Second file") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.Button(onClick = {
                scope.launch { running = true; result = FileCompare.compare(leftPath, rightPath); running = false }
            }, enabled = !running, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Compare") }

            val r = result
            when {
                running -> com.nexarq.app.ui.components.LoadingState("Comparing…")
                r == null -> com.nexarq.app.ui.components.EmptyState("Choose two files")
                else -> {
                    Text(FileCompare.describe(r), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    if (r.textDiff != null) {
                        Text("Text diff", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        LazyColumn(Modifier.weight(1f)) {
                            items(r.textDiff) { line -> DiffLine(line) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLine(line: ComparisonLine) {
    val color = when {
        line.text.startsWith("+ ") -> MaterialTheme.colorScheme.primary
        line.text.startsWith("- ") -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(line.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color, modifier = Modifier.fillMaxWidth())
}
