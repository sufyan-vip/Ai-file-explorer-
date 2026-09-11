package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexarq.app.core.Format
import com.nexarq.app.core.Intents
import com.nexarq.app.tools.ApkInfo
import com.nexarq.app.tools.ApkInspector
import com.nexarq.app.ui.Navigator
import com.nexarq.app.ui.components.LoadingState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkInspectorScreen(path: String, navigator: Navigator) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<ApkInfo?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        info = ApkInspector.inspect(context, path)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(path).name) },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { Intents.installApk(context, path) }) { Icon(Icons.Default.InstallMobile, "Install") }
                    IconButton(onClick = { Intents.share(context, path) }) { Icon(Icons.Default.Share, "Share") }
                },
            )
        },
    ) { padding ->
        if (loading) {
            LoadingState("Inspecting APK…")
        } else {
            val i = info
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(i?.label ?: "Unknown app", style = MaterialTheme.typography.titleLarge)
                Text(i?.packageName ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        InfoRow("Version", "${i?.versionName ?: "—"} (${i?.versionCode ?: "—"})")
                        InfoRow("Min SDK", i?.minSdk?.toString() ?: "—")
                        InfoRow("Target SDK", i?.targetSdk?.toString() ?: "—")
                        InfoRow("Size", if (i != null) Format.bytes(i.fileSize) else "—")
                        InfoRow("Install location", i?.installLocation ?: "—")
                        if (!i?.supportedAbis.isNullOrEmpty()) {
                            InfoRow("ABIs", i!!.supportedAbis.sorted().joinToString(", "))
                        }
                    }
                }
                if (!i?.requestedPermissions.isNullOrEmpty()) {
                    Text("Permissions (${i!!.requestedPermissions.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    i.requestedPermissions.forEach { perm ->
                        Text("• $perm", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
