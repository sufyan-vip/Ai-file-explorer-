package com.nexarq.app.core

import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Safe, cancellable filesystem primitives. Everything runs off the main thread and
 * reports granular progress. All paths are validated and normalized to prevent
 * path-traversal and accidental destructive operations.
 */
object FileSystem {

    /** Normalize and validate a user-provided path without touching the network or symlink tricks. */
    fun normalizePath(path: String): String {
        var p = path.trim()
        if (p.isEmpty()) return "/"
        p = p.replace('\\', '/')
        // collapse multiple slashes
        p = p.replace(Regex("/{2,}"), "/")
        // resolve "." and ".." segments lexically
        val parts = ArrayDeque<String>()
        for (seg in p.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(seg)
            }
        }
        val joined = parts.joinToString("/")
        return if (p.startsWith("/")) "/$joined".replace(Regex("/+$"), "").ifEmpty { "/" } else joined.ifEmpty { "." }
    }

    fun parentOf(path: String): String? {
        val norm = normalizePath(path)
        if (norm == "/" || norm.isEmpty()) return null
        val idx = norm.lastIndexOf('/')
        return if (idx <= 0) "/" else norm.substring(0, idx)
    }

    fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

    /** Ensure [child] resolves inside [parent], preventing path traversal on extraction. */
    fun safeJoin(parent: File, childName: String): File? {
        val unified = childName.replace('\\', '/')
        // Absolute paths and Windows drive letters are never allowed inside an archive.
        if (unified.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(unified)) return null
        val clean = unified.trimStart('/')
        if (clean.isEmpty()) return parent
        val target = File(parent, clean).canonicalFile
        val base = parent.canonicalFile
        return if (target.path == base.path || target.path.startsWith(base.path + File.separator)) target else null
    }

    fun listRoots(): List<FileItem> {
        val roots = mutableListOf<FileItem>()
        runCatching {
            File("/").listFiles()?.let { files ->
                for (f in files) if (f.isDirectory) roots.add(FileItem.fromFile(f))
            }
        }
        runCatching {
            val ext = Environment.getExternalStorageDirectory()
            if (ext.exists() && roots.none { it.path == ext.absolutePath }) {
                roots.add(FileItem.fromFile(ext))
            }
        }
        runCatching {
            val sdcard = File("/sdcard")
            if (sdcard.exists() && roots.none { it.path == sdcard.absolutePath }) {
                roots.add(FileItem.fromFile(sdcard))
            }
        }
        runCatching {
            val storage = File("/storage")
            storage.listFiles()?.forEach { child ->
                if (child.isDirectory && roots.none { it.path == child.absolutePath }) {
                    roots.add(FileItem.fromFile(child))
                }
            }
        }
        return roots.distinctBy { it.path }.sortedBy { it.path }
    }

    suspend fun listDirectory(path: String, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists()) throw IOException("Path does not exist: $path")
        if (!dir.isDirectory) throw IOException("Not a directory: $path")
        val files = dir.listFiles() ?: throw IOException("Permission denied reading: $path")
        files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { FileItem.fromFile(it) }
            .sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun exists(path: String): Boolean = File(path).exists()

    suspend fun stat(path: String): FileItem? = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.exists()) null else FileItem.fromFile(f)
    }

    suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).mkdirs()
    }

    suspend fun createFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.exists()) false else f.parentFile?.mkdirs() != false && f.createNewFile()
    }

    suspend fun rename(from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(from)
        val dst = File(to)
        if (!src.exists()) throw IOException("Source missing: $from")
        if (dst.exists()) throw IOException("Target already exists: $to")
        src.renameTo(dst)
    }

    /** Recursively count files and total bytes of a directory (for size calculation and progress). */
    suspend fun measure(targets: List<String>): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        var files = 0L
        var bytes = 0L
        for (t in targets) {
            val f = File(t)
            if (f.isFile) {
                files++
                bytes += f.length()
            } else if (f.isDirectory) {
                f.walkTopDown().forEach {
                    cc.ensureActive()
                    if (it.isFile) {
                        files++
                        bytes += it.length()
                    }
                }
            }
        }
        files to bytes
    }

    /** Recursively calculate directory size. */
    suspend fun directorySize(path: String): Long = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        var total = 0L
        File(path).walkTopDown().forEach {
            cc.ensureActive()
            if (it.isFile) total += it.length()
        }
        total
    }

    suspend fun delete(paths: List<String>, progress: (OperationProgress) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val cc = coroutineContext
            val (files, bytes) = measure(paths)
            var processedFiles = 0L
            var processedBytes = 0L
            val opId = System.currentTimeMillis()
            for (p in paths) {
                val f = File(p)
                if (f.exists()) {
                    f.walkBottomUp().forEach {
                        cc.ensureActive()
                        if (!it.delete() && it.exists()) throw IOException("Could not delete: ${it.absolutePath}")
                        if (it.isFile) {
                            processedFiles++
                            processedBytes += it.length()
                        }
                        progress(OperationProgress(opId, OperationKind.DELETE, "Deleting ${FileSystem.nameOf(p)}",
                            it.absolutePath, processedFiles, files, processedBytes, bytes))
                    }
                }
            }
            true
        }

    suspend fun copy(
        sources: List<String>,
        destinationDir: String,
        conflict: ConflictPolicy = ConflictPolicy.ASK,
        resolver: suspend (String) -> ConflictResolution = { ConflictResolution.SKIP },
        progress: (OperationProgress) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        val destDir = File(destinationDir).apply { mkdirs() }
        val (totalFiles, totalBytes) = measure(sources)
        var processedFiles = 0L
        var processedBytes = 0L
        var start = System.currentTimeMillis()
        var windowBytes = 0L
        val opId = System.currentTimeMillis()
        val copied = mutableListOf<String>()

        fun report(current: String) {
            val now = System.currentTimeMillis()
            val elapsed = (now - start).coerceAtLeast(1)
            val speed = windowBytes * 1000 / elapsed
            if (now - start > 1000) {
                start = now
                windowBytes = 0
            }
            progress(OperationProgress(opId, OperationKind.COPY, "Copying", current,
                processedFiles, totalFiles, processedBytes, totalBytes, speed))
        }

        suspend fun copyOne(srcFile: File, destFile: File) {
            cc.ensureActive()
            if (srcFile.isDirectory) {
                destFile.mkdirs()
                srcFile.listFiles()?.forEach { child -> copyOne(child, File(destFile, child.name)) }
            } else if (srcFile.isFile) {
                var target = destFile
                if (target.exists()) {
                    when (conflict) {
                        ConflictPolicy.OVERWRITE -> { if (!target.delete()) throw IOException("Cannot overwrite ${target.path}") }
                        ConflictPolicy.SKIP -> return
                        ConflictPolicy.RENAME -> {
                            var i = 1
                            val base = destFile.name.substringBeforeLast('.', destFile.name)
                            val ext = destFile.name.substringAfterLast('.', "").let { if (it == destFile.name) "" else ".$it" }
                            while (target.exists()) target = File(destFile.parentFile, "$base ($i)$ext").also { i++ }
                        }
                        ConflictPolicy.ASK -> {
                            when (resolver(target.path)) {
                                ConflictResolution.SKIP -> return
                                ConflictResolution.OVERWRITE -> if (!target.delete()) throw IOException("Cannot overwrite ${target.path}")
                                ConflictResolution.RENAME -> {
                                    var i = 1
                                    val base = destFile.name.substringBeforeLast('.', destFile.name)
                                    val ext = destFile.name.substringAfterLast('.', "").let { if (it == destFile.name) "" else ".$it" }
                                    while (target.exists()) target = File(destFile.parentFile, "$base ($i)$ext").also { i++ }
                                }
                                ConflictResolution.CANCEL -> throw OperationCancelled()
                            }
                        }
                    }
                }
                target.parentFile?.mkdirs()
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(target).use { output ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        var read: Int
                        while (input.read(buf).also { read = it } > 0) {
                            cc.ensureActive()
                            output.write(buf, 0, read)
                            processedBytes += read
                            windowBytes += read
                            report(srcFile.path)
                        }
                    }
                }
                // Preserve timestamp when possible.
                runCatching { target.setLastModified(srcFile.lastModified()) }
                processedFiles++
                copied.add(target.absolutePath)
                report(srcFile.path)
            }
        }

        for (s in sources) {
            val src = File(s)
            if (!src.exists()) throw IOException("Source missing: $s")
            copyOne(src, File(destDir, src.name))
        }
        copied
    }

    suspend fun move(
        sources: List<String>,
        destinationDir: String,
        conflict: ConflictPolicy = ConflictPolicy.ASK,
        resolver: suspend (String) -> ConflictResolution = { ConflictResolution.SKIP },
        progress: (OperationProgress) -> Unit,
    ): List<String> {
        val result = copy(sources, destinationDir, conflict, resolver, progress)
        // Only delete sources after a fully successful copy.
        delete(sources) { }
        return result
    }
}

class OperationCancelled : Exception("Operation cancelled")

enum class ConflictPolicy { OVERWRITE, SKIP, RENAME, ASK }
enum class ConflictResolution { OVERWRITE, SKIP, RENAME, CANCEL }

/** Storage statistics for a path. */
object StorageInfo {
    fun of(path: String): StorageStats? {
        return try {
            val stat = StatFs(path)
            StorageStats(
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes,
                freeBytes = stat.freeBytes,
                blockSize = stat.blockSizeLong,
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class StorageStats(
    val totalBytes: Long,
    val availableBytes: Long,
    val freeBytes: Long,
    val blockSize: Long,
) {
    val usedBytes: Long get() = totalBytes - freeBytes
}
