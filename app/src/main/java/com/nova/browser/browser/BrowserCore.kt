package com.nova.browser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.App
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

data class StoreOffer(val kind: String, val key: String, val name: String)

object BrowserCore {
    val tabs = mutableStateListOf<TabState>()
    var activeIndex by mutableIntStateOf(-1)
    var totalBlocked by mutableStateOf(0L)
    var lastDownloadMessage by mutableStateOf<String?>(null)
    var pendingExternalIntent by mutableStateOf<String?>(null)
    var lastShieldNotice by mutableStateOf<String?>(null)
    var storeOffer by mutableStateOf<StoreOffer?>(null)
    var fullscreen by mutableStateOf(false)

    private val sessions = HashMap<Int, GeckoSession>()
    private val sessionLoads = HashMap<Int, String?>()
    private val crashTimes = HashMap<Int, MutableList<Long>>()
    private var geckoView: GeckoView? = null
    private var nextId = 1

    val activeTab: TabState? get() = tabs.getOrNull(activeIndex)
    fun activeSession(): GeckoSession? = activeTab?.let { sessions[it.id] }

    fun newTab(url: String? = null, isPrivate: Boolean = false): Int {
        val id = nextId++
        val shield = Store.adblockLevel != "off"
        val tab = TabState(id = id, isPrivate = isPrivate, shield = shield)
        tabs.add(tab)
        activate(tabs.lastIndex)
        if (!url.isNullOrBlank()) tabs[tabs.lastIndex] = tabs[tabs.lastIndex].copy(url = url)
        return tabs.lastIndex
    }

    fun attachView(context: Context, tabId: Int): GeckoView {
        val v = geckoView ?: GeckoView(context).also { geckoView = it }
        val session = sessionFor(tabId)
        if (v.getSession() !== session) {
            runCatching { v.releaseSession() }
            runCatching { v.setSession(session) }
        }
        return v
    }

    private fun sessionFor(tabId: Int, autoLoad: Boolean = true): GeckoSession {
        sessions[tabId]?.let { s ->
            if (autoLoad) maybeLoad(tabId, s)
            return s
        }
        if (!App.geckoRuntimeReady) {
            val placeholder = GeckoSession(GeckoSessionSettings.Builder().build())
            sessions[tabId] = placeholder
            return placeholder
        }
        val runtime = App.geckoRuntime
        val tab = tabs.firstOrNull { it.id == tabId }
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(tab?.isPrivate == true)
            .build()
        settings.useTrackingProtection = tab?.shield ?: true
        settings.userAgentMode =
            if (tab?.desktopSite == true) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        settings.displayMode = GeckoSessionSettings.DISPLAY_MODE_BROWSER

        val session = GeckoSession(settings)
        configureDelegates(session, tabId)
        session.open(runtime)
        sessions[tabId] = session
        if (autoLoad) maybeLoad(tabId, session)
        return session
    }

