package com.nova.browser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.App
import com.nova.browser.engine.AdBlocker
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store
import java.io.ByteArrayInputStream

object BrowserCore {
    val tabs = mutableStateListOf<TabState>()
    var activeIndex by mutableIntStateOf(-1)
    var totalBlocked by mutableStateOf(0L)
    var lastDownloadMessage by mutableStateOf<String?>(null)
    var pendingExternalIntent by mutableStateOf<String?>(null)
    var lastShieldNotice by mutableStateOf<String?>(null)
    var storeOffer by mutableStateOf<Pair<String, String>?>(null)

    private val webViews = HashMap<Int, WebView>()
    private var nextId = 1

    val activeTab: TabState? get() = tabs.getOrNull(activeIndex)

    fun newTab(url: String? = null, isPrivate: Boolean = false): Int {
        val id = nextId++
        val shield = Store.adblockLevel != "off"
        val tab = TabState(id = id, isPrivate = isPrivate, shield = shield)
        tabs.add(tab)
        activate(tabs.lastIndex)
        if (!url.isNullOrBlank()) tabs[tabs.lastIndex] = tabs[tabs.lastIndex].copy(url = url)
        return tabs.lastIndex
    }

    fun attachView(context: Context, tabId: Int): WebView {
        return webViews.getOrPut(tabId) {
            val tab = tabs.firstOrNull { it.id == tabId }
            val v = WebView(context)
            v.tag = tabId
            configure(v, tab)
            val u = tab?.url
            if (!u.isNullOrBlank() && !(tab?.isStartPage == true)) {
                v.loadUrl(u)
            }
            v
        }
    }

