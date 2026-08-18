package com.nova.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.ext.ExtRuntime
import com.nova.browser.store.Store
import java.net.URLEncoder

class BrowserTab(val id: Int) {
    var url by mutableStateOf(BrowserCore.NEW_TAB)
    var title by mutableStateOf("New tab")
    var progress by mutableStateOf(0)
    var webView: WebView? = null
}

class BrowserCore(
    private val context: Context,
    private val store: Store,
    val runtime: ExtRuntime
) {

    companion object {
        const val NEW_TAB = "nova://newtab"
        private var nextTabId = 1
    }

    private val _tabs = mutableStateListOf<BrowserTab>()
    val tabs: List<BrowserTab> get() = _tabs

    var activeIndex by mutableStateOf(0)
        private set

    val activeTab: BrowserTab? get() = _tabs.getOrNull(activeIndex)

    var urlBarText by mutableStateOf("")
        private set

    fun setUrlBar(text: String) {
        urlBarText = text
    }

    var settings by mutableStateOf(store.loadSettings())
        private set

    val container = FrameLayout(context)

    init {
        runtime.core = this
        openNewTab(null)
    }

    fun updateSettings() {
        settings = store.loadSettings()
        for (tab in _tabs) {
            tab.webView?.settings?.javaScriptEnabled = settings.javaScriptEnabled
            tab.webView?.settings?.blockNetworkImage = !settings.showImages
        }
    }

    // ---------------- tab management ----------------

    fun openNewTab(url: String?, inBackground: Boolean = false) {
        if (_tabs.size >= 12) {
            if (inBackground) return
            closeTab(_tabs.lastIndex)
        }
        val tab = BrowserTab(nextTabId++)
        if (!url.isNullOrBlank() && url != NEW_TAB) {
            tab.url = url
            tab.title = url
        }
        _tabs.add(tab)
        if (!inBackground) switchTo(_tabs.lastIndex)
    }

    fun switchTo(index: Int) {
        if (index !in _tabs.indices) return
        detachCurrent()
        activeIndex = index
        val tab = activeTab ?: return
        attachTab(tab)
        urlBarText = if (tab.url == NEW_TAB) "" else tab.url
    }

    fun closeTab(index: Int) {
        if (index !in _tabs.indices) return
        val wasActive = index == activeIndex
        val removed = _tabs.removeAt(index)
        removed.webView?.let { wv ->
            if (wv.parent != null) (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        if (_tabs.isEmpty()) {
            openNewTab(null)
            return
        }
        if (wasActive) {
            switchTo(if (index < _tabs.size) index else _tabs.lastIndex)
        }
    }

    private fun detachCurrent() {
        val wv = activeTab?.webView
        if (wv != null && wv.parent != null) {
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
    }

    private fun attachTab(tab: BrowserTab) {
        val wv = tab.webView ?: createWebView(tab)
        if (wv.parent == null) {
            container.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    // ---------------- navigation ----------------

    fun navigate(text: String) {
        val tab = activeTab ?: return
        val url = normalize(text)
        tab.url = url
        tab.title = url
        urlBarText = url
        if (url == NEW_TAB) {
            detachCurrent()
            tab.webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            return
        }
        val wv = tab.webView ?: createWebView(tab)
        attachTab(tab)
        wv.loadUrl(url)
    }

    fun goBack() {
        activeTab?.webView?.goBack()
    }

    fun goForward() {
        activeTab?.webView?.goForward()
    }

    fun reload() {
        activeTab?.webView?.reload()
    }

    fun stop() {
        activeTab?.webView?.stopLoading()
    }

    fun canGoBack(): Boolean = activeTab?.webView?.canGoBack() == true
    fun canGoForward(): Boolean = activeTab?.webView?.canGoForward() == true

    private fun normalize(text: String): String {
        val t = text.trim()
        if (t.isEmpty() || t.equals("nova://newtab", true)) return NEW_TAB
        if (t.startsWith("javascript:")) return t
        if (t.startsWith("about:") || t.startsWith("data:") || t.startsWith("file:") || t.startsWith("chrome:")) return t
        if (t.contains("://")) return t
        val hasSpace = t.contains(' ')
        val lower = t.lowercase()
        val looksLikeUrl = !hasSpace && (t.contains('.') || lower.startsWith("localhost") ||
            lower.startsWith("192.168.") || lower.startsWith("10.") || lower.startsWith("127.") || lower.startsWith("172."))
        return if (looksLikeUrl) "https://$t" else searchUrl(t)
    }

    private fun searchUrl(query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return when (settings.searchEngine) {
            "duckduckgo" -> "https://duckduckgo.com/?q=$q"
            "bing" -> "https://www.bing.com/search?q=$q"
            else -> "https://www.google.com/search?q=$q"
        }
    }

    // ---------------- webview creation ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView {
        val wv = WebView(context)
        with(wv.settings) {
            javaScriptEnabled = settings.javaScriptEnabled
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            setAllowFileAccessFromFileURLs(true)
            setAllowUniversalAccessFromFileURLs(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            blockNetworkImage = !settings.showImages
            userAgentString = WebSettings.getDefaultUserAgent(context) + " NovaBrowser/1.0"
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
        }

        val bridge = runtime.createBridge(wv)
        wv.addJavascriptInterface(bridge, "novaBridge")

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val u = url ?: return
                if (u == "about:blank") return
                tab.url = u
                tab.progress = 10
                if (view == activeTab?.webView) urlBarText = u
                runtime.injectContentScripts(wv, u, "document_start")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val u = url ?: return
                tab.progress = 100
                if (!settings.privateMode) store.addHistory(u, tab.title)
                runtime.injectContentScripts(wv, u, "document_idle")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank() && tab.title == tab.url) tab.title = title
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val temp = WebView(context)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url?.toString() ?: return false
                        openNewTab(u)
                        return true
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                        if (url != null) openNewTab(url)
                        return true
                    }
                }
                transport.webView = temp
                resultMsg.sendToTarget()
                temp.postDelayed({ temp.destroy() }, 2000)
                return true
            }
        }

        wv.setDownloadListener { url, _, _, _, _ -> openNewTab(url) }

        tab.webView = wv
        return wv
    }
}
