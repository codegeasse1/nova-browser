package com.nova.browser.engine

import android.content.Context
import com.nova.browser.store.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

object AdBlocker {
    var ready = false
        private set
    private val io = CoroutineScope(Dispatchers.IO)

    private val exceptions = mutableListOf<Rule>()
    private val hostRules = HashMap<String, MutableList<Rule>>()
    private val exactRules = mutableListOf<Rule>()
    private val prefixRules = mutableListOf<Rule>()
    private val regexRules = mutableListOf<Rule>()
    private val plainRules = mutableListOf<Rule>()

    var loadedRules = 0
        private set

    fun init(context: Context) {
        load(context)
    }

    fun applyLevel(context: Context) {
        load(context)
    }

    private var loadJob: Job? = null

    fun load(context: Context) {
        loadJob?.cancel()
        ready = false
        loadJob = io.launch {
            runCatching {
                val names = when (Store.adblockLevel) {
                    "off" -> emptyList()
                    "strict" -> listOf("easylist.txt", "easyprivacy.txt", "annoyances.txt")
                    else -> listOf("easylist.txt", "easyprivacy.txt")
                }
                synchronized(parseLock) {
                    clearRules()
                    for (name in names) {
                        runCatching {
                            val text = context.assets.open("filters/$name").bufferedReader().use { it.readText() }
                            parseList(text)
                        }
                    }
                }
                loadedRules = countRules()
                ready = true
            }
        }
    }

    private val parseLock = Any()

    private fun clearRules() {
        exceptions.clear()
        hostRules.clear()
        exactRules.clear()
        prefixRules.clear()
        regexRules.clear()
        plainRules.clear()
    }

    private fun countRules(): Int =
        exceptions.size + exactRules.size + prefixRules.size + regexRules.size + plainRules.size + hostRules.values.sumOf { it.size }

