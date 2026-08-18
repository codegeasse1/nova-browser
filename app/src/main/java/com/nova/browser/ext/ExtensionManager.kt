package com.nova.browser.ext

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.util.zip.ZipInputStream

data class ExtensionUi(
    val webExtension: WebExtension,
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

    private val controller: WebExtensionController get() = App.runtime.webExtensionController

    fun attach() {
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse>? =
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, true))

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<String>,
                newOrigins: Array<String>,
                newDataCollectionPermissions: Array<String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(AllowOrDeny.ALLOW)

            override fun onOptionalPrompt(
                extension: WebExtension,
                newPermissions: Array<String>,
                newOrigins: Array<String>,
                newDataCollectionPermissions: Array<String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(AllowOrDeny.ALLOW)
        })

        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) = refresh()
            override fun onUninstalled(extension: WebExtension) = refresh()
            override fun onEnabled(extension: WebExtension) = refresh()
            override fun onDisabled(extension: WebExtension) = refresh()
            override fun onInstallationFailed(extension: WebExtension?, installException: WebExtension.InstallException) {
                message = "Extension install failed: ${installException.message ?: "unknown error"}"
            }
        })

        controller.ensureBuiltIn("resource://android/assets/extensions/sample/", "nova-sample@nova.browser")
            .accept({ refresh() }, { message = "Bundled sample extension unavailable" })
        refresh()
    }

    fun refresh() {
        controller.list().accept(
            { list ->
                extensions.clear()
                for (ext in list) extensions.add(toUi(ext))
            },
            { _ -> },
        )
    }

    private fun toUi(ext: WebExtension): ExtensionUi {
        val md = ext.metaData
        return ExtensionUi(
            webExtension = ext,
            id = ext.id,
            name = md.name.ifBlank { ext.id },
            version = md.version,
            description = md.description,
            enabled = md.enabled,
            isBuiltIn = ext.isBuiltIn,
            permissions = (md.requiredPermissions.toList() + md.requiredOrigins.toList()).distinct(),
        )
    }

    fun installFromFile(context: Context, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        if (busy) return
        busy = true
        message = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val extId = "ext-" + System.currentTimeMillis().toString(36)
                val tmp = File(context.cacheDir, "$extId.zip")
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    tmp.outputStream().use { ous -> ins.copyTo(ous) }
                } ?: throw Exception("could not read file")

                val dir = File(context.filesDir, "extensions/$extId")
                dir.mkdirs()
                unzip(tmp, dir)
                ensureManifest(dir, extId)

                val result = controller.install(
                    Uri.fromFile(dir).toString(),
                    WebExtensionController.INSTALLATION_METHOD_FROM_FILE,
                )
                result.accept(
                    { ext ->
                        busy = false
                        message = "Installed \"${ext.metaData.name}\""
                        refresh()
                        onDone(true)
                    },
                    { e ->
                        busy = false
                        message = "Could not install extension: ${e.message}"
                        onDone(false)
                    },
                )
            } catch (e: Exception) {
                busy = false
                message = "Import failed: ${e.message}"
                onDone(false)
            }
        }
    }

    fun uninstall(ext: ExtensionUi) {
        controller.uninstall(ext.webExtension)
            .accept({ refresh() }, { message = "Uninstall failed: ${it.message}" })
    }

    fun setEnabled(ext: ExtensionUi, enabled: Boolean) {
        if (enabled) {
            controller.enable(ext.webExtension, WebExtensionController.EnableSource.USER)
                .accept({ refresh() }, { message = "Enable failed: ${it.message}" })
        } else {
            controller.disable(ext.webExtension, WebExtensionController.EnableSource.USER)
                .accept({ refresh() }, { message = "Disable failed: ${it.message}" })
        }
    }

    fun allowInPrivateBrowsing(ext: ExtensionUi, allow: Boolean) {
        controller.setAllowedInPrivateBrowsing(ext.webExtension, allow).accept({}, {})
    }

    private fun unzip(zip: File, dest: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = File(dest, entry.name)
                    out.parentFile?.mkdirs()
                    out.outputStream().use { ous -> zin.copyTo(ous, 4 * 1024 * 1024) }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        val manifest = File(dest, "manifest.json")
        if (!manifest.exists()) {
            val sub = dest.listFiles()?.firstOrNull { it.isDirectory && File(it, "manifest.json").exists() }
            if (sub != null) {
                sub.copyRecursively(dest, overwrite = true)
                sub.deleteRecursively()
            } else {
                throw Exception("manifest.json not found — not a valid extension package")
            }
        }
    }

    private fun ensureManifest(dir: File, extId: String) {
        val manifestFile = File(dir, "manifest.json")
        val json = runCatching { JSONObject(manifestFile.readText()) }
            .getOrNull() ?: throw Exception("invalid manifest.json")
        if (!json.has("manifest_version")) json.put("manifest_version", 2)
        if (!json.has("name")) json.put("name", "Extension")
        if (!json.has("version")) json.put("version", "1.0")

        val apps = json.optJSONObject("applications")
        val existingId = apps?.optJSONObject("gecko")?.optString("id").orEmpty()
        if (existingId.isBlank()) {
            val bs = json.optJSONObject("browser_specific_settings")
                ?: JSONObject().also { json.put("browser_specific_settings", it) }
            val gecko = bs.optJSONObject("gecko")
                ?: JSONObject().also { bs.put("gecko", it) }
            if (gecko.optString("id").isBlank()) gecko.put("id", "$extId@nova.browser")
        }
        manifestFile.writeText(json.toString(2))
    }
}
