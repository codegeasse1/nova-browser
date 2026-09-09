/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components

/**
 * Nova: foreground service that keeps the browser alive while the app is in the
 * background with an enabled site open. Three jobs while it runs:
 *  - keeps the process at foreground priority so Android does not kill it,
 *  - periodically re-activates the enabled site's engine session so the page
 *    stays reported as visible even though its surface is gone (sites like
 *    YouTube pause when the page reports itself hidden),
 *  - holds a partial wake lock so the CPU keeps running with the screen off
 *    (the lock is only held while the screen is off).
 * Stops when the app returns to the foreground or when no enabled site is open.
 */
class NovaBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            try {
                keepEnabledSitesVisible()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, KEEP_ALIVE_INTERVAL_MS)
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> releaseWakeLock()
                Intent.ACTION_SCREEN_OFF -> acquireWakeLock()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
        try {
            keepEnabledSitesVisible()
        } catch (_: Exception) {
        }
        handler.removeCallbacks(keepAliveRunnable)
        handler.postDelayed(keepAliveRunnable, FIRST_REASSERT_DELAY_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(keepAliveRunnable)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * When the app is backgrounded, the browser's surface is destroyed and the
     * engine session is marked inactive (page hidden), which makes pages like
     * YouTube pause playback. Re-activate the enabled site's sessions so the
     * pages keep believing they are visible and their media and timers keep
     * running.
     */
    private fun keepEnabledSitesVisible() {
        val store = applicationContext.components.core.store
        NovaPlaybackKeeper.reassertNeededSessions(store, applicationContext)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovaBrowser::keepAlive")
            wl.setReferenceCounted(false)
            wl.acquire()
            wakeLock = wl
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock!!.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.nova_background_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status_logo)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.nova_background_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "nova_background"
        private const val NOTIFICATION_ID = 1000
        private const val KEEP_ALIVE_INTERVAL_MS = 1500L
        private const val FIRST_REASSERT_DELAY_MS = 500L

        @Volatile
        var isRunning: Boolean = false

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, NovaBackgroundService::class.java),
                )
            } catch (_: Exception) {
                // Nova: Android 12+ forbids starting a foreground service from the
                // background unless the app is exempt (e.g. it is actively playing
                // audio through a media session). A backgrounded site that is not
                // playing anything has no exemption, so the system refuses the start
                // and throws (which used to crash the app). The keep-alive service
                // is only useful while something is actually playing, so skipping
                // the start is the right behaviour here - it must never take the
                // app down.
                NovaDebugLog.log(context, "bg service start refused - skipping keep-alive")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NovaBackgroundService::class.java))
        }
    }
}
