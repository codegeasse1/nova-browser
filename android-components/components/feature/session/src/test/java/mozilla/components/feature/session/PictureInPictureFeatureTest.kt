/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.session

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestScope
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.support.test.any
import mozilla.components.support.test.middleware.CaptureActionsMiddleware
import mozilla.components.support.test.mock
import mozilla.components.support.test.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PictureInPictureFeatureTest {

    private val crashReporting: CrashReporting = mock()
    private val activity: Activity = Mockito.mock(Activity::class.java, Mockito.RETURNS_DEEP_STUBS)

    @Before
    fun setUp() {
        whenever(activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            .thenReturn(true)
    }

    @Test
    fun `home never enters PiP automatically`() {
        val feature = PictureInPictureFeature(BrowserStore(), activity, crashReporting)
        assertFalse(feature.onHomePressed())
        verify(activity, never()).enterPictureInPictureMode(any())
    }

    @Test
    fun `manual PiP enters for fullscreen playing video`() {
        val controller = mock<MediaSession.Controller>()
        val session = createTab(
            url = "https://mozilla.org",
            mediaSessionState = MediaSessionState(
                playbackState = MediaSession.PlaybackState.PLAYING,
                controller = controller,
                elementMetadata = MediaSession.ElementMetadata(
                    width = 1920,
                    height = 1080,
                    videoTrackCount = 1,
                ),
            ),
        ).copy(content = createTab("https://mozilla.org").content.copy(fullScreen = true))

        val store = BrowserStore(
            BrowserState(
                tabs = listOf(session),
                selectedTabId = session.id,
            ),
        )
        whenever(activity.enterPictureInPictureMode(any())).thenReturn(true)

        val feature = PictureInPictureFeature(
            store,
            activity,
            crashReporting,
            isEnabled = { true },
        )

        assertTrue(feature.enterPipMode())
        verify(activity).enterPictureInPictureMode(any())
    }

    @Test
    fun `manual PiP stays disabled when Nova setting is off`() {
        val feature = PictureInPictureFeature(
            BrowserStore(),
            activity,
            crashReporting,
            isEnabled = { false },
        )

        assertFalse(feature.enterPipMode())
        verify(activity, never()).enterPictureInPictureMode(any())
    }

    @Test
    fun `manual PiP requires a video track`() {
        val controller = mock<MediaSession.Controller>()
        val session = createTab(
            url = "https://mozilla.org",
            mediaSessionState = MediaSessionState(
                playbackState = MediaSession.PlaybackState.PLAYING,
                controller = controller,
                elementMetadata = MediaSession.ElementMetadata(
                    width = 1920,
                    height = 1080,
                    videoTrackCount = 0,
                ),
            ),
        ).copy(content = createTab("https://mozilla.org").content.copy(fullScreen = true))

        val store = BrowserStore(
            BrowserState(
                tabs = listOf(session),
                selectedTabId = session.id,
            ),
        )
        val feature = PictureInPictureFeature(store, activity, crashReporting)

        assertFalse(feature.enterPipMode())
        verify(activity, never()).enterPictureInPictureMode(any())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun `manual PiP returns false without system support`() {
        whenever(activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            .thenReturn(false)

        val feature = PictureInPictureFeature(BrowserStore(), activity, crashReporting)

        assertFalse(feature.enterPipMode())
        verify(activity, never()).enterPictureInPictureMode(any())
    }

    @Test
    fun `PiP mode changes are dispatched to the selected session`() {
        val captureActionsMiddleware = CaptureActionsMiddleware<BrowserState, BrowserAction>()
        val store = BrowserStore(
            initialState = BrowserState(),
            middleware = listOf(captureActionsMiddleware) + EngineMiddleware.create(
                engine = mock(),
                TestScope(),
            ),
        )

        val feature = PictureInPictureFeature(
            store = store,
            activity = activity,
            crashReporting = crashReporting,
            tabId = "tab-id",
        )

        feature.onPictureInPictureModeChanged(true)
        captureActionsMiddleware.assertFirstAction(ContentAction.PictureInPictureChangedAction::class) { action ->
            assertTrue(action.pipEnabled)
        }

        feature.onPictureInPictureModeChanged(false)
        captureActionsMiddleware.assertLastAction(ContentAction.PictureInPictureChangedAction::class) { action ->
            assertFalse(action.pipEnabled)
        }
    }
}
