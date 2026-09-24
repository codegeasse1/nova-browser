package org.mozilla.fenix.components

import android.content.Context

/**
 * Stores the user's preference for Nova's manual video Picture-in-Picture control.
 *
 * PiP is opt-in from the three-dot menu. The preference is kept separate from
 * Android's activity-level PiP support so the in-video control can be hidden
 * immediately when the user turns the feature off.
 */
object NovaPictureInPicture {

    private const val PREFERENCES_NAME = "NovaPictureInPicture"
    private const val KEY_ENABLED = "novaPictureInPictureEnabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Whether the in-video PiP control is enabled. Defaults to off. */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
