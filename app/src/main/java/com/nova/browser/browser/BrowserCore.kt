package com.nova.browser.browser

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.browser.App
import com.nova.browser.store.Store
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse

data class TabState(
    val id: Int,
    val session: GeckoSession,
    val isPrivate: Boolean,
    val title: String = "",
    val url: String = "",
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean = false,
    val blocked: Int = 0,
    val shield: Boolean = true,
) {
    val isStartPage: Boolean get() = url.isBlank()
    val host: String get() = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
}

object BrowserCore {
    val tabs = mutableStateListOf<TabState>()
    var activeIndex by mutableIntStateOf(-1)
    var totalBlocked by mutableStateOf(0L)
    var lastDownloadMessage by mutableStateOf<String?>(null)
    var pendingExternalIntent by mutableStateOf<String?>(null)
    private var nextId = 1

    val activeTab: TabState? get() = tabs.getOrNull(activeIndex)

    fun newTab(url: String? = null, isPrivate: Boolean = false): Int {
        val settings = GeckoSessionSettings.Builder().usePrivateMode(isPrivate).build()
        val session = GeckoSession(settings)
        val tab = TabState(id = nextId++, session = session, isPrivate = isPrivate, shield = Store.adblockLevel != "off")
        wire(session, tab.id)
        runCatching { session.settings.useTrackingProtection = tab.shield }
        session.open(App.runtime)
        tabs.add(tab)
        activate(tabs.lastIndex)
        if (!url.isNullOrBlank()) session.loadUri(url)
        return tabs.lastIndex
    }

    fun activate(index: Int) {
        if (index !in tabs.indices) return
        tabs.getOrNull(activeIndex)?.session?.setActive(false)
        activeIndex = index
        tabs[index].session.setActive(true)
    }

    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        runCatching { tab.session.close() }
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            activeIndex = -1
            newTab()
        } else {
            activate(if (index < tabs.size) index else tabs.lastIndex)
        }
    }

    fun closeAllPrivate() {
        tabs.filter { it.isPrivate }.forEach { runCatching { it.session.close() } }
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
        tab.session.loadUri(url)
    }

    fun loadInTab(index: Int, input: String) {
        val tab = tabs.getOrNull(index) ?: return
        val url = interpret(input) ?: return
        activate(index)
        tab.session.loadUri(url)
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
        patch(tab.id) { copy(url = "", title = "", progress = 0, blocked = 0) }
        runCatching { tab.session.loadUri("about:blank") }
    }

    fun toggleShield() {
        val tab = activeTab ?: return
        val next = !tab.shield
        runCatching { tab.session.settings.useTrackingProtection = next }
        patch(tab.id) { copy(shield = next) }
    }

    fun applyShieldToAll() {
        val on = Store.adblockLevel != "off"
        tabs.forEach { tab ->
            runCatching { tab.session.settings.useTrackingProtection = on }
            patch(tab.id) { copy(shield = on) }
        }
    }

    fun back() = activeTab?.session?.goBack()
    fun forward() = activeTab?.session?.goForward()
    fun reload() = activeTab?.session?.reload()
    fun stop() = activeTab?.session?.stop()

    fun openExternal(raw: String) {
        val activity = App.activity ?: return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw)))
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

    private fun wire(session: GeckoSession, id: Int) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) = patch(id) { copy(progress = progress) }
            override fun onPageStart(session: GeckoSession, url: String) = patch(id) { copy(progress = 0, title = "", secure = false, blocked = 0) }
            override fun onPageStop(session: GeckoSession, success: Boolean) = patch(id) { copy(progress = 100) }
            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) = patch(id) { copy(secure = securityInfo.isSecure) }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!url.isNullOrBlank()) {
                    patch(id) { copy(url = url) }
                    if (!url.startsWith("about:") && !url.startsWith("nova:") && !url.startsWith("chrome:")) {
                        val title = tabs.firstOrNull { it.id == id }?.title.orEmpty()
                        Store.addHistory(title, url)
                    }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = patch(id) { copy(canGoBack = canGoBack) }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) = patch(id) { copy(canGoForward = canGoForward) }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val scheme = uri?.substringBefore(":")?.lowercase() ?: ""
                if (scheme in KNOWN_WEB_SCHEMES) return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                if (scheme.isNotEmpty()) {
                    openExternal(uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val isPriv = tabs.firstOrNull { it.id == id }?.isPrivate ?: false
                val newSettings = GeckoSessionSettings.Builder().usePrivateMode(isPriv).build()
                val newSession = GeckoSession(newSettings)
                val newId = nextId++
                wire(newSession, newId)
                runCatching { newSession.settings.useTrackingProtection = Store.adblockLevel != "off" }
                newSession.open(App.runtime)
                tabs.add(TabState(id = newId, session = newSession, isPrivate = isPriv, shield = Store.adblockLevel != "off"))
                activate(tabs.lastIndex)
                if (!uri.isNullOrBlank()) newSession.loadUri(uri)
                return GeckoResult.fromValue(newSession)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) = patch(id) { copy(title = title ?: "") }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                Downloads.start(App.context, response) { msg -> lastDownloadMessage = msg }
            }

            override fun onCloseRequest(session: GeckoSession) {
                val i = tabs.indexOfFirst { it.id == id }
                if (i >= 0) closeTab(i)
            }

            override fun onCrash(session: GeckoSession) {
                val i = tabs.indexOfFirst { it.id == id }
                if (i >= 0) closeTab(i)
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (event.isBlocking) {
                    patch(id) { copy(blocked = blocked + 1) }
                    totalBlocked += 1
                }
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> = GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                App.requestAndroidPermissions((permissions ?: emptyArray()).toList()) { granted ->
                    if (granted) callback.grant() else callback.reject()
                }
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                val chosen = video?.firstOrNull() ?: audio?.firstOrNull()
                if (chosen != null) callback.grant(chosen) else callback.reject()
            }
        }
    }

    private val KNOWN_WEB_SCHEMES = setOf("http", "https", "about", "data", "file", "blob", "nova")
}
