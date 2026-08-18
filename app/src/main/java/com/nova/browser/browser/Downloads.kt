package com.nova.browser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import com.nova.browser.store.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

data class DownloadItem(
    val id: Long,
    val title: String,
    val url: String,
    val filename: String,
    val size: Long,
    val state: String,
    val date: Long,
)

object Downloads {
    val items = mutableStateListOf<DownloadItem>()
    private val io = CoroutineScope(Dispatchers.IO)
    private var nextId = 1L

    fun restore() {
        runCatching {
            val saved = Store.loadObjects(Store.KEY_DOWNLOADS_LIST)
            items.clear()
            saved.forEach { o ->
                items.add(
                    DownloadItem(
                        id = o.optLong("id"),
                        title = o.optString("t"),
                        url = o.optString("u"),
                        filename = o.optString("f"),
                        size = o.optLong("s"),
                        state = o.optString("st"),
                        date = o.optLong("d"),
                    ),
                )
            }
            nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
        }
    }

    fun start(context: Context, url: String, userAgent: String, contentDisposition: String?, mimetype: String?, contentLength: Long, onResult: (String) -> Unit) {
        val id = nextId++
        val name = filenameFrom(url, contentDisposition)
        val item = DownloadItem(id, name, url, name, contentLength, "downloading", System.currentTimeMillis())
        items.add(0, item)
        persist()

        io.launch {
            var bytes: ByteArray? = null
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                if (userAgent.isNotBlank()) conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "*/*")
                val code = conn.responseCode
                if (code in 200..299) {
                    bytes = conn.inputStream.use { it.readBytes() }
                } else {
                    throw Exception("HTTP $code")
                }
            } catch (e: Exception) {
                val i = items.indexOfFirst { it.id == id }
                if (i >= 0) {
                    items[i] = items[i].copy(state = "failed")
                    persist()
                }
                onResult("Download failed: ${e.message}")
                return@launch
            }

            val data = bytes ?: return@launch
            val saved = save(context, name, data)
            val i = items.indexOfFirst { it.id == id }
            if (i >= 0) {
                items[i] = items[i].copy(state = if (saved) "done" else "failed", size = data.size.toLong())
                persist()
            }
            onResult(if (saved) "Downloaded: $name" else "Could not save $name")
        }
    }

    fun open(context: Context, item: DownloadItem) {
        val file = fileFor(context, item.filename) ?: return
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, guessMime(item.filename))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open ${item.filename}"))
        } catch (_: Exception) {
        }
    }

    fun share(context: Context, item: DownloadItem) {
        val file = fileFor(context, item.filename) ?: return
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = guessMime(item.filename)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${item.filename}"))
        } catch (_: Exception) {
        }
    }

    fun delete(context: Context, item: DownloadItem) {
        fileFor(context, item.filename)?.let { runCatching { it.delete() } }
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) items.removeAt(i)
        persist()
    }

    fun clearAll(context: Context) {
        items.forEach { fileFor(context, it.filename)?.let { f -> runCatching { f.delete() } } }
        items.clear()
        persist()
    }

    private fun fileFor(context: Context, filename: String): File? {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads"), "Nova")
        return File(dir, filename)
    }

    private fun save(context: Context, name: String, data: ByteArray): Boolean =
        runCatching {
            val out = fileFor(context, name) ?: return@runCatching false
            if (!out.parentFile.exists()) out.parentFile.mkdirs()
            out.writeBytes(data)
            true
        }.getOrDefault(false)

    private fun persist() {
        val list = items.map {
            JSONObject()
                .put("id", it.id)
                .put("t", it.title)
                .put("u", it.url)
                .put("f", it.filename)
                .put("s", it.size)
                .put("st", it.state)
                .put("d", it.date)
        }
        Store.saveObjects(Store.KEY_DOWNLOADS_LIST, list)
    }

    private fun filenameFrom(url: String, contentDisposition: String?): String {
        contentDisposition?.let { cd ->
            val m = Regex("filename\\*?=(?:UTF-8'')?[\"']?([^\"';\\s]+)").find(cd)
            if (m != null) {
                val name = runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrDefault(m.groupValues[1])
                if (name.isNotBlank()) return sanitize(name)
            }
        }
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        var name = decoded.substringAfterLast('/').substringBefore('?').substringBefore('#').trim()
        if (name.isEmpty()) name = "download"
        return sanitize(name)
    }

    private fun sanitize(name: String): String {
        var n = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (!n.contains('.')) n += ".bin"
        if (n.length > 120) n = n.take(100) + n.substringAfterLast('.')
        return n
    }

    fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "mp4", "webm", "mkv" -> "video/mp4"
        "mp3", "ogg", "wav", "m4a" -> "audio/mpeg"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "txt", "csv" -> "text/plain"
        "crx" -> "application/x-chrome-extension"
        "xpi" -> "application/x-xpinstall"
        else -> "application/octet-stream"
    }
}
