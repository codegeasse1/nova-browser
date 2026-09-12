/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import mozilla.components.concept.engine.webextension.EnableSource
import mozilla.components.concept.engine.webextension.WebExtensionRuntime

/**
 * Nova: user-controllable switch for the bundled Nova Video Downloader add-on.
 *
 * The preference lives in shared preferences (so the 3-dot menu can show the
 * current state immediately) and is applied to the engine by enabling or
 * disabling the bundled `nova-video@nova.browser` WebExtension. When the
 * extension is disabled its content scripts stop running, so no download
 * button is shown anywhere.
 */
object NovaVideoDownloader {

    const val ADDON_ID = "nova-video@nova.browser"

    private const val PREFERENCES_NAME = "NovaVideoDownloader"
    private const val KEY_ENABLED = "novaVideoDownloaderEnabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Whether the video downloader is currently switched on. Defaults to on. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /** Stores the user's choice. Use [apply] to also update the running engine. */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Enables or disables the bundled extension in the engine so the change
     * takes effect without restarting the app.
     */
    fun apply(
        engine: WebExtensionRuntime,
        enabled: Boolean,
        onFinished: (Boolean) -> Unit = {},
    ) {
        engine.listInstalledWebExtensions(
            onSuccess = { extensions ->
                val extension = extensions.firstOrNull { it.id == ADDON_ID }
                if (extension == null) {
                    onFinished(false)
                    return@listInstalledWebExtensions
                }
                if (enabled) {
                    engine.enableWebExtension(
                        extension = extension,
                        source = EnableSource.USER,
                        onSuccess = { onFinished(true) },
                        onError = { onFinished(false) },
                    )
                } else {
                    engine.disableWebExtension(
                        extension = extension,
                        source = EnableSource.USER,
                        onSuccess = { onFinished(true) },
                        onError = { onFinished(false) },
                    )
                }
            },
            onError = { onFinished(false) },
        )
    }
}
