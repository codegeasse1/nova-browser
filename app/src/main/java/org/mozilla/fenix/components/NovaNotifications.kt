/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Nova: makes sure the app can post notifications. On Android 13+ an app must
 * hold the POST_NOTIFICATIONS runtime permission, and it is only granted after
 * the user accepts the system dialog. Nova asks for it once, on the first
 * launch, so the update / background-site notifications can actually show up.
 */
object NovaNotifications {
    private const val REQUEST_CODE = 9001
    private const val PREFS = "nova_notifications"
    private const val KEY_ASKED = "asked"

    fun ensureNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE,
        )
    }
}
