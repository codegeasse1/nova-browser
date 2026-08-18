package com.nova.browser.ext

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.store.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class ContentScript(
    val matches: List<Regex>,
    val js: List<String>,
    val css: List<String>,
)

data class ExtensionUi(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val permissions: List<String>,
    val contentScripts: List<ContentScript> = emptyList(),
)

object ExtensionManager {
    val extensions = mutableStateListOf<ExtensionUi>()
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    private val io = CoroutineScope(Dispatchers.IO)

    fun attach() {
        loadInstalled()
    }

    fun refresh() {
        loadInstalled()
    }

    fun loadInstalled() {
        val context = com.nova.browser.App.context
        val list = mutableListOf<ExtensionUi>()
        val disabled = Store.disabledExtensions()

        runCatching {
            val assets = context.assets.list("extensions") ?: emptyArray()
            assets.forEach { dir ->
                val builtIn = readExtension(context.assets.open("extensions/$dir/manifest.json").bufferedReader().use { it.readText() }, dir, isBuiltIn = true)
                if (builtIn != null) list.add(builtIn)
            }
        }

        val extRoot = File(context.filesDir, "extensions")
        runCatching {
            extRoot.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val mf = File(dir, "manifest.json")
                    if (mf.exists()) {
                        val ext = readExtension(mf.readText(), dir.name, isBuiltIn = false)
                        if (ext != null) list.add(ext)
                    }
                }
            }
        }

        synchronized(extensions) {
            extensions.clear()
            extensions.addAll(list.map { if (it.id in disabled) it.copy(enabled = false) else it })
        }
    }

    private fun readExtension(jsonText: String, id: String, isBuiltIn: Boolean): ExtensionUi? {
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val name = json.optString("name", id)
        val version = json.optString("version", "1.0")
        val description = json.optString("description", "")
        val permissions = (json.optJSONArray("permissions")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()).distinct()

        val scripts = mutableListOf<ContentScript>()
        json.optJSONArray("content_scripts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val cs = arr.optJSONObject(i) ?: continue
                val matches = (cs.optJSONArray("matches")?.let { m ->
                    (0 until m.length()).mapNotNull { patternToRegex(m.optString(it)) }
                } ?: emptyList())
                val js = (cs.optJSONArray("js")?.let { m -> (0 until m.length()).mapNotNull { m.optString(it) } } ?: emptyList())
                val css = (cs.optJSONArray("css")?.let { m -> (0 until m.length()).mapNotNull { m.optString(it) } } ?: emptyList())
                if (js.isNotEmpty() || css.isNotEmpty()) {
                    scripts.add(ContentScript(matches, js, css))
                }
            }
        }
        return ExtensionUi(id, name, version, description, enabled = true, isBuiltIn = isBuiltIn, permissions = permissions, contentScripts = scripts)
    }

    private fun patternToRegex(p: String): Regex? {
        if (p == "<all_urls>") return Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)
        val m = Regex("^(\\*|https|http|file|ftp)://(\\*|[^/]+)(/.*)?$").find(p) ?: return null
        val scheme = if (m.groupValues[1] == "*") "[a-z][a-z0-9+.-]*" else Regex.escape(m.groupValues[1])
        val hostRaw = m.groupValues[2]
        val hostRe = if (hostRaw == "*") {
            "[^/]*"
        } else {
            hostRaw.split(".").joinToString("\\.") { seg ->
                if (seg == "*") "(?:[^./]+\\.)?" else Regex.escape(seg)
            }
        }
        val path = m.groupValues[3] ?: "/"
        val pathRe = path.replace("*", ".*")
        return runCatching { Regex("^$scheme://$hostRe$pathRe", RegexOption.IGNORE_CASE) }.getOrNull()
    }

    fun injectInto(view: WebView, url: String) {
        if (url.isBlank() || !url.startsWith("http")) return
        runCatching {
            synchronized(extensions) {
                for (ext in extensions) {
                    if (!ext.enabled) continue
                    for (cs in ext.contentScripts) {
                        val ok = cs.matches.isEmpty() || cs.matches.any { it.matches(url) }
                        if (!ok) continue
                        cs.css.forEach { cssFile ->
                            val css = readAssetOrFile(ext, cssFile) ?: return@forEach
                            view.evaluateJavascript(
                                "(function(){var s=document.createElement('style');s.setAttribute('data-nova-ext','${ext.id}');s.textContent=" +
                                    JSONObject.quote(css) +
                                    ";document.documentElement.appendChild(s);})()", null,
                            )
                        }
                        cs.js.forEach { jsFile ->
                            val js = readAssetOrFile(ext, jsFile) ?: return@forEach
                            view.evaluateJavascript("(function(){\n" + js + "\n})()", null)
                        }
                    }
                }
            }
        }
    }

    private fun readAssetOrFile(ext: ExtensionUi, name: String): String? {
        val context = com.nova.browser.App.context
        if (ext.isBuiltIn) {
            return runCatching { context.assets.open("extensions/${ext.id}/$name").bufferedReader().use { it.readText() } }.getOrNull()
        }
        val file = File(File(context.filesDir, "extensions"), "${ext.id}/$name")
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun installFromUri(context: Context, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        if (busy) return
        busy = true
        message = null
        io.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("could not read file")
                installBytes(context, bytes, onDone)
            } catch (e: Exception) {
                busy = false
                message = "Import failed: ${e.message}"
                onDone(false)
            }
        }
    }

    fun installFromUrl(context: Context, url: String, onDone: (Boolean) -> Unit = {}) {
        if (busy) return
        busy = true
        message = null
        io.launch {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20000
                conn.readTimeout = 40000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")
                conn.setRequestProperty("Accept", "*/*")
                val code = conn.responseCode
                if (code == 404) throw Exception("extension not found (check the store link or ID)")
                if (code !in 200..299) throw Exception("HTTP $code")
                val bytes = conn.inputStream.use { it.readBytes() }
                installBytes(context, bytes, onDone)
            } catch (e: Exception) {
                busy = false
                message = "Could not fetch extension: ${e.message}"
                onDone(false)
            }
        }
    }

    fun chromeStoreIdFrom(input: String): String? {
        val t = input.trim()
        if (t.isBlank()) return null
        return Regex("[a-p]{32}").find(t)?.value
    }

    fun installFromChromeStore(context: Context, input: String, onDone: (Boolean) -> Unit = {}) {
        val id = chromeStoreIdFrom(input)
        if (id == null) {
            message = "No Chrome extension ID found — paste a store link (chromewebstore.google.com/detail/...) or the 32-character ID."
            onDone(false)
            return
        }
        val url = "https://clients2.google.com/service/update2/crx?response=redirect&acceptformat=crx2,crx3&prodversion=136.0.0.0&x=id%3D$id%26uc"
        installFromUrl(context, url, onDone)
    }

    private fun installBytes(context: Context, bytes: ByteArray, onDone: (Boolean) -> Unit) {
        try {
            val zipBytes = stripCrxHeader(bytes)
            val zipEntries = readZipEntries(zipBytes)
            val manifestRaw = zipEntries["manifest.json"]
                ?: throw Exception("manifest.json not found — not a valid extension package")
            val manifest = JSONObject(String(manifestRaw, Charsets.UTF_8))

            val hasContentScripts = manifest.optJSONArray("content_scripts") != null
            if (!hasContentScripts) {
                busy = false
                message = "This extension has no content scripts — Nova only supports content-script extensions (the engine is Chromium/WebView)."
                onDone(false)
                return
            }

            val id = "ext-" + sha1(bytes).take(10)
            val dir = File(context.filesDir, "extensions/$id")
            dir.deleteRecursively()
            dir.mkdirs()
            for ((name, content) in zipEntries) {
                if (name.contains("..")) continue
                val out = File(dir, name)
                out.parentFile?.mkdirs()
                out.writeBytes(content)
            }

            busy = false
            message = "Installed \"${manifest.optString("name", id)}\""
            Store.setExtensionEnabled(id, true)
            refresh()
            onDone(true)
        } catch (e: Exception) {
            busy = false
            message = "Could not install extension: ${e.message}"
            onDone(false)
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    val data = zin.readBytes()
                    map[name] = data
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return map
    }

    private fun stripCrxHeader(bytes: ByteArray): ByteArray {
        if (bytes.size < 12) return bytes
        val magic = bytes[0] == 'C'.code.toByte() && bytes[1] == 'r'.code.toByte() && bytes[2] == '2'.code.toByte() && bytes[3] == '4'.code.toByte()
        if (!magic) return bytes
        val version = leInt(bytes, 4)
        return when (version) {
            2 -> {
                if (bytes.size < 16) return bytes
                val pubLen = leInt(bytes, 8)
                val sigLen = leInt(bytes, 12)
                val offset = 16 + pubLen + sigLen
                if (offset >= bytes.size) bytes else bytes.copyOfRange(offset, bytes.size)
            }
            3 -> {
                if (bytes.size < 12) return bytes
                val headerLen = leInt(bytes, 8)
                val offset = 12 + headerLen
                if (offset >= bytes.size) bytes else bytes.copyOfRange(offset, bytes.size)
            }
            else -> bytes
        }
    }

    private fun leInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private fun sha1(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun setEnabled(ext: ExtensionUi, enabled: Boolean) {
        Store.setExtensionEnabled(ext.id, enabled)
        val i = extensions.indexOfFirst { it.id == ext.id }
        if (i >= 0) extensions[i] = extensions[i].copy(enabled = enabled)
    }

    fun uninstall(ext: ExtensionUi) {
        if (ext.isBuiltIn) {
            message = "Bundled extensions can't be uninstalled (disable them instead)"
            return
        }
        val dir = File(com.nova.browser.App.context.filesDir, "extensions/${ext.id}")
        runCatching { dir.deleteRecursively() }
        Store.setExtensionEnabled(ext.id, false)
        val i = extensions.indexOfFirst { it.id == ext.id }
        if (i >= 0) extensions.removeAt(i)
        message = "Uninstalled \"${ext.name}\""
    }
}
