/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import java.net.URI

/**
 * Nova: per-site "allow background use" settings.
 *
 * The user can enable a site (from the browser menu) so it keeps working while
 * the app is backgrounded and the screen is locked. Enabled hosts are remembered
 * in a private SharedPreferences file keyed by the bare hostname ("youtube.com").
 */
object NovaBackgroundSites {
    private const val PREFS = "nova_background_sites"
    private const val KEY = "enabled"

    fun enabledHosts(context: Context): Set<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getStringSet(KEY, emptySet()) ?: emptySet()
    }

    fun isEnabled(context: Context, host: String): Boolean = host in enabledHosts(context)

    fun toggle(context: Context, host: String) {
        val current = HashSet(enabledHosts(context))
        if (!current.add(host)) current.remove(host)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, current)
            .apply()
    }

    fun hostOf(url: String): String {
        if (url.isBlank()) return ""
        return try {
            val host = URI(url).host ?: return ""
            host.removePrefix("www.").lowercase()
        } catch (_: Exception) {
            ""
        }
    }

    fun isEnabledSiteOpen(context: Context, components: Components): Boolean {
        val hosts = enabledHosts(context)
        if (hosts.isEmpty()) return false
        return components.core.store.state.tabs.any { tab ->
            tab.content.url.isNotBlank() && hostOf(tab.content.url) in hosts
        }
    }

    /**
     * Hosts that should be kept running in the background: the sites with
     * "Allow background playback" enabled.
     */
    fun keepAliveHosts(context: Context): Set<String> = enabledHosts(context)

    fun isKeepAliveSiteOpen(context: Context, components: Components): Boolean {
        val hosts = keepAliveHosts(context)
        if (hosts.isEmpty()) return false
        return components.core.store.state.tabs.any { tab ->
            tab.content.url.isNotBlank() && hostOf(tab.content.url) in hosts
        }
    }
}
