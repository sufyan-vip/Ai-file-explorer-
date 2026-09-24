package com.nexarq.app.data

import com.nexarq.app.core.TrashedItem
import kotlinx.serialization.Serializable

@Serializable
private data class TrashFile(val items: List<TrashedItem> = emptyList())

/**
 * Persistent index of the recycle bin. The actual trashed files live under the
 * app's private files dir (see TrashManager); this repository only tracks metadata
 * so restore/empty/purge operations stay fast.
 */
class TrashRepository(private val store: JsonStore) {
    private val serializer = TrashFile.serializer()

    fun list(): List<TrashedItem> =
        store.read("trash.json", serializer, TrashFile()).items.sortedByDescending { it.trashedAt }

    fun add(item: TrashedItem) {
        val items = list().toMutableList()
        items.add(item)
        store.write("trash.json", serializer, TrashFile(items))
    }

    fun find(id: Long): TrashedItem? = list().firstOrNull { it.id == id }

    fun remove(id: Long) {
        store.write("trash.json", serializer, TrashFile(list().filterNot { it.id == id }))
    }

    fun clear() {
        store.write("trash.json", serializer, TrashFile())
    }

    /** Drop records older than [cutoffMillis] and return the dropped items. */
    fun pruneOlderThan(cutoffMillis: Long): List<TrashedItem> {
        val items = list()
        val expired = items.filter { it.trashedAt < cutoffMillis }
        if (expired.isNotEmpty()) {
            store.write("trash.json", serializer, TrashFile(items - expired.toSet()))
        }
        return expired
    }

    fun totalSize(): Long = list().sumOf { it.size }

    /** Monotonic unique id for a new trashed item. */
    fun nextId(): Long = maxOf(list().maxOfOrNull { it.id } ?: 0L, System.currentTimeMillis()) + 1
}
