/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.session

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.view.View
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.selector.findTabOrCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.support.base.log.logger.Logger

/**
 * A simple implementation of Picture-in-picture mode if on a supported platform.
 *
 * @param store Browser Store for observing the selected session's fullscreen mode changes.
 * @param activity the activity with the EngineView for calling PIP mode when required; the AndroidX Fragment
 * doesn't support this.
 * @param crashReporting Instance of `CrashReporting` to record unexpected caught exceptions
 * @param tabId ID of tab or custom tab session.
 */
class PictureInPictureFeature(
    private val store: BrowserStore,
    private val activity: Activity,
    private val crashReporting: CrashReporting? = null,
    private val tabId: String? = null,
    private val playerView: View? = null,
    private val isEnabled: () -> Boolean = { true },
) {
    internal val logger = Logger("PictureInPictureFeature")

    private val hasSystemFeature =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun onHomePressed(): Boolean {
        // PiP is deliberately user-initiated from the in-video button. Do not
        // intercept Home and unexpectedly move a video into PiP.
        return false
    }

    /**
     * Enters PiP from the dedicated in-video control.
     */
    fun enterPipMode(): Boolean {
        if (!isEnabled() || !hasSystemFeature || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val session = store.state.findTabOrCustomTabOrSelectedTab(tabId)
        val mediaSession = session?.mediaSessionState
        val canEnter =
            mediaSession?.playbackState in listOf(
                MediaSession.PlaybackState.PLAYING,
                MediaSession.PlaybackState.PAUSED,
            ) &&
            mediaSession?.elementMetadata?.videoTrackCount?.let { it > 0 } == true

        if (!canEnter) {
            return false
        }

        return try {
            updatePipParams(session)
            activity.enterPictureInPictureMode(buildPipParams(session))
        } catch (e: IllegalStateException) {
            logger.warn("Entering PipMode failed", e)
            crashReporting?.submitCaughtException(e)
            false
        }
    }

    /**
     * Enter Picture-in-Picture mode.
     */
    fun enterPipModeCompat(session: SessionState? = null) = when {
        !hasSystemFeature -> false
        else -> enterPipModeForO(session)
    }

    fun updatePipParams(session: SessionState?) {
        if (!hasSystemFeature) {
            return
        }

        val mediaSession = session?.mediaSessionState
        val isVideoPlaying =
            mediaSession?.playbackState in listOf(
                MediaSession.PlaybackState.PLAYING,
                MediaSession.PlaybackState.PAUSED,
            ) &&
            mediaSession?.elementMetadata?.videoTrackCount?.let { it > 0 } == true

        val builder = PictureInPictureParams.Builder()

        if (isVideoPlaying) {
            val metadata = mediaSession?.elementMetadata
            val width = metadata?.width ?: 0L
            val height = metadata?.height ?: 0L

            if (width > 0L && height > 0L) {
                val ratio = width.toDouble() / height.toDouble()
                if (ratio in (1.0 / 2.39)..2.39) {
                    builder.setAspectRatio(
                        android.util.Rational(
                            width.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            height.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        ),
                    )
                }
            }

            playerView?.let { view ->
                val sourceRect = Rect()
                if (view.getGlobalVisibleRect(sourceRect)) {
                    builder.setSourceRectHint(sourceRect)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(false)
                builder.setSeamlessResizeEnabled(true)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false)
        }

        activity.setPictureInPictureParams(builder.build())
    }

    private fun enterPipModeForO(session: SessionState?) =
        activity.enterPictureInPictureMode(buildPipParams(session))

    private fun buildPipParams(session: SessionState?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val metadata = session?.mediaSessionState?.elementMetadata
        val width = metadata?.width ?: 0L
        val height = metadata?.height ?: 0L

        if (width > 0L && height > 0L) {
            val ratio = width.toDouble() / height.toDouble()
            if (ratio in (1.0 / 2.39)..2.39) {
                builder.setAspectRatio(
                    android.util.Rational(
                        width.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        height.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    ),
                )
            }
        }

        playerView?.let { view ->
            val sourceRect = Rect()
            if (view.getGlobalVisibleRect(sourceRect)) {
                builder.setSourceRectHint(sourceRect)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    /**
     * Should be called when the system informs you of changes to and from picture-in-picture mode.
     * @param isInPipMode True if the activity is in picture-in-picture mode.
     */
    fun onPictureInPictureModeChanged(isInPipMode: Boolean) {
        val sessionId = tabId ?: store.state.selectedTabId ?: return
        store.state.selectedTab?.engineState?.engineSession?.onPipModeChanged(isInPipMode)
        store.dispatch(ContentAction.PictureInPictureChangedAction(sessionId, isInPipMode))
    }
}