    private fun configure(view: WebView, tab: TabState?) {
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        settings.safeBrowsingEnabled = Store.safeBrowsing
        settings.textZoom = 100
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return runCatching { handleUrl(view, request.url.toString(), request.isForMainFrame) }.getOrDefault(false)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return runCatching { handleUrl(view, url, true) }.getOrDefault(false)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return runCatching {
                    val id = view.tag as? Int ?: return null
                    val tab = tabs.firstOrNull { it.id == id } ?: return null
                    if (!tab.shield) return null
                    val url = request.url.toString()
                    if (AdBlocker.shouldBlock(url, view.url ?: "")) {
                        view.post {
                            patch(id) { copy(blocked = blocked + 1) }
                            totalBlocked += 1
                        }
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    null
                }.getOrNull()
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                runCatching {
                    val id = view.tag as? Int ?: return
                    if (storeOffer != null) storeOffer = null
                    patch(id) {
                        copy(url = url, title = "", progress = 0, secure = url.startsWith("https://"), blocked = 0)
                    }
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                runCatching {
                    val id = view.tag as? Int ?: return
                    patch(id) { copy(url = url, secure = url.startsWith("https://")) }
                    val tab = tabs.firstOrNull { it.id == id }
                    if (tab?.desktopSite == true && url.startsWith("http")) injectDesktopViewport(view)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                runCatching {
                    val id = view.tag as? Int ?: return
                    val tab = tabs.firstOrNull { it.id == id } ?: return
                    val title = view.title ?: ""
                    val secure = url.startsWith("https://")
                    patch(id) {
                        copy(
                            url = url,
                            title = title,
                            progress = 100,
                            secure = secure,
                            canGoBack = view.canGoBack(),
                            canGoForward = view.canGoForward(),
                        )
                    }
                    if (!tab.isPrivate && url.startsWith("http")) {
                        Store.addHistory(title.ifBlank { url }, url)
                    }
                    ExtensionManager.injectInto(view, url)
                    if (tab.desktopSite && url.startsWith("http")) injectDesktopViewport(view)
                    offerStoreInstall(url)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                runCatching {
                    if (request.isForMainFrame) {
                        val id = view.tag as? Int ?: return
                        patch(id) { copy(progress = 100) }
                    }
                }
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                runCatching {
                    val id = view.tag as? Int ?: return
                    patch(id) { copy(progress = newProgress) }
                }
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                runCatching {
                    val id = view.tag as? Int ?: return
                    patch(id) { copy(title = title) }
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                runCatching {
                    App.filePathCallback = filePathCallback
                    App.activity?.openFileChooser()
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runCatching { request.grant(request.resources) }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback,
            ) {
                runCatching {
                    App.requestAndroidPermissions(
                        listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
                    ) { granted ->
                        callback.invoke(origin, granted, false)
                    }
                }
            }

            override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                runCatching {
                    if (view != null && callback != null) App.activity?.showFullscreenView(view, callback)
                }
            }

            override fun onHideCustomView() {
                runCatching { App.activity?.hideFullscreenView() }
            }
        }

        view.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            runCatching {
                val lower = url.lowercase()
                val isExt =
                    lower.endsWith(".crx") ||
                        lower.endsWith(".xpi") ||
                        contentDisposition?.contains(".xpi", ignoreCase = true) == true ||
                        lower.endsWith(".zip") && url.contains("extension")
                if (isExt) {
                    ExtensionManager.installFromUrl(App.context, url)
                } else {
                    Downloads.start(App.context, url, userAgent, contentDisposition, mimetype, contentLength) { msg ->
                        lastDownloadMessage = msg
                    }
                }
            }
        })
    }

    private fun offerStoreInstall(url: String) {
        runCatching {
            val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
            val isStore = host == "chromewebstore.google.com" || host == "newebstore.google.com" || host.endsWith(".chromewebstore.google.com") || host.endsWith(".newebstore.google.com")
            if (!isStore) return
            val m = Regex("/detail/([^/]+)/([a-p]{32})").find(url) ?: return
            val id = m.groupValues[2]
            if (storeOffer?.first == id) return
            val slug = m.groupValues[1]
            val name = slug.split('-', '_').joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            storeOffer = id to name
        }
    }

    private fun injectDesktopViewport(view: WebView) {
        runCatching {
            view.evaluateJavascript(
                """(function(){
                    function setVp(){
                        var head = document.head || document.documentElement;
                        if (!head) return false;
                        var content = 'width=1024, initial-scale=1.0';
                        var existing = document.querySelector('meta[name="viewport"]');
                        if (existing) { existing.setAttribute('content', content); }
                        else {
                            var m = document.createElement('meta');
                            m.setAttribute('name', 'viewport');
                            m.setAttribute('content', content);
                            head.appendChild(m);
                        }
                        return true;
                    }
                    if (!setVp() && document.readyState !== 'complete') {
                        document.addEventListener('DOMContentLoaded', setVp);
                    }
                })()""",
                null,
            )
        }
    }

    private fun handleUrl(view: WebView, url: String, isMainFrame: Boolean): Boolean {
        val scheme = url.substringBefore(":").lowercase()
        if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "file") {
            return false
        }
        if (isMainFrame && (scheme == "intent" || scheme == "mailto" || scheme == "tel" || scheme == "sms" || scheme == "geo" || scheme == "market")) {
            openExternal(url)
            return true
        }
        return true
    }

