package com.nova.browser.browser

import android.net.Uri

data class TabState(
    val id: Int,
    val isPrivate: Boolean,
    val title: String = "",
    val url: String = "",
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val secure: Boolean = false,
    val blocked: Int = 0,
    val shield: Boolean = true,
    val desktopSite: Boolean = false,
) {
    val isStartPage: Boolean
        get() {
            val u = url.trim()
            return u.isEmpty() || u == "about:blank" || u == "nova:start" || u == "about:start"
        }
    val host: String get() = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
}
