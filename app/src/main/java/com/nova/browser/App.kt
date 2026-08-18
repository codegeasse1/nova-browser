package com.nova.browser

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.ValueCallback
import com.nova.browser.engine.AdBlocker
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object App {
    lateinit var context: Context
        private set
    var activity: MainActivity? = null

    lateinit var geckoRuntime: GeckoRuntime
        private set

    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var pendingPermissionResult: ((Boolean) -> Unit)? = null

    var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    var pendingFilePromptResult: org.mozilla.geckoview.GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null

    private var crashHandlerInstalled = false
    private var geckoCreated = false

    val isReady: Boolean get() = ::context.isInitialized

    fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        installCrashHandler()
        Store.init(context)
        if (Store.autoClearHistory) Store.clearHistory()
        AdBlocker.init(context)
        com.nova.browser.browser.Downloads.restore()
        createGeckoRuntime()
        ExtensionManager.attach()
    }

    private fun buildSettings(): GeckoRuntimeSettings {
        val cb = ContentBlocking.Settings.Builder()
        when (Store.adblockLevel) {
            "off" -> {
                cb.antiTracking(0)
                cb.enhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.CUSTOM)
            }
            "strict" -> cb.enhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STRICT)
            else -> cb.enhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STANDARD)
        }
        cb.safeBrowsing(if (Store.safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE)
        val builder = GeckoRuntimeSettings.Builder()
            .contentBlocking(cb.build())
        val dns = dnsSettings()
        if (dns != null) {
            builder.trustedRecursiveResolverMode(dns.first)
            builder.trustedRecursiveResolverUri(dns.second)
        }
        return builder.build()
    }

    fun dnsSettings(): Pair<Int, String>? = when (Store.dnsMode) {
        "cloudflare" -> GeckoRuntimeSettings.TRR_MODE_FIRST to "https://cloudflare-dns.com/dns-query"
        "google" -> GeckoRuntimeSettings.TRR_MODE_FIRST to "https://dns.google/dns-query"
        "quad9" -> GeckoRuntimeSettings.TRR_MODE_FIRST to "https://dns.quad9.net/dns-query"
        else -> null
    }

    private fun createGeckoRuntime() {
        if (geckoCreated) return
        geckoCreated = true
        runCatching {
            geckoRuntime = GeckoRuntime.create(context, buildSettings())
            Log.i("Nova", "GeckoRuntime created (dns=${Store.dnsMode})")
        }.onFailure { t ->
            Log.e("Nova", "GeckoRuntime creation failed", t)
        }
    }

    fun recreateGeckoRuntime() {
        if (!geckoCreated) return
        runCatching {
            com.nova.browser.browser.BrowserCore.closeAllSessions()
            geckoCreated = false
            geckoRuntime = GeckoRuntime.create(context, buildSettings())
            ExtensionManager.attach()
            com.nova.browser.browser.BrowserCore.reopenActiveSession()
            Log.i("Nova", "GeckoRuntime recreated (dns=${Store.dnsMode})")
        }.onFailure { t ->
            Log.e("Nova", "GeckoRuntime recreate failed", t)
        }
    }

    fun finishFilePrompt(uri: Uri?) {
        val prompt = pendingFilePrompt ?: return
        val result = pendingFilePromptResult
        pendingFilePrompt = null
        pendingFilePromptResult = null
        if (result == null) return
        runCatching {
            if (uri == null) {
                result.complete(prompt.dismiss())
            } else {
                if (prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE) {
                    result.complete(prompt.confirm(context, arrayOf(uri)))
                } else {
                    result.complete(prompt.confirm(context, uri))
                }
            }
        }
    }

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (throwable is org.mozilla.geckoview.GeckoResult.UncaughtException) {
                runCatching {
                    val f = File(context.filesDir, "geckoresult.log")
                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    f.appendText("[$stamp] $throwable\n")
                }
            } else {
                runCatching {
                    val f = File(context.filesDir, "crashlog.txt")
                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    val entry = "[$stamp] $throwable\n${Log.getStackTraceString(throwable)}\n\n"
                    f.appendText(entry)
                }
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    fun lastCrash(): String? {
        val f = File(context.filesDir, "crashlog.txt")
        return if (f.exists()) f.readText().trim().take(4000) else null
    }

    fun clearCrashLog() {
        File(context.filesDir, "crashlog.txt").delete()
    }

    fun requestAndroidPermissions(permissions: List<String>, onResult: (Boolean) -> Unit) {
        val a = activity
        if (a == null) {
            onResult(false)
            return
        }
        val needed = permissions.filter { a.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) {
            onResult(true)
            return
        }
        pendingPermissionResult = onResult
        a.launchPermissionRequest(needed.toTypedArray())
    }

    fun finishAndroidPermissionRequest(granted: Boolean) {
        pendingPermissionResult?.invoke(granted)
        pendingPermissionResult = null
    }
}
