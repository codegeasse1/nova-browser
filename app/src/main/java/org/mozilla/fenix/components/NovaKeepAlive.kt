/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context

/**
 * Nova: keeps the browser awake while it is backgrounded (and the screen locked)
 * if any open tab is on a site the user enabled from the browser menu
 * ("Allow background playback"). A foreground service holds a partial wake lock,
 * so timers, streams and AI responses keep running until the user returns,
 * closes the tab or swipes the app away.
 */
object NovaKeepAlive {
    fun onAppBackground(context: Context, components: Components) {
        NovaPlaybackKeeper.isBackgrounded = true
        if (NovaBackgroundSites.isKeepAliveSiteOpen(context, components)) {
            NovaBackgroundService.start(context)
        } else {
            NovaBackgroundService.stop(context)
        }
    }

    fun onAppForeground(context: Context) {
        NovaPlaybackKeeper.isBackgrounded = false
        // The keeper loop stops the background service once nothing needs it.
    }
}
