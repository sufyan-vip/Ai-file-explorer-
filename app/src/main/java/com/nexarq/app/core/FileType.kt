package com.nexarq.app.core

/** File type categorization used for icons, filters and the analyzer. */
object FileType {
    enum class Category(val label: String) {
        FOLDER("Folder"), IMAGE("Image"), VIDEO("Video"), AUDIO("Audio"),
        DOCUMENT("Document"), ARCHIVE("Archive"), APK("APK"), CODE("Code"),
        TEXT("Text"), BINARY("Binary"), OTHER("Other"),
    }

    private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "ico")
    private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "m4v", "ts")
    private val audioExt = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "mid", "midi")
    private val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub", "pages", "numbers", "key")
    private val archiveExt = setOf("zip", "7z", "tar", "gz", "gzip", "bz2", "bzip2", "xz", "zst", "zstd", "rar", "jar", "apk", "iso", "tgz", "tbz2", "txz", "lz4", "lz", "z", "cab", "arj", "deb", "rpm", "xar", "cpio")
    private val codeExt = setOf("kt", "java", "c", "cpp", "h", "hpp", "cs", "py", "js", "ts", "tsx", "jsx", "go", "rs", "rb", "php", "swift", "sh", "bat", "sql", "html", "css", "scss", "xml", "json", "yaml", "yml", "gradle", "toml", "properties")
    private val textExt = setOf("txt", "md", "log", "csv", "ini", "cfg", "conf", "srt", "nfo")
    private val mediaExt = imageExt + videoExt + audioExt

    fun category(name: String, isDirectory: Boolean): Category {
        if (isDirectory) return Category.FOLDER
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return Category.OTHER
        return when {
            imageExt.contains(ext) -> Category.IMAGE
            videoExt.contains(ext) -> Category.VIDEO
            audioExt.contains(ext) -> Category.AUDIO
            docExt.contains(ext) -> Category.DOCUMENT
            ext == "apk" -> Category.APK
            archiveExt.contains(ext) -> Category.ARCHIVE
            codeExt.contains(ext) -> Category.CODE
            textExt.contains(ext) -> Category.TEXT
            else -> Category.BINARY
        }
    }

    fun isTextLike(ext: String): Boolean =
        codeExt.contains(ext) || textExt.contains(ext) || ext in setOf("json", "xml", "md", "log", "txt")

    fun isImage(ext: String): Boolean = imageExt.contains(ext)
    fun isVideo(ext: String): Boolean = videoExt.contains(ext)
    fun isAudio(ext: String): Boolean = audioExt.contains(ext)
    fun isMedia(name: String): Boolean = mediaExt.contains(name.substringAfterLast('.', "").lowercase())

    /** Best-effort MIME type from extension. */
    fun mimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "html" -> "text/html"
            "csv" -> "text/csv"
            "zip" -> "application/zip"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
