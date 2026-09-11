package com.nexarq.app.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexarq.app.archive.ArchiveEngine
import com.nexarq.app.archive.ArchiveFormat
import com.nexarq.app.core.ArchivePreset
import com.nexarq.app.core.ConflictPolicy
import com.nexarq.app.core.ConflictResolution
import com.nexarq.app.core.Format
import com.nexarq.app.ui.LocalContainer
import com.nexarq.app.ui.components.OperationProgressBar
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CompressDialog(
    sources: List<String>,
    onDone: (String?) -> Unit,
) {
    val container = LocalContainer.current ?: return
    val scope = rememberCoroutineScope()
    val presets = remember { container.presets.all() }
    var name by remember { mutableStateOf(defaultArchiveName(sources)) }
    var format by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var level by remember { mutableStateOf(6) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var outputDir by remember { mutableStateOf(defaultOutputDir(sources)) }
    var preset by remember { mutableStateOf<ArchivePreset?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<com.nexarq.app.core.OperationProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyPreset(p: ArchivePreset) {
        preset = p
        format = ArchiveFormat.entries.firstOrNull { it.id == p.format } ?: ArchiveFormat.ZIP
        level = p.compressionLevel
        if (p.encrypt && password.isBlank()) password = ""
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDone(null) },
        title = { Text("Create archive") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Archive name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(outputDir, { outputDir = it }, label = { Text("Output folder") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                // Presets
                Text("Presets", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    presets.forEach { p ->
                        FilterChip(selected = preset?.id == p.id, onClick = { applyPreset(p) }, label = { Text(p.name) })
                    }
                }

                FormatDropdown(format) { format = it; preset = null }
                Spacer(Modifier.height(8.dp))
                Text("Compression level: $level", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = level.toFloat(), onValueChange = { level = it.toInt(); preset = null },
                    valueRange = 0f..9f, steps = 8,
                )
                if (format == ArchiveFormat.ZIP) {
                    OutlinedTextField(password, { password = it }, label = { Text("Password (optional)") },
                        singleLine = true, leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth())
                    if (password.isNotBlank()) {
                        OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("Confirm password") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                } else if (format == ArchiveFormat.SEVEN_ZIP) {
                    Text("7z encryption is not supported by the built-in engine.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
                progress?.let { p ->
                    OperationProgressBar(label = p.label, detail = p.currentFile, fraction = p.fraction, indeterminate = p.totalBytes == 0L)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !running, onClick = {
                if (name.isBlank()) { error = "Enter an archive name"; return@TextButton }
                if (password.isNotBlank() && password != confirmPassword) { error = "Passwords do not match"; return@TextButton }
                if (format == ArchiveFormat.SEVEN_ZIP && password.isNotBlank()) { error = "7z encryption unsupported"; return@TextButton }
                val fileName = if (name.substringAfterLast('.', "") in listOf("zip", "7z", "tar", "gz", "bz2", "xz", "zst")) name
                else "$name.${format.extensions.first()}"
                val out = File(outputDir, fileName)
                running = true
                scope.launch {
                    runCatching {
                        ArchiveEngine.create(sources, out, ArchiveEngine.CreateOptions(
                            format = format, compressionLevel = level,
                            password = password.toCharArray().takeIf { it.isNotEmpty() },
                        )) { progress = it }
                    }.onSuccess { onDone(out.absolutePath) }
                        .onFailure { error = it.message ?: "Compression failed"; running = false }
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(enabled = !running, onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

@Composable
fun ExtractDialog(
    archivePath: String,
    entryNames: List<String>? = null,
    encrypted: Boolean = false,
    onDone: (String?) -> Unit,
) {
    val container = LocalContainer.current ?: return
    val scope = rememberCoroutineScope()
    val parent = File(archivePath).parentFile?.absolutePath ?: "/"
    var destination by remember { mutableStateOf("$parent/${File(archivePath).nameWithoutExtension}") }
    var password by remember { mutableStateOf("") }
    var conflict by remember { mutableStateOf(ConflictPolicy.ASK) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<com.nexarq.app.core.OperationProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!running) onDone(null) },
        title = { Text("Extract archive") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(destination, { destination = it }, label = { Text("Extract to") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("If a file exists:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(ConflictPolicy.ASK to "Ask", ConflictPolicy.OVERWRITE to "Overwrite",
                        ConflictPolicy.SKIP to "Skip", ConflictPolicy.RENAME to "Rename").forEach { (p, label) ->
                        FilterChip(selected = conflict == p, onClick = { conflict = p }, label = { Text(label) })
                    }
                }
                if (encrypted) {
                    OutlinedTextField(password, { password = it }, label = { Text("Password") },
                        singleLine = true, leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth())
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
                progress?.let { p ->
                    OperationProgressBar(label = p.label, detail = p.currentFile, fraction = p.fraction, indeterminate = p.totalBytes == 0L)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !running, onClick = {
                running = true
                scope.launch {
                    runCatching {
                        ArchiveEngine.extract(
                            path = archivePath,
                            destinationDir = destination,
                            password = password.toCharArray().takeIf { it.isNotEmpty() },
                            entryFilter = { entry -> entryNames == null || entry.path in entryNames },
                            conflict = conflict,
                            resolver = { ConflictResolution.SKIP },
                        ) { progress = it }
                    }.onSuccess { onDone(destination) }
                        .onFailure { error = it.message ?: "Extraction failed"; running = false }
                }
            }) { Text("Extract") }
        },
        dismissButton = { TextButton(enabled = !running, onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun FormatDropdown(current: ArchiveFormat, onSelect: (ArchiveFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val formats = listOf(
        ArchiveFormat.ZIP, ArchiveFormat.SEVEN_ZIP, ArchiveFormat.TAR,
        ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_ZSTD,
        ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ, ArchiveFormat.ZSTD,
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.id.uppercase(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            formats.forEach { f ->
                DropdownMenuItem(text = { Text(f.id) }, onClick = { onSelect(f); expanded = false })
            }
        }
    }
}

private fun defaultArchiveName(sources: List<String>): String {
    val first = sources.firstOrNull()?.substringAfterLast('/') ?: "archive"
    val base = first.substringBeforeLast('.').ifEmpty { "archive" }
    val date = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
    return "$base-$date"
}

private fun defaultOutputDir(sources: List<String>): String {
    val first = sources.firstOrNull() ?: return "/storage/emulated/0/Download"
    val f = File(first)
    return if (f.isDirectory) f.parentFile?.absolutePath ?: f.absolutePath else f.parentFile?.absolutePath ?: f.absolutePath
}

fun String?.orEmptyDefault(): String = this ?: ""
