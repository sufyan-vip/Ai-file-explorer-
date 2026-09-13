package com.nexarq.app.tools

import com.nexarq.app.core.DuplicateGroup
import com.nexarq.app.core.Format
import com.nexarq.app.core.OperationKind
import com.nexarq.app.core.OperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class DuplicateScanProgress(
    val scanned: Long = 0,
    val total: Long = 0,
    val groups: Int = 0,
    val phase: Phase = Phase.SIZING,
) {
    enum class Phase { SIZING, HASHING, DONE }
}

/**
 * Two-pass duplicate finder: group by size first, then confirm with SHA-256 hashing.
 * Nothing is ever deleted automatically — the caller presents the groups for review.
 */
object DuplicateFinder {

    suspend fun find(
        roots: List<String>,
        onProgress: (DuplicateScanProgress) -> Unit = {},
        onOperation: (OperationProgress) -> Unit = {},
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        // Pass 1: collect files and group by size
        val bySize = mutableMapOf<Long, MutableList<File>>()
        var totalFiles = 0L
        for (root in roots) {
            val rootFile = File(root)
            if (!rootFile.exists()) continue
            if (rootFile.isFile) {
                bySize.getOrPut(rootFile.length()) { mutableListOf() }.add(rootFile)
                totalFiles++
            } else {
                rootFile.walkTopDown().forEach {
                    cc.ensureActive()
                    if (it.isFile && it.length() > 0) {
                        bySize.getOrPut(it.length()) { mutableListOf() }.add(it)
                        totalFiles++
                    }
                }
            }
        }
        val candidates = bySize.filterValues { it.size > 1 }
        val toHash = candidates.values.sumOf { it.size }
        var hashed = 0L
        var groups = 0

        onProgress(DuplicateScanProgress(0, totalFiles, 0, DuplicateScanProgress.Phase.HASHING))

        val results = mutableListOf<DuplicateGroup>()
        for ((size, files) in candidates) {
            val byHash = mutableMapOf<String, MutableList<File>>()
            for (f in files) {
                cc.ensureActive()
                val digest = hashFile(f) { p -> onOperation(p) }
                byHash.getOrPut(digest) { mutableListOf() }.add(f)
                hashed++
                if (hashed % 20 == 0L) onProgress(DuplicateScanProgress(hashed, toHash.toLong(), groups, DuplicateScanProgress.Phase.HASHING))
            }
            for ((hash, dups) in byHash) {
                if (dups.size > 1) {
                    results.add(DuplicateGroup(hash, size, dups))
                    groups++
                }
            }
        }
        onProgress(DuplicateScanProgress(toHash.toLong(), toHash.toLong(), groups, DuplicateScanProgress.Phase.DONE))
        results.sortedByDescending { it.size * (it.files.size - 1) }
    }

    fun wastedBytes(groups: List<DuplicateGroup>): Long =
        groups.sumOf { it.size * (it.files.size - 1) }

    private suspend fun hashFile(file: File, onOp: (OperationProgress) -> Unit): String {
        val cc = coroutineContext
        val md = MessageDigest.getInstance("SHA-256")
        val opId = System.currentTimeMillis()
        var processed = 0L
        FileInputStream(file).use { input ->
            val buf = ByteArray(1024 * 1024)
            var r: Int
            while (input.read(buf).also { r = it } > 0) {
                cc.ensureActive()
                md.update(buf, 0, r)
                processed += r
                onOp(OperationProgress(opId, OperationKind.HASH, "Hashing duplicates", file.name, 0, 0, processed, file.length()))
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

fun String.toPrettySize(): String = Format.bytes(toLongOrNull() ?: 0)
