package com.nova.browser

import android.content.Context
import android.content.pm.PackageManager
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object App {
    lateinit var context: Context
        private set
    lateinit var runtime: GeckoRuntime
        private set
    var activity: MainActivity? = null

    private var pendingPermissionResult: ((Boolean) -> Unit)? = null

    val isReady: Boolean get() = ::runtime.isInitialized

    fun init(ctx: Context) {
        if (::runtime.isInitialized) return
        context = ctx.applicationContext
        Store.init(context)

        val adblock = Store.adblockLevel
        val etp = when (adblock) {
            "strict" -> ContentBlocking.EtpLevel.STRICT
            else -> ContentBlocking.EtpLevel.DEFAULT
        }
        val cb = ContentBlocking.Settings.Builder()
            .setAntiTracking(etp)
            .setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .setStrictSocialTrackingProtection(etp == ContentBlocking.EtpLevel.STRICT)
            .build()

        val builder = GeckoRuntimeSettings.Builder()
            .setJavaScriptEnabled(true)
            .setAllowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
            .contentBlocking(cb)

        when (Store.dohMode) {
            "first" -> builder.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST)
            "only" -> builder.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_ONLY)
            else -> builder.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
        }
        val dohUri = Store.dohProvider
        if (dohUri.isNotBlank()) builder.setTrustedRecursiveResolverUri(dohUri)

        runtime = GeckoRuntime.create(context, builder.build())
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
