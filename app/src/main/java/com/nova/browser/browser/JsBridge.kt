package com.nova.browser.browser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.nova.browser.ext.ExtRuntime

/**
 * The JavascriptInterface exposed to every WebView as `novaBridge`.
 * Methods are called from the shim JS on a non-main thread; ExtRuntime
 * hops to the main thread internally.
 */
class JsBridge(val webView: WebView, val runtime: ExtRuntime) {

    @JavascriptInterface
    fun runtimeSendMessage(extId: String, msgJson: String, cbId: String) {
        runtime.runtimeSendMessage(webView, extId, msgJson, cbId, this)
    }

    @JavascriptInterface
    fun runtimeReply(extId: String, cbId: String, respJson: String) {
        runtime.runtimeReply(extId, cbId, respJson)
    }

    @JavascriptInterface
    fun storageGet(extId: String, ignored: String, cbId: String) {
        runtime.storageGet(extId, cbId, this)
    }

    @JavascriptInterface
    fun storageSet(extId: String, dataJson: String, cbId: String) {
        runtime.storageSet(extId, dataJson, cbId, this)
    }

    @JavascriptInterface
    fun storageRemove(extId: String, keysJson: String, cbId: String) {
        runtime.storageRemove(extId, keysJson, cbId, this)
    }

    @JavascriptInterface
    fun storageClear(extId: String, cbId: String) {
        runtime.storageClear(extId, cbId, this)
    }

    @JavascriptInterface
    fun tabsQuery(queryJson: String, cbId: String) {
        runtime.tabsQuery(queryJson, cbId, this)
    }

    @JavascriptInterface
    fun tabsCreate(url: String) {
        runtime.tabsCreate(url)
    }

    @JavascriptInterface
    fun tabsUpdate(url: String) {
        runtime.tabsUpdate(url)
    }

    @JavascriptInterface
    fun actionSetBadge(extId: String, text: String, color: String) {
        runtime.actionSetBadge(extId, text, color)
    }

    @JavascriptInterface
    fun actionSetBadgeColor(extId: String, color: String) {
        runtime.actionSetBadgeColor(extId, color)
    }
}
