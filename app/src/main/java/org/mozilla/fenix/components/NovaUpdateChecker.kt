/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.fenix.BuildConfig
import org.mozilla.fenix.R
import java.net.HttpURLConnection
import java.net.URL

/**
 * Nova: checks the Nova Browser GitHub release once per app launch. If a newer
 * version is out, a notification offers "Download" (saves the APK via
 * DownloadManager), "GitHub" (opens the releases page) and "Later" (reminds
 * again after a day).
 */
object NovaUpdateChecker {
    private const val REPO_RELEASES_LATEST =
        "https://api.github.com/repos/codegeasse1/nova-browser/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/codegeasse1/nova-browser/releases"
    private const val CHANNEL_ID = "nova_updates"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "nova_update"
    private const val SUPPRESS_MS = 24L * 60 * 60 * 1000

    fun check(context: Context) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val conn = URL(REPO_RELEASES_LATEST).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "NovaBrowser")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@launch
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val version = json.optString("name", "")
                    .split(" ")
                    .lastOrNull { it.isNotEmpty() && it[0].isDigit() }
                    ?.trim()
                    ?: return@launch
                if (!isNewer(version, BuildConfig.VERSION_NAME)) return@launch

                val assets = json.optJSONArray("assets") ?: return@launch
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNullOrEmpty()) return@launch

                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (System.currentTimeMillis() < prefs.getLong("suppressed_until", 0L)) {
                    return@launch
                }

                post(context, version, apkUrl)
            } catch (_: Exception) {
            }
        }
    }

    private fun versionTuple(v: String): List<Int> =
        v.trim().split(".").mapNotNull { it.toIntOrNull() }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = versionTuple(latest)
        val b = versionTuple(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun post(context: Context, version: String, apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.nova_update_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        val downloadIntent = Intent(context, NovaUpdateActionService::class.java)
            .putExtra(NovaUpdateActionService.EXTRA_ACTION, NovaUpdateActionService.ACTION_DOWNLOAD)
            .putExtra(NovaUpdateActionService.EXTRA_URL, apkUrl)
            .putExtra(NovaUpdateActionService.EXTRA_VERSION, version)
        val openIntent = Intent(context, NovaUpdateActionService::class.java)
            .putExtra(NovaUpdateActionService.EXTRA_ACTION, NovaUpdateActionService.ACTION_OPEN)
            .putExtra(NovaUpdateActionService.EXTRA_URL, RELEASES_PAGE)
        val laterIntent = Intent(context, NovaUpdateActionService::class.java)
            .putExtra(NovaUpdateActionService.EXTRA_ACTION, NovaUpdateActionService.ACTION_LATER)

        val piDownload = PendingIntent.getService(
            context, 10, downloadIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val piOpen = PendingIntent.getService(
            context, 11, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val piLater = PendingIntent.getService(
            context, 12, laterIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status_logo)
            .setContentTitle(context.getString(R.string.nova_update_notification_title))
            .setContentText(context.getString(R.string.nova_update_notification_text, version))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.nova_update_action_download), piDownload)
            .addAction(0, context.getString(R.string.nova_update_action_github), piOpen)
            .addAction(0, context.getString(R.string.nova_update_action_later), piLater)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }
}
