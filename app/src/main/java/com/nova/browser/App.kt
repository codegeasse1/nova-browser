package com.nova.browser

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.ValueCallback
import com.nova.browser.engine.AdBlocker
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store

object App {
    lateinit var context: Context
        private set
    var activity: MainActivity? = null

    var filePathCallback: ValueCallback<Array<Uri>>? = null
    var pendingPermissionResult: ((Boolean) -> Unit)? = null

    val isReady: Boolean get() = ::context.isInitialized

    fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        Store.init(context)
        AdBlocker.init(context)
        com.nova.browser.browser.Downloads.restore()
        ExtensionManager.attach()
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
