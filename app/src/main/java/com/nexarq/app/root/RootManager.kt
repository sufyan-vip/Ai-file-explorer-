package com.nexarq.app.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Safe root-abstraction layer.
 *
 * - Never assumes root exists; detection is passive.
 * - Never executes arbitrary user/archive-derived strings: commands are built from
 *   typed, whitelisted verbs with individually quoted arguments.
 * - Every command has a timeout, captured stdout/stderr and an exit code.
 */
object RootManager {

    private const val TAG = "NexarqRoot"

    private val suCandidates = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/su/bin/su", "/data/adb/magisk/busybox", "/system/bin/magisk",
    )

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean) {
        val success: Boolean get() = exitCode == 0
        val output: String get() = stdout.ifBlank { stderr }
    }

    data class RootState(val available: Boolean, val suPath: String?, val version: String?)

    @Volatile
    private var cachedState: RootState? = null

    fun isRooted(): Boolean = detect().available

    fun detect(): RootState {
        cachedState?.let { return it }
        val state = runCatching { detectInternal() }.getOrDefault(RootState(false, null, null))
        cachedState = state
        return state
    }

    private fun detectInternal(): RootState {
        for (path in suCandidates) {
            val f = File(path)
            if (f.exists() && f.canExecute()) {
                return RootState(true, path, null)
            }
        }
        // PATH-based detection
        val pathEnv = System.getenv("PATH") ?: "/sbin:/system/bin:/system/xbin"
        for (dir in pathEnv.split(':')) {
            val f = File(dir, "su")
            if (f.exists() && f.canExecute()) return RootState(true, f.absolutePath, null)
        }
        return RootState(false, null, null)
    }

    fun refresh() {
        cachedState = null
        detect()
    }

    // ---------------------------------------------------------------- EXEC

    /**
     * Execute a command through `su`. Arguments are shell-quoted individually.
     * The command itself must be one of the known-safe verbs (checked by [isAllowed]).
     */
    suspend fun execRoot(args: List<String>, timeoutMs: Long = 15_000): ShellResult = withContext(Dispatchers.IO) {
        val state = detect()
        if (!state.available) return@withContext ShellResult(-1, "", "Root is not available", false)
        if (args.isEmpty()) return@withContext ShellResult(-1, "", "Empty command", false)
        if (!isAllowed(args)) return@withContext ShellResult(-2, "", "Command blocked by safety policy", false)
        val commandLine = args.joinToString(" ") { shellQuote(it) } + "\nexit\n"
        runSuProcess(commandLine, timeoutMs)
    }

    /** Very short allow-list of verbs the app will ever invoke via root. */
    private val allowedVerbs = setOf("ls", "cat", "stat", "id", "mount", "df", "cp", "mv", "rm", "mkdir",
        "touch", "chmod", "chown", "chgrp", "ln", "readlink", "du", "find", "echo", "pm", "getprop")

    private val dangerousPatterns = listOf(
        Regex("rm\\s+-rf\\s+/\\s*$"),
        Regex("rm\\s+-rf\\s+/\\s+\\*"),
        Regex("mkfs"),
        Regex("dd\\s+of=/dev"),
        Regex(">\\s*/dev/"),
    )

    private fun isAllowed(args: List<String>): Boolean {
        val verb = args.first()
        if (verb !in allowedVerbs) return false
        val line = args.joinToString(" ")
        return dangerousPatterns.none { it.containsMatchIn(line) }
    }

    private fun runSuProcess(commandLine: String, timeoutMs: Long): ShellResult {
        val process = ProcessBuilder("su")
            .redirectErrorStream(false)
            .start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val outThread = Thread { process.inputStream.bufferedReader().use { r -> r.forEachLine { stdout.appendLine(it) } } }
        val errThread = Thread { process.errorStream.bufferedReader().use { r -> r.forEachLine { stderr.appendLine(it) } } }
        outThread.start()
        errThread.start()

        runCatching {
            process.outputStream.use { os ->
                os.write(commandLine.toByteArray())
                os.flush()
            }
        }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

        if (!finished) {
            process.destroy()
            runCatching { process.waitFor(2, TimeUnit.SECONDS) }
            process.destroyForcibly()
            return ShellResult(-1, "", "Command timed out", timedOut = true)
        }
        outThread.join(2000)
        errThread.join(2000)
        return ShellResult(process.exitValue(), stdout.toString(), stderr.toString(), timedOut = false)
    }

    private fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    // ---------------------------------------------------------------- CONVENIENCE

    suspend fun listDirectoryDetailed(path: String): List<FileStat> = withContext(Dispatchers.IO) {
        val res = execRoot(listOf("ls", "-la", path))
        if (!res.success) return@withContext emptyList()
        parseLsOutput(res.stdout, path)
    }

    suspend fun readFile(path: String, maxBytes: Int = 256 * 1024): String = withContext(Dispatchers.IO) {
        val res = execRoot(listOf("cat", path))
        if (!res.success) throw SecurityException(res.output.trim())
        res.stdout.take(maxBytes)
    }

    suspend fun getMounts(): List<MountInfo> = withContext(Dispatchers.IO) {
        val res = execRoot(listOf("mount"))
        if (!res.success) return@withContext emptyList()
        res.stdout.lineSequence().filter { it.isNotBlank() }.map { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 6)
            MountInfo(
                device = parts.getOrElse(0) { "" },
                mountPoint = parts.getOrElse(1) { "" },
                fsType = parts.getOrElse(2) { "" },
                options = parts.getOrElse(3) { "" },
            )
        }.toList()
    }

    suspend fun chmod(path: String, mode: String): ShellResult =
        execRoot(listOf("chmod", mode, path))

    suspend fun chown(path: String, owner: String, group: String? = null): ShellResult {
        val spec = if (group != null) "$owner:$group" else owner
        return execRoot(listOf("chown", spec, path))
    }

    suspend fun createSymlink(target: String, linkPath: String): ShellResult =
        execRoot(listOf("ln", "-s", target, linkPath))

    private fun parseLsOutput(output: String, basePath: String): List<FileStat> {
        val result = mutableListOf<FileStat>()
        for (line in output.lineSequence()) {
            if (line.isBlank() || line.startsWith("total")) continue
            val parts = line.trim().split(Regex("\\s+"))
            // perms links owner group size month day time name...
            if (parts.size < 9) continue
            val perms = parts[0]
            val owner = parts[2]
            val group = parts[3]
            val size = parts[4].toLongOrNull() ?: 0L
            val rawName = parts.subList(8, parts.size).joinToString(" ")
            val linkTarget = rawName.substringAfter(" -> ", "").takeIf { rawName.contains(" -> ") }
            val name = rawName.substringBefore(" -> ")
            result.add(
                FileStat(
                    name = name,
                    path = if (basePath.endsWith("/")) basePath + name else "$basePath/$name",
                    permissions = perms,
                    owner = owner,
                    group = group,
                    size = size,
                    isDirectory = perms.startsWith('d'),
                    isSymlink = perms.startsWith('l'),
                    linkTarget = linkTarget,
                )
            )
        }
        return result
    }
}

data class FileStat(
    val name: String,
    val path: String,
    val permissions: String,
    val owner: String,
    val group: String,
    val size: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val linkTarget: String? = null,
)

data class MountInfo(
    val device: String,
    val mountPoint: String,
    val fsType: String,
    val options: String,
)
