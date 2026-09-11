package com.nexarq.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexarq.app.ui.Navigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navigator: Navigator) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("NEXARQ", style = MaterialTheme.typography.headlineMedium)
            Text("Archive & File Power Suite", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))

            Text("Privacy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                "NEXARQ is privacy-first. All file operations are local. Nothing is uploaded unless you " +
                "explicitly configure an AI provider and send content yourself. There is no analytics, no " +
                "advertising SDK, and no hidden telemetry. AI API keys are stored encrypted in the Android Keystore.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
            )

            Text("Open-source components", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            listOf(
                "Apache Commons Compress (Apache-2.0)",
                "XZ for Java (public domain)",
                "zip4j (Apache-2.0)",
                "zstd-jni (BSD)",
                "OkHttp (Apache-2.0)",
                "Jetpack Compose & AndroidX (Apache-2.0)",
                "Coil (Apache-2.0)",
                "kotlinx.serialization (Apache-2.0)",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }

            Text("Limitations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                "• Root features require a rooted device and are disabled otherwise.\n" +
                "• 7z archive encryption (writing) is not supported by the built-in engine; use encrypted ZIP.\n" +
                "• Android scoped storage restricts direct access to other apps' private data without root.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp),
            )

            Text("NEXARQ is an original implementation and is not affiliated with ZArchiver.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp))
        }
    }
}
