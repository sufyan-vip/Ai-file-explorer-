package com.nexarq.app.tools

import com.nexarq.app.core.FileType
import com.nexarq.app.core.Format
import com.nexarq.app.core.OperationKind
import com.nexarq.app.core.OperationProgress
import com.nexarq.app.core.TypeBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class LargeEntry(val path: String, val name: String, val size: Long, val isDirectory: Boolean)

data class StorageAnalysis(
    val totalBytes: Long,
    val fileCount: Long,
    val dirCount: Long,
    val buckets: List<TypeBucket>,
    val largestFiles: List<LargeEntry>,
    val largestDirs: List<LargeEntry>,
)

/**
 * Efficient storage analyzer. Runs fully offline and off the main thread.
 * Statistics are computed from what the OS actually exposes — no invented numbers.
 */
object StorageAnalyzer {

    suspend fun analyze(root: String, onProgress: (OperationProgress) -> Unit = {}): StorageAnalysis =
        withContext(Dispatchers.IO) {
            val cc = coroutineContext
            val rootFile = File(root)
            var totalBytes = 0L
            var fileCount = 0L
            var dirCount = 0L
            val bucketBytes = mutableMapOf<FileType.Category, Long>()
            val bucketCount = mutableMapOf<FileType.Category, Long>()
            val largestFiles = mutableListOf<LargeEntry>()
            val dirSizes = mutableMapOf<File, Long>()
            val opId = System.currentTimeMillis()
            var scanned = 0L

            fun record(path: String, name: String, size: Long, isDir: Boolean) {
                scanned++
                if (scanned % 200 == 0L) {
                    onProgress(OperationProgress(opId, OperationKind.SCAN, "Scanning $root", path, scanned, 0, totalBytes, 0))
                }
                if (isDir) dirCount++ else {
                    fileCount++
                    totalBytes += size
                    val cat = FileType.category(name, false)
                    bucketBytes[cat] = (bucketBytes[cat] ?: 0) + size
                    bucketCount[cat] = (bucketCount[cat] ?: 0) + 1
                    largestFiles.add(LargeEntry(path, name, size, false))
                    largestFiles.sortByDescending { it.size }
                    if (largestFiles.size > 50) largestFiles.removeAt(largestFiles.size - 1)
                }
            }

            if (rootFile.isFile) {
                record(rootFile.path, rootFile.name, rootFile.length(), false)
            } else if (rootFile.isDirectory) {
                rootFile.walkBottomUp().forEach { f ->
                    cc.ensureActive()
                    if (f.isFile) {
                        record(f.path, f.name, f.length(), false)
                        f.parentFile?.let { dirSizes[it] = (dirSizes[it] ?: 0) + f.length() }
                    } else if (f.isDirectory) {
                        record(f.path, f.name, 0, true)
                    }
                }
            }
            val largestDirs = dirSizes.entries
                .sortedByDescending { it.value }
                .take(50)
                .map { LargeEntry(it.key.path, it.key.name, it.value, true) }

            val buckets = FileType.Category.entries
                .filter { (bucketCount[it] ?: 0) > 0 }
                .map { TypeBucket(it.label, bucketCount[it] ?: 0, bucketBytes[it] ?: 0) }
                .sortedByDescending { it.bytes }

            StorageAnalysis(totalBytes, fileCount, dirCount, buckets, largestFiles, largestDirs)
        }
}
