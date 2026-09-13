package com.nexarq.app.tools

import com.nexarq.app.core.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ComparisonLine(val leftNumber: Int?, val rightNumber: Int?, val text: String, val changed: Boolean)

data class FileComparison(
    val leftPath: String,
    val rightPath: String,
    val sizeEqual: Boolean,
    val sameSize: Long,
    val leftSize: Long,
    val rightSize: Long,
    val leftModified: Long,
    val rightModified: Long,
    val typeEqual: Boolean,
    val checksumEqual: Boolean?,
    val leftChecksum: String?,
    val rightChecksum: String?,
    val binaryEqual: Boolean?,
    val textDiff: List<ComparisonLine>?,
)

/**
 * Two-file comparison: metadata, checksums, binary equality and a simple line diff.
 */
object FileCompare {

    suspend fun compare(leftPath: String, rightPath: String): FileComparison = withContext(Dispatchers.IO) {
        val left = File(leftPath)
        val right = File(rightPath)
        val leftSize = left.length()
        val rightSize = right.length()
        val sizeEqual = leftSize == rightSize
        val typeEqual = FileTypeOf(leftPath) == FileTypeOf(rightPath)

        var checksumEqual: Boolean? = null
        var leftChecksum: String? = null
        var rightChecksum: String? = null
        var binaryEqual: Boolean? = null

        if (sizeEqual) {
            val lh = Hashing.hashFile(left, HashAlgorithm.SHA256)
            val rh = Hashing.hashFile(right, HashAlgorithm.SHA256)
            leftChecksum = lh
            rightChecksum = rh
            checksumEqual = lh == rh
            binaryEqual = lh == rh
        }

        val textDiff: List<ComparisonLine>? =
            if (isText(leftPath) && isText(rightPath) && leftSize < 2 * 1024 * 1024 && rightSize < 2 * 1024 * 1024) {
                diff(left.readText(), right.readText())
            } else null

        FileComparison(
            leftPath, rightPath, sizeEqual, minOf(leftSize, rightSize), leftSize, rightSize,
            left.lastModified(), right.lastModified(), typeEqual, checksumEqual,
            leftChecksum, rightChecksum, binaryEqual, textDiff,
        )
    }

    private fun FileTypeOf(path: String): String = path.substringAfterLast('.', "").lowercase()

    private fun isText(path: String): Boolean = FileTypeOf(path) in setOf("txt", "md", "log", "json", "xml", "csv", "kt", "java", "xml")

    /** Simple LCS-free line diff (trimmed-compare, adequate for small files). */
    private fun diff(left: String, right: String): List<ComparisonLine> {
        val leftLines = left.lines()
        val rightLines = right.lines()
        val max = maxOf(leftLines.size, rightLines.size)
        val out = mutableListOf<ComparisonLine>()
        for (i in 0 until max) {
            val l = leftLines.getOrNull(i)
            val r = rightLines.getOrNull(i)
            if (l == null) {
                out.add(ComparisonLine(null, i + 1, "+ ${r!!}", true))
            } else if (r == null) {
                out.add(ComparisonLine(i + 1, null, "- $l", true))
            } else if (l != r) {
                out.add(ComparisonLine(i + 1, i + 1, "- $l", true))
                out.add(ComparisonLine(null, i + 1, "+ $r", true))
            } else {
                out.add(ComparisonLine(i + 1, i + 1, "  $l", false))
            }
        }
        return out
    }

    fun describe(c: FileComparison): String = buildString {
        appendLine("Left : ${c.leftPath} (${Format.bytes(c.leftSize)})")
        appendLine("Right: ${c.rightPath} (${Format.bytes(c.rightSize)})")
        appendLine("Size equal: ${c.sizeEqual}")
        appendLine("Type equal: ${c.typeEqual}")
        appendLine("SHA-256 left : ${c.leftChecksum ?: "—"}")
        appendLine("SHA-256 right: ${c.rightChecksum ?: "—"}")
        appendLine("Checksum equal: ${c.checksumEqual ?: "n/a"}")
    }
}
