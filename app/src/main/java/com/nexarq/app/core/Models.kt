package com.nexarq.app.core

import java.io.File
import java.util.Date

/** A single filesystem entry shown in the file browser. */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val isHidden: Boolean,
    val isSymlink: Boolean = false,
    val permissions: String? = null,
    val owner: String? = null,
    val group: String? = null,
    val mimeType: String? = null,
    val target: String? = null,
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    companion object {
        fun fromFile(file: File): FileItem = FileItem(
            name = file.name,
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0L,
            modified = file.lastModified(),
            isHidden = file.name.startsWith("."),
            isSymlink = isSymlink(file),
        )

        private fun isSymlink(file: File): Boolean {
            return try {
                file.canonicalPath != file.absolutePath
            } catch (_: Exception) {
                false
            }
        }
    }
}

/** An entry inside an archive. */
data class ArchiveEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long = size,
    val modified: Long = 0L,
    val encrypted: Boolean = false,
    val method: String? = null,
    val crc: Long? = null,
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

/** State of a long-running file operation, surfaced to the UI. */
data class OperationProgress(
    val operationId: Long,
    val kind: OperationKind,
    val label: String,
    val currentFile: String = "",
    val processedFiles: Long = 0,
    val totalFiles: Long = 0,
    val processedBytes: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val status: OperationStatus = OperationStatus.RUNNING,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (processedBytes.toDouble() / totalBytes.toDouble()).toFloat()
            totalFiles > 0 -> (processedFiles.toDouble() / totalFiles.toDouble()).toFloat()
            else -> 0f
        }
}

@kotlinx.serialization.Serializable
enum class OperationKind { COPY, MOVE, DELETE, RENAME, EXTRACT, COMPRESS, HASH, SIZE, SCAN, OTHER }

@kotlinx.serialization.Serializable
enum class OperationStatus { RUNNING, COMPLETED, FAILED, CANCELLED }

@kotlinx.serialization.Serializable
data class OperationRecord(
    val id: Long,
    val kind: OperationKind,
    val label: String,
    val status: OperationStatus,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/** A favourite / bookmark. */
@kotlinx.serialization.Serializable
data class Bookmark(
    val id: Long,
    val path: String,
    val label: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A recently accessed file. */
@kotlinx.serialization.Serializable
data class RecentFile(
    val path: String,
    val name: String,
    val accessedAt: Long = System.currentTimeMillis(),
)

/** A saved archive preset. */
@kotlinx.serialization.Serializable
data class ArchivePreset(
    val id: String,
    val name: String,
    val format: String,
    val compressionLevel: Int,
    val encrypt: Boolean = false,
) {
    companion object {
        fun defaults(): List<ArchivePreset> = listOf(
            ArchivePreset("fast_zip", "Fast ZIP", "zip", 1),
            ArchivePreset("max_zip", "Maximum ZIP", "zip", 9),
            ArchivePreset("balanced_7z", "7Z Balanced", "7z", 5),
            ArchivePreset("backup", "Backup Archive", "tar.gz", 6),
            ArchivePreset("encrypted", "Encrypted Backup", "zip", 9, encrypt = true),
        )
    }
}

/** A file-type bucket used by the storage analyzer. */
data class TypeBucket(val category: String, val count: Long, val bytes: Long)

/** A duplicate group found by the duplicate finder. */
data class DuplicateGroup(
    val hash: String,
    val size: Long,
    val files: List<File>,
)

/** A proposed AI file operation that must be approved by the user. */
data class AiActionProposal(
    val operation: OperationKind,
    val sources: List<String>,
    val destination: String?,
    val reason: String,
)

/** A single chat message in the AI center. */
@kotlinx.serialization.Serializable
data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    @kotlinx.serialization.Serializable
    enum class Role { USER, ASSISTANT, SYSTEM, ERROR }
}

/** A file or folder sitting in the recycle bin, awaiting restore or permanent deletion. */
@kotlinx.serialization.Serializable
data class TrashedItem(
    val id: Long,
    val name: String,
    val originalPath: String,
    val trashPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val trashedAt: Long = System.currentTimeMillis(),
)

/** Common date formatter used across the app. */
object TimeFormat {
    private val fmt = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
    fun format(millis: Long): String = if (millis <= 0) "—" else fmt.format(Date(millis))
}