    private fun maybeLoad(tabId: Int, session: GeckoSession) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val url = tab.url
        if (url.isBlank() || tab.isStartPage) return
        if (sessionLoads[tabId] == url) return
        sessionLoads[tabId] = url
        runCatching { session.loadUri(url) }
    }

    private fun configureDelegates(session: GeckoSession, tabId: Int) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val isMainFrame = request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT
                return if (handleExternalUrl(uri, isMainFrame)) GeckoResult.deny() else null
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                val u = url ?: return
                runCatching {
                    sessionLoads[tabId] = u
                    patch(tabId) { copy(url = u, secure = u.startsWith("https://")) }
                    offerStoreInstall(u)
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                runCatching { patch(tabId) { copy(canGoBack = canGoBack) } }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                runCatching { patch(tabId) { copy(canGoForward = canGoForward) } }
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val tab = tabs.firstOrNull { it.id == tabId }
                val index = newTab(url = uri, isPrivate = tab?.isPrivate == true)
                val newTabId = tabs[index].id
                val newSession = sessionFor(newTabId, autoLoad = false)
                return GeckoResult.fromValue(newSession)
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                runCatching { patch(tabId) { copy(progress = 100) } }
                return null
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                runCatching {
                    sessionLoads[tabId] = url
                    if (storeOffer != null) storeOffer = null
                    patch(tabId) {
                        copy(url = url, title = "", progress = 0, secure = url.startsWith("https://"), blocked = 0)
                    }
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                runCatching {
                    val tab = tabs.firstOrNull { it.id == tabId } ?: return
                    patch(tabId) { copy(progress = 100) }
                    if (Store.historyEnabled && !tab.isPrivate && tab.url.startsWith("http")) {
                        Store.addHistory(tab.title.ifBlank { tab.url }, tab.url)
                    }
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                runCatching {
                    val cur = tabs.firstOrNull { it.id == tabId }?.progress ?: return
                    if (kotlin.math.abs(progress - cur) < 3 && progress < 100) return
                    patch(tabId) { copy(progress = progress.coerceIn(0, 100)) }
                }
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                runCatching { patch(tabId) { copy(secure = securityInfo.isSecure) } }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                runCatching {
                    if (!title.isNullOrBlank()) patch(tabId) { copy(title = title) }
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                runCatching {
                    session.settings.displayMode =
                        if (fullScreen) GeckoSessionSettings.DISPLAY_MODE_FULLSCREEN else GeckoSessionSettings.DISPLAY_MODE_BROWSER
                    setFullscreenUiState(fullScreen)
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                runCatching {
                    val url = response.uri
                    val lower = url.lowercase()
                    val isExt = lower.endsWith(".xpi") || lower.endsWith(".crx")
                    if (isExt) {
                        ExtensionManager.installFromUrl(App.context, url)
                    } else {
                        val mime = response.headers?.get("Content-Type")
                        Downloads.start(App.context, url, "", null, mime, -1L) { msg ->
                            lastDownloadMessage = msg
                        }
                    }
                }
            }

            override fun onCloseRequest(session: GeckoSession) {
                runCatching {
                    val i = tabs.indexOfFirst { it.id == tabId }
                    if (i >= 0) closeTab(i)
                }
            }

            override fun onCrash(session: GeckoSession) {
                runCatching {
                    val url = tabs.firstOrNull { it.id == tabId }?.url ?: "about:blank"
                    val now = System.currentTimeMillis()
                    val times = crashTimes.getOrPut(tabId) { mutableListOf() }
                    times.add(now)
                    val recent = times.count { now - it < 10_000 }
                    if (recent > 2) {
                        patch(tabId) { copy(progress = 100, title = "This page keeps crashing") }
                        lastShieldNotice = "This page crashed repeatedly, so it was stopped. Tap reload to try once more."
                    } else {
                        patch(tabId) { copy(progress = 100, title = "Page crashed") }
                        lastShieldNotice = "The page crashed — reloading…"
                        session.loadUri(url)
                    }
                }
            }

            override fun onKill(session: GeckoSession) {
                runCatching {
                    patch(tabId) { copy(progress = 100, title = "Page was killed") }
                    lastShieldNotice = "The system stopped this page — reload to try again."
                }
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                runCatching {
                    totalBlocked += 1
                    patch(tabId) { copy(blocked = blocked + 1) }
                }
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                App.requestAndroidPermissions(permissions?.toList() ?: emptyList()) { granted ->
                    if (granted) callback.grant() else callback.reject()
                }
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int>? {
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                App.pendingFilePrompt = prompt
                App.pendingFilePromptResult = result
                App.activity?.openFileChooser()
                return result
            }
        }
    }

    private fun handleExternalUrl(url: String, isMainFrame: Boolean): Boolean {
        val scheme = url.substringBefore(":").lowercase()
        if (scheme in WEB_SCHEMES) return false
        if (isMainFrame) openExternal(url)
        return true
    }

    private fun offerStoreInstall(url: String) {
        val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
        if (host.isEmpty()) return
        if (host == "addons.mozilla.org" || host.endsWith(".addons.mozilla.org")) {
            val m = Regex("/(?:firefox/)?addon/([^/?]+)").find(url) ?: return
            val slug = m.groupValues[1]
            val key = "amo:$slug"
            if (storeOffer?.key == key) return
            val name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() }
            storeOffer = StoreOffer("amo", slug, name)
            return
        }
        val isChromeStore =
            host == "chromewebstore.google.com" || host == "newebstore.google.com" || host.endsWith(".chromewebstore.google.com") || host.endsWith(".newebstore.google.com")
        if (isChromeStore) {
            val m = Regex("/detail/([^/]+)/([a-p]{32})").find(url) ?: return
            val id = m.groupValues[2]
            val key = "chrome:$id"
            if (storeOffer?.key == key) return
            val slug = m.groupValues[1]
            val name = slug.split('-', '_').joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            storeOffer = StoreOffer("chrome", id, name)
        }
    }

    fun activate(index: Int) {
        if (index !in tabs.indices) return
        activeIndex = index
    }

    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        sessions.remove(tab.id)?.let {
            runCatching {
                it.settings.displayMode = GeckoSessionSettings.DISPLAY_MODE_BROWSER
                it.close()
            }
        }
        sessionLoads.remove(tab.id)
        crashTimes.remove(tab.id)
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
            sessions.remove(id)?.let { runCatching { it.close() } }
            sessionLoads.remove(id)
        }
        tabs.removeAll { it.isPrivate }
        if (tabs.isEmpty()) {
            activeIndex = -1
            newTab()
        } else if (activeIndex >= tabs.size) {
            activeIndex = tabs.lastIndex
        }
    }

    fun closeAllSessions() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
        sessionLoads.clear()
        crashTimes.clear()
    }

    fun reopenActiveSession() {
        val tab = activeTab ?: return
        val session = sessionFor(tab.id)
        geckoView?.let { v ->
            runCatching { v.releaseSession() }
            runCatching { v.setSession(session) }
        }
    }

    fun navigate(input: String) {
        val tab = activeTab ?: return
        val url = interpret(input) ?: return
        patch(tab.id) { copy(url = url) }
        val session = sessionFor(tab.id)
        if (sessionLoads[tab.id] != url) {
            sessionLoads[tab.id] = null
            runCatching { session.loadUri(url) }
            sessionLoads[tab.id] = url
        }
    }

    fun loadInTab(index: Int, input: String) {
        val tab = tabs.getOrNull(index) ?: return
        val url = interpret(input) ?: return
        activate(index)
        patch(tab.id) { copy(url = url) }
        val session = sessionFor(tab.id)
        if (sessionLoads[tab.id] != url) {
            sessionLoads[tab.id] = null
            runCatching { session.loadUri(url) }
            sessionLoads[tab.id] = url
        }
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
        sessions[tab.id]?.let { runCatching { it.loadUri("about:blank") } }
    }

    fun toggleShield() {
        val tab = activeTab ?: return
        val next = !tab.shield
        patch(tab.id) { copy(shield = next) }
        sessions[tab.id]?.let { session ->
            runCatching {
                session.settings.useTrackingProtection = next
                session.reload()
            }
        }
        if (Store.adblockLevel != "off") {
            lastShieldNotice = "Ad blocking turned ${if (next) "on" else "off"} for this site. Reload the page."
        }
    }

    fun applyShieldToAll() {
        val on = Store.adblockLevel != "off"
        tabs.forEach { tab ->
            patch(tab.id) { copy(shield = on) }
            sessions[tab.id]?.let { session ->
                runCatching { session.settings.useTrackingProtection = on }
            }
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
        sessions[tab.id]?.let { session ->
            runCatching {
                session.settings.userAgentMode =
                    if (next) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                session.reload()
            }
        }
    }

    fun setFullscreenUiState(on: Boolean) {
        if (fullscreen == on) return
        fullscreen = on
        runCatching {
            if (on) App.activity?.setFullscreenUi(true) else App.activity?.setFullscreenUi(false)
        }
    }

    fun exitFullscreen() {
        activeSession()?.let { session ->
            runCatching {
                session.settings.displayMode = GeckoSessionSettings.DISPLAY_MODE_BROWSER
            }
        }
        setFullscreenUiState(false)
    }

    fun reportBlocked(tabId: Int, n: Int = 1) {
        totalBlocked += n
        patch(tabId) { copy(blocked = blocked + n) }
    }

    fun back(): Boolean {
        val tab = activeTab ?: return false
        return if (tab.canGoBack) {
            activeSession()?.goBack()
            true
        } else false
    }

    fun forward() {
        activeSession()?.goForward()
    }

    fun reload() {
        val tab = activeTab ?: return
        if (tab.isStartPage) return
        activeSession()?.reload()
    }

    fun stop() {
        activeSession()?.stop()
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

    private val WEB_SCHEMES = setOf("http", "https", "about", "data", "file", "javascript", "blob", "ws", "wss")
}
