package com.nova.browser.ext

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nova.browser.browser.BrowserCore
import com.nova.browser.browser.JsBridge
import com.nova.browser.store.Store
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes chrome.* API calls between content scripts, background pages and popups,
 * owns the hidden background WebViews and injects content scripts into pages.
 */
class ExtRuntime(private val context: Context, val store: Store) {

    var core: BrowserCore? = null
    val manager = ExtensionManager(context)

    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, JsBridge>()
    private val bgViews = ConcurrentHashMap<String, WebView>()
    private val badges = ConcurrentHashMap<String, String>()

    fun createBridge(webView: WebView): JsBridge = JsBridge(webView, this)

    /** Creates a WebView configured for extension pages (background/popup). */
    fun makeExtensionWebView(): WebView {
        val wv = WebView(context)
        configureExtensionWebView(wv)
        return wv
    }

    private fun configureExtensionWebView(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = true
        wv.settings.setAllowFileAccessFromFileURLs(true)
        wv.settings.setAllowUniversalAccessFromFileURLs(true)
        wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        wv.addJavascriptInterface(createBridge(wv), "novaBridge")
        wv.webViewClient = object : WebViewClient() {}
    }

    // ---------------- content scripts ----------------

    fun injectContentScripts(webView: WebView, url: String, runAt: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val exts = manager.list().filter { it.enabled }
        for (ext in exts) {
            for (cs in ext.manifest.contentScripts) {
                if (cs.js.isEmpty() && cs.css.isEmpty()) continue
                val csRunAt = if (cs.runAt == "document_start") "document_start" else "document_idle"
                if (csRunAt != runAt) continue
                if (!MatchPattern.matchesAny(cs.matches, url)) continue
                val js = buildInjection(ext.manifest, cs)
                main.post { runCatching { webView.evaluateJavascript(js, null) } }
            }
        }
    }

    private fun buildInjection(mf: ExtManifest, cs: ExtContentScript): String {
        val sb = StringBuilder()
        sb.append("window.__novaExtId='").append(mf.id).append("';\n")
        sb.append(ShimJs.SHIM).append("\n")
        for (cssFile in cs.css) {
            val css = readExtFile(mf.dir, cssFile) ?: continue
            val quoted = JSONObject().put("css", css).toString()
            sb.append("(function(){var s=document.createElement('style');s.textContent=").append(quoted)
                .append(".css;document.head.appendChild(s);})();\n")
        }
        for (jsFile in cs.js) {
            val code = readExtFile(mf.dir, jsFile) ?: continue
            sb.append("\n(function(){try{\n").append(code).append("\n}catch(e){console.error('Nova ext ")
                .append(mf.name).append(" error:',e);}})();\n")
        }
        return sb.toString()
    }

