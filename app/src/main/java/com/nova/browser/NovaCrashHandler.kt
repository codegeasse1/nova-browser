package com.nova.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.mozilla.geckoview.GeckoRuntime
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receives ACTION_CRASHED intents from GeckoView. MUST run in its own process
 * (see android:process=":crash" in the manifest) — GeckoRuntime.init throws
 * IllegalArgumentException otherwise and the engine never starts.
 */
class NovaCrashHandler : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            val minidump = intent?.getStringExtra(GeckoRuntime.EXTRA_MINIDUMP_PATH)
            val processType = intent?.getStringExtra(GeckoRuntime.EXTRA_CRASH_PROCESS_TYPE)
            val remoteType = intent?.getStringExtra(GeckoRuntime.EXTRA_CRASH_REMOTE_TYPE)
            val visibility = intent?.getStringExtra(GeckoRuntime.EXTRA_CRASH_PROCESS_VISIBILITY)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            File(filesDir, "crashlog.txt")
                .appendText("[$stamp] NATIVE CRASH process=$processType remote=$remoteType visibility=$visibility minidump=$minidump\n")
            runCatching { File(filesDir, "shutdown.marker").delete() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(
                    NotificationChannel("nova_crash", "Nova", NotificationManager.IMPORTANCE_MIN)
                )
                startForeground(
                    1,
                    Notification.Builder(this, "nova_crash")
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentTitle("Nova")
                        .setContentText("Recovering from a crash…")
                        .build(),
                )
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
        return Service.START_NOT_STICKY
    }
}
