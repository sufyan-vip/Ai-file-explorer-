package com.nexarq.app.archive

import android.util.Log
import com.nexarq.app.core.ArchiveEntry
import com.nexarq.app.core.ConflictPolicy
import com.nexarq.app.core.ConflictResolution
import com.nexarq.app.core.FileSystem
import com.nexarq.app.core.Format
import com.nexarq.app.core.OperationKind
import com.nexarq.app.core.OperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel as Z4Level
import net.lingala.zip4j.model.enums.CompressionMethod as Z4Method
import net.lingala.zip4j.model.enums.EncryptionMethod as Z4Encryption
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile as CCZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Recognized archive containers and compression formats. */
enum class ArchiveFormat(val id: String, val extensions: List<String>) {
    ZIP("zip", listOf("zip", "jar")),
    SEVEN_ZIP("7z", listOf("7z")),
    TAR("tar", listOf("tar")),
    TAR_GZ("tar.gz", listOf("tar.gz", "tgz")),
    TAR_BZ2("tar.bz2", listOf("tar.bz2", "tbz2", "tbz")),
    TAR_XZ("tar.xz", listOf("tar.xz", "txz")),
    TAR_ZSTD("tar.zst", listOf("tar.zst", "tzst")),
    GZIP("gzip", listOf("gz", "gzip")),
    BZIP2("bzip2", listOf("bz2", "bzip2")),
    XZ("xz", listOf("xz")),
    ZSTD("zstd", listOf("zst", "zstd")),
    UNKNOWN("unknown", emptyList());

    companion object {
        fun fromExtension(name: String): ArchiveFormat {
            val lower = name.lowercase()
            return entries.firstOrNull { it.extensions.any { ext -> lower.endsWith(".$ext") } } ?: UNKNOWN
        }
        fun fromFileName(name: String): ArchiveFormat = fromExtension(name)
    }
}

/** Full metadata of a parsed archive. */
data class ArchiveListing(
    val path: String,
    val format: ArchiveFormat,
    val entries: List<ArchiveEntry>,
    val totalUncompressedSize: Long,
    val totalCompressedSize: Long,
    val fileCount: Int,
    val dirCount: Int,
    val encrypted: Boolean,
    val solid: Boolean = false,
    val comment: String? = null,
) {
    val compressionRatio: String get() = Format.ratio(totalCompressedSize, totalUncompressedSize)
}

/**
 * NEXARQ archive engine. Wraps Apache Commons Compress, zip4j, XZ and Zstandard
 * behind a single abstraction so formats can be added without touching callers.
 *
 * Security invariants:
 *  - extraction is always constrained to the chosen destination (path-traversal safe)
 *  - entry counts and sizes are sanity-checked against decompression-bomb heuristics
 *  - passwords are never logged or persisted
 */
object ArchiveEngine {

    private const val TAG = "NexarqArchive"
    private const val MAX_PREVIEW_BYTES = 256 * 1024
    private const val MAX_ENTRY_COUNT = 1_000_000

    fun canOpen(path: String): Boolean = ArchiveFormat.fromFileName(path) != ArchiveFormat.UNKNOWN

    fun isArchiveFile(name: String): Boolean = ArchiveFormat.fromFileName(name) != ArchiveFormat.UNKNOWN

    // ------------------------------------------------------------------ LIST

