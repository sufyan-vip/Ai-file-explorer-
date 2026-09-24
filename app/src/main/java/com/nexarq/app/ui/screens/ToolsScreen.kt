package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.Screen
import com.nexarq.app.ui.defaultStoragePath

private data class Tool(val title: String, val icon: ImageVector, val route: Screen)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navigator: Navigator) {
    val tools = listOf(
        Tool("Storage analyzer", Icons.Default.Dashboard, Screen.Analyzer),
        Tool("Duplicate finder", Icons.Default.ContentCopy, Screen.Duplicates),
        Tool("Hash calculator", Icons.Default.Calculate, Screen.HashTool),
        Tool("Trash bin", Icons.Default.Delete, Screen.Trash),
        Tool("Wi-Fi transfer", Icons.Default.Wifi, Screen.WifiTransfer(defaultStoragePath())),
        Tool("Batch rename", Icons.Default.DriveFileRenameOutline, Screen.Search),
        Tool("File compare", Icons.Default.Compare, Screen.Search),
        Tool("APK inspector", Icons.Default.Security, Screen.Search),
        Tool("Hex viewer", Icons.Default.FindInPage, Screen.Search),
        Tool("Operations", Icons.Default.Timeline, Screen.Operations),
        Tool("Root browser", Icons.Default.Terminal, Screen.RootBrowser),
        Tool("About & licenses", Icons.Default.Archive, Screen.About),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            items(tools) { tool ->
                Card(onClick = { navigator.push(tool.route) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Text(tool.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}
