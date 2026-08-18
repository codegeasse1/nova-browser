package com.nova.browser

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.ValueCallback
import com.nova.browser.engine.AdBlocker
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object App {
    lateinit var context: Context
        private set
    var activity: MainActivity? = null

    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var pendingPermissionResult: ((Boolean) -> Unit)? = null

    private var crashHandlerInstalled = false

    val isReady: Boolean get() = ::context.isInitialized

    fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        installCrashHandler()
        Store.init(context)
        AdBlocker.init(context)
        com.nova.browser.browser.Downloads.restore()
        ExtensionManager.attach()
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
