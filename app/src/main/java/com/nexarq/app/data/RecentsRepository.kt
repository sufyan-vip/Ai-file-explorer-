package com.nexarq.app.data

import com.nexarq.app.core.RecentFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class RecentsFile(val items: List<RecentFile> = emptyList())

class RecentsRepository(private val store: JsonStore) {
    private val serializer = RecentsFile.serializer()

    fun all(): List<RecentFile> = store.read("recents.json", serializer, RecentsFile()).items

    fun record(path: String, name: String) {
        val current = all().toMutableList()
        current.removeAll { it.path == path }
        current.add(0, RecentFile(path, name))
        if (current.size > 100) current.subList(100, current.size).clear()
        store.write("recents.json", serializer, RecentsFile(current))
    }

    fun remove(path: String) {
        val current = all().filterNot { it.path == path }
        store.write("recents.json", serializer, RecentsFile(current))
    }

    fun clear() = store.write("recents.json", serializer, RecentsFile())
}
