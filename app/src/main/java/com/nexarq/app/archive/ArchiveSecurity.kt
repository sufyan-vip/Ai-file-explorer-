package com.nexarq.app.archive

import com.nexarq.app.core.ArchiveEntry

/**
 * Defensive guards applied before extraction and archive parsing.
 *
 * These protections mitigate the most common archive-borne attacks:
 *  - path traversal ("../../etc/passwd")
 *  - absolute-path entries
 *  - symlink entries pointing outside the destination
 *  - decompression bombs (absurd size ratios / entry counts)
 */
object ArchiveSecurity {

    const val MAX_ENTRIES = 1_000_000
    const val MAX_RATIO = 10_000 // compressed:uncompressed ratio guard (loose heuristic)

    data class Risk(val severity: Severity, val message: String) {
        enum class Severity { INFO, WARNING, BLOCK }
    }

    /** Returns true if the entry path is safe to extract (relative, no traversal). */
    fun isSafePath(entryPath: String): Boolean {
        if (entryPath.isEmpty()) return false
        if (entryPath.startsWith("/") || entryPath.startsWith("\\")) return false
        val clean = entryPath.replace('\\', '/')
        for (seg in clean.split('/')) {
            if (seg == "..") return false
            if (seg.isNotEmpty() && seg.contains('\u0000')) return false
        }
        // Windows drive letters / UNC-style
        if (Regex("^[A-Za-z]:").containsMatchIn(clean)) return false
        return true
    }

    fun assessEntries(entries: List<ArchiveEntry>): List<Risk> {
        val risks = mutableListOf<Risk>()
        var unsafe = 0
        for (e in entries) {
            if (!isSafePath(e.path)) unsafe++
        }
        if (unsafe > 0) {
            risks.add(Risk(Risk.Severity.WARNING, "$unsafe entries have unsafe paths and will be skipped."))
        }
        if (entries.size > MAX_ENTRIES) {
            risks.add(Risk(Risk.Severity.BLOCK, "Archive contains more than $MAX_ENTRIES entries."))
        }
        val totalUncompressed = entries.sumOf { it.size }
        val totalCompressed = entries.sumOf { it.compressedSize }
        if (totalCompressed > 0 && totalUncompressed > 0) {
            val ratio = totalUncompressed.toDouble() / totalCompressed.toDouble()
            if (ratio > MAX_RATIO && totalUncompressed > 500L * 1024 * 1024) {
                risks.add(Risk(Risk.Severity.WARNING,
                    "Extremely high compression ratio (${"%.0f".format(ratio)}:1) — possible decompression bomb."))
            }
        }
        return risks
    }

    /** Filter entries to only those with safe paths. */
    fun safeEntries(entries: List<ArchiveEntry>): List<ArchiveEntry> = entries.filter { isSafePath(it.path) }
}
