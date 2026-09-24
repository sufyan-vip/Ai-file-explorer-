package com.nexarq.app.trash

import android.content.Context
import com.nexarq.app.core.FileSystem
import com.nexarq.app.core.OperationKind
import com.nexarq.app.core.OperationProgress
import com.nexarq.app.core.TrashedItem
import com.nexarq.app.data.TrashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Recycle bin engine. Deleted files are moved into the app's private trash
 * directory (invisible to other apps and to MediaStore) together with enough
 * metadata to restore them to their original location.
 *
 * Trash lives in app-private storage, so no storage permission is required and
 * trashed files are removed automatically if the app is uninstalled.
 */
object TrashManager {

    fun trashDir(context: Context): File = File(context.filesDir, ".trash").apply { mkdirs() }

    /** Move files/folders into the trash instead of deleting them permanently. */
    suspend fun moveToTrash(
        context: Context,
        repo: TrashRepository,
        paths: List<String>,
        progress: (OperationProgress) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        val dir = trashDir(context)
        val opId = System.currentTimeMillis()
        var done = 0
        for (p in paths) {
            cc.ensureActive()
            val src = File(p)
            if (!src.exists()) {
                done++
                continue
            }
            val id = repo.nextId()
            val target = uniqueTarget(dir, src.name)
            val size = if (src.isDirectory) FileSystem.directorySize(p) else src.length()
            if (!src.renameTo(target)) throw IOException("Could not move to trash: $p")
            repo.add(
                TrashedItem(
                    id = id,
                    name = src.name,
                    originalPath = src.absolutePath,
                    trashPath = target.absolutePath,
                    isDirectory = src.isDirectory,
                    size = size,
                ),
            )
            done++
            progress(
                OperationProgress(
                    opId, OperationKind.DELETE, "Moving to trash", p,
                    done.toLong(), paths.size.toLong(), done.toLong(), paths.size.toLong(),
                ),
            )
        }
    }

    /**
     * Restore a trashed item to its original location. If something now exists at
     * the original path, the item is restored next to it with a numbered suffix.
     * Returns the path it was restored to.
     */
    suspend fun restore(context: Context, repo: TrashRepository, id: Long): String =
        withContext(Dispatchers.IO) {
            val item = repo.find(id) ?: throw IOException("Item is no longer in the trash")
            val src = File(item.trashPath)
            if (!src.exists()) {
                repo.remove(id)
                throw IOException("Trashed file is missing: ${item.name}")
            }
            val desired = File(item.originalPath)
            desired.parentFile?.mkdirs()
            val dest = if (desired.exists()) uniqueTarget(desired.parentFile!!, desired.name) else desired
            if (!src.renameTo(dest)) throw IOException("Could not restore ${item.name}")
            repo.remove(id)
            dest.absolutePath
        }

    /** Permanently delete one trashed item. */
    suspend fun deletePermanently(repo: TrashRepository, id: Long): Unit =
        withContext(Dispatchers.IO) {
            val item = repo.find(id) ?: return@withContext
            File(item.trashPath).deleteRecursively()
            repo.remove(id)
        }

    /** Permanently delete everything in the trash, including orphaned files. */
    suspend fun emptyTrash(context: Context, repo: TrashRepository): Unit =
        withContext(Dispatchers.IO) {
            repo.list().forEach { File(it.trashPath).deleteRecursively() }
            repo.clear()
            trashDir(context).listFiles()?.forEach { it.deleteRecursively() }
        }

    /**
     * Permanently delete items trashed more than [retentionDays] ago.
     * Returns the number of purged items. A non-positive retention disables purging.
     */
    suspend fun purgeExpired(context: Context, repo: TrashRepository, retentionDays: Int): Int =
        withContext(Dispatchers.IO) {
            if (retentionDays <= 0) return@withContext 0
            val cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600L * 1000L
            val expired = repo.pruneOlderThan(cutoff)
            expired.forEach { File(it.trashPath).deleteRecursively() }
            expired.size
        }

    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val (base, ext) = splitName(name)
        var i = 1
        while (target.exists()) {
            target = File(dir, "$base ($i)$ext")
            i++
        }
        return target
    }

    private fun splitName(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
    }
}
