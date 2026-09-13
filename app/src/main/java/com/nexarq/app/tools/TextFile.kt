package com.nexarq.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Maximum size we are willing to open as an editable text file. */
const val MAX_EDITABLE_TEXT_BYTES = 5L * 1024 * 1024

data class TextReadResult(
    val text: String,
    val readOnly: Boolean,
    val truncated: Boolean,
    val encoding: String,
    val totalBytes: Long,
)

/**
 * Streaming text file reader/writer with encoding detection and large-file protection.
 */
object TextFile {

    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    suspend fun read(path: String, maxBytes: Int = MAX_EDITABLE_TEXT_BYTES.toInt()): TextReadResult =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) throw java.io.IOException("File not found")
            val size = file.length()
            val readOnly = size > maxBytes
            val limit = if (readOnly) maxBytes.toLong() else size
            val bytes = ByteArray(limit.toInt())
            var read = 0
            FileInputStream(file).use { input ->
                while (read < limit && read < bytes.size) {
                    val r = input.read(bytes, read, (bytes.size - read).toInt().coerceAtLeast(1))
                    if (r < 0) break
                    read += r
                }
            }
            val data = bytes.copyOf(read)
            val (encoding, text) = decode(data)
            TextReadResult(text, readOnly, size > limit, encoding, size)
        }

    suspend fun write(path: String, content: String, encoding: String = "UTF-8") = withContext(Dispatchers.IO) {
        val bytes = when (encoding) {
            "UTF-16LE" -> content.encodeToByteArray()
            else -> content.toByteArray(Charsets.UTF_8)
        }
        val file = File(path)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(bytes) }
    }

    private fun decode(data: ByteArray): Pair<String, String> {
        if (data.size >= 3 && data[0] == BOM_UTF8[0] && data[1] == BOM_UTF8[1] && data[2] == BOM_UTF8[2]) {
            return "UTF-8" to String(data, 3, data.size - 3, Charsets.UTF_8)
        }
        if (data.size >= 2 && data[0] == BOM_UTF16_LE[0] && data[1] == BOM_UTF16_LE[1]) {
            return "UTF-16LE" to String(data, 2, data.size - 2, Charsets.UTF_16LE)
        }
        if (data.size >= 2 && data[0] == BOM_UTF16_BE[0] && data[1] == BOM_UTF16_BE[1]) {
            return "UTF-16BE" to String(data, 2, data.size - 2, Charsets.UTF_16BE)
        }
        return "UTF-8" to String(data, Charsets.UTF_8)
    }
}