    fun activate(index: Int) {
        if (index !in tabs.indices) return
        activeIndex = index
    }

    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        webViews.remove(tab.id)?.let {
            runCatching {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.destroy()
            }
        }
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            activeIndex = -1
            newTab()
        } else {
            activate(if (index < tabs.size) index else tabs.lastIndex)
        }
    }

    fun closeAllPrivate() {
        val ids = tabs.filter { it.isPrivate }.map { it.id }
        ids.forEach { id ->
            webViews.remove(id)?.let {
                runCatching {
                    it.stopLoading()
                    it.loadUrl("about:blank")
                    it.destroy()
                }
            }
        }
        tabs.removeAll { it.isPrivate }
        if (tabs.isEmpty()) {
            activeIndex = -1
            newTab()
        } else if (activeIndex >= tabs.size) {
            activeIndex = tabs.lastIndex
        }
    }

    fun navigate(input: String) {
        val tab = activeTab ?: return
        val url = interpret(input) ?: return
        patch(tab.id) { copy(url = url) }
        val wv = webViews[tab.id]
        if (wv != null) wv.loadUrl(url) else attachView(App.activity ?: App.context, tab.id)
    }

    fun loadInTab(index: Int, input: String) {
        val tab = tabs.getOrNull(index) ?: return
        val url = interpret(input) ?: return
        activate(index)
        patch(tab.id) { copy(url = url) }
        val wv = webViews[tab.id]
        if (wv != null) wv.loadUrl(url) else attachView(App.activity ?: App.context, tab.id)
    }

    fun interpret(raw: String): String? {
        val q = raw.trim()
        if (q.isEmpty()) return null
        val hasScheme = q.contains("://")
        val looksLikeUrl = !q.contains(" ") && (q.contains(".") || hasScheme || q.startsWith("localhost"))
        return if (looksLikeUrl) {
            if (hasScheme) q else "https://$q"
        } else {
            searchUrl(q)
        }
    }

    fun searchUrl(query: String): String {
        val q = Uri.encode(query)
        return when (Store.searchEngine) {
            "duckduckgo" -> "https://duckduckgo.com/?q=$q"
            "bing" -> "https://www.bing.com/search?q=$q"
            else -> "https://www.google.com/search?q=$q"
        }
    }

    fun goHome() {
        val tab = activeTab ?: return
        patch(tab.id) { copy(url = "", title = "", progress = 0, blocked = 0, secure = false, canGoBack = false, canGoForward = false) }
        webViews[tab.id]?.loadUrl("about:blank")
    }

    fun toggleShield() {
        val tab = activeTab ?: return
        patch(tab.id) { copy(shield = !shield) }
        if (Store.adblockLevel != "off") {
            lastShieldNotice = "Ad blocking turned ${if (!tab.shield) "off" else "on"} for this site. Reload the page."
        }
    }

    fun applyShieldToAll() {
        val on = Store.adblockLevel != "off"
        tabs.forEach { tab ->
            patch(tab.id) { copy(shield = on) }
        }
        if (Store.adblockLevel == "off") {
            lastShieldNotice = "Ad blocking is off for all tabs. Reload pages to apply."
        } else {
            lastShieldNotice = "Ad blocking updated for all tabs. Reload pages to apply."
        }
    }

    fun toggleDesktopSite() {
        val tab = activeTab ?: return
        val next = !tab.desktopSite
        patch(tab.id) { copy(desktopSite = next) }
        webViews[tab.id]?.let { view ->
            view.settings.userAgentString = if (next) DESKTOP_UA else ""
            view.settings.useWideViewPort = true
            view.settings.loadWithOverviewMode = !next
            view.reload()
        }
    }

    fun back(): Boolean {
        val tab = activeTab ?: return false
        return if (tab.canGoBack) {
            webViews[tab.id]?.goBack()
            true
        } else false
    }

    fun forward() {
        activeTab?.let { webViews[it.id]?.goForward() }
    }

    fun reload() {
        val tab = activeTab ?: return
        if (tab.isStartPage) return
        webViews[tab.id]?.reload()
    }

    fun stop() {
        activeTab?.let { webViews[it.id]?.stopLoading() }
    }

    fun openExternal(raw: String) {
        val activity = App.activity ?: return
        try {
            val uri = if (raw.startsWith("intent:")) {
                runCatching {
                    val u = Uri.parse(raw)
                    Uri.parse(u.getQueryParameter("browser_fallback_url") ?: raw)
                }.getOrDefault(Uri.parse(raw))
            } else Uri.parse(raw)
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            pendingExternalIntent = raw
        }
    }

    fun faviconUrl(url: String): String {
        val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
        return "https://www.google.com/s2/favicons?domain=${Uri.encode(host)}&sz=64"
    }

    private fun patch(id: Int, transform: TabState.() -> TabState) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        tabs[i] = transform(tabs[i])
    }

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.6943.99 Safari/537.36"
}