    private fun readExtFile(dir: String, rel: String): String? {
        return try {
            val f = File(dir, rel)
            if (f.exists() && f.isFile && f.length() < 2 * 1024 * 1024) f.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- messaging ----------------

    fun runtimeSendMessage(fromWebView: WebView, extId: String, msgJson: String, cbId: String, bridge: JsBridge) {
        pending[cbId] = bridge
        main.post {
            val bg = bgViews[extId]
            if (bg != null) {
                runCatching { bg.evaluateJavascript("window.__novaOnMessage($msgJson, '$cbId')", null) }
            } else {
                deliver(cbId, "null")
            }
        }
    }

    fun runtimeReply(extId: String, cbId: String, respJson: String) {
        main.post { deliver(cbId, respJson) }
    }

    private fun deliver(cbId: String, json: String) {
        val bridge = pending.remove(cbId) ?: return
        val wv = bridge.webView
        wv.post {
            runCatching { wv.evaluateJavascript("window.__novaResp('$cbId', $json)", null) }
        }
    }

    // ---------------- storage ----------------

    fun storageGet(extId: String, cbId: String, bridge: JsBridge) {
        pending[cbId] = bridge
        main.post { deliver(cbId, manager.readStorage(extId).toString()) }
    }

    fun storageSet(extId: String, dataJson: String, cbId: String, bridge: JsBridge) {
        pending[cbId] = bridge
        main.post {
            try {
                val data = JSONObject(dataJson).getJSONObject("data")
                val storage = manager.readStorage(extId)
                val keys = data.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    storage.put(k, data.get(k))
                }
                manager.writeStorage(extId, storage)
            } catch (e: Exception) {
            }
            deliver(cbId, "null")
        }
    }

    fun storageRemove(extId: String, keysJson: String, cbId: String, bridge: JsBridge) {
        pending[cbId] = bridge
        main.post {
            try {
                val payload = JSONObject(keysJson)
                val keys = payload.opt("keys")
                val storage = manager.readStorage(extId)
                val toRemove = mutableListOf<String>()
                if (keys is JSONArray) {
                    for (i in 0 until keys.length()) toRemove.add(keys.getString(i))
                } else if (keys is String) {
                    toRemove.add(keys)
                }
                for (k in toRemove) storage.remove(k)
                manager.writeStorage(extId, storage)
            } catch (e: Exception) {
            }
            deliver(cbId, "null")
        }
    }

    fun storageClear(extId: String, cbId: String, bridge: JsBridge) {
        pending[cbId] = bridge
        main.post {
            manager.writeStorage(extId, JSONObject())
            deliver(cbId, "null")
        }
    }

    // ---------------- tabs ----------------

    fun tabsQuery(queryJson: String, cbId: String, bridge: JsBridge) {
        pending[cbId] = bridge
        main.post {
            val arr = JSONArray()
            val c = core ?: run { deliver(cbId, arr.toString()); return@post }
            for ((i, tab) in c.tabs.withIndex()) {
                val o = JSONObject()
                    .put("id", tab.id)
                    .put("index", i)
                    .put("url", tab.url)
                    .put("title", tab.title)
                    .put("active", i == c.activeIndex)
                arr.put(o)
            }
            deliver(cbId, arr.toString())
        }
    }

    fun tabsCreate(url: String) {
        main.post { core?.openNewTab(url.ifBlank { null }) }
    }

    fun tabsUpdate(url: String) {
        main.post { core?.navigate(url) }
    }

    // ---------------- badges ----------------

    fun actionSetBadge(extId: String, text: String, color: String) {
        main.post {
            if (text.isBlank()) badges.remove(extId) else badges[extId] = text
        }
    }

    fun actionSetBadgeColor(extId: String, color: String) {
        // stored color currently not rendered
    }

    fun badgeText(extId: String): String? = badges[extId]

    // ---------------- background pages ----------------

    fun ensureBackground(mf: ExtManifest) {
        if (!mf.hasBackground) return
        main.post {
            if (bgViews.containsKey(mf.id)) return@post
            val wv = makeExtensionWebView()
            bgViews[mf.id] = wv
            val html = buildBackgroundHtml(mf)
            wv.loadDataWithBaseURL("file://${mf.dir}/", html, "text/html", "utf-8", null)
        }
    }

    fun destroyBackground(id: String) {
        main.post {
            bgViews.remove(id)?.destroy()
        }
    }

    private fun buildBackgroundHtml(mf: ExtManifest): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'></head><body><script>")
        sb.append("window.__novaExtId='").append(mf.id).append("';\n")
        sb.append(ShimJs.SHIM).append("\n</script>")
        if (mf.backgroundPage != null) {
            val page = readExtFile(mf.dir, mf.backgroundPage) ?: ""
            sb.append(inlineHtmlPage(page, mf.dir))
        } else {
            val scripts = if (mf.backgroundScripts.isNotEmpty()) {
                mf.backgroundScripts
            } else {
                mf.serviceWorker?.let { listOf(it) } ?: emptyList()
            }
            for (s in scripts) {
                val code = readExtFile(mf.dir, s) ?: continue
                sb.append("<script>").append(code).append("</script>")
            }
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun inlineHtmlPage(html: String, dir: String): String {
        var out = html
        val scriptRe = Regex("<script[^>]*src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>\\s*</script>", RegexOption.IGNORE_CASE)
        out = scriptRe.replace(out) { m ->
            val src = m.groupValues[1]
            if (src.startsWith("http") || src.startsWith("//")) m.value
            else {
                val code = readExtFile(dir, src) ?: ""
                "<script>" + code + "</script>"
            }
        }
        val cssRe = Regex(
            "<link[^>]*rel\\s*=\\s*[\"']stylesheet[\"'][^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
            RegexOption.IGNORE_CASE
        )
        out = cssRe.replace(out) { m ->
            val href = m.groupValues[1]
            if (href.startsWith("http") || href.startsWith("//")) m.value
            else {
                val css = readExtFile(dir, href) ?: ""
                "<style>" + css + "</style>"
            }
        }
        return out
    }

    // ---------------- popup ----------------

    fun buildPopupHtml(mf: ExtManifest): String? {
        val popupFile = mf.popup ?: return null
        val html = readExtFile(mf.dir, popupFile) ?: return null
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='margin:0'>")
        sb.append("<script>window.__novaExtId='").append(mf.id).append("';\n")
        sb.append(ShimJs.SHIM).append("\n</script>")
        sb.append(inlineHtmlPage(html, mf.dir))
        sb.append("</body></html>")
        return sb.toString()
    }
}
