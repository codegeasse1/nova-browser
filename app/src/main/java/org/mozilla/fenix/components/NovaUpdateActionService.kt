/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.DownloadManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.IBinder

/**
 * Nova: handles the actions of the "update available" notification.
 * - "download": saves the new APK to the Downloads folder via DownloadManager.
 * - "open": opens the GitHub releases page inside Nova Browser.
 * - "later": suppresses the notification for a day.
 */
class NovaUpdateActionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                startDownload(url, intent.getStringExtra(EXTRA_VERSION) ?: "")
            }
            ACTION_OPEN -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                openInBrowser(url)
            }
            ACTION_LATER -> {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong("suppressed_until", System.currentTimeMillis() + SUPPRESS_MS)
                    .apply()
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, version: String) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(getString(org.mozilla.fenix.R.string.app_name))
                .setDescription(
                    getString(org.mozilla.fenix.R.string.nova_update_downloading, version),
                )
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Nova.Browser.$version.apk",
                )
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            dm.enqueue(request)
        } catch (_: Exception) {
        }
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(packageName) // open inside Nova Browser itself
                },
            )
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_ACTION = "nova_action"
        const val EXTRA_URL = "nova_url"
        const val EXTRA_VERSION = "nova_version"
        const val ACTION_DOWNLOAD = "download"
        const val ACTION_OPEN = "open"
        const val ACTION_LATER = "later"
        private const val PREFS = "nova_update"
        private const val SUPPRESS_MS = 24L * 60 * 60 * 1000
    }
}
