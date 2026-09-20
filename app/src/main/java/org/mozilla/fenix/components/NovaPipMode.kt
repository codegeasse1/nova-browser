/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context

/**
 * Nova: user-controllable switch for PIP mode.
 *
 * When on, the bundled Nova Video Downloader extension peeks a small button
 * over the page's <video> for three seconds whenever the video starts, is
 * paused or is tapped, and that button opens the browser's picture-in-picture
 * window. Nova deliberately does NOT draw its own player - the page's own
 * player stays in charge and the video is never moved or restyled.
 *
 * The preference lives in shared preferences so the 3-dot menu can show its
 * state immediately. The extension reads it through the native bridge (the
 * `novaVideoYtdlp` handler, action `prefs`), which is also why the extension
 * itself stays enabled while either the downloader or PIP mode is on.
 */
object NovaPipMode {

    private const val PREFERENCES_NAME = "NovaPipMode"
    private const val KEY_ENABLED = "novaPipModeEnabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Whether the PIP button should be offered over videos. Defaults to off. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Stores the user's choice. Use [NovaVideoDownloader.apply] to update the engine. */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
