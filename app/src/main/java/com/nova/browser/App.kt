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
        AdBlocker.init(context)
        com.nova.browser.browser.Downloads.restore()
        createGeckoRuntime()
        ExtensionManager.attach()
    }

    private fun createGeckoRuntime() {
        if (geckoCreated) return
        geckoCreated = true
        runCatching {
            val cb = ContentBlocking.Settings.Builder()
            when (Store.adblockLevel) {
                "off" -> cb.setAntiTracking(0)
                "strict" -> cb.setEnhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STRICT)
                else -> cb.setEnhancedTrackingProtectionCategory(ContentBlocking.EtpCategory.STANDARD)
            }
            cb.setSafeBrowsing(if (Store.safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE)
            val settings = GeckoRuntimeSettings.Builder()
                .contentBlocking(cb.build())
                .build()
            geckoRuntime = GeckoRuntime.create(context, settings)
            Log.i("Nova", "GeckoRuntime created")
        }.onFailure { t ->
            Log.e("Nova", "GeckoRuntime creation failed", t)
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
            runCatching {
                val f = File(context.filesDir, "crashlog.txt")
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = "[$stamp] $throwable\n${Log.getStackTraceString(throwable)}\n\n"
                f.appendText(entry)
            }
            previous?.uncaughtException(thread, throwable)
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
