package com.nexarq.app.data

import com.nexarq.app.core.ArchivePreset
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class PresetsFile(val items: List<ArchivePreset> = ArchivePreset.defaults())

class PresetRepository(private val store: JsonStore) {
    private val serializer = PresetsFile.serializer()

    fun all(): List<ArchivePreset> {
        val stored = store.read("presets.json", serializer, PresetsFile())
        return if (stored.items.isEmpty()) ArchivePreset.defaults() else stored.items
    }

    fun save(preset: ArchivePreset) {
        val items = all().toMutableList()
        val idx = items.indexOfFirst { it.id == preset.id }
        if (idx >= 0) items[idx] = preset else items.add(preset)
        store.write("presets.json", serializer, PresetsFile(items))
    }

    fun remove(id: String) {
        store.write("presets.json", serializer, PresetsFile(all().filterNot { it.id == id }))
    }
}
