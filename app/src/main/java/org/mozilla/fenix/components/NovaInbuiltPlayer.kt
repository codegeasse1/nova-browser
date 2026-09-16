/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context

/**
 * Nova: user-controllable switch for Nova's own in-page video player.
 *
 * When on, the bundled Nova Video Downloader extension draws Nova's player
 * controls over the page's <video> element (play/pause, seek, volume,
 * brightness, playback speed, rotate, fullscreen and a download shortcut)
 * instead of leaving the page's own chrome in charge.
 *
 * The preference lives in shared preferences so the 3-dot menu can show its
 * state immediately. The extension reads it through the native bridge (the
 * `novaVideoYtdlp` handler, action `prefs`), which is also why the extension
 * itself stays enabled while either the downloader or the player is on.
 */
object NovaInbuiltPlayer {

    private const val PREFERENCES_NAME = "NovaInbuiltPlayer"
    private const val KEY_ENABLED = "novaInbuiltPlayerEnabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Whether Nova's own player controls should be shown. Defaults to off. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Stores the user's choice. Use [NovaVideoDownloader.apply] to update the engine. */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
