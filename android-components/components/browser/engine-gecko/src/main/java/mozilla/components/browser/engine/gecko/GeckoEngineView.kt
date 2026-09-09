/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import mozilla.components.browser.engine.gecko.activity.GeckoViewActivityContextDelegate
import mozilla.components.browser.engine.gecko.selection.GeckoSelectionActionDelegate
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme
import mozilla.components.concept.engine.selection.SelectionActionDelegate
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.lang.ref.WeakReference
import androidx.core.view.OnApplyWindowInsetsListener as AndroidxOnApplyWindowInsetsListener

/**
 * Gecko-based EngineView implementation.
 */
class GeckoEngineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), EngineView {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var geckoView = object : NestedGeckoView(context) {

        override fun onAttachedToWindow() {
            try {
                super.onAttachedToWindow()
            } catch (_: IllegalStateException) {
                // Nova: "display already acquired" happens when this view's
                // session still holds its display because a previous window
                // detach was skipped (mini/floating-window transition). Release
                // the display through this GeckoView (which acquired it), finish
                // attaching, then re-set the session so the display is
                // re-acquired here instead of crashing the app.
                try {
                    val s = releaseSession()
                    if (s != null) {
                        super.onAttachedToWindow()
                        setSession(s)
                        attachSelectionActionDelegate(s)
                        verticalScrollListener.observe(s)
                    }
                } catch (e2: Exception) {
                    android.util.Log.w("NovaGeckoView", "display re-attach failed", e2)
                }
            }
        }

        override fun onDetachedFromWindow() {
            // We are releasing the session before GeckoView gets detached from the window. Otherwise
            // GeckoView will close the session automatically and we do not want that.
            releaseSession()

            super.onDetachedFromWindow()
        }
    }.apply {
        // Explicitly mark this view as important for autofill. The default "auto" doesn't seem to trigger any
        // autofill behavior for us here.
        ViewCompat.setImportantForAutofill(this, IMPORTANT_FOR_AUTOFILL_YES)
    }

    internal fun setColorScheme(preferredColorScheme: PreferredColorScheme) {
        var colorScheme = preferredColorScheme
        if (preferredColorScheme == PreferredColorScheme.System) {
            colorScheme =
                if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    == Configuration.UI_MODE_NIGHT_YES
                ) {
                    PreferredColorScheme.Dark
                } else {
                    PreferredColorScheme.Light
                }
        }