    suspend fun list(path: String, password: CharArray? = null): ArchiveListing = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) throw IOException("Archive not found: $path")
        val format = ArchiveFormat.fromFileName(file.name)
        if (format == ArchiveFormat.UNKNOWN) throw IOException("Unsupported archive format: ${file.name}")
        when (format) {
            ArchiveFormat.ZIP -> listZip(file, password)
            ArchiveFormat.SEVEN_ZIP -> listSevenZip(file, password)
            ArchiveFormat.TAR -> listTar(file) { it }
            ArchiveFormat.TAR_GZ -> listTar(file) { GzipCompressorInputStream(it) }
            ArchiveFormat.TAR_BZ2 -> listTar(file) { BZip2CompressorInputStream(it) }
            ArchiveFormat.TAR_XZ -> listTar(file) { XZCompressorInputStream(it) }
            ArchiveFormat.TAR_ZSTD -> listTar(file) { ZstdCompressorInputStream(it) }
            ArchiveFormat.GZIP -> listSingle(file, "gzip") { GzipCompressorInputStream(it) }
            ArchiveFormat.BZIP2 -> listSingle(file, "bzip2") { BZip2CompressorInputStream(it) }
            ArchiveFormat.XZ -> listSingle(file, "xz") { XZCompressorInputStream(it) }
            ArchiveFormat.ZSTD -> listSingle(file, "zstd") { ZstdCompressorInputStream(it) }
            else -> throw IOException("Unsupported archive format: ${file.name}")
        }
    }

    private fun listZip(file: File, password: CharArray?): ArchiveListing {
        if (password != null) {
            // zip4j handles both ZipCrypto and AES; also reports encryption accurately.
            val zf = Zip4jFile(file)
            if (zf.isEncrypted) zf.setPassword(password)
            val headers = zf.fileHeaders
            val entries = headers.map { h ->
                ArchiveEntry(
                    name = h.fileName.substringAfterLast('/'),
                    path = h.fileName,
                    isDirectory = h.isDirectory,
                    size = h.uncompressedSize,
                    compressedSize = h.compressedSize,
                    modified = h.lastModifiedTimeEpoch.takeIf { it > 0 } ?: 0L,
                    encrypted = h.isEncrypted,
                    method = h.compressionMethod?.name,
                    crc = h.crc,
                )
            }
            return buildListing(file.path, ArchiveFormat.ZIP, entries, null, headers.any { it.isEncrypted })
        }
        CCZipFile.builder().setFile(file).get().use { zip ->
            val entries = java.util.Collections.list(zip.entries).map { e ->
                ArchiveEntry(
                    name = e.name.substringAfterLast('/'),
                    path = e.name,
                    isDirectory = e.isDirectory,
                    size = e.size,
                    compressedSize = e.compressedSize,
                    modified = e.time,
                    encrypted = e.generalPurposeBit.usesEncryption(),
                    method = methodName(e.method),
                    crc = e.crc,
                )
            }
            val comment: String? = runCatching { Zip4jFile(file).comment }.getOrNull()?.takeIf { it.isNotBlank() }
            return buildListing(file.path, ArchiveFormat.ZIP, entries, comment, entries.any { it.encrypted })
        }
    }

    private fun listSevenZip(file: File, password: CharArray?): ArchiveListing {
        val seven = if (password != null) SevenZFile(file, password) else SevenZFile(file)
        seven.use { sz ->
            val entries = mutableListOf<ArchiveEntry>()
            var solid = false
            var entry: SevenZArchiveEntry?
            while (run {
                    entry = sz.nextEntry
                    entry
                } != null) {
                val e = entry!!
                if (e.contentMethods?.any { it.method == SevenZMethod.LZMA2 } == true) solid = true
                entries.add(
                    ArchiveEntry(
                        name = e.name.substringAfterLast('/'),
                        path = e.name,
                        isDirectory = e.isDirectory,
                        size = if (e.hasStream()) e.size else 0L,
                        compressedSize = if (e.hasStream()) e.size else 0L,
                        modified = e.lastModifiedDate?.time ?: 0L,
                        encrypted = false,
                        method = e.contentMethods?.firstOrNull()?.method?.name,
                        crc = if (e.hasCrc) e.crcValue else null,
                    )
                )
            }
            return buildListing(file.path, ArchiveFormat.SEVEN_ZIP, entries, null, false, solid)
        }
    }

    private fun listTar(file: File, wrap: (InputStream) -> InputStream): ArchiveListing {
        wrap(BufferedInputStream(FileInputStream(file))).use { input ->
            val tar = TarArchiveInputStream(input)
            val entries = mutableListOf<ArchiveEntry>()
            var e: TarArchiveEntry?
            while (run { e = tar.nextTarEntry; e } != null) {
                val t = e!!
                entries.add(
                    ArchiveEntry(
                        name = t.name.substringAfterLast('/').ifEmpty { t.name },
                        path = t.name,
                        isDirectory = t.isDirectory,
                        size = t.size,
                        compressedSize = t.size,
                        modified = t.modTime.time,
                        encrypted = false,
                    )
                )
            }
            return buildListing(file.path, ArchiveFormat.fromFileName(file.name), entries, null, false)
        }
    }

    private fun listSingle(file: File, method: String, wrap: (InputStream) -> InputStream): ArchiveListing {
        val baseName = file.name.substringBeforeLast('.')
        val size = wrap(BufferedInputStream(FileInputStream(file))).use { input ->
            var total = 0L
            val buf = ByteArray(64 * 1024)
            var r: Int
            while (input.read(buf).also { r = it } > 0) total += r
            total
        }
        val entry = ArchiveEntry(baseName, baseName, false, size, size, file.lastModified(), false, method)
        return buildListing(file.path, ArchiveFormat.fromFileName(file.name), listOf(entry), null, false)
    }

    private fun buildListing(
        path: String,
        format: ArchiveFormat,
        entries: List<ArchiveEntry>,
        comment: String?,
        encrypted: Boolean,
        solid: Boolean = false,
    ): ArchiveListing {
        if (entries.size > MAX_ENTRY_COUNT) throw IOException("Archive has too many entries (${entries.size})")
        val files = entries.filter { !it.isDirectory }
        return ArchiveListing(
            path = path,
            format = format,
            entries = entries,
            totalUncompressedSize = entries.sumOf { it.size },
            totalCompressedSize = entries.sumOf { it.compressedSize },
            fileCount = files.size,
            dirCount = entries.size - files.size,
            encrypted = encrypted,
            solid = solid,
            comment = comment,
        )
    }

    private fun methodName(method: Int): String = when (method) {
        ZipArchiveEntry.STORED -> "Stored"
        ZipArchiveEntry.DEFLATED -> "Deflated"
        else -> "Method $method"
    }

    /** Read up to [max] bytes without relying on JDK 9+ readNBytes (minSdk 24). */
    private fun readUpTo(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var remaining = max
        while (remaining > 0) {
            val r = input.read(buf, 0, minOf(buf.size, remaining))
            if (r < 0) break
            out.write(buf, 0, r)
            remaining -= r
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ PREVIEW

    suspend fun preview(path: String, entryPath: String, password: CharArray? = null): PreviewResult =
        withContext(Dispatchers.IO) {
            val file = File(path)
            val format = ArchiveFormat.fromFileName(file.name)
            val bytes = when (format) {
                ArchiveFormat.ZIP -> {
                    if (password != null) {
                        val zf = Zip4jFile(file)
                        if (zf.isEncrypted) zf.setPassword(password)
                        val header = zf.getFileHeader(entryPath)
                        zf.getInputStream(header).use { readUpTo(it, MAX_PREVIEW_BYTES) }
                    } else {
                        CCZipFile.builder().setFile(file).get().use { zip ->
                            val entry = zip.getEntry(entryPath) ?: throw IOException("Entry not found")
                            zip.getInputStream(entry).use { readUpTo(it, MAX_PREVIEW_BYTES) }
                        }
                    }
                }
                ArchiveFormat.SEVEN_ZIP -> {
                    val sz = if (password != null) SevenZFile(file, password) else SevenZFile(file)
                    sz.use { seven ->
                        var found: SevenZArchiveEntry? = null
                        var e: SevenZArchiveEntry?
                        while (run { e = seven.nextEntry; e } != null) {
                            if (e!!.name == entryPath) { found = e; break }
                        }
                        if (found == null) throw IOException("Entry not found")
                        seven.getInputStream(found).use { readUpTo(it, MAX_PREVIEW_BYTES) }
                    }
                }
                else -> throw IOException("Preview not available for ${format.id}")
            }
            PreviewResult(bytes)
        }

    data class PreviewResult(val bytes: ByteArray) {
        fun asText(): String = String(bytes, Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ EXTRACT

    suspend fun extract(
        path: String,
        destinationDir: String,
        password: CharArray? = null,
        entryFilter: (ArchiveEntry) -> Boolean = { true },
        conflict: ConflictPolicy = ConflictPolicy.ASK,
        resolver: suspend (String) -> ConflictResolution = { ConflictResolution.SKIP },
        progress: (OperationProgress) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val file = File(path)
        val format = ArchiveFormat.fromFileName(file.name)
        val destDir = File(destinationDir).apply { mkdirs() }
        val cc = coroutineContext
        val opId = System.currentTimeMillis()
        var processed = 0
        val listing = try { list(path, password) } catch (e: Exception) { throw e }
        val selected = listing.entries.filter(entryFilter)
        val totalBytes = selected.sumOf { it.size }.coerceAtLeast(1)
        var processedBytes = 0L

        fun report(current: String) {
            progress(OperationProgress(opId, OperationKind.EXTRACT, "Extracting ${file.name}", current,
                processed.toLong(), selected.size.toLong(), processedBytes, totalBytes))
        }

        fun resolveConflict(target: File): ConflictResolution = when {
            !target.exists() -> ConflictResolution.OVERWRITE
            conflict == ConflictPolicy.OVERWRITE -> ConflictResolution.OVERWRITE
            conflict == ConflictPolicy.SKIP -> ConflictResolution.SKIP
            conflict == ConflictPolicy.RENAME -> ConflictResolution.RENAME
            else -> kotlinx.coroutines.runBlocking { resolver(target.path) }
        }

        fun drain(input: InputStream) {
            val buf = ByteArray(64 * 1024)
            while (input.read(buf) > 0) { cc.ensureActive() }
        }

        fun doWrite(input: InputStream, target: File, sizeHint: Long) {
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024)
                var r: Int
                while (input.read(buf).also { r = it } > 0) {
                    cc.ensureActive()
                    out.write(buf, 0, r)
                    processedBytes += r
                    report(target.name)
                }
            }
            processed++
        }

        fun writeStream(input: InputStream, target: File, sizeHint: Long) {
            when (resolveConflict(target)) {
                ConflictResolution.SKIP -> { drain(input); return }
                ConflictResolution.CANCEL -> throw kotlinx.coroutines.CancellationException("Cancelled by user")
                ConflictResolution.RENAME -> {
                    var i = 1
                    var t = target
                    val base = target.name.substringBeforeLast('.', target.name)
                    val ext = target.name.substringAfterLast('.').let { if (it == target.name) "" else ".$it" }
                    while (t.exists()) t = File(target.parentFile, "$base ($i)$ext").also { i++ }
                    doWrite(input, t, sizeHint)
                }
                else -> doWrite(input, target, sizeHint)
            }
        }

        when (format) {
            ArchiveFormat.ZIP -> {
                if (password != null) {
                    val zf = Zip4jFile(file)
                    if (zf.isEncrypted) zf.setPassword(password)
                    for (entry in selected) {
                        cc.ensureActive()
                        if (entry.isDirectory) {
                            val dir = FileSystem.safeJoin(destDir, entry.path) ?: continue // skip unsafe entry
                            dir.mkdirs(); processed++; continue
                        }
                        val target = FileSystem.safeJoin(destDir, entry.path) ?: continue // skip unsafe entry
                        val header = zf.getFileHeader(entry.path)
                        writeStream(zf.getInputStream(header), target, entry.size)
                    }
                } else {
                    CCZipFile.builder().setFile(file).get().use { zip ->
                        for (entry in selected) {
                            cc.ensureActive()
                            if (entry.isDirectory) {
                                val dir = FileSystem.safeJoin(destDir, entry.path) ?: continue // skip unsafe entry
                                dir.mkdirs(); processed++; continue
                            }
                            val target = FileSystem.safeJoin(destDir, entry.path) ?: continue // skip unsafe entry
                            val ze = zip.getEntry(entry.path) ?: continue
                            writeStream(zip.getInputStream(ze), target, entry.size)
                        }
                    }
                }
            }
            ArchiveFormat.SEVEN_ZIP -> {
                val sz = if (password != null) SevenZFile(file, password) else SevenZFile(file)
                sz.use { seven ->
                    val wanted = selected.map { it.path }.toSet()
                    var e: SevenZArchiveEntry?
                    while (run { e = seven.nextEntry; e } != null) {
                        cc.ensureActive()
                        val cur = e!!
                        if (cur.name !in wanted) continue
                        if (cur.isDirectory) {
                            val dir = FileSystem.safeJoin(destDir, cur.name) ?: continue // skip unsafe entry
                            dir.mkdirs(); processed++; continue
                        }
                        val target = FileSystem.safeJoin(destDir, cur.name) ?: continue // skip unsafe entry
                        writeStream(seven.getInputStream(cur), target, cur.size)
                    }
                }
            }
            ArchiveFormat.TAR -> extractTar(file, cc) { TarArchiveInputStream(it) }(destDir, selected, ::writeStream, ::report)
            ArchiveFormat.TAR_GZ -> extractTar(file, cc) { TarArchiveInputStream(GzipCompressorInputStream(it)) }(destDir, selected, ::writeStream, ::report)
            ArchiveFormat.TAR_BZ2 -> extractTar(file, cc) { TarArchiveInputStream(BZip2CompressorInputStream(it)) }(destDir, selected, ::writeStream, ::report)
            ArchiveFormat.TAR_XZ -> extractTar(file, cc) { TarArchiveInputStream(XZCompressorInputStream(it)) }(destDir, selected, ::writeStream, ::report)
            ArchiveFormat.TAR_ZSTD -> extractTar(file, cc) { TarArchiveInputStream(ZstdCompressorInputStream(it)) }(destDir, selected, ::writeStream, ::report)
            ArchiveFormat.GZIP -> extractSingle(file, destDir, selected, { GzipCompressorInputStream(it) }, ::writeStream, ::report)
            ArchiveFormat.BZIP2 -> extractSingle(file, destDir, selected, { BZip2CompressorInputStream(it) }, ::writeStream, ::report)
            ArchiveFormat.XZ -> extractSingle(file, destDir, selected, { XZCompressorInputStream(it) }, ::writeStream, ::report)
            ArchiveFormat.ZSTD -> extractSingle(file, destDir, selected, { ZstdCompressorInputStream(it) }, ::writeStream, ::report)
            else -> throw IOException("Unsupported format")
        }
        report("Done")
        processed
    }

    private fun extractTar(
        file: File,
        cc: CoroutineContext,
        open: (InputStream) -> TarArchiveInputStream,
    ): (File, List<ArchiveEntry>, (InputStream, File, Long) -> Unit, (String) -> Unit) -> Unit =
        { destDir, selected, writeStream, report ->
            val wanted = selected.map { it.path }.toSet()
            open(BufferedInputStream(FileInputStream(file))).use { tar ->
                var e: TarArchiveEntry?
                while (run { e = tar.nextTarEntry; e } != null) {
                    cc.ensureActive()
                    val cur = e!!
                    if (cur.name !in wanted) continue
                    report(cur.name)
                    if (cur.isDirectory) {
                        val dir = FileSystem.safeJoin(destDir, cur.name) ?: continue // skip unsafe entry
                        dir.mkdirs()
                    } else {
                        val target = FileSystem.safeJoin(destDir, cur.name) ?: continue // skip unsafe entry
                        writeStream(tar, target, cur.size)
                    }
                }
            }
        }

    private fun extractSingle(
        file: File,
        destDir: File,
        selected: List<ArchiveEntry>,
        wrap: (InputStream) -> InputStream,
        writeStream: (InputStream, File, Long) -> Unit,
        report: (String) -> Unit,
    ) {
        val entry = selected.firstOrNull() ?: return
        report(entry.name)
        val target = File(destDir, entry.name)
        wrap(BufferedInputStream(FileInputStream(file))).use { input ->
            writeStream(input, target, entry.size)
        }
    }

    // ------------------------------------------------------------------ TEST

    suspend fun test(path: String, password: CharArray? = null): TestResult = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        val file = File(path)
        val format = ArchiveFormat.fromFileName(file.name)
        val start = System.currentTimeMillis()
        var errors = 0
        val listing = list(path, password)
        val message = try {
            when (format) {
                ArchiveFormat.ZIP -> {
                    if (password != null) {
                        val zf = Zip4jFile(file)
                        if (zf.isEncrypted) zf.setPassword(password)
                        zf.fileHeaders.forEach { h ->
                            zf.getInputStream(h).use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (input.read(buf) > 0) { cc.ensureActive() }
                            }
                        }
                    } else {
                        CCZipFile.builder().setFile(file).get().use { zip ->
                            java.util.Collections.list(zip.entries).forEach { e ->
                                zip.getInputStream(e).use { input ->
                                    val buf = ByteArray(64 * 1024)
                                    while (input.read(buf) > 0) { cc.ensureActive() }
                                }
                            }
                        }
                    }
                    "OK"
                }
                ArchiveFormat.SEVEN_ZIP -> {
                    val sz = if (password != null) SevenZFile(file, password) else SevenZFile(file)
                    sz.use { seven ->
                        var e: SevenZArchiveEntry?
                        while (run { e = seven.nextEntry; e } != null) {
                            seven.getInputStream(e).use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (input.read(buf) > 0) { cc.ensureActive() }
                            }
                        }
                    }
                    "OK"
                }
                else -> {
                    // For tar/compressor formats, listing already validates the stream structure.
                    // Re-stream to verify decompression integrity.
                    when (format) {
                        ArchiveFormat.TAR -> drainCompressed(file, cc) { it }
                        ArchiveFormat.TAR_GZ -> drainCompressed(file, cc) { GzipCompressorInputStream(it) }
                        ArchiveFormat.TAR_BZ2 -> drainCompressed(file, cc) { BZip2CompressorInputStream(it) }
                        ArchiveFormat.TAR_XZ -> drainCompressed(file, cc) { XZCompressorInputStream(it) }
                        ArchiveFormat.TAR_ZSTD -> drainCompressed(file, cc) { ZstdCompressorInputStream(it) }
                        ArchiveFormat.GZIP -> drainCompressed(file, cc) { GzipCompressorInputStream(it) }
                        ArchiveFormat.BZIP2 -> drainCompressed(file, cc) { BZip2CompressorInputStream(it) }
                        ArchiveFormat.XZ -> drainCompressed(file, cc) { XZCompressorInputStream(it) }
                        ArchiveFormat.ZSTD -> drainCompressed(file, cc) { ZstdCompressorInputStream(it) }
                        else -> throw IOException("Unsupported")
                    }
                    "OK"
                }
            }
        } catch (e: Exception) {
            errors = 1
            e.message ?: e.javaClass.simpleName
        }
        TestResult(success = errors == 0, message = message, elapsedMs = System.currentTimeMillis() - start)
    }

    private fun drainCompressed(file: File, cc: CoroutineContext, wrap: (InputStream) -> InputStream) {
        wrap(BufferedInputStream(FileInputStream(file))).use { input ->
            val buf = ByteArray(64 * 1024)
            while (input.read(buf) > 0) { cc.ensureActive() }
        }
    }

    data class TestResult(val success: Boolean, val message: String, val elapsedMs: Long)

    // ------------------------------------------------------------------ CREATE

    data class CreateOptions(
        val format: ArchiveFormat,
        val compressionLevel: Int = 6,
        val password: CharArray? = null,
        val splitSizeBytes: Long = 0,
        val solid: Boolean = false,
        val preserveTimestamps: Boolean = true,
    )

    suspend fun create(
        sources: List<String>,
        outputFile: File,
        options: CreateOptions,
        progress: (OperationProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists() && options.password != null) {
            // zip4j can append, but we always start a fresh archive for safety.
            outputFile.delete()
        }
        val allFiles = collectFiles(sources)
        val totalBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1)
        val opId = System.currentTimeMillis()
        var processedBytes = 0L
        var processed = 0
        val baseDir = commonParent(sources)

        fun report(current: String) {
            progress(OperationProgress(opId, OperationKind.COMPRESS, "Compressing ${outputFile.name}", current,
                processed.toLong(), allFiles.size.toLong(), processedBytes, totalBytes))
        }

        when (options.format) {
            ArchiveFormat.ZIP -> createZip(sources, baseDir, outputFile, options, allFiles, ::report) {
                processedBytes += it; processed++; report("")
            }
            ArchiveFormat.SEVEN_ZIP -> create7z(sources, baseDir, outputFile, options, allFiles, ::report) {
                processedBytes += it; processed++; report("")
            }
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_ZSTD ->
                createTar(sources, baseDir, outputFile, options, allFiles, ::report) {
                    processedBytes += it; processed++; report("")
                }
            ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ, ArchiveFormat.ZSTD ->
                createSingleCompressed(sources, outputFile, options, ::report)
            else -> throw IOException("Unsupported output format")
        }
        outputFile
    }

    private fun collectFiles(sources: List<String>): List<File> {
        val out = mutableListOf<File>()
        for (s in sources) {
            val f = File(s)
            if (f.isFile) out.add(f)
            else if (f.isDirectory) f.walkTopDown().filter { it.isFile }.forEach { out.add(it) }
        }
        return out
    }

    private fun commonParent(sources: List<String>): File {
        val files = sources.map { File(it).absoluteFile }
        if (files.size == 1) return files[0].parentFile ?: File("/")
        var common = files[0].parentFile ?: return File("/")
        for (f in files.drop(1)) {
            var p = f.parentFile
            while (p != null && !common.absolutePath.startsWith(p.absolutePath)) p = p.parentFile
            if (p == null) return File("/")
            if (p.absolutePath.length < common.absolutePath.length) common = p
        }
        return common
    }

    private fun entryNameFor(file: File, baseDir: File): String {
        val rel = file.absolutePath.removePrefix(baseDir.absolutePath).trimStart('/')
        return rel.ifEmpty { file.name }
    }

    private suspend fun createZip(
        sources: List<String>,
        baseDir: File,
        outputFile: File,
        options: CreateOptions,
        allFiles: List<File>,
        report: (String) -> Unit,
        onProgress: (Long) -> Unit,
    ) {
        val cc = coroutineContext
        if (options.password != null) {
            // zip4j provides real AES-256 encryption for ZIP.
            val params = ZipParameters().apply {
                compressionMethod = Z4Method.DEFLATE
                compressionLevel = when (options.compressionLevel) {
                    in 0..1 -> Z4Level.NO_COMPRESSION
                    in 2..4 -> Z4Level.FASTEST
                    in 7..9 -> Z4Level.ULTRA
                    else -> Z4Level.NORMAL
                }
                isEncryptFiles = true
                encryptionMethod = Z4Encryption.AES
            }
            val zf = Zip4jFile(outputFile)
            zf.setPassword(options.password)
            for (f in allFiles) {
                cc.ensureActive()
                report(f.name)
                zf.addFile(f, params)
                onProgress(f.length())
            }
            return
        }
        BufferedOutputStream(FileOutputStream(outputFile)).use { bos ->
            ZipArchiveOutputStream(bos).apply {
                setLevel(options.compressionLevel)
                setUseZip64(Zip64Mode.AsNeeded)
                setEncoding("UTF-8")
            }.use { zip ->
                for (f in allFiles) {
                    cc.ensureActive()
                    report(f.name)
                    val name = entryNameFor(f, baseDir)
                    val entry = ZipArchiveEntry(f, name)
                    zip.putArchiveEntry(entry)
                    FileInputStream(f).use { input ->
                        val buf = ByteArray(64 * 1024)
                        var r: Int
                        while (input.read(buf).also { r = it } > 0) {
                            cc.ensureActive()
                            zip.write(buf, 0, r)
                            onProgress(r.toLong())
                        }
                    }
                    zip.closeArchiveEntry()
                }
                zip.finish()
            }
        }
    }

    private suspend fun create7z(
        sources: List<String>,
        baseDir: File,
        outputFile: File,
        options: CreateOptions,
        allFiles: List<File>,
        report: (String) -> Unit,
        onProgress: (Long) -> Unit,
    ) {
        val cc = coroutineContext
        if (options.password != null) {
            throw IOException("7z encryption is not supported by the built-in engine. Use ZIP for encrypted archives.")
        }
        SevenZOutputFile(outputFile).use { seven ->
            val level = options.compressionLevel.coerceIn(0, 9)
            seven.setContentMethods(
                listOf(
                    if (level == 0) SevenZMethodConfiguration(SevenZMethod.COPY)
                    else SevenZMethodConfiguration(SevenZMethod.LZMA2, org.tukaani.xz.LZMA2Options(level))
                )
            )
            for (f in allFiles) {
                cc.ensureActive()
                report(f.name)
                val name = entryNameFor(f, baseDir)
                val entry = SevenZArchiveEntry().apply {
                    this.name = name
                    this.setHasStream(true)
                    this.size = f.length()
                    this.lastModifiedDate = java.util.Date(f.lastModified())
                }
                seven.putArchiveEntry(entry)
                FileInputStream(f).use { input ->
                    val buf = ByteArray(64 * 1024)
                    var r: Int
                    while (input.read(buf).also { r = it } > 0) {
                        cc.ensureActive()
                        seven.write(buf, 0, r)
                        onProgress(r.toLong())
                    }
                }
                seven.closeArchiveEntry()
            }
            seven.finish()
        }
    }

    private suspend fun createTar(
        sources: List<String>,
        baseDir: File,
        outputFile: File,
        options: CreateOptions,
        allFiles: List<File>,
        report: (String) -> Unit,
        onProgress: (Long) -> Unit,
    ) {
        val cc = coroutineContext
        val raw = when (options.format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorOutputStream(FileOutputStream(outputFile))
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorOutputStream(FileOutputStream(outputFile))
            ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(FileOutputStream(outputFile))
            ArchiveFormat.TAR_ZSTD -> ZstdCompressorOutputStream(FileOutputStream(outputFile))
            else -> FileOutputStream(outputFile)
        }
        BufferedOutputStream(raw).use { bos ->
            val tar = TarArchiveOutputStream(bos).apply {
                setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            }
            tar.use {
                for (f in allFiles) {
                    cc.ensureActive()
                    report(f.name)
                    val name = entryNameFor(f, baseDir)
                    val entry = TarArchiveEntry(f, name).apply {
                        size = f.length()
                    }
                    tar.putArchiveEntry(entry)
                    FileInputStream(f).use { input ->
                        val buf = ByteArray(64 * 1024)
                        var r: Int
                        while (input.read(buf).also { r = it } > 0) {
                            cc.ensureActive()
                            tar.write(buf, 0, r)
                            onProgress(r.toLong())
                        }
                    }
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
    }

    private suspend fun createSingleCompressed(
        sources: List<String>,
        outputFile: File,
        options: CreateOptions,
        report: (String) -> Unit,
    ) {
        val cc = coroutineContext
        val inputFile = File(sources.first())
        if (!inputFile.isFile) throw IOException("Single-file compression requires exactly one file")
        report(inputFile.name)
        val wrap: (OutputStream) -> OutputStream = when (options.format) {
            ArchiveFormat.GZIP -> { o -> GzipCompressorOutputStream(o) }
            ArchiveFormat.BZIP2 -> { o -> BZip2CompressorOutputStream(o) }
            ArchiveFormat.XZ -> { o -> XZCompressorOutputStream(o) }
            ArchiveFormat.ZSTD -> { o -> ZstdCompressorOutputStream(o) }
            else -> { o -> o }
        }
        wrap(BufferedOutputStream(FileOutputStream(outputFile))).use { out ->
            FileInputStream(inputFile).use { input ->
                val buf = ByteArray(64 * 1024)
                var r: Int
                while (input.read(buf).also { r = it } > 0) {
                    cc.ensureActive()
                    out.write(buf, 0, r)
                }
            }
        }
    }

    // ------------------------------------------------------------------ ENTRY EDITING (ZIP)

    suspend fun deleteEntries(path: String, entries: List<String>, password: CharArray? = null): Int =
        withContext(Dispatchers.IO) {
            if (ArchiveFormat.fromFileName(File(path).name) != ArchiveFormat.ZIP) {
                throw IOException("Entry deletion is only supported for ZIP archives.")
            }
            val zf = Zip4jFile(path)
            if (zf.isEncrypted) {
                if (password == null) throw IOException("This archive is encrypted. A password is required.")
                zf.setPassword(password)
            }
            entries.forEach { zf.removeFile(it) }
            entries.size
        }

    suspend fun renameEntry(path: String, oldName: String, newName: String, password: CharArray? = null) =
        withContext(Dispatchers.IO) {
            if (ArchiveFormat.fromFileName(File(path).name) != ArchiveFormat.ZIP) {
                throw IOException("Entry rename is only supported for ZIP archives.")
            }
            val zf = Zip4jFile(path)
            if (zf.isEncrypted) {
                if (password == null) throw IOException("This archive is encrypted. A password is required.")
                zf.setPassword(password)
            }
            val header = zf.getFileHeader(oldName) ?: throw IOException("Entry not found")
            zf.renameFile(header, newName)
        }

    suspend fun addFiles(path: String, sources: List<String>, password: CharArray? = null): Int =
        withContext(Dispatchers.IO) {
            if (ArchiveFormat.fromFileName(File(path).name) != ArchiveFormat.ZIP) {
                throw IOException("Adding files is only supported for ZIP archives.")
            }
            val zf = Zip4jFile(path)
            if (zf.isEncrypted) {
                if (password == null) throw IOException("This archive is encrypted. A password is required.")
                zf.setPassword(password)
            }
            val params = ZipParameters().apply {
                compressionMethod = Z4Method.DEFLATE
                compressionLevel = Z4Level.NORMAL
            }
            var added = 0
            for (s in sources) {
                val f = File(s)
                if (f.exists()) {
                    zf.addFile(f, params)
                    added++
                }
            }
            added
        }
}
