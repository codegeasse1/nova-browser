package com.nova.browser.browser

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.net.URLDecoder

object Downloads {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(context: Context, response: WebResponse, onResult: (String) -> Unit) {
        val body = response.body ?: return
        val uri = response.uri
        val name = filenameFromUrl(uri)
        scope.launch {
            try {
                val bytes = body.readBytes()
                val saved = save(context, name, bytes)
                onResult(if (saved) "Downloaded: $name" else "Could not save $name")
            } catch (e: Exception) {
                onResult("Download failed: ${e.message}")
            } finally {
                runCatching { body.close() }
            }
        }
    }

    private fun filenameFromUrl(url: String?): String {
        if (url.isNullOrBlank()) return "download.bin"
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        var name = decoded.substringAfterLast('/').substringBefore('?').substringBefore('#').trim()
        if (name.isEmpty() || name.contains('\\')) name = "download"
        if (!name.contains('.')) name += mimeExtension(url)
        return name
    }

    private fun mimeExtension(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".png") -> ".png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> ".jpg"
            lower.endsWith(".gif") -> ".gif"
            lower.endsWith(".pdf") -> ".pdf"
            lower.endsWith(".mp4") || lower.endsWith(".webm") -> ".mp4"
            lower.endsWith(".mp3") -> ".mp3"
            lower.endsWith(".zip") -> ".zip"
            lower.endsWith(".apk") -> ".apk"
            lower.endsWith(".xpi") -> ".xpi"
            lower.endsWith(".crx") -> ".crx"
            else -> ".bin"
        }
    }

    private fun save(context: Context, name: String, bytes: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, guessMime(name))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nova")
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val item = context.contentResolver.insert(collection, values) ?: return false
                context.contentResolver.openOutputStream(item)?.use { it.write(bytes) } ?: return false
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nova")
                dir.mkdirs()
                File(dir, name).writeBytes(bytes)
                true
            }
        } catch (_: Exception) {
            runCatching {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Nova")
                dir.mkdirs()
                File(dir, name).writeBytes(bytes)
            }.isSuccess
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "xpi" -> "application/x-xpinstall"
        "crx" -> "application/x-chrome-extension"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
