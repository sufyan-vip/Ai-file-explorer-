package com.nexarq.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Intent helpers for opening and sharing files via the platform. */
object Intents {

    fun uriFor(context: Context, path: String): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
    }

    fun openWith(context: Context, path: String, mime: String? = null) {
        val uri = uriFor(context, path)
        val type = mime ?: FileType.mimeType(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
    }

    fun share(context: Context, path: String, mime: String? = null) {
        val uri = uriFor(context, path)
        val type = mime ?: FileType.mimeType(path)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            this.type = type
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
    }

    fun shareMultiple(context: Context, paths: List<String>) {
        val uris = paths.map { uriFor(context, it) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
    }

    fun installApk(context: Context, path: String) {
        val uri = uriFor(context, path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
