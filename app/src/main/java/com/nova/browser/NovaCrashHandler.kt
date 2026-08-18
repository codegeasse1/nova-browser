package com.nova.browser

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NovaCrashHandler : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            val minidump = intent?.getStringExtra(org.mozilla.geckoview.GeckoRuntime.EXTRA_MINIDUMP_PATH)
            val processType = intent?.getStringExtra(org.mozilla.geckoview.GeckoRuntime.EXTRA_CRASH_PROCESS_TYPE)
            val remoteType = intent?.getStringExtra(org.mozilla.geckoview.GeckoRuntime.EXTRA_CRASH_REMOTE_TYPE)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            File(filesDir, "crashlog.txt")
                .appendText("[$stamp] NATIVE CRASH process=$processType remote=$remoteType minidump=$minidump\n")
        }
        stopSelf()
        return Service.START_NOT_STICKY
    }
}
