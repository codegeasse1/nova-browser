/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.WebExtension
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nova: native half of PIP mode.
 *
 * GeckoView's page-facing `video.requestPictureInPicture()` is only exposed when
 * the picture-in-picture pref is switched on, and it is off in Nova's engine - so
 * the extension's PIP button asks us instead and we put the browser window into
 * Android's picture-in-picture mode ourselves. That is the same transition
 * Fenix's own picture-in-picture button performs, and it is why the button works
 * on videos whose page never exposed the API at all.
 *
 * The extension reaches us with
 * `browser.runtime.sendNativeMessage("novaPip", ...)` (through the background
 * script, like the yt-dlp bridge). Registration has to happen on the main
 * thread - see [registerHandler] - exactly like [NovaYtDlp].
 */
object NovaPip {

    /** Name the extension uses in `runtime.sendNativeMessage`. */
    const val NATIVE_APP = "novaPip"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val registered = AtomicBoolean(false)

    @Volatile
    private var activityRef: WeakReference<Activity> = WeakReference(null)

    /** The browser window that would be minimised. Set from HomeActivity. */
    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /** Drops the reference once that window is gone. */
    fun detach(activity: Activity) {
        if (activityRef.get() === activity) activityRef = WeakReference(null)
    }

    /**
     * Hooks the bridge up to the installed extension. Idempotent, and retried on
     * the main thread because `registerBackgroundMessageHandler` ends up in
     * `setMessageDelegate` (a `@UiThread` method) and the engine may still be
     * warming up while the install callback fires.
     */
    fun registerHandler(extension: WebExtension) {
        if (!registered.compareAndSet(false, true)) return
        registerOnMain(extension, 0)
    }

    private fun registerOnMain(extension: WebExtension, attempt: Int) {
        mainHandler.post {
            try {
                extension.registerBackgroundMessageHandler(NATIVE_APP, handler)
            } catch (e: Throwable) {
                if (attempt < 30) {
                    val delay = minOf(3000L, 400L * (attempt + 1))
                    mainHandler.postDelayed({ registerOnMain(extension, attempt + 1) }, delay)
                } else {
                    registered.set(false)
                }
            }
        }
    }

    private val handler: MessageHandler = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any? {
            val json = toJson(message) ?: return error("Unsupported message")
            return try {
                when (json.optString("action")) {
                    "enter" -> enter(json)
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

    /**
     * Puts the browser window into picture-in-picture, sized to the video the
     * extension measured. The window is only *paused* while it floats, so the
     * page (and the video in it) keeps playing.
     */
    private fun enter(json: JSONObject): JSONObject {
        val activity = activityRef.get() ?: return error("No browser window to float")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return error("Picture-in-picture needs Android 8 or newer")
        }
        val width = json.optInt("width", 0)
        val height = json.optInt("height", 0)
        mainHandler.post {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio(width, height))
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (e: Throwable) {
                /* The window may have gone, or the device may refuse the ratio. */
            }
        }
        return JSONObject().put("ok", true)
    }

    /**
     * Android only accepts a picture-in-picture window between 1:2.39 and
     * 2.39:1, and rejects a ratio built from anything non-positive, so odd (or
     * missing) video dimensions fall back to a sensible window.
     */
    private fun aspectRatio(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) return Rational(16, 9)
        val ratio = width.toDouble() / height.toDouble()
        if (ratio > 2.39) return Rational(239, 100)
        if (ratio < 0.418) return Rational(100, 239)
        return Rational(width, height)
    }
}