        if (colorScheme == PreferredColorScheme.Dark) {
            geckoView.coverUntilFirstPaint(DARK_COVER)
        } else {
            geckoView.coverUntilFirstPaint(Color.WHITE)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var currentSession: GeckoEngineSession? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var currentSelection: BasicSelectionActionDelegate? = null

    override var selectionActionDelegate: SelectionActionDelegate? = null

    @VisibleForTesting
    internal var verticalScrollListener = GeckoVerticalScrollListener()
    override val verticalScrollPosition = verticalScrollListener.scrollYPosition
    override val verticalScrollDelta = verticalScrollListener.scrollYDeltas

    init {
        addView(geckoView)

        /*
         * With the current design, we have a [NestedGeckoView] inside this
         * [GeckoEngineView]. In our supported embedders, we wrap this with the
         * AndroidX `SwipeRefreshLayout` to enable features like Pull-To-Refresh:
         *
         * ```
         *  SwipeRefreshLayout
         * └── GeckoEngineView
         *    └── NestedGeckoView
         * ```
         *
         * `SwipeRefreshLayout` only looks at the direct child to see if it has nested scrolling
         * enabled. As we embed [NestedGeckoView] inside [GeckoEngineView], we change the hierarchy
         * so that [NestedGeckoView] is no longer the direct child of `SwipeRefreshLayout`.
         *
         * To fix this we enable nested scrolling on the GeckoEngineView to emulate this
         * information. This is required information for `View.requestDisallowInterceptTouchEvent`
         * to work correctly in the [NestedGeckoView].
         */
        isNestedScrollingEnabled = true
    }

    /**
     * Render the content of the given session.
     */
    @Synchronized
    override fun render(session: EngineSession) {
        val internalSession = session as GeckoEngineSession
        currentSession = session

        if (geckoView.session != internalSession.geckoSession) {
            geckoView.session?.let {
                // Release a previously assigned session. Otherwise GeckoView will close it
                // automatically.
                detachSelectionActionDelegate(it)
                geckoView.releaseSession()
            }

            try {
                geckoView.setSession(internalSession.geckoSession)
                attachSelectionActionDelegate(internalSession.geckoSession)
                verticalScrollListener.observe(internalSession.geckoSession)
            } catch (_: IllegalStateException) {
                // Nova: "display already acquired" happens when the session's
                // display is still held by a previous window (mini/floating-
                // window relaunch). Force-release the session's display and
                // re-set it so the display is re-acquired here instead of
                // crashing the app.
                try {
                    forceReleaseSessionDisplay(internalSession.geckoSession)
                    geckoView.setSession(internalSession.geckoSession)
                    attachSelectionActionDelegate(internalSession.geckoSession)
                    verticalScrollListener.observe(internalSession.geckoSession)
                } catch (e2: Exception) {
                    android.util.Log.w("NovaGeckoView", "display re-set failed", e2)
                }
            }
        }
    }

    private fun forceReleaseSessionDisplay(session: GeckoSession) {
        try {
            val field = GeckoSession::class.java.getDeclaredField("mDisplay")
            field.isAccessible = true
            val display = field.get(session) as? org.mozilla.geckoview.GeckoDisplay ?: return
            session.releaseDisplay(display)
        } catch (e: Exception) {
            android.util.Log.w("NovaGeckoView", "forced display release failed", e)
        }
    }

    private fun attachSelectionActionDelegate(session: GeckoSession) {
        val delegate = GeckoSelectionActionDelegate.maybeCreate(context, selectionActionDelegate)
        if (delegate != null) {
            session.selectionActionDelegate = delegate
            currentSelection = delegate
        }
    }

    private fun detachSelectionActionDelegate(session: GeckoSession?) {
        if (currentSelection != null) {
            session?.selectionActionDelegate = null
            currentSelection = null
        }
    }

    @Synchronized
    override fun release() {
        detachSelectionActionDelegate(currentSession?.geckoSession)
        verticalScrollListener.release()

        currentSession = null

        geckoView.releaseSession()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        release()
    }

    override fun canClearSelection() = !currentSelection?.selection?.text.isNullOrEmpty()

    override fun canScrollVerticallyUp() = currentSession?.let { it.scrollY > 0 } != false

    override fun canScrollVerticallyDown() =
        true // waiting for this issue https://bugzilla.mozilla.org/show_bug.cgi?id=1507569

    override fun getInputResultDetail() = geckoView.inputResultDetail

    override fun setVerticalClipping(clippingHeight: Int) {
        geckoView.setVerticalClipping(clippingHeight)
    }

    override fun setDynamicToolbarMaxHeight(height: Int) {
        geckoView.setDynamicToolbarMaxHeight(height)
    }

    override fun setActivityContext(context: Context?) {
        geckoView.activityContextDelegate = GeckoViewActivityContextDelegate(WeakReference(context))
    }

    override fun captureThumbnail(onFinish: (Bitmap?) -> Unit) {
        val geckoResult = geckoView.capturePixels()
        geckoResult.then(
            { bitmap ->
                onFinish(bitmap)
                GeckoResult()
            },
            {
                onFinish(null)
                GeckoResult<Void>()
            },
        )
    }

    override fun clearSelection() {
        currentSelection?.clearSelection()
    }

    override fun setVisibility(visibility: Int) {
        // GeckoView doesn't react to onVisibilityChanged so we need to propagate ourselves for now:
        // https://bugzilla.mozilla.org/show_bug.cgi?id=1630775
        // We do this to prevent the content from resizing when the view is not visible:
        // https://github.com/mozilla-mobile/android-components/issues/6664
        geckoView.visibility = visibility
        super.setVisibility(visibility)
    }

    override fun addWindowInsetsListener(
        key: String,
        listener: AndroidxOnApplyWindowInsetsListener?,
    ) = geckoView.addWindowInsetsListener(key, listener)

    override fun removeWindowInsetsListener(key: String) = geckoView.removeWindowInsetsListener(key)

    companion object {
        internal const val DARK_COVER = 0xFF2A2A2E.toInt()
    }
}
