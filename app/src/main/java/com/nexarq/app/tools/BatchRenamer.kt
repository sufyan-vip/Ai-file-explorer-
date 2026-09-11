package com.nexarq.app.tools

import java.io.File

enum class RenameMode { PREFIX, SUFFIX, REPLACE, NUMBERED }

data class RenamePlan(
    val mode: RenameMode,
    val prefix: String = "",
    val suffix: String = "",
    val find: String = "",
    val replace: String = "",
    val startNumber: Int = 1,
    val padTo: Int = 2,
    val keepExtension: Boolean = true,
)

data class RenameItem(val originalPath: String, val originalName: String, val newName: String) {
    val valid: Boolean get() = newName.isNotBlank() && newName != originalName
}

/**
 * Batch rename engine. Generates a full preview (with collision detection) and only
 * applies after explicit confirmation.
 */
object BatchRenamer {

    fun preview(files: List<File>, plan: RenamePlan): List<RenameItem> {
        val items = files.mapIndexed { index, file ->
            RenameItem(file.absolutePath, file.name, computeName(file, plan, index))
        }
        // collision detection within the preview
        val seen = mutableSetOf<String>()
        return items.map { item ->
            var name = item.newName
            if (name in seen) name = uniqueName(name, seen)
            seen.add(name)
            item.copy(newName = name)
        }
    }

    private fun computeName(file: File, plan: RenamePlan, index: Int): String {
        val original = file.name
        val ext = if (plan.keepExtension) original.substringAfterLast('.', "") else ""
        val hasExt = plan.keepExtension && original.contains('.')
        val base = if (hasExt) original.substringBeforeLast('.') else original
        val dotExt = if (hasExt) ".$ext" else ""
        return when (plan.mode) {
            RenameMode.PREFIX -> plan.prefix + original
            RenameMode.SUFFIX -> base + plan.suffix + dotExt
            RenameMode.REPLACE -> base.replace(plan.find, plan.replace) + dotExt
            RenameMode.NUMBERED -> {
                val num = (plan.startNumber + index).toString().padStart(plan.padTo.coerceAtLeast(1), '0')
                if (plan.prefix.isNotBlank() || plan.suffix.isNotBlank()) {
                    plan.prefix + num + plan.suffix + dotExt
                } else {
                    num + dotExt
                }
            }
        }
    }

    private fun uniqueName(name: String, seen: Set<String>): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        var candidate = "$base ($i)$ext"
        while (candidate in seen) { i++; candidate = "$base ($i)$ext" }
        return candidate
    }

    /** Apply the previewed rename plan. Returns the number of files renamed. */
    fun apply(items: List<RenameItem>): Int {
        var count = 0
        for (item in items) {
            if (!item.valid) continue
            val src = File(item.originalPath)
            val dst = File(src.parentFile, item.newName)
            if (!src.exists()) continue
            if (dst.exists()) continue // collision safety
            if (src.renameTo(dst)) count++
        }
        return count
    }
}
