/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession
import org.mozilla.fenix.ext.components

/**
 * Nova: keeps video/audio playing even when the page is not the one being shown.
 *
 * When a tab is switched away from (or the app is backgrounded and the surface is
 * destroyed), GeckoView marks that session inactive, which reports the page as
 * hidden - and sites like YouTube pause. This keeper periodically re-activates the
 * sessions that should keep running (tabs that are actually playing media, plus any
 * tab on a site the user enabled via "Allow background playback"), so two videos in
 * two tabs keep playing at the same time, and YouTube keeps playing in the
 * background on the first attempt.
 *
 * It also keeps the [NovaBackgroundService] foreground service running while an
 * enabled site is playing (started from the foreground, before the app can hit the
 * Android 12+ restriction on starting a foreground service from the background), so
 * the process and a partial wake lock are already held when the app is backgrounded.
 */
object NovaPlaybackKeeper {
    private const val TICK_MS = 500L

    /** True while the app is not in the foreground (set by [NovaKeepAlive]). */
    @Volatile
    var isBackgrounded: Boolean = false

    private var handler: Handler? = null
    private var started = false
    private var contextRef: Context? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                tickOnce()
            } catch (_: Exception) {
            }
            handler?.postDelayed(this, TICK_MS)
        }
    }

    /** Called once from [Core] when the browser engine is created. */
    fun start(context: Context) {
        if (started) return
        started = true
        contextRef = context.applicationContext
        val h = Handler(Looper.getMainLooper())
        handler = h
        h.post(tick)
    }

    /**
     * Re-activate the engine sessions that should keep running right now. Called
     * periodically from both the foreground loop here and the background service.
     */
    fun reassertNeededSessions(store: BrowserStore, context: Context) {
        val state = store.state
        val selectedId = state.selectedTabId
        val enabledHosts = NovaBackgroundSites.keepAliveHosts(context)
        for (tab in state.tabs) {
            val playing = tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING
            val url = tab.content.url
            val hostEnabled = url.isNotBlank() && NovaBackgroundSites.hostOf(url) in enabledHosts
            // Keep a non-selected tab that is actually playing (cross-tab simultaneous
            // playback) and any enabled site that is playing or in the background.
            val keepVisible = (playing && tab.id != selectedId) || (hostEnabled && (isBackgrounded || playing))
            if (keepVisible) {
                (tab.engineState.engineSession as? GeckoEngineSession)?.keepVisibleInBackground()
            }
        }
    }

    private fun tickOnce() {
        val ctx = contextRef ?: return
        val store = ctx.components.core.store

        reassertNeededSessions(store, ctx)

        // Keep the background service running while an enabled site is playing (so it
        // is already started - from the foreground - when the app goes to the
        // background) or while the app is backgrounded with an enabled site open.
        val enabledHosts = NovaBackgroundSites.keepAliveHosts(ctx)
        var anyEnabledOpen = false
        var anyEnabledPlaying = false
        for (tab in store.state.tabs) {
            val url = tab.content.url
            if (url.isNotBlank() && NovaBackgroundSites.hostOf(url) in enabledHosts) {
                anyEnabledOpen = true
                if (tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING) {
                    anyEnabledPlaying = true
                }
            }
        }
        val serviceWanted = anyEnabledPlaying || (isBackgrounded && anyEnabledOpen)
        if (serviceWanted && !NovaBackgroundService.isRunning) {
            NovaBackgroundService.start(ctx)
        } else if (!serviceWanted && NovaBackgroundService.isRunning) {
            NovaBackgroundService.stop(ctx)
        }
    }
}
