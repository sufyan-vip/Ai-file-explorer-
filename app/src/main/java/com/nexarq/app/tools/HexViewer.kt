package com.nexarq.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** One decoded row of the hex viewer. */
data class HexRow(val offset: Long, val bytes: ByteArray, val ascii: String)

/**
 * Chunked, read-only hex viewer. Never loads more than [chunkSize] bytes at a time.
 */
object HexViewer {

    const val BYTES_PER_ROW = 16
    private const val DEFAULT_CHUNK = 4096

    suspend fun readChunk(path: String, startOffset: Long, chunkSize: Int = DEFAULT_CHUNK): List<HexRow> =
        withContext(Dispatchers.IO) {
            val file = File(path)
            val rows = mutableListOf<HexRow>()
            RandomAccessFile(file, "r").use { raf ->
                if (startOffset >= raf.length()) return@withContext rows
                raf.seek(startOffset)
                val buf = ByteArray(chunkSize)
                val read = raf.read(buf)
                if (read <= 0) return@withContext rows
                var i = 0
                while (i < read) {
                    val rowBytes = ByteArray(minOf(BYTES_PER_ROW, read - i))
                    System.arraycopy(buf, i, rowBytes, 0, rowBytes.size)
                    val ascii = buildString {
                        for (b in rowBytes) {
                            append(if (b in 0x20..0x7E) b.toInt().toChar() else '.')
                        }
                    }
                    rows.add(HexRow(startOffset + i, rowBytes, ascii))
                    i += BYTES_PER_ROW
                }
            }
            rows
        }

    suspend fun fileSize(path: String): Long = withContext(Dispatchers.IO) { File(path).length() }

    /** Locate a byte/ASCII pattern, returning the first matching offset (or -1). */
    suspend fun search(path: String, pattern: ByteArray, fromOffset: Long = 0): Long =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (pattern.isEmpty()) return@withContext -1
            RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(1024 * 1024)
                var base = fromOffset
                var carry = ByteArray(0)
                while (base < raf.length()) {
                    raf.seek(base)
                    val read = raf.read(buf)
                    if (read <= 0) break
                    val window = if (carry.isNotEmpty()) carry + buf.copyOf(read) else buf.copyOf(read)
                    val idx = indexOf(window, pattern)
                    if (idx >= 0) return@withContext base - carry.size + idx
                    carry = if (window.size >= pattern.size) window.copyOfRange(window.size - pattern.size + 1, window.size) else window
                    base += read
                }
                -1
            }
        }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
