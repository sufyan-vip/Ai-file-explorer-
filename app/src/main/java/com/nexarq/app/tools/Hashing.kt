package com.nexarq.app.tools

import com.nexarq.app.core.OperationKind
import com.nexarq.app.core.OperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class HashAlgorithm(val label: String) {
    MD5("MD5"), SHA1("SHA-1"), SHA256("SHA-256"), SHA512("SHA-512");

    val digestName: String get() = when (this) {
        MD5 -> "MD5"
        SHA1 -> "SHA-1"
        SHA256 -> "SHA-256"
        SHA512 -> "SHA-512"
    }
}

object Hashing {

    fun hash(bytes: ByteArray, algorithm: HashAlgorithm): String =
        digestHex(MessageDigest.getInstance(algorithm.digestName).digest(bytes))

    suspend fun hashFile(file: File, algorithm: HashAlgorithm, progress: (OperationProgress) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val cc = coroutineContext
            val md = MessageDigest.getInstance(algorithm.digestName)
            val total = file.length().coerceAtLeast(1)
            val opId = System.currentTimeMillis()
            var processed = 0L
            FileInputStream(file).use { input ->
                val buf = ByteArray(1024 * 1024)
                var r: Int
                while (input.read(buf).also { r = it } > 0) {
                    cc.ensureActive()
                    md.update(buf, 0, r)
                    processed += r
                    progress(OperationProgress(opId, OperationKind.HASH, "Hashing ${file.name}",
                        file.name, 0, 0, processed, total))
                }
            }
            digestHex(md.digest())
        }

    fun digestHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** Compare a computed hash against an expected value (case-insensitive). */
    fun verify(actual: String, expected: String): Boolean =
        actual.equals(expected.trim(), ignoreCase = true)
}
