package com.nexarq.app.data

import com.nexarq.app.core.Bookmark
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class BookmarksFile(val items: List<Bookmark> = emptyList())

class BookmarkRepository(private val store: JsonStore) {
    private val serializer = BookmarksFile.serializer()

    fun all(): List<Bookmark> = store.read("bookmarks.json", serializer, BookmarksFile()).items

    fun add(path: String, label: String? = null): Bookmark {
        val items = all().toMutableList()
        val bookmark = Bookmark(
            id = System.currentTimeMillis(),
            path = path,
            label = label ?: path.substringAfterLast('/').ifEmpty { path },
        )
        items.add(bookmark)
        store.write("bookmarks.json", serializer, BookmarksFile(items))
        return bookmark
    }

    fun remove(id: Long) {
        store.write("bookmarks.json", serializer, BookmarksFile(all().filterNot { it.id == id }))
    }

    fun rename(id: Long, label: String) {
        store.write("bookmarks.json", serializer, BookmarksFile(all().map {
            if (it.id == id) it.copy(label = label) else it
        }))
    }

    fun contains(path: String): Boolean = all().any { it.path == path }
}