    private fun parseList(text: String) {
        for (raw in text.lines()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("!") || line.startsWith("[")) continue
            if (line.contains("##") || line.contains("#@#") || line.contains("#?#")) continue

            var isException = false
            if (line.startsWith("@")) {
                if (!line.startsWith("@@")) continue
                isException = true
                line = line.substring(2)
            }

            var options = ""
            val dollar = line.lastIndexOf('$')
            if (dollar > 0) {
                val opt = line.substring(dollar + 1)
                if (opt.isNotBlank() && opt.length < 120 && !opt.contains("://")) {
                    options = opt
                    line = line.substring(0, dollar)
                }
            }

            val rule = buildRule(line, options) ?: continue
            if (isException) {
                exceptions.add(rule)
            } else {
                when (rule.kind) {
                    Kind.HOST -> hostRules.getOrPut(rule.domainKey!!) { mutableListOf() }.add(rule)
                    Kind.EXACT -> exactRules.add(rule)
                    Kind.PREFIX -> prefixRules.add(rule)
                    Kind.REGEX -> regexRules.add(rule)
                    Kind.PLAIN -> plainRules.add(rule)
                }
            }
        }
    }

    private enum class Kind { HOST, EXACT, PREFIX, REGEX, PLAIN }

    private data class Rule(
        val kind: Kind,
        val pattern: String,
        val domainKey: String?,
        val pathPart: String?,
        val regex: Regex?,
        val thirdPartyOnly: Boolean,
        val firstPartyOnly: Boolean,
        val allowedPages: List<String>,
        val excludedPages: List<String>,
    )

    private fun buildRule(pattern: String, options: String): Rule? {
        val opts = options.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val thirdParty = opts.any { it == "third-party" || it == "3p" }
        val firstParty = opts.any { it == "first-party" }
        var allowedPages = emptyList<String>()
        var excludedPages = emptyList<String>()
        opts.filter { it.startsWith("domain=") || it.startsWith("domain:") }.forEach { d ->
            val list = d.substringAfter('=').substringAfter(':').split('|').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
            allowedPages = list.filterNot { it.startsWith("~") }
            excludedPages = list.filter { it.startsWith("~") }.map { it.drop(1) }
        }

        var p = pattern.trim()
        if (p.length < 3) return null

        if (p.startsWith("/") && p.length > 2 && p.endsWith("/") && !p.endsWith("\\/")) {
            val inner = p.substring(1, p.length - 1)
            val re = runCatching { Regex(inner) }.getOrNull() ?: return null
            return Rule(Kind.REGEX, inner, null, null, re, thirdParty, firstParty, allowedPages, excludedPages)
        }

        if (p.startsWith("||")) {
            var host = p.substring(2)
            var pathPart: String? = null
            val slash = host.indexOf('/')
            if (slash >= 0) {
                pathPart = host.substring(slash)
                host = host.substring(0, slash)
            }
            host = host.trimEnd('^').trimEnd('|')
            if (host.isEmpty()) return null
            host = host.lowercase(Locale.ROOT)
            if (host.length < 3) return null
            return Rule(Kind.HOST, p, host, pathPart?.trimEnd('^'), null, thirdParty, firstParty, allowedPages, excludedPages)
        }

        val exact = p.startsWith("|") && p.endsWith("|")
        if (exact) {
            val inner = p.substring(1, p.length - 1)
            if (inner.length < 3) return null
            return Rule(Kind.EXACT, inner, null, null, null, thirdParty, firstParty, allowedPages, excludedPages)
        }

        if (p.startsWith("|")) {
            val inner = p.substring(1)
            if (inner.length < 3) return null
            return Rule(Kind.PREFIX, inner, null, null, null, thirdParty, firstParty, allowedPages, excludedPages)
        }

        return Rule(Kind.PLAIN, p, null, null, null, thirdParty, firstParty, allowedPages, excludedPages)
    }

    fun shouldBlock(url: String, pageUrl: String): Boolean {
        if (!ready || url.isBlank()) return false
        if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("about:") || url.startsWith("chrome:")) return false

        val host = hostOf(url) ?: return false
        val pageHost = hostOf(pageUrl) ?: host
        val thirdParty = host != pageHost &&
            !host.endsWith(".$pageHost") && !pageHost.endsWith(".$host")

        synchronized(parseLock) {
            if (matchesList(exceptions, url, host, pageHost, thirdParty)) return false

            var h = host
            while (h.isNotEmpty()) {
                hostRules[h]?.let { rules ->
                    if (matchesList(rules, url, host, pageHost, thirdParty)) return true
                }
                val dot = h.indexOf('.')
                if (dot < 0 || dot == h.lastIndex) break
                h = h.substring(dot + 1)
            }

            if (matchesList(exactRules, url, host, pageHost, thirdParty)) return true
            if (matchesList(prefixRules, url, host, pageHost, thirdParty)) return true
            if (matchesList(regexRules, url, host, pageHost, thirdParty)) return true
            if (matchesList(plainRules, url, host, pageHost, thirdParty)) return true
        }
        return false
    }

    private fun matchesList(rules: List<Rule>, url: String, host: String, pageHost: String, thirdParty: Boolean): Boolean {
        for (r in rules) {
            if (!matchRule(r, url, host, pageHost, thirdParty)) continue
            return true
        }
        return false
    }

    private fun matchRule(r: Rule, url: String, host: String, pageHost: String, thirdParty: Boolean): Boolean {
        if (r.thirdPartyOnly && !thirdParty) return false
        if (r.firstPartyOnly && thirdParty) return false
        if (r.allowedPages.isNotEmpty() && !pageHost.isAllowedBy(r.allowedPages)) return false
        if (pageHost.isInList(r.excludedPages)) return false

        return when (r.kind) {
            Kind.HOST -> {
                if (!host.endsWith(r.domainKey!!) && host != r.domainKey) return false
                if (r.pathPart == null) true
                else {
                    val path = pathOf(url)
                    path.startsWith(r.pathPart) || url.contains(r.domainKey + r.pathPart)
                }
            }
            Kind.EXACT -> url == r.pattern
            Kind.PREFIX -> url.startsWith(r.pattern)
            Kind.REGEX -> r.regex?.matches(url) == true
            Kind.PLAIN -> r.pattern.length >= 5 && url.contains(r.pattern)
        }
    }

    private fun String.isAllowedBy(list: List<String>): Boolean = list.any { this == it || this.endsWith(".$it") }

    private fun String.isInList(list: List<String>): Boolean = list.any { this == it || this.endsWith(".$it") }

    private fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull()

    private fun pathOf(url: String): String = runCatching {
        val uri = URI(url)
        val p = uri.path ?: "/"
        val q = uri.rawQuery
        if (q == null) p else "$p?$q"
    }.getOrDefault(url)
}
