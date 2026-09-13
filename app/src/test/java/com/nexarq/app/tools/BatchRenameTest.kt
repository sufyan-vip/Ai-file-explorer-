package com.nexarq.app.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BatchRenameTest {

    private val files = listOf(File("a.txt"), File("b.txt"), File("c.txt"))

    @Test
    fun prefixAddsPrefix() {
        val plan = RenamePlan(mode = RenameMode.PREFIX, prefix = "2024_")
        val preview = BatchRenamer.preview(files, plan)
        assertEquals("2024_a.txt", preview[0].newName)
    }

    @Test
    fun numberedKeepsExtension() {
        val plan = RenamePlan(mode = RenameMode.NUMBERED, startNumber = 1, padTo = 3)
        val preview = BatchRenamer.preview(files, plan)
        assertEquals("001.txt", preview[0].newName)
        assertEquals("003.txt", preview[2].newName)
    }

    @Test
    fun replaceSwapsText() {
        val plan = RenamePlan(mode = RenameMode.REPLACE, find = "a", replace = "x")
        val preview = BatchRenamer.preview(files, plan)
        assertEquals("x.txt", preview[0].newName)
    }

    @Test
    fun collisionDetectionRenamesUniquely() {
        // two files with same base name -> second gets a suffix
        val plan = RenamePlan(mode = RenameMode.REPLACE, find = "", replace = "")
        val dup = listOf(File("x.txt"), File("y.txt"))
        val preview = BatchRenamer.preview(dup, plan.copy(mode = RenameMode.SUFFIX, suffix = "_s"))
        assertTrue(preview[0].newName != preview[1].newName)
    }
}
