package com.nexarq.app.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

/**
 * Password-based file encryption using AES-256-GCM.
 *
 * Format: magic(4) "NXQ\x01" | salt(16) | iv(12) | ciphertext+tag.
 * Keys are derived with PBKDF2-HMAC-SHA256 (120k iterations). GCM authentication
 * means a wrong password fails loudly instead of producing garbage.
 *
 * Encrypted files use the ".nxq" extension so the browser can offer decryption.
 */
object CryptoFile {

    const val EXTENSION = "nxq"

    private val MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'Q'.code.toByte(), 0x01)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val HEADER_LEN = 4 + SALT_LEN + IV_LEN

    fun isEncryptedFile(name: String): Boolean =
        name.substringAfterLast('.', "").equals(EXTENSION, ignoreCase = true)

    fun encryptedName(src: File): String = "${src.name}.$EXTENSION"

    fun decryptedName(src: File): String {
        val name = src.name
        return if (isEncryptedFile(name)) {
            name.dropLast(EXTENSION.length + 1).ifEmpty { "decrypted" }
        } else {
            "$name.decrypted"
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypt [src] into [dst]. Reports (bytesDone, bytesTotal). */
    suspend fun encryptFile(
        src: File,
        dst: File,
        password: CharArray,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        if (!src.isFile) throw IOException("Not a file: ${src.path}")
        require(password.isNotEmpty()) { "Password is empty" }
        if (dst.exists()) throw IOException("Output already exists: ${dst.path}")

        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))

        val total = src.length()
        var done = 0L
        dst.parentFile?.mkdirs()
        try {
            FileOutputStream(dst).use { fos ->
                fos.write(MAGIC)
                fos.write(salt)
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(src).use { fis ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            cc.ensureActive()
                            val n = fis.read(buf)
                            if (n < 0) break
                            if (n > 0) {
                                cos.write(buf, 0, n)
                                done += n
                                onProgress(done, total)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            dst.delete()
            throw e
        } catch (e: Exception) {
            dst.delete()
            throw e
        }
        onProgress(total, total)
    }

    /**
     * Decrypt [src] (must be a .nxq file) into [dst]. A wrong password or a
     * corrupted file throws IOException; no partial output is left behind.
     */
    suspend fun decryptFile(
        src: File,
        dst: File,
        password: CharArray,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Unit = withContext(Dispatchers.IO) {
        val cc = coroutineContext
        if (!src.isFile) throw IOException("Not a file: ${src.path}")
        require(password.isNotEmpty()) { "Password is empty" }
        if (dst.exists()) throw IOException("Output already exists: ${dst.path}")

        val header = ByteArray(HEADER_LEN)
        FileInputStream(src).use { fis ->
            var read = 0
            while (read < HEADER_LEN) {
                val n = fis.read(header, read, HEADER_LEN - read)
                if (n < 0) break
                read += n
            }
            if (read < HEADER_LEN || !header.copyOfRange(0, 4).contentEquals(MAGIC)) {
                throw IOException("Not a NEXARQ encrypted file")
            }
        }
        val salt = header.copyOfRange(4, 4 + SALT_LEN)
        val iv = header.copyOfRange(4 + SALT_LEN, HEADER_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))

        val total = (src.length() - HEADER_LEN).coerceAtLeast(0L)
        var done = 0L
        dst.parentFile?.mkdirs()
        try {
            FileInputStream(src).use { fis ->
                // Skip the header we already validated.
                var skipped = 0L
                while (skipped < HEADER_LEN) {
                    val n = fis.skip(HEADER_LEN - skipped)
                    if (n <= 0) throw IOException("Could not read file header")
                    skipped += n
                }
                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(dst).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            cc.ensureActive()
                            val n = try {
                                cis.read(buf)
                            } catch (e: IOException) {
                                throw toAuthError(e)
                            }
                            if (n < 0) break
                            if (n > 0) {
                                fos.write(buf, 0, n)
                                done += n
                                onProgress(done, total)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            dst.delete()
            throw e
        } catch (e: Exception) {
            dst.delete()
            throw toAuthError(e)
        }
        onProgress(total, total)
    }

    /**
     * Best-effort estimate of the decrypted size (ciphertext minus GCM tag).
     * Used only for progress display; returns -1 when unknown.
     */
    fun estimatedDecryptedSize(encrypted: File): Long {
        val s = encrypted.length() - HEADER_LEN - GCM_TAG_BITS / 8
        return if (s >= 0) s else -1L
    }

    private fun toAuthError(e: Exception): IOException {
        // javax.crypto.AEADBadTagException is wrapped in IOException by CipherInputStream.
        var cause: Throwable? = e
        while (cause != null) {
            if (cause.javaClass.name == "javax.crypto.AEADBadTagException") {
                return IOException("Wrong password or file is corrupted")
            }
            cause = cause.cause
        }
        return if (e is IOException) e else IOException(e.message ?: "Decryption failed", e)
    }
}
