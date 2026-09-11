package com.nexarq.app.core

import java.util.Locale

object Format {
    fun bytes(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024.0 && unit < units.size - 1)
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun speed(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"

    fun eta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0 || remainingBytes <= 0) return "—"
        val seconds = remainingBytes / bytesPerSecond
        return duration(seconds)
    }

    fun duration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "—"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "%d:%02d:%02d".format(h, m, s)
            m > 0 -> "%d:%02d".format(m, s)
            else -> "%ds".format(s)
        }
    }

    fun count(n: Long): String {
        return String.format(Locale.US, "%,d", n)
    }

    fun ratio(compressed: Long, original: Long): String {
        if (original <= 0) return "—"
        val pct = 100.0 * (1.0 - compressed.toDouble() / original.toDouble())
        return String.format(Locale.US, "%.1f%%", pct)
    }
}
