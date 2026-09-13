package com.nexarq.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Minimal JSON persistence for small lists (bookmarks, recents, presets).
 * Writes are atomic (temp file + rename) so a crash cannot corrupt state.
 */
class JsonStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun <T> read(name: String, serializer: KSerializer<T>, default: T): T {
        val file = File(dir, name)
        if (!file.exists()) return default
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (_: Exception) {
            default
        }
    }

    fun <T> write(name: String, serializer: KSerializer<T>, value: T) {
        dir.mkdirs()
        val file = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
