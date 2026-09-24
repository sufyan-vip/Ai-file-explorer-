package com.nexarq.app.transfer

import com.nexarq.app.core.FileSystem
import com.nexarq.app.core.FileType
import com.nexarq.app.core.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections

/**
 * Tiny embedded HTTP file server for Wi-Fi transfer between the phone and a PC
 * browser. No third-party dependencies — plain java.net sockets.
 *
 * - `GET /` or `GET /?p=<relpath>` — HTML directory listing with download links
 *   and an upload form.
 * - `GET /dl?p=<relpath>` — download a file.
 * - `POST /upload?p=<relpath>` — multipart/form-data upload (streamed to disk,
 *   never fully buffered in memory).
 *
 * The server only serves files under [rootDir]; every requested path is validated
 * with [FileSystem.safeJoin] to block traversal attacks. Bind to the Wi-Fi
 * interface IP shown to the user; anyone on the same network can reach it, so
 * the UI warns the user to stop the server when done.
 */
class HttpFileServer(private val rootDir: File, private val preferredPort: Int = 8080) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean get() = serverSocket?.let { !it.isClosed } == true
    val port: Int get() = serverSocket?.localPort ?: -1

    /** Start listening. Returns the bound port (falls back to an ephemeral port). */
    @Synchronized
    fun start(): Int {
        stop()
        val ss = try {
            ServerSocket(preferredPort)
        } catch (_: IOException) {
            ServerSocket(0)
        }
        serverSocket = ss
        acceptJob = scope.launch {
            while (isActive) {
                try {
                    val socket = ss.accept()
                    launch { handleConnection(socket) }
                } catch (_: IOException) {
                    break // socket closed by stop()
                }
            }
        }
        return ss.localPort
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    /** Best-effort LAN IPv4 address (prefers a site-local address, e.g. Wi-Fi). */
    fun localIpAddress(): String? {
        return try {
            var fallback: String? = null
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in Collections.list(ni.inetAddresses)) {
                    if (addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    if (host.contains(":")) continue // skip IPv6
                    if (addr.isSiteLocalAddress) return host
                    if (fallback == null) fallback = host
                }
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    fun baseUrl(): String? = localIpAddress()?.let { "http://$it:$port" }

    // ------------------------------------------------------------------ HTTP --

    private fun handleConnection(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 30_000
                val input = s.getInputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    respond(s.getOutputStream(), 400, "Bad Request")
                    return
                }
                val method = parts[0].uppercase()
                val target = parts[1]
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))
                when {
                    method == "GET" && (path == "/" || path.isEmpty()) -> serveListing(s.getOutputStream(), query["p"] ?: "")
                    method == "GET" && path == "/dl" -> serveDownload(s.getOutputStream(), query["p"] ?: "")
                    method == "POST" && path == "/upload" -> handleUpload(s.getOutputStream(), input, headers, query["p"] ?: "")
                    else -> respond(s.getOutputStream(), 404, "Not Found")
                }
            }
        } catch (_: Exception) {
            // Connection-level errors are ignored; the server keeps running.
        }
    }

    private fun serveListing(out: OutputStream, rel: String) {
        val dir = FileSystem.safeJoin(rootDir, rel)
        if (dir == null || !dir.isDirectory) {
            respond(out, 403, "Forbidden")
            return
        }
        val entries = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        ) ?: emptyList()
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>NEXARQ Wi-Fi transfer</title>")
            append("<style>body{font-family:sans-serif;max-width:760px;margin:24px auto;padding:0 16px}")
            append("table{width:100%;border-collapse:collapse}a{text-decoration:none}")
            append("td,th{padding:8px;border-bottom:1px solid #ddd;text-align:left}")
            append(".path{color:#555;font-size:14px}</style></head><body>")
            append("<h2>\uD83D\uDCF1 NEXARQ Wi-Fi transfer</h2>")
            append("<p class=\"path\">${escapeHtml(dir.absolutePath)}</p>")
            val parentRel = parentRelOf(rel)
            if (parentRel != null) {
                append("<p><a href=\"/?p=${urlEncode(parentRel)}\">\u2B06 Up</a></p>")
            }
            append("<h3>Upload files</h3>")
            append("<form method=\"post\" action=\"/upload?p=${urlEncode(rel)}\" enctype=\"multipart/form-data\">")
            append("<input type=\"file\" name=\"file\" multiple> ")
            append("<input type=\"submit\" value=\"Upload\"></form>")
            append("<h3>Files</h3><table><tr><th>Name</th><th>Size</th><th></th></tr>")
            for (f in entries) {
                val childRel = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                append("<tr><td>")
                if (f.isDirectory) {
                    append("\uD83D\uDCC1 <a href=\"/?p=${urlEncode(childRel)}\">${escapeHtml(f.name)}/</a>")
                    append("</td><td>—</td><td></td>")
                } else {
                    append("\uD83D\uDCC4 ${escapeHtml(f.name)}</td>")
                    append("<td>${Format.bytes(f.length())}</td>")
                    append("<td><a href=\"/dl?p=${urlEncode(childRel)}\">Download</a></td>")
                }
                append("</tr>")
            }
            append("</table><p class=\"path\">Stop the server in NEXARQ when you are done.</p>")
            append("</body></html>")
        }
        val body = html.toByteArray(Charsets.UTF_8)
        respond(
            out, 200, "OK",
            mapOf("Content-Type" to "text/html; charset=utf-8", "Content-Length" to body.size.toString()),
            body,
        )
    }

    private fun serveDownload(out: OutputStream, rel: String) {
        val file = FileSystem.safeJoin(rootDir, rel)
        if (file == null || !file.isFile) {
            respond(out, 404, "Not Found")
            return
        }
        val headers = mutableMapOf(
            "Content-Type" to FileType.mimeType(file.name),
            "Content-Length" to file.length().toString(),
            "Content-Disposition" to "attachment; filename=\"${file.name.replace("\"", "")}\"",
        )
        writeStatus(out, 200, "OK", headers)
        try {
            FileInputStream(file).use { fis ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = fis.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.flush()
            }
        } catch (_: IOException) {
            // Client disconnected mid-download; nothing to do.
        }
    }

    private fun handleUpload(out: OutputStream, input: InputStream, headers: Map<String, String>, rel: String) {
        val dir = FileSystem.safeJoin(rootDir, rel)
        if (dir == null || !dir.isDirectory) {
            respond(out, 403, "Forbidden")
            return
        }
        val contentType = headers["content-type"] ?: ""
        if (!contentType.startsWith("multipart/form-data")) {
            respond(out, 400, "Bad Request")
            return
        }
        val boundary = contentType.substringAfter("boundary=", "").trim().trim('"')
        if (boundary.isBlank()) {
            respond(out, 400, "Bad Request")
            return
        }
        try {
            MultipartParser(input, boundary).parse { filename ->
                val clean = filename.substringAfterLast('/').substringAfterLast('\\').trim()
                if (clean.isEmpty() || clean == "." || clean == "..") return@parse null
                val target = FileSystem.safeJoin(dir, clean) ?: return@parse null
                FileOutputStream(target)
            }
        } catch (e: IOException) {
            respond(out, 500, "Upload failed: ${escapeHtml(e.message ?: "")}")
            return
        }
        // Redirect back to the listing so the browser refreshes.
        respond(out, 303, "See Other", mapOf("Location" to "/?p=${urlEncode(rel)}"))
    }

    // --------------------------------------------------------------- helpers --

    private fun parentRelOf(rel: String): String? {
        if (rel.isEmpty()) return null
        val idx = rel.lastIndexOf('/')
        return if (idx <= 0) "" else rel.substring(0, idx)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else urlDecode(pair.substring(0, idx)) to urlDecode(pair.substring(idx + 1))
        }.toMap()
    }

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun urlDecode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun readLine(input: InputStream, maxLen: Int = 8192): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > maxLen) throw IOException("Header line too long")
        }
        return sb.toString()
    }

    private fun respond(out: OutputStream, code: Int, message: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null) {
        val all = headers.toMutableMap()
        if (body != null) all.putIfAbsent("Content-Length", body.size.toString())
        if (body == null) all.putIfAbsent("Content-Length", "0")
        all.putIfAbsent("Content-Type", "text/plain; charset=utf-8")
        all["Connection"] = "close"
        writeStatus(out, code, message, all)
        if (body != null) {
            out.write(body)
            out.flush()
        }
    }

    private fun writeStatus(out: OutputStream, code: Int, message: String, headers: Map<String, String>) {
        val w = OutputStreamWriter(out, Charsets.ISO_8859_1)
        w.write("HTTP/1.1 $code $message\r\n")
        for ((k, v) in headers) w.write("$k: $v\r\n")
        w.write("\r\n")
        w.flush()
    }

    /**
     * Minimal streaming multipart/form-data parser. File bytes are written
     * directly to disk as they arrive; only a 64 KB window is ever buffered.
     * For each file part, [openSink] provides the output stream (or null to skip).
     */
    private class MultipartParser(input: InputStream, boundary: String) {
        private val input: InputStream = input
        private val firstBoundary = "--$boundary"
        private val delimiter = "\r\n--$boundary".toByteArray(Charsets.ISO_8859_1)
        private val buf = ByteArray(64 * 1024)
        private var pos = 0
        private var limit = 0
        private var eof = false

        fun parse(openSink: (filename: String) -> OutputStream?) {
            val first = readLine() ?: throw IOException("Truncated upload")
            if (first != firstBoundary) throw IOException("Bad multipart body")
            while (true) {
                var filename: String? = null
                while (true) {
                    val line = readLine() ?: throw IOException("Truncated upload headers")
                    if (line.isEmpty()) break
                    if (line.lowercase().startsWith("content-disposition:")) {
                        filename = line.substringAfter("filename=", "").trim().trim('"')
                    }
                }
                val sink = if (!filename.isNullOrEmpty()) openSink(filename) else null
                try {
                    val more = copyUntilDelimiter(sink)
                    sink?.flush()
                    if (!more) return
                } finally {
                    (sink as? FileOutputStream)?.close() ?: sink?.close()
                }
            }
        }

        /**
         * Copies bytes until the part delimiter. Returns true when another part
         * follows, false at the final boundary.
         */
        private fun copyUntilDelimiter(out: OutputStream?): Boolean {
            val pending = ByteArray(delimiter.size)
            var match = 0
            while (true) {
                val b = readByte() ?: throw IOException("Truncated upload body")
                if (b == delimiter[match]) {
                    pending[match] = b
                    match++
                    if (match == delimiter.size) {
                        val c1 = readByte() ?: throw IOException("Truncated upload body")
                        val c2 = readByte() ?: throw IOException("Truncated upload body")
                        return !(c1 == '-'.code.toByte() && c2 == '-'.code.toByte())
                    }
                } else {
                    if (match > 0) {
                        out?.write(pending, 0, match)
                        match = 0
                        // Re-examine this byte: it may start a new match.
                        if (b == delimiter[0]) {
                            pending[0] = b
                            match = 1
                            continue
                        }
                    }
                    out?.write(b.toInt())
                }
            }
        }

        private fun readByte(): Byte? {
            if (pos >= limit) {
                if (eof) return null
                val n = input.read(buf)
                if (n < 0) {
                    eof = true
                    return null
                }
                pos = 0
                limit = n
            }
            return buf[pos++]
        }

        private fun readLine(): String? {
            val sb = StringBuilder()
            while (true) {
                val b = readByte() ?: return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code.toByte()) break
                if (b != '\r'.code.toByte()) sb.append(b.toInt().toChar())
                if (sb.length > 8192) throw IOException("Header line too long")
            }
            return sb.toString()
        }
    }
}
