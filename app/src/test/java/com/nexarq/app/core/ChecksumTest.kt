package com.nexarq.app.core

import com.nexarq.app.tools.Hashing
import com.nexarq.app.tools.HashAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumTest {

    @Test
    fun md5_matchesKnownVector() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Hashing.hash(ByteArray(0), HashAlgorithm.MD5))
        assertEquals("5d41402abc4b2a76b9719d911017c592", Hashing.hash("hello".toByteArray(), HashAlgorithm.MD5))
    }

    @Test
    fun sha256_matchesKnownVector() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            Hashing.hash("hello".toByteArray(), HashAlgorithm.SHA256),
        )
    }

    @Test
    fun verify_isCaseInsensitive() {
        assertTrue(Hashing.verify("ABC123", "abc123"))
        assertFalse(Hashing.verify("ABC123", "abc124"))
    }

    @Test
    fun digestHex_masksHighBytes() {
        // bytes with high bit set must not sign-extend
        assertEquals("ff00", Hashing.digestHex(byteArrayOf(0xFF.toByte(), 0x00)))
    }
}
