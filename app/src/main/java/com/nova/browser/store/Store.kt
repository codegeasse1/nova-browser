package com.nova.browser.store

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

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
        if (list.size > 200) list.subList(200, list.size).clear()
        saveTriples(KEY_HISTORY, list)
    }

    fun history(): List<Triple<String, String, Long>> = loadTriples(KEY_HISTORY)

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

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
                "Perchance" to "https://perchance.org",
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
    private const val KEY_DOH = "doh_mode"
    private const val KEY_DOH_PROVIDER = "doh_provider"
    private const val KEY_SEARCH = "search_engine"
    private const val KEY_THEME = "theme"

    var adblockLevel: String
        get() = prefs.getString(KEY_ADBLOCK, "standard") ?: "standard"
        set(v) = prefs.edit().putString(KEY_ADBLOCK, v).apply()

    var dohMode: String
        get() = prefs.getString(KEY_DOH, "first") ?: "first"
        set(v) = prefs.edit().putString(KEY_DOH, v).apply()

    var dohProvider: String
        get() = prefs.getString(KEY_DOH_PROVIDER, "https://mozilla.cloudflare-dns.com/dns-query")
            ?: "https://mozilla.cloudflare-dns.com/dns-query"
        set(v) = prefs.edit().putString(KEY_DOH_PROVIDER, v).apply()

    var searchEngine: String
        get() = prefs.getString(KEY_SEARCH, "google") ?: "google"
        set(v) = prefs.edit().putString(KEY_SEARCH, v).apply()

    var theme: String
        get() = prefs.getString(KEY_THEME, "auto") ?: "auto"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    fun clearAllData() {
        prefs.edit().remove(KEY_BOOKMARKS).remove(KEY_HISTORY).remove(KEY_DIALS).apply()
    }

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
}
