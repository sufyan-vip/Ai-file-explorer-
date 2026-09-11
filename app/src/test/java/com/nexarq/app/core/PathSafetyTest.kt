package com.nexarq.app.core

import com.nexarq.app.archive.ArchiveSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PathSafetyTest {

    @Test
    fun normalizePath_collapsesSlashesAndDots() {
        assertEquals("/a/b/c", FileSystem.normalizePath("/a//b/./c"))
        assertEquals("/a/c", FileSystem.normalizePath("/a/b/../c"))
        assertEquals("/", FileSystem.normalizePath("/../../.."))
        assertEquals("/a/b", FileSystem.normalizePath("\\a\\b"))
    }

    @Test
    fun parentOf_handlesRootsAndLeaves() {
        assertEquals("/a", FileSystem.parentOf("/a/b"))
        assertEquals("/", FileSystem.parentOf("/a"))
        assertNull(FileSystem.parentOf("/"))
        assertNull(FileSystem.parentOf(""))
    }

    @Test
    fun safeJoin_blocksTraversal() {
        val root = File("/tmp/nexarq-test-root")
        assertNotNull(FileSystem.safeJoin(root, "a/b/c.txt"))
        assertNotNull(FileSystem.safeJoin(root, "a/../b/c.txt"))
        assertNull(FileSystem.safeJoin(root, "../../etc/passwd"))
        assertNull(FileSystem.safeJoin(root, "/etc/passwd"))
        assertNull(FileSystem.safeJoin(root, "C:\\windows\\system32"))
    }

    @Test
    fun archiveSecurity_rejectsUnsafeEntryPaths() {
        assertTrue(ArchiveSecurity.isSafePath("docs/readme.txt"))
        assertTrue(ArchiveSecurity.isSafePath("a/./b.txt"))
        assertFalse(ArchiveSecurity.isSafePath("../evil.txt"))
        assertFalse(ArchiveSecurity.isSafePath("a/../../evil.txt"))
        assertFalse(ArchiveSecurity.isSafePath("/absolute/path.txt"))
        assertFalse(ArchiveSecurity.isSafePath("C:/evil.txt"))
        assertFalse(ArchiveSecurity.isSafePath(""))
    }

    @Test
    fun archiveSecurity_assessesBombHeuristics() {
        val safe = listOf(ArchiveEntry("a", "a", false, 100, 100))
        assertTrue(ArchiveSecurity.assessEntries(safe).none { it.severity == ArchiveSecurity.Risk.Severity.BLOCK })

        val unsafe = listOf(ArchiveEntry("..", "../evil", false, 10, 10))
        assertTrue(ArchiveSecurity.assessEntries(unsafe).any { it.severity == ArchiveSecurity.Risk.Severity.WARNING })
    }
}
