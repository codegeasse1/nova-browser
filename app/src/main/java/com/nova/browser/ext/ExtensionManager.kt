package com.nova.browser.ext

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.App
import com.nova.browser.browser.BrowserCore
import com.nova.browser.store.Store
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

data class ExtensionUi(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val permissions: List<String>,
)

object ExtensionManager {
    val extensions = mutableStateListOf<ExtensionUi>()
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var openAmoSearch by mutableStateOf<String?>(null)

    private val live = HashMap<String, WebExtension>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private const val NATIVE_APP = "nova"
    const val SHIELD_ID = "nova-shield@nova.browser"

    private val BUILT_INS = listOf(
        "nightshift@nova.browser",
        "imageblocker@nova.browser",
        "textsizer@nova.browser",
        SHIELD_ID,
    )

    private val DEFAULT_OFF = listOf(
        "nightshift@nova.browser",
        "imageblocker@nova.browser",
        "textsizer@nova.browser",
    )

    private fun controller(): WebExtensionController = App.geckoRuntime.webExtensionController

    private fun safe(block: () -> Unit) {
        runCatching { block() }.onFailure { t ->
            Log.w("Nova", "Extension callback error", t)
        }
    }

    private fun voidOp(result: GeckoResult<*>, what: String) {
        result.accept({}, { t -> Log.w("Nova", "$what: ${t?.message}") })
    }

    fun attach() {
        safe {
            controller().setPromptDelegate(object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>,
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, false, false))
                }

