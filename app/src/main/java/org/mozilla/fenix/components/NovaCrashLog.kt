/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Nova: appends the stack trace of any uncaught Java exception (crash) to the
 * same nova-debug.log the rest of Nova writes to, so a crash that we have not
 * fixed yet still leaves a record of exactly where it happened on the device.
 */
object NovaCrashLog {
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = context.getExternalFilesDir(null)
                    ?.resolve("nova-debug.log") ?: return@setDefaultUncaughtExceptionHandler
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file.appendText(
                    "=== CRASH " +
                        java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss.SSS",
                            java.util.Locale.US,
                        ).format(java.util.Date()) +
                        " ===" +
                        "\nThread: " + thread.name + "\n" + sw.toString() + "\n",
                )
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
