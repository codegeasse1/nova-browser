/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.WebExtension
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Nova: native bridge for the bundled Nova Video Downloader extension.
 *
 * The extension alone can only download what the page exposes as a plain media
 * file or a HLS/DASH manifest. This bridge gives it a second path: the page URL
 * is handed to a bundled `yt-dlp` (via the youtubedl-android library), which
 * extracts the media itself. That is what makes YouTube - and other sites that
 * hide their streams behind a player - downloadable.
 *
 * The extension talks to us with `browser.runtime.sendNativeMessage("novaVideoYtdlp", ...)`.
 * Messages are handled synchronously (GeckoView's [MessageHandler.onMessage] is
 * not suspend), so a download is started in the background and the extension
 * polls `action: "status"` for progress.
 */
object NovaYtDlp {

    /** Name the extension uses in `runtime.sendNativeMessage` / `connectNative`. */
    const val NATIVE_APP = "novaVideoYtdlp"

    private const val CHANNEL_ID = "nova_downloads"
    private const val CHANNEL_NAME = "Downloads"
    private const val PREFERENCES_NAME = "NovaVideoDownloader"
    private const val KEY_LAST_UPDATE = "novaYtDlpLastUpdate"
    private const val UPDATE_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
    private const val JOB_TTL_MS = 10L * 60L * 1000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Matches the size + speed chunk of a yt-dlp progress line. */
    private val sizeSpeed = Regex("""of\s+~?\s*([\d.]+\s*[KMGT]?i?B)(?:\s+at\s+([^\s]+/s))?""")
    private val etaPattern = Regex("""ETA\s+(\d+(?::\d+)+|\d+)""")

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nova-ytdlp")
    }
    private val jobs = ConcurrentHashMap<String, DownloadJob>()
    private val notificationIds = AtomicInteger(4200)
    private val registered = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var ready = false

    @Volatile
    private var lastError: String? = null

    private class DownloadJob(val id: String, val url: String, val notificationId: Int) {
        @Volatile var state: String = "running"
        @Volatile var progress: Float = 0f
        @Volatile var message: String = ""
        @Volatile var filename: String = ""
        @Volatile var error: String? = null
        @Volatile var processId: String? = null
        @Volatile var canceled: Boolean = false
        @Volatile var lastNotify: Long = 0L
    }

    /**
     * Prepares the bundled yt-dlp/ffmpeg binaries off the main thread. Safe to
     * call on every launch: after the first run the binaries are already
     * extracted, so this is cheap.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        worker.execute { initialize() }
    }

    /**
     * Hooks the bridge up to the installed extension. Idempotent - the engine
     * only allows one handler per name, and install is re-run on every launch.
     *
     * `setMessageDelegate` has to run on the UI thread and the engine may still
     * be warming up while the install callback fires. If registration silently
     * failed, the extension's `runtime.sendNativeMessage` promise would never
     * settle (the engine just queues messages for a name with no delegate), so
     * retry on the main thread instead of giving up on the first failure.
     */
    fun registerHandler(extension: WebExtension) {
        if (!registered.compareAndSet(false, true)) return
        registerOnMain(extension, 0)
    }

    private fun registerOnMain(extension: WebExtension, attempt: Int) {
        mainHandler.post {
            try {
                extension.registerBackgroundMessageHandler(NATIVE_APP, handler)
                appContext?.let { NovaDebugLog.log(it, "Nova yt-dlp bridge registered") }
            } catch (e: Throwable) {
                if (attempt < 30) {
                    val delay = minOf(3000L, 400L * (attempt + 1))
                    mainHandler.postDelayed({ registerOnMain(extension, attempt + 1) }, delay)
                } else {
                    registered.set(false)
                    appContext?.let { NovaDebugLog.log(it, "Nova yt-dlp bridge registration failed: ${e.message}") }
                }
            }
        }
    }

    private fun initialize() {
        val ctx = appContext ?: return
        if (ready) return
        try {
            YoutubeDL.getInstance().init(ctx)
            FFmpeg.getInstance().init(ctx)
            ready = true
            lastError = null
            NovaDebugLog.log(ctx, "Nova yt-dlp ready (${YoutubeDL.versionName(ctx) ?: "unknown"})")
        } catch (e: Throwable) {
            lastError = e.message ?: e.toString()
            NovaDebugLog.log(ctx, "Nova yt-dlp init failed: $lastError")
        }
        Thread { maybeUpdate(ctx) }.start()
    }

    /*
     * yt-dlp itself is what breaks when a site changes, so refresh the bundled
     * binary from the official release feed about once a week.
     */
    private fun maybeUpdate(ctx: Context) {
        try {
            val prefs = ctx.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_LAST_UPDATE, 0L)
            if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) return
            YoutubeDL.getInstance().updateYoutubeDL(ctx)
            prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            NovaDebugLog.log(ctx, "Nova yt-dlp updated to ${YoutubeDL.versionName(ctx) ?: "unknown"}")
        } catch (e: Throwable) {
            NovaDebugLog.log(ctx, "Nova yt-dlp update failed: ${e.message}")
        }
    }

    private val handler: MessageHandler = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any? {
            val json = toJson(message) ?: return error("Unsupported message")
            return try {
                when (json.optString("action")) {
                    "ping" -> ping()
                    "prefs" -> prefs()
                    "start" -> start(json)
                    "status" -> status(json)
                    "cancel" -> cancel(json)
                    "list" -> list()
                    else -> error("Unknown action")
                }
            } catch (e: Throwable) {
                error(e.message ?: e.toString())
            }
        }
    }

    private fun toJson(message: Any): JSONObject? = when (message) {
        is JSONObject -> message
        is String -> try {
            JSONObject(message)
        } catch (e: Throwable) {
            null
        }
        else -> null
    }

    private fun error(message: String) = JSONObject().put("ok", false).put("error", message)

    private fun ping(): JSONObject {
        val out = JSONObject().put("ok", true).put("ready", ready)
        appContext?.let { out.put("version", YoutubeDL.versionName(it) ?: "") }
        if (!ready && lastError != null) out.put("error", lastError)
        return out
    }

    /**
     * The two Nova video feature switches, so the extension can adapt at
     * runtime without the page being reloaded: `downloader` hides the download
     * button, `inbuiltPlayer` shows Nova's own player controls. Read locally
     * (no disk first-load cost after boot) and always answers, so the content
     * script never has to wait on a timeout for a normal answer.
     */
    private fun prefs(): JSONObject {
        val ctx = appContext
        val out = JSONObject().put("ok", true)
        out.put("downloader", ctx?.let { NovaVideoDownloader.isEnabled(it) } ?: true)
        out.put("inbuiltPlayer", ctx?.let { NovaInbuiltPlayer.isEnabled(it) } ?: false)
        return out
    }

    private fun start(json: JSONObject): JSONObject {
        val url = json.optString("url").trim()
        if (url.isEmpty()) return error("Missing url")
        val ctx = appContext ?: return error("Not ready")
        val audioOnly = json.optBoolean("audioOnly", false)
        val id = UUID.randomUUID().toString()
        val job = DownloadJob(id, url, notificationIds.incrementAndGet())
        job.message = "Starting\u2026"
        jobs[id] = job
        worker.execute { runJob(ctx, id, job, url, audioOnly) }
        return JSONObject().put("ok", true).put("id", id)
    }

    private fun status(json: JSONObject): JSONObject {
        val job = jobs[json.optString("id")] ?: return error("Unknown job")
        val out = JSONObject()
            .put("ok", true)
            .put("url", job.url)
            .put("state", job.state)
            .put("progress", job.progress.toDouble())
            .put("message", job.message)
        if (job.filename.isNotEmpty()) out.put("filename", job.filename)
        job.error?.let { out.put("error", it) }
        return out
    }

    private fun cancel(json: JSONObject): JSONObject {
        val job = jobs[json.optString("id")] ?: return error("Unknown job")
        job.canceled = true
        job.message = "Cancelling\u2026"
        job.processId?.let {
            try {
                YoutubeDL.getInstance().destroyProcessById(it)
            } catch (e: Throwable) {
                /* the process may already be gone */
            }
        }
        return JSONObject().put("ok", true)
    }

    /**
     * Every job the bridge currently knows about. The extension uses this to
     * re-attach to downloads after its UI was closed/reopened (the WebExtension
     * context can be torn down while a native download keeps running).
     */
    private fun list(): JSONObject {
        val array = org.json.JSONArray()
        for (job in jobs.values) {
            val item = JSONObject()
                .put("id", job.id)
                .put("url", job.url)
                .put("state", job.state)
                .put("progress", job.progress.toDouble())
                .put("message", job.message)
            if (job.filename.isNotEmpty()) item.put("filename", job.filename)
            job.error?.let { item.put("error", it) }
            array.put(item)
        }
        return JSONObject().put("ok", true).put("jobs", array)
    }

    /** Drops a finished job after a grace period so `status`/`list` stay bounded. */
    private fun scheduleCleanup(job: DownloadJob) {
        mainHandler.postDelayed({ jobs.remove(job.id) }, JOB_TTL_MS)
    }

    private fun runJob(ctx: Context, id: String, job: DownloadJob, url: String, audioOnly: Boolean) {
        if (!ready) {
            job.state = "error"
            job.error = lastError ?: "The downloader is still starting up. Please try again in a moment."
            scheduleCleanup(job)
            return
        }
        val baseDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val outDir = File(File(baseDir, "nova-ytdlp"), id)
        outDir.mkdirs()
        try {
            job.message = "Preparing\u2026"
            publishProgress(ctx, job, "Preparing\u2026")

            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--newline")
            request.addOption("--restrict-filenames")
            if (audioOnly) {
                request.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
            } else {
                /* Prefer H.264 + AAC inside an MP4 container. Android's own
                 * gallery/player (MediaExtractor) can't decode VP9/AV1-with-Opus
                 * MP4s, which is why those files look "broken"/audio-only there
                 * while VLC and MX Player (which bundle their own codecs) play
                 * them fine. The fallback chain still finds *something* for
                 * sites that only offer other codecs. */
                request.addOption(
                    "-f",
                    "bv*[vcodec^=avc1]+ba[acodec^=mp4a]/b[vcodec^=avc1][acodec^=mp4a]/b[ext=mp4]/b",
                )
                request.addOption("-S", "vcodec:h264,acodec:aac,res:1080")
                request.addOption("--merge-output-format", "mp4")
                request.addOption("--postprocessor-args", "Merger:-movflags +faststart")
            }
            request.addOption("-o", File(outDir, "%(title).120B [%(id)s].%(ext)s").absolutePath)

            val processId = "nova-ytdlp-$id"
            job.processId = processId
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                if (job.canceled) return@execute
                val ratio = when {
                    progress < 0f -> 0f
                    progress > 1f -> progress / 100f
                    else -> progress
                }
                job.progress = ratio
                job.message = describeProgress(ratio, eta, line) ?: job.message
                publishProgress(ctx, job, job.message)
            }
            job.processId = null

            if (job.canceled) {
                job.state = "canceled"
                job.message = "Canceled"
                cancelNotification(ctx, job)
                cleanup(outDir)
                scheduleCleanup(job)
                return
            }

            val produced = outDir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") }
                ?.maxByOrNull { it.length() }
            if (produced == null) {
                job.state = "error"
                job.error = "The download finished but produced no file."
                cancelNotification(ctx, job)
                cleanup(outDir)
                scheduleCleanup(job)
                return
            }

            job.filename = produced.name
            job.progress = 1f
            job.message = "Saving\u2026"
            publishProgress(ctx, job, "Saving\u2026")
            val saved = publish(ctx, produced)
            job.state = "done"
            job.message = if (saved) {
                "Saved to Downloads/" + produced.name
            } else {
                "Saved to app storage: " + produced.name
            }
            finishNotification(ctx, job)
            cleanup(outDir)
            scheduleCleanup(job)
        } catch (e: Throwable) {
            job.processId = null
            if (job.canceled) {
                job.state = "canceled"
                job.message = "Canceled"
                cancelNotification(ctx, job)
            } else {
                job.state = "error"
                job.error = friendly(e)
                NovaDebugLog.log(ctx, "Nova yt-dlp download failed: ${e.message}")
            }
            cleanup(outDir)
            scheduleCleanup(job)
        }
    }

    /**
     * Turns a yt-dlp progress line into a human message.
     */
    private fun describeProgress(ratio: Float, eta: Long, line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            return if (ratio > 0f) "Downloading ${(ratio * 100).toInt()}%" else null
        }
        if (ratio <= 0f && !trimmed.startsWith("[download]")) {
            /* Post-processing / merge / extract-audio / remux lines. */
            val tag = trimmed.substringAfter('[', "").substringBefore(']')
            return when {
                tag.startsWith("Merger") -> "Merging video and audio\u2026"
                tag.startsWith("ExtractAudio") -> "Extracting audio\u2026"
                tag.startsWith("VideoConvertor") || tag.startsWith("VideoRemuxer") -> "Converting\u2026"
                tag.startsWith("Metadata") -> "Writing metadata\u2026"
                tag.startsWith("Fixup") -> "Finalising\u2026"
                else -> null
            }
        }
        val parts = mutableListOf("Downloading ${(ratio * 100).toInt()}%")
        sizeSpeed.find(trimmed)?.let { match ->
            match.groupValues[1].takeIf { it.isNotEmpty() }?.let { parts.add(it.trim()) }
            match.groupValues[2].takeIf { it.isNotEmpty() }?.let { parts.add(it.trim()) }
        }
        val seconds = if (eta > 0) {
            eta
        } else {
            etaPattern.find(trimmed)?.groupValues?.get(1)?.let { parseEta(it) } ?: 0L
        }
        if (seconds > 0) parts.add("ETA ${formatEta(seconds)}")
        return parts.joinToString(" \u00b7 ")
    }

    private fun parseEta(value: String): Long {
        val bits = value.split(":").mapNotNull { it.trim().toLongOrNull() }
        if (bits.isEmpty()) return 0L
        var seconds = 0L
        for (bit in bits) seconds = seconds * 60 + bit
        return seconds
    }

    private fun formatEta(seconds: Long): String {
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        val rest = seconds % 60
        return if (minutes < 60) "${minutes}m ${rest}s" else "${minutes / 60}h ${minutes % 60}m"
    }

    private fun friendly(e: Throwable): String {
        val raw = (e.message ?: e.toString()).trim()
        if (raw.isEmpty()) return "Download failed"
        val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull() ?: raw
        return if (line.length > 220) line.substring(0, 220) else line
    }

    private fun cleanup(dir: File) {
        try {
            dir.walkBottomUp().forEach { it.delete() }
        } catch (e: Throwable) {
            /* best effort */
        }
    }

    private fun publish(ctx: Context, file: File): Boolean {
        val name = file.name
        val mime = mimeOf(name)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishQ(ctx, file, name, mime)
            } else {
                publishLegacy(ctx, file, name, mime)
            }
        } catch (e: Throwable) {
            NovaDebugLog.log(ctx, "Nova yt-dlp publish failed: ${e.message}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishQ(ctx: Context, file: File, name: String, mime: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        val stream = resolver.openOutputStream(uri) ?: return false
        stream.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(ctx: Context, file: File, name: String, mime: String): Boolean {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists() && !downloads.mkdirs()) return false
        val dest = File(downloads, name)
        file.copyTo(dest, overwrite = true)
        try {
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
        } catch (e: Throwable) {
            /* not fatal - the file is on disk either way */
        }
        return true
    }

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "m4a", "m4b" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "opus", "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }

    /* ----------------------------- notifications ---------------------------- */

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        channel.description = "Nova Browser downloads"
        manager.createNotificationChannel(channel)
    }

    private fun builder(ctx: Context, job: DownloadJob, title: String, ongoing: Boolean): NotificationCompat.Builder {
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(job.filename.ifEmpty { job.message })
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (ongoing && job.progress > 0f) {
            notification.setProgress(100, (job.progress * 100).toInt(), false)
        }
        return notification
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, job: DownloadJob, notification: Notification) {
        try {
            ensureChannel(ctx)
            NotificationManagerCompat.from(ctx).notify(job.notificationId, notification)
        } catch (e: Throwable) {
            /* notifications are optional */
        }
    }

    private fun publishProgress(ctx: Context, job: DownloadJob, title: String) {
        val now = System.currentTimeMillis()
        if (now - job.lastNotify < 500L) return
        job.lastNotify = now
        post(ctx, job, builder(ctx, job, title, true).build())
    }

    private fun finishNotification(ctx: Context, job: DownloadJob) {
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(job.filename)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        post(ctx, job, notification)
    }

    private fun cancelNotification(ctx: Context, job: DownloadJob) {
        try {
            NotificationManagerCompat.from(ctx).cancel(job.notificationId)
        } catch (e: Throwable) {
            /* nothing to do */
        }
    }
}