                override fun onUpdatePrompt(
                    extension: WebExtension,
                    newPermissions: Array<String>,
                    newOrigins: Array<String>,
                    newDataCollectionPermissions: Array<String>,
                ): GeckoResult<AllowOrDeny>? {
                    return GeckoResult.allow()
                }
            })
            if (!Store.extDefaultsApplied) {
                Store.extDefaultsApplied = true
                DEFAULT_OFF.forEach { Store.setExtensionEnabled(it, false) }
            }
            BUILT_INS.forEach { ensureBuiltIn(it) }
            setShieldEnabled(Store.adblockLevel != "off")
            refresh()
        }
    }

    private fun ensureBuiltIn(id: String) {
        val folder = id.removeSuffix("@nova.browser")
        controller().ensureBuiltIn("resource://android/assets/extensions/$folder/", id)
            .accept({ ext ->
                safe {
                    if (ext != null) {
                        live[id] = ext
                        if (id in Store.disabledExtensions()) {
                            voidOp(controller().disable(ext, WebExtensionController.EnableSource.USER), "disable built-in $id")
                        }
                        if (id == SHIELD_ID) attachShieldBridge(ext)
                        refresh()
                    }
                }
            }, { t ->
                Log.w("Nova", "Could not load built-in extension $id: ${t?.message}")
            })
    }

    private fun attachShieldBridge(ext: WebExtension) {
        ext.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onMessage(
                nativeApp: String,
                message: Any,
                sender: WebExtension.MessageSender,
            ): GeckoResult<Any>? {
                if (message !is JSONObject) return null
                when (message.optString("type", "")) {
                    "getConfig" -> {
                        val out = JSONObject()
                        out.put("level", Store.adblockLevel)
                        out.put("blockedDomains", JSONArray(Store.blockedDomains()))
                        out.put("whitelistedDomains", JSONArray(Store.whitelistedDomains()))
                        return GeckoResult.fromValue<Any>(out)
                    }
                    "stats" -> {
                        val tabsArr = message.optJSONArray("tabs")
                        if (tabsArr != null) {
                            mainHandler.post {
                                safe {
                                    for (i in 0 until tabsArr.length()) {
                                        val pair = tabsArr.optJSONArray(i) ?: continue
                                        val tid = pair.optInt(0)
                                        val n = pair.optInt(1)
                                        if (tid > 0 && n > 0) {
                                            val target = BrowserCore.tabs.firstOrNull { it.id == tid }
                                            if (target != null) BrowserCore.reportBlocked(tid, n)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return GeckoResult.fromValue<Any>(JSONObject())
            }
        }, NATIVE_APP)
    }

    fun refresh() {
        safe {
            controller().list().accept({ list ->
                safe {
                    synchronized(extensions) {
                        live.clear()
                        extensions.clear()
                        val disabled = Store.disabledExtensions()
                        for (ext in list ?: emptyList()) {
                            live[ext.id] = ext
                            val md = ext.metaData
                            val name = runCatching { md.name?.ifBlank { ext.id } }.getOrDefault(ext.id) ?: ext.id
                            val perms = runCatching { md.requiredPermissions?.toList() ?: emptyList() }.getOrDefault(emptyList())
                            val version = runCatching { md.version }.getOrDefault("1.0") ?: "1.0"
                            val desc = runCatching { md.description }.getOrDefault("") ?: ""
                            extensions.add(
                                ExtensionUi(
                                    id = ext.id,
                                    name = name,
                                    version = version.ifBlank { "1.0" },
                                    description = desc,
                                    enabled = ext.id !in disabled,
                                    isBuiltIn = ext.isBuiltIn,
                                    permissions = perms,
                                ),
                            )
                        }
                        extensions.sortBy { it.name.lowercase() }
                    }
                }
            }, { t ->
                Log.w("Nova", "Could not list extensions: ${t?.message}")
            })
        }
    }

    fun setShieldEnabled(on: Boolean) {
        val ext = live[SHIELD_ID] ?: return
        safe {
            val src = WebExtensionController.EnableSource.USER
            if (on) voidOp(controller().enable(ext, src), "enable shield")
            else voidOp(controller().disable(ext, src), "disable shield")
        }
    }

    fun installFromAmo(context: Context, slug: String, onDone: (Boolean) -> Unit = {}) {
        val url = "https://addons.mozilla.org/firefox/downloads/latest/$slug/"
        installFromUrl(context, url, onDone)
    }

    fun installFromUrl(context: Context, url: String, onDone: (Boolean) -> Unit = {}) {
        if (busy) return
        busy = true
        message = null
        val trimmed = url.trim()
        val target = if (!trimmed.startsWith("http")) {
            val m = Regex("/(?:firefox/)?addon/([^/?]+)").find(trimmed)
            if (m != null) "https://addons.mozilla.org/firefox/downloads/latest/${m.groupValues[1]}/" else trimmed
        } else trimmed
        if (!target.startsWith("http")) {
            busy = false
            message = "That doesn't look like an add-on link."
            onDone(false)
            return
        }
        val dir = File(context.filesDir, "extension-downloads")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "addon.xpi")
        Thread {
            try {
                val conn = java.net.URL(target).openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Gecko/154.0 Firefox/154.0")
                val code = conn.responseCode
                if (code != 200) throw Exception("server returned HTTP $code")
                val len = conn.contentLengthLong
                if (len > 60L * 1024 * 1024) throw Exception("add-on file is too large")
                conn.inputStream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
                if (file.length() < 1000) throw Exception("file is not a valid add-on")
                mainHandler.post {
                    safe { installFile(context, file, onDone) }
                }
            } catch (e: Exception) {
                runCatching { file.delete() }
                mainHandler.post {
                    safe {
                        busy = false
                        message = "Download failed: ${e.message ?: "unknown error"}"
                        onDone(false)
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "nova-addon-download"
            start()
        }
    }

    private fun installFile(context: Context, file: File, onDone: (Boolean) -> Unit) {
        message = null
        if (!App.geckoRuntimeReady) {
            busy = false
            message = "The browser engine is still starting — try installing again in a moment."
            onDone(false)
            return
        }
        safe {
            controller().install(Uri.fromFile(file).toString()).accept({ ext ->
                safe {
                    if (ext != null) {
                        busy = false
                        live[ext.id] = ext
                        val name = runCatching { ext.metaData.name?.ifBlank { ext.id } }.getOrDefault(ext.id) ?: ext.id
                        message = "Installed \"$name\""
                        Store.setExtensionEnabled(ext.id, true)
                        refresh()
                        onDone(true)
                    } else {
                        busy = false
                        message = "Could not install add-on"
                        onDone(false)
                    }
                }
            }, { t ->
                safe {
                    busy = false
                    message = "Could not install this add-on — GeckoView only accepts add-ons signed by Mozilla. For Chrome extensions, use the Firefox version from the Add-ons store."
                    onDone(false)
                }
            })
        }
    }

    private fun installBytes(context: Context, bytes: ByteArray, filename: String, onDone: (Boolean) -> Unit) {
        val dir = File(context.filesDir, "extension-imports")
        if (!dir.exists()) dir.mkdirs()
        val extName = if (filename.substringAfterLast('.', "").lowercase() == "xpi") "import.xpi" else "import.zip"
        val file = File(dir, extName)
        file.writeBytes(bytes)
        busy = true
        message = null
        installFile(context, file, onDone)
    }

    /**
     * Installs an add-on from a file picked by the user (.xpi / .zip / .crx).
     * Mozilla-signed .xpi bundles install directly. Chrome Web Store .crx/.zip
     * files run on Chrome's Blink engine and can't load in GeckoView, so the
     * user is pointed to the matching Firefox add-on instead.
     */
    fun installFromUri(context: Context, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        if (busy) return
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("could not read file")
            val filename = runCatching { uri.lastPathSegment ?: "import.xpi" }.getOrDefault("import.xpi")
            val cls = classify(bytes, filename)
            when (cls) {
                is ExtClass.Chrome -> {
                    message = "\"${cls.name}\" is a Chrome Web Store extension — those run on Chrome's engine and can't load in Nova (Firefox engine). Looking for it on the Firefox Add-ons store…"
                    openAmoSearch = cls.name
                    onDone(false)
                }
                is ExtClass.SignedXpi -> installBytes(context, bytes, filename, onDone)
                is ExtClass.Unknown -> installBytes(context, bytes, filename, onDone)
            }
        } catch (e: Exception) {
            busy = false
            message = "Import failed: ${e.message}"
            onDone(false)
        }
    }

    private sealed class ExtClass {
        data class Chrome(val name: String) : ExtClass()
        object SignedXpi : ExtClass()
        object Unknown : ExtClass()
    }

    private fun classify(bytes: ByteArray, filename: String): ExtClass {
        var payload = bytes
        if (bytes.size > 8 && bytes[0] == 'C'.code.toByte() && bytes[1] == 'r'.code.toByte() && bytes[2] == '2'.code.toByte() && bytes[3] == '4'.code.toByte()) {
            var off = 4
            val readInt = {
                val v = (payload[off].toInt() and 0xFF) or ((payload[off + 1].toInt() and 0xFF) shl 8) or ((payload[off + 2].toInt() and 0xFF) shl 16) or ((payload[off + 3].toInt() and 0xFF) shl 24)
                off += 4
                v
            }
            val version = readInt()
            if (version == 2) {
                val pubLen = readInt()
                val sigLen = readInt()
                off += pubLen + sigLen
            } else {
                val headerSize = readInt()
                off += headerSize
            }
            if (off < bytes.size) payload = bytes.copyOfRange(off, bytes.size)
        }

        var manifest: JSONObject? = null
        var hasMozillaSignature = false
        runCatching {
            ZipInputStream(ByteArrayInputStream(payload)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name.endsWith("META-INF/mozilla.rsa", ignoreCase = true) || name.endsWith("META-INF/mozilla.sf", ignoreCase = true)) {
                        hasMozillaSignature = true
                    }
                    if (manifest == null && name.substringAfterLast('/') == "manifest.json" && !name.startsWith("_metadata")) {
                        val text = zis.readBytes().toString(Charsets.UTF_8)
                        manifest = runCatching { JSONObject(text) }.getOrNull()
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
        }

        val m = manifest
        if (m != null) {
            val hasFirefoxMarkers = m.has("browser_specific_settings") || m.has("applications")
            val name = m.optString("name", "").ifBlank { filename.substringBeforeLast('.') }
            val isChromeOnly = m.optString("minimum_chrome_version").isNotBlank() || m.has("_metadata")
            if (isChromeOnly && !hasFirefoxMarkers) {
                if (hasMozillaSignature) return ExtClass.SignedXpi
                return ExtClass.Chrome(name)
            }
        }
        if (hasMozillaSignature) return ExtClass.SignedXpi
        return ExtClass.Unknown
    }

    fun setEnabled(ext: ExtensionUi, enabled: Boolean) {
        Store.setExtensionEnabled(ext.id, enabled)
        val i = extensions.indexOfFirst { it.id == ext.id }
        if (i >= 0) extensions[i] = extensions[i].copy(enabled = enabled)
        val liveExt = live[ext.id] ?: return
        safe {
            val src = WebExtensionController.EnableSource.USER
            if (enabled) voidOp(controller().enable(liveExt, src), "enable ${ext.id}")
            else voidOp(controller().disable(liveExt, src), "disable ${ext.id}")
        }
    }

    fun uninstall(ext: ExtensionUi) {
        if (ext.isBuiltIn) {
            message = "Bundled extensions can't be uninstalled (disable them instead)"
            return
        }
        val liveExt = live[ext.id]
        if (liveExt != null) {
            safe {
                controller().uninstall(liveExt).accept({
                    safe {
                        live.remove(ext.id)
                        Store.setExtensionEnabled(ext.id, false)
                        val i = extensions.indexOfFirst { it.id == ext.id }
                        if (i >= 0) extensions.removeAt(i)
                        message = "Uninstalled \"${ext.name}\""
                    }
                }, { t ->
                    message = "Could not uninstall: ${t?.message}"
                })
            }
        } else {
            Store.setExtensionEnabled(ext.id, false)
            val i = extensions.indexOfFirst { it.id == ext.id }
            if (i >= 0) extensions.removeAt(i)
            message = "Uninstalled \"${ext.name}\""
        }
    }
}
