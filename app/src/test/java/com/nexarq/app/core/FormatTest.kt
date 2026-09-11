package com.nexarq.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun bytes_formatsCorrectly() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.0 KB", Format.bytes(1024))
        assertEquals("1.5 MB", Format.bytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("—", Format.bytes(-1))
    }

    @Test
    fun ratio_computesCompression() {
        assertEquals("50.0%", Format.ratio(50, 100))
        assertEquals("—", Format.ratio(50, 0))
    }

    @Test
    fun eta_computes() {
        assertEquals("—", Format.eta(100, 0))
        assertTrue(Format.eta(2048, 1024).endsWith("s"))
    }
}
