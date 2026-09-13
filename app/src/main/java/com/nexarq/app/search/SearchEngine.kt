package com.nexarq.app.search

import com.nexarq.app.core.FileItem
import com.nexarq.app.core.FileType
import com.nexarq.app.core.OperationKind
import com.nexarq.app.core.OperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class SearchQuery(
    val root: String,
    val term: String = "",
    val extension: String? = null,
    val category: FileType.Category? = null,
    val minSize: Long? = null,
    val includeHidden: Boolean = false,
    val exactName: Boolean = false,
    val maxResults: Int = 500,
)

data class SearchResult(val items: List<FileItem>, val truncated: Boolean, val scanned: Long)

/**
 * Bounded local search. Never walks the entire filesystem; it is always rooted at a
 * user-chosen directory and stops early after [SearchQuery.maxResults] matches.
 */
object SearchEngine {

    suspend fun search(query: SearchQuery, onProgress: (OperationProgress) -> Unit = {}): SearchResult =
        withContext(Dispatchers.IO) {
            val root = File(query.root)
            if (!root.exists()) return@withContext SearchResult(emptyList(), false, 0)
            val matches = mutableListOf<FileItem>()
            var scanned = 0L
            var truncated = false
            val opId = System.currentTimeMillis()
            val termLower = query.term.lowercase()

            fun consider(file: File): Boolean {
                scanned++
                if (scanned % 200 == 0L) {
                    onProgress(OperationProgress(opId, OperationKind.SCAN, "Searching", file.path, scanned, 0, 0, 0))
                }
                val name = file.name
                if (!query.includeHidden && name.startsWith(".")) return true
                if (query.extension != null && !name.lowercase().endsWith("." + query.extension.lowercase())) return true
                if (query.category != null && FileType.category(name, file.isDirectory) != query.category) return true
                if (query.minSize != null && file.isFile && file.length() < query.minSize) return true
                if (termLower.isNotEmpty()) {
                    val match = if (query.exactName) name.lowercase() == termLower else name.lowercase().contains(termLower)
                    if (!match) return true
                }
                matches.add(FileItem.fromFile(file))
                if (matches.size >= query.maxResults) {
                    truncated = true
                    return false
                }
                return true
            }

            if (root.isFile) {
                consider(root)
            } else {
                val stack = ArrayDeque<File>()
                stack.add(root)
                while (stack.isNotEmpty()) {
                    coroutineContext.ensureActive()
                    val dir = stack.removeLast()
                    val children = dir.listFiles() ?: continue
                    for (child in children) {
                        coroutineContext.ensureActive()
                        if (!consider(child)) {
                            truncated = true
                            return@withContext SearchResult(matches, truncated, scanned)
                        }
                        if (child.isDirectory) stack.add(child)
                    }
                }
            }
            SearchResult(matches, truncated, scanned)
        }
}
