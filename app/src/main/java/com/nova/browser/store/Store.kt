package com.nova.browser.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Settings(
    val homeUrl: String = "https://www.google.com",
    val searchEngine: String = "google",
    val privateMode: Boolean = false,
    val javaScriptEnabled: Boolean = true,
    val showImages: Boolean = true
)

data class PageEntry(val url: String, val title: String, val time: Long)

class Store(context: Context) {

    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val bookmarksFile = File(dir, "bookmarks.json")
    private val historyFile = File(dir, "history.json")
    private val settingsFile = File(dir, "settings.json")

    fun loadBookmarks(): List<PageEntry> {
        val arr = readJsonArray(bookmarksFile)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            PageEntry(o.optString("url"), o.optString("title"), o.optLong("time"))
        }
    }

    fun addBookmark(url: String, title: String) {
        val list = loadBookmarks().filter { it.url != url }.toMutableList()
        list.add(0, PageEntry(url, title.ifBlank { url }, System.currentTimeMillis()))
        writeJsonArray(bookmarksFile, list.map { it -> JSONObject().put("url", it.url).put("title", it.title).put("time", it.time) })
    }

    fun removeBookmark(url: String) {
        writeJsonArray(bookmarksFile, loadBookmarks().filter { it.url != url }.map { it -> JSONObject().put("url", it.url).put("title", it.title).put("time", it.time) })
    }

    fun isBookmarked(url: String): Boolean = loadBookmarks().any { it.url == url }

    fun loadHistory(limit: Int = 500): List<PageEntry> = loadHistoryRaw().take(limit)

    private fun loadHistoryRaw(): List<PageEntry> {
        val arr = readJsonArray(historyFile)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            PageEntry(o.optString("url"), o.optString("title"), o.optLong("time"))
        }
    }

    fun addHistory(url: String, title: String) {
        if (url.isBlank()) return
        if (url.startsWith("nova://") || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("file:")) return
        val existing = loadHistoryRaw()
        val list = existing.filter { it.url != url }.toMutableList()
        list.add(0, PageEntry(url, title.ifBlank { url }, System.currentTimeMillis()))
        writeJsonArray(historyFile, list.take(1000).map { it -> JSONObject().put("url", it.url).put("title", it.title).put("time", it.time) })
    }

    fun clearHistory() {
        writeJsonArray(historyFile, emptyList())
    }

    fun loadSettings(): Settings {
        return try {
            val o = JSONObject(settingsFile.readText())
            Settings(
                homeUrl = o.optString("homeUrl", "https://www.google.com"),
                searchEngine = o.optString("searchEngine", "google"),
                privateMode = o.optBoolean("privateMode", false),
                javaScriptEnabled = o.optBoolean("javaScriptEnabled", true),
                showImages = o.optBoolean("showImages", true)
            )
        } catch (e: Exception) {
            Settings()
        }
    }

    fun saveSettings(s: Settings) {
        val o = JSONObject()
            .put("homeUrl", s.homeUrl)
            .put("searchEngine", s.searchEngine)
            .put("privateMode", s.privateMode)
            .put("javaScriptEnabled", s.javaScriptEnabled)
            .put("showImages", s.showImages)
        settingsFile.writeText(o.toString())
    }

    fun externalStorageDir(context: Context): File =
        File(context.filesDir, "extensions").apply { mkdirs() }

    private fun readJsonArray(f: File): JSONArray = try {
        JSONArray(f.readText())
    } catch (e: Exception) {
        JSONArray()
    }

    private fun writeJsonArray(f: File, items: List<JSONObject>) {
        f.writeText(JSONArray(items).toString())
    }
}
