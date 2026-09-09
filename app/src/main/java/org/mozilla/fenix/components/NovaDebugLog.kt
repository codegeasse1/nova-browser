/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes Nova Browser diagnostics to logcat (tag "NovaDebug") and to a plain-text
 * log file at <external files dir>/nova-debug.log so the user can inspect what the
 * app actually did without adb. Every write goes to disk asynchronously so it is
 * safe to call from lifecycle callbacks.
 */
object NovaDebugLog {
    fun log(context: Context, message: String) {
        Log.i("NovaDebug", message)
        try {
            val file = context.getExternalFilesDir(null)?.resolve("nova-debug.log") ?: return
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    file.appendText(java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()) + " " + message + "\n")
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context) {
        try {
            context.getExternalFilesDir(null)?.resolve("nova-debug.log")?.delete()
        } catch (_: Exception) {
        }
    }
}
