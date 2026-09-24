package com.nexarq.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ClipboardMode { COPY, MOVE }

/**
 * App-wide copy/move clipboard. Persisted only in memory for the session — Android's
 * clipboard can outlive the app and be read by others, so sensitive paths stay internal.
 */
object Clipboard {
    val paths = mutableStateListOf<String>()
    var mode by mutableStateOf(ClipboardMode.COPY)

    fun set(newPaths: List<String>, newMode: ClipboardMode) {
        paths.clear()
        paths.addAll(newPaths)
        mode = newMode
    }

    fun clear() = paths.clear()

    val hasContent: Boolean get() = paths.isNotEmpty()
}
