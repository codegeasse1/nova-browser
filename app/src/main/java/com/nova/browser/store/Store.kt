package com.nova.browser.store

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object Store {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("nova_prefs", Context.MODE_PRIVATE)
    }

    private const val KEY_BOOKMARKS = "bookmarks"

    fun addBookmark(title: String, url: String) {
        val list = bookmarks().filter { it.second != url }.toMutableList()
        list.add(0, title to url)
        savePairs(KEY_BOOKMARKS, list)
    }

    fun removeBookmark(url: String) {
        savePairs(KEY_BOOKMARKS, bookmarks().filter { it.second != url })
    }

    fun isBookmarked(url: String): Boolean = bookmarks().any { it.second == url }

    fun bookmarks(): List<Pair<String, String>> = loadPairs(KEY_BOOKMARKS)

    private const val KEY_HISTORY = "history"

    fun addHistory(title: String, url: String) {
        val list = history().filter { it.second != url }.toMutableList()
        list.add(0, Triple(title.ifBlank { url }, url, System.currentTimeMillis()))
        if (list.size > 300) list.subList(300, list.size).clear()
        saveTriples(KEY_HISTORY, list)
    }

    fun history(): List<Triple<String, String, Long>> = loadTriples(KEY_HISTORY)

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    fun randomizeHistory() {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        val list = mutableListOf<Triple<String, String, Long>>()
        var i = 0
        while (list.size < 14) {
            val (t, q) = STUDY_TOPICS[Random.nextInt(STUDY_TOPICS.size)]
            val url = "https://www.google.com/search?q=${android.net.Uri.encode(q)}"
            val time = now - Random.nextLong(1, 14) * day - Random.nextLong(0, day)
            if (list.none { it.second == url }) {
                list.add(Triple(t, url, time))
            }
            i++
            if (i > 60) break
        }
        saveTriples(KEY_HISTORY, list)
    }

    private val STUDY_TOPICS = listOf(
        "Photosynthesis explained" to "how does photosynthesis work",
        "World War 2 timeline" to "world war 2 major events timeline",
        "Newton's laws of motion" to "newtons laws of motion examples",
        "The water cycle" to "water cycle diagram explained",
        "Grammar rules" to "english grammar tenses explained",
        "Algebra basics" to "how to solve linear equations",
        "Human body systems" to "human digestive system function",
        "Solar system planets" to "solar system planets facts",
        "Chemical reactions" to "what is a chemical reaction",
        "The Cold War" to "cold war causes and consequences",
        "Cell biology" to "animal cell parts and functions",
        "Shakespeare's plays" to "william shakespeare famous plays summary",
        "World geography" to "countries and capitals quiz",
        "Maths fractions" to "how to add fractions",
        "The French Revolution" to "french revolution causes summary",
        "DNA and genetics" to "what is dna structure",
        "Climate change basics" to "climate change causes and effects",
        "Essay writing tips" to "how to write a good essay introduction",
    )

    private const val KEY_DIALS = "speed_dials"

    fun speedDials(): List<Pair<String, String>> {
        val d = loadPairs(KEY_DIALS)
        if (d.isEmpty()) {
            return listOf(
                "YouTube" to "https://youtube.com",
                "Google" to "https://google.com",
                "Wikipedia" to "https://en.wikipedia.org",
                "X" to "https://x.com",
                "GitHub" to "https://github.com",
            )
        }
        return d
    }

    fun addSpeedDial(title: String, url: String) {
        val list = speedDials().filter { it.second != url }.toMutableList()
        list.add(0, title to url)
        savePairs(KEY_DIALS, list)
    }

    fun removeSpeedDial(url: String) {
        savePairs(KEY_DIALS, speedDials().filter { it.second != url })
    }

    private const val KEY_ADBLOCK = "adblock_level"
    private const val KEY_SAFE_BROWSING = "safe_browsing"
    private const val KEY_SEARCH = "search_engine"
    private const val KEY_THEME = "theme"
    private const val KEY_DNS = "dns_mode"
    private const val KEY_QUICK_ACCESS = "quick_access"
    private const val KEY_HISTORY_ENABLED = "history_enabled"
    private const val KEY_AUTO_CLEAR_HISTORY = "auto_clear_history"
    private const val KEY_BLOCKED_DOMAINS = "blocked_domains"
    private const val KEY_WHITELISTED_DOMAINS = "whitelisted_domains"
    private const val KEY_EXT_DEFAULTS = "ext_defaults_applied"

    var adblockLevel: String
        get() = prefs.getString(KEY_ADBLOCK, "standard") ?: "standard"
        set(v) = prefs.edit().putString(KEY_ADBLOCK, v).apply()

    var safeBrowsing: Boolean
        get() = prefs.getBoolean(KEY_SAFE_BROWSING, true)
        set(v) = prefs.edit().putBoolean(KEY_SAFE_BROWSING, v).apply()

    var searchEngine: String
        get() = prefs.getString(KEY_SEARCH, "google") ?: "google"
        set(v) = prefs.edit().putString(KEY_SEARCH, v).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "auto") ?: "auto"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var dnsMode: String
        get() = prefs.getString(KEY_DNS, "off") ?: "off"
        set(v) = prefs.edit().putString(KEY_DNS, v).apply()

    var quickAccessEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUICK_ACCESS, true)
        set(v) = prefs.edit().putBoolean(KEY_QUICK_ACCESS, v).apply()

    var historyEnabled: Boolean
        get() = prefs.getBoolean(KEY_HISTORY_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_HISTORY_ENABLED, v).apply()

    var autoClearHistory: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLEAR_HISTORY, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_CLEAR_HISTORY, v).apply()

    var extDefaultsApplied: Boolean
        get() = prefs.getBoolean(KEY_EXT_DEFAULTS, false)
        set(v) = prefs.edit().putBoolean(KEY_EXT_DEFAULTS, v).apply()

    fun blockedDomains(): List<String> = loadStringList(KEY_BLOCKED_DOMAINS)

    fun whitelistedDomains(): List<String> = loadStringList(KEY_WHITELISTED_DOMAINS)

    fun addBlockedDomain(domain: String) {
        saveStringList(KEY_BLOCKED_DOMAINS, (blockedDomains() + domain.trim().lowercase().removePrefix("www.")).distinct())
    }

    fun removeBlockedDomain(domain: String) {
        saveStringList(KEY_BLOCKED_DOMAINS, blockedDomains().filter { it != domain })
    }

    fun addWhitelistedDomain(domain: String) {
        saveStringList(KEY_WHITELISTED_DOMAINS, (whitelistedDomains() + domain.trim().lowercase().removePrefix("www.")).distinct())
    }

    fun removeWhitelistedDomain(domain: String) {
        saveStringList(KEY_WHITELISTED_DOMAINS, whitelistedDomains().filter { it != domain })
    }

    fun clearAllData() {
        prefs.edit().remove(KEY_BOOKMARKS).remove(KEY_HISTORY).remove(KEY_DIALS).apply()
    }

    private const val KEY_EXT_DISABLED = "ext_disabled"

    fun disabledExtensions(): Set<String> = prefs.getStringSet(KEY_EXT_DISABLED, emptySet()) ?: emptySet()

    fun setExtensionEnabled(id: String, enabled: Boolean) {
        val cur = disabledExtensions().toMutableSet()
        if (enabled) cur.remove(id) else cur.add(id)
        prefs.edit().putStringSet(KEY_EXT_DISABLED, cur).apply()
    }

    private const val KEY_DOWNLOADS = "downloads"

    fun loadObjects(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    fun saveObjects(key: String, list: List<JSONObject>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    const val KEY_DOWNLOADS_LIST = KEY_DOWNLOADS

    private fun loadPairs(key: String): List<Pair<String, String>> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getString("t") to o.getString("u")
            }
        }.getOrDefault(emptyList())
    }

    private fun savePairs(key: String, list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.forEach { (t, u) -> arr.put(JSONObject().put("t", t).put("u", u)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadTriples(key: String): List<Triple<String, String, Long>> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Triple(o.getString("t"), o.getString("u"), o.getLong("x"))
            }
        }.getOrDefault(emptyList())
    }

    private fun saveTriples(key: String, list: List<Triple<String, String, Long>>) {
        val arr = JSONArray()
        list.forEach { (t, u, x) -> arr.put(JSONObject().put("t", t).put("u", u).put("x", x)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadStringList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun saveStringList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
