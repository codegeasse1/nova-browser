package com.nova.browser.ext

import org.json.JSONObject

data class ExtContentScript(
    val matches: List<String>,
    val js: List<String>,
    val css: List<String>,
    val runAt: String,
    val allFrames: Boolean
)

data class ExtManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val manifestVersion: Int,
    val permissions: List<String>,
    val contentScripts: List<ExtContentScript>,
    val backgroundScripts: List<String>,
    val backgroundPage: String?,
    val serviceWorker: String?,
    val popup: String?,
    val icons: Map<String, String>,
    val dir: String
) {
    val hasBackground: Boolean get() = backgroundScripts.isNotEmpty() || backgroundPage != null || serviceWorker != null

    companion object {
        fun parse(id: String, rootJson: String, dir: String): ExtManifest {
            val j = JSONObject(rootJson)
            val mv = j.optInt("manifest_version", 2)

            val contentScripts = mutableListOf<ExtContentScript>()
            val cs = j.optJSONArray("content_scripts")
            if (cs != null) {
                for (i in 0 until cs.length()) {
                    val o = cs.optJSONObject(i) ?: continue
                    contentScripts.add(
                        ExtContentScript(
                            matches = optStrList(o, "matches"),
                            js = optStrList(o, "js"),
                            css = optStrList(o, "css"),
                            runAt = o.optString("run_at", "document_idle"),
                            allFrames = o.optBoolean("all_frames", false)
                        )
                    )
                }
            }

            var bgScripts = emptyList<String>()
            var bgPage: String? = null
            var sw: String? = null
            val bg = j.optJSONObject("background")
            if (bg != null) {
                bgScripts = optStrList(bg, "scripts")
                bgPage = bg.optString("page").ifBlank { null }
                sw = bg.optString("service_worker").ifBlank { null }
            } else if (j.has("background") && j.opt("background") is String) {
                bgPage = j.optString("background")
            }

            var popup: String? = null
            val action = j.optJSONObject("action")
            if (action != null) popup = action.optString("default_popup").ifBlank { null }
            val baction = j.optJSONObject("browser_action")
            if (baction != null) popup = baction.optString("default_popup").ifBlank { popup }

            val icons = mutableMapOf<String, String>()
            val ico = j.optJSONObject("icons")
            if (ico != null) {
                val keys = ico.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    icons[k] = ico.optString(k)
                }
            }

            return ExtManifest(
                id = id,
                name = j.optString("name", "Unnamed extension"),
                version = j.optString("version", "0.0"),
                description = j.optString("description", ""),
                manifestVersion = mv,
                permissions = optStrList(j, "permissions"),
                contentScripts = contentScripts,
                backgroundScripts = bgScripts,
                backgroundPage = bgPage,
                serviceWorker = sw,
                popup = popup,
                icons = icons,
                dir = dir
            )
        }

        private fun optStrList(o: JSONObject, key: String): List<String> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) out.add(arr.optString(i))
            return out
        }
    }
}

object MatchPattern {

    fun matches(pattern: String, url: String): Boolean {
        val clean = pattern.trim()
        if (clean == "<all_urls>") return url.startsWith("http://") || url.startsWith("https://")
        val u = try { java.net.URI(url) } catch (e: Exception) { return false }
        val scheme = u.scheme?.lowercase() ?: return false
        val host = u.host?.lowercase() ?: ""
        val path = u.path.ifEmpty { "/" }

        if (!clean.contains("://")) {
            return globMatch(clean, url)
        }
        val parts = clean.split("://", limit = 2)
        if (parts.size < 2) return false
        val patScheme = parts[0].lowercase()
        if (patScheme != "*" && patScheme != scheme) return false

        val rest = parts[1]
        val slash = rest.indexOf('/')
        val patHost = (if (slash < 0) rest else rest.substring(0, slash)).lowercase()
        val patPath = if (slash < 0) "/*" else rest.substring(slash)

        if (!hostMatches(patHost, host)) return false
        return globMatch(patPath, path)
    }

    fun matchesAny(patterns: List<String>, url: String): Boolean = patterns.any { matches(it, url) }

    private fun hostMatches(pat: String, host: String): Boolean {
        if (pat == "*" || pat.isEmpty()) return true
        if (pat.startsWith("*.")) {
            val base = pat.substring(2)
            return host == base || host.endsWith("." + base)
        }
        return pat == host
    }

    private fun globMatch(pattern: String, text: String): Boolean {
        val sb = StringBuilder("^")
        for (ch in pattern) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.', '(', ')', '[', ']', '{', '}', '^', '$', '|', '+', '\\' -> { sb.append('\\'); sb.append(ch) }
                else -> sb.append(ch)
            }
        }
        sb.append('$')
        return try {
            Regex(sb.toString()).matches(text)
        } catch (e: Exception) {
            false
        }
    }
}
