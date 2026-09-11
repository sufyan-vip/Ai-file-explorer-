package com.nexarq.app.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.nexarq.app.MainActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Handles "Send to NEXARQ" intents. Stream URIs are copied into the app's cache so the
 * file manager can operate on a real path, then the main activity opens at that location.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) { finish(); return }
        val uris = mutableListOf<Uri>()
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }

        if (uris.isEmpty()) {
            Toast.makeText(this, "No file received", Toast.LENGTH_SHORT).show()
            openMain(null)
            return
        }

        val cacheDir = File(cacheDir, "shared")
        cacheDir.mkdirs()
        thread {
            val savedPaths = mutableListOf<String>()
            for (uri in uris) {
                runCatching {
                    val name = queryDisplayName(uri) ?: "shared_${System.currentTimeMillis()}"
                    val target = File(cacheDir, name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    savedPaths.add(target.absolutePath)
                }
            }
            runOnUiThread {
                openMain(savedPaths.firstOrNull())
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }

    private fun openMain(path: String?) {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (path != null) putExtra("open_path", path)
        }
        startActivity(i)
        finish()
    }
}
