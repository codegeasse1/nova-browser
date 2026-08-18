package com.nova.browser.ext

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.App
import com.nova.browser.browser.BrowserCore
import com.nova.browser.engine.AdBlocker
import com.nova.browser.store.Store
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.json.JSONObject
import java.io.File

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

    private val live = HashMap<String, WebExtension>()

    private const val NATIVE_APP = "nova"
    const val SHIELD_ID = "nova-shield@nova.browser"

    private val BUILT_INS = listOf(
        "nightshift@nova.browser",
        "imageblocker@nova.browser",
        "textsizer@nova.browser",
        SHIELD_ID,
    )

    private fun controller(): WebExtensionController = App.geckoRuntime.webExtensionController

    fun attach() {
        runCatching {
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
            BUILT_INS.forEach { ensureBuiltIn(it) }
            setShieldEnabled(Store.adblockLevel != "off")
            refresh()
        }
    }

    private fun ensureBuiltIn(id: String) {
        val folder = id.removeSuffix("@nova.browser")
        controller().ensureBuiltIn("resource://android/assets/extensions/$folder/", id)
            .accept({ ext ->
                if (ext != null) {
                    live[id] = ext
                    if (id == SHIELD_ID) attachShieldBridge(ext)
                    refresh()
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
                val url = message.optString("url", "")
                val pageUrl = message.optString("pageUrl", "")
                val tabId = message.optInt("tabId", -1)
                val blocked = runCatching { AdBlocker.shouldBlock(url, pageUrl) }.getOrDefault(false)
                if (blocked) {
                    val target = BrowserCore.tabs.firstOrNull { it.id == tabId }
                    val id = target?.id ?: BrowserCore.activeTab?.id ?: -1
                    if (id > 0) BrowserCore.reportBlocked(id, 1)
                }
                val result = JSONObject()
                result.put("block", blocked)
                return GeckoResult.fromValue<Any>(result)
            }
        }, NATIVE_APP)
    }

    fun refresh() {
        runCatching {
            controller().list().accept({ list ->
                synchronized(extensions) {
                    live.clear()
                    extensions.clear()
                    val disabled = Store.disabledExtensions()
                    for (ext in list ?: emptyList()) {
                        live[ext.id] = ext
                        val md = ext.metaData
                        val name = md.name?.ifBlank { ext.id } ?: ext.id
                        val perms = md.requiredPermissions?.toList() ?: emptyList()
                        extensions.add(
                            ExtensionUi(
                                id = ext.id,
                                name = name,
                                version = md.version?.ifBlank { "1.0" } ?: "1.0",
                                description = md.description ?: "",
                                enabled = ext.id !in disabled,
                                isBuiltIn = ext.isBuiltIn,
                                permissions = perms,
                            ),
                        )
                    }
                    extensions.sortBy { it.name.lowercase() }
                }
            }, { t ->
                Log.w("Nova", "Could not list extensions: ${t?.message}")
            })
        }
    }

    fun setShieldEnabled(on: Boolean) {
        val ext = live[SHIELD_ID] ?: return
        runCatching {
            val src = WebExtensionController.EnableSource.USER
            if (on) controller().enable(ext, src) else controller().disable(ext, src)
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
        runCatching {
            controller().install(target).accept({ ext ->
                if (ext != null) {
                    busy = false
                    live[ext.id] = ext
                    val md = ext.metaData
                    message = "Installed \"${md.name?.ifBlank { ext.id } ?: ext.id}\""
                    Store.setExtensionEnabled(ext.id, true)
                    refresh()
                    onDone(true)
                } else {
                    busy = false
                    message = "Could not install add-on"
                    onDone(false)
                }
            }, { t ->
                busy = false
                message = "Could not install add-on: ${t?.message}"
                onDone(false)
            })
        }.onFailure {
            busy = false
            message = "Could not install add-on: ${it.message}"
            onDone(false)
        }
    }

    fun installFromUri(context: Context, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        if (busy) return
        busy = true
        message = null
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw Exception("could not read file")
            val dir = File(context.filesDir, "extension-imports")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "import.xpi")
            file.writeBytes(bytes)
            controller().install(Uri.fromFile(file).toString()).accept({ ext ->
                if (ext != null) {
                    busy = false
                    live[ext.id] = ext
                    val md = ext.metaData
                    message = "Installed \"${md.name?.ifBlank { ext.id } ?: ext.id}\""
                    Store.setExtensionEnabled(ext.id, true)
                    refresh()
                    onDone(true)
                } else {
                    busy = false
                    message = "Could not install add-on"
                    onDone(false)
                }
            }, { t ->
                busy = false
                message = "Could not install add-on: ${t?.message}"
                onDone(false)
            })
        } catch (e: Exception) {
            busy = false
            message = "Import failed: ${e.message}"
            onDone(false)
        }
    }

    fun setEnabled(ext: ExtensionUi, enabled: Boolean) {
        Store.setExtensionEnabled(ext.id, enabled)
        val i = extensions.indexOfFirst { it.id == ext.id }
        if (i >= 0) extensions[i] = extensions[i].copy(enabled = enabled)
        val liveExt = live[ext.id] ?: return
        runCatching {
            val src = WebExtensionController.EnableSource.USER
            if (enabled) controller().enable(liveExt, src) else controller().disable(liveExt, src)
        }
    }

    fun uninstall(ext: ExtensionUi) {
        if (ext.isBuiltIn) {
            message = "Bundled extensions can't be uninstalled (disable them instead)"
            return
        }
        val liveExt = live[ext.id]
        if (liveExt != null) {
            runCatching {
                controller().uninstall(liveExt).accept({
                    live.remove(ext.id)
                    Store.setExtensionEnabled(ext.id, false)
                    val i = extensions.indexOfFirst { it.id == ext.id }
                    if (i >= 0) extensions.removeAt(i)
                    message = "Uninstalled \"${ext.name}\""
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
