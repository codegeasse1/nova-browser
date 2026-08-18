package com.nova.browser.ext

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class InstalledExtension(
    val manifest: ExtManifest,
    var enabled: Boolean,
    val installedAt: Long
)

class ExtensionManager(private val context: Context) {

    val extsDir = File(context.filesDir, "extensions").apply { mkdirs() }

    fun list(): List<InstalledExtension> {
        val out = mutableListOf<InstalledExtension>()
        extsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val inst = File(dir, "installed.json")
                if (inst.exists()) {
                    try {
                        val o = JSONObject(inst.readText())
                        val mf = ExtManifest.parse(o.getString("id"), o.getString("manifest"), dir.absolutePath)
                        out.add(InstalledExtension(mf, o.optBoolean("enabled", true), o.optLong("installedAt")))
                    } catch (e: Exception) {
                    }
                }
            }
        }
        return out.sortedBy { it.manifest.name.lowercase() }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val inst = File(File(extsDir, id), "installed.json")
        if (!inst.exists()) return
        try {
            val o = JSONObject(inst.readText())
            o.put("enabled", enabled)
            inst.writeText(o.toString())
        } catch (e: Exception) {
        }
    }

    fun remove(id: String) {
        File(extsDir, id).deleteRecursively()
    }

    /** Returns null on success, or an error message. */
    fun installFromUri(uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return "Cannot read the selected file"
        return try {
            val din = DataInputStream(input)
            val magic = din.readInt()
            if (magic == 0x34324443) { // "Cr24" (CRX)
                val version = din.readInt()
                val headerSize = if (version == 2 || version == 3) din.readInt() else 0
                if (headerSize in 1 until (1024 * 1024)) din.skipBytes(headerSize)
            }
            val zip = ZipInputStream(din)
            var entry = zip.nextEntry
            if (entry == null) return "The file does not contain an extension archive"
            val tmpDir = File(context.cacheDir, "nova_ext_install_" + System.currentTimeMillis())
            tmpDir.mkdirs()
            while (entry != null) {
                val name = entry.name
                if (name.isNotBlank() && !name.contains("..") && !name.startsWith("/") && !name.startsWith("__MACOSX")) {
                    val outFile = File(tmpDir, name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            val buf = ByteArray(8192)
                            var n = zip.read(buf)
                            while (n > 0) {
                                fos.write(buf, 0, n)
                                n = zip.read(buf)
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()
            val manifestFile = File(tmpDir, "manifest.json")
            if (!manifestFile.exists()) {
                tmpDir.deleteRecursively()
                return "No manifest.json found in the archive"
            }
            val manifestText = manifestFile.readText()
            val id = makeId(manifestText)
            val destDir = File(extsDir, id)
            if (destDir.exists()) destDir.deleteRecursively()
            copyRecursive(tmpDir, destDir)
            tmpDir.deleteRecursively()
            ExtManifest.parse(id, manifestText, destDir.absolutePath)
            val o = JSONObject()
                .put("id", id)
                .put("enabled", true)
                .put("installedAt", System.currentTimeMillis())
                .put("manifest", manifestText)
            File(destDir, "installed.json").writeText(o.toString())
            null
        } catch (e: Exception) {
            "Failed to install: " + (e.message ?: e.toString())
        }
    }

    /** Installs the bundled sample extension. Returns null on success, or an error message. */
    fun installSample(): String? {
        val sampleDir = File(context.cacheDir, "nova_sample_ext")
        if (!sampleDir.exists()) {
            sampleDir.mkdirs()
            val assets = arrayOf("manifest.json", "content.js", "background.js", "popup.html", "popup.js")
            for (a in assets) {
                val outFile = File(sampleDir, a)
                outFile.parentFile?.mkdirs()
                context.assets.open("sample-ext/$a").use { input ->
                    FileOutputStream(outFile).use { fos ->
                        val buf = ByteArray(8192)
                        var n = input.read(buf)
                        while (n > 0) {
                            fos.write(buf, 0, n)
                            n = input.read(buf)
                        }
                    }
                }
            }
        }
        val manifestFile = File(sampleDir, "manifest.json")
        val manifestText = manifestFile.readText()
        val id = makeId(manifestText)
        val destDir = File(extsDir, id)
        if (destDir.exists()) return null
        copyRecursive(sampleDir, destDir)
        val o = JSONObject()
            .put("id", id)
            .put("enabled", true)
            .put("installedAt", System.currentTimeMillis())
            .put("manifest", manifestText)
        File(destDir, "installed.json").writeText(o.toString())
        return null
    }

    fun storageFile(id: String): File = File(File(extsDir, id), "storage.json")

    fun readStorage(id: String): JSONObject = try {
        JSONObject(storageFile(id).readText())
    } catch (e: Exception) {
        JSONObject()
    }

    fun writeStorage(id: String, obj: JSONObject) {
        storageFile(id).writeText(obj.toString())
    }

    private fun makeId(manifestJson: String): String {
        val hex1 = Integer.toHexString(manifestJson.hashCode()).padStart(8, '0')
        val hex2 = Integer.toHexString(manifestJson.length * 31).padStart(8, '0')
        return "ext_$hex1$hex2"
    }

    private fun copyRecursive(src: File, dst: File) {
        src.walkTopDown().forEach { f ->
            val target = File(dst, f.relativeTo(src).path)
            if (f.isDirectory) target.mkdirs()
            else if (f.isFile) {
                target.parentFile?.mkdirs()
                f.copyTo(target, overwrite = true)
            }
        }
    }
}
