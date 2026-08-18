package com.nova.browser

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nova.browser.ui.BrowserApp

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            App.finishAndroidPermissionRequest(result.values.all { it })
        }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            App.filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            App.filePathCallback = null
        }

    private lateinit var fullscreenHost: FrameLayout
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    val isFullscreen: Boolean get() = fullscreenView != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.activity = this
        App.init(applicationContext)
        enableEdgeToEdge()
        setContent { BrowserApp() }

        fullscreenHost = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        addContentView(fullscreenHost, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun showFullscreenView(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenView?.let { runCatching { fullscreenHost.removeView(it) } }
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = callback
        fullscreenView = view
        fullscreenHost.removeAllViews()
        fullscreenHost.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fullscreenHost.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        runCatching {
            WindowInsetsControllerCompat(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun hideFullscreenView() {
        fullscreenView?.let { runCatching { fullscreenHost.removeView(it) } }
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreenHost.visibility = View.GONE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        runCatching {
            WindowInsetsControllerCompat(window, fullscreenHost).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun launchPermissionRequest(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    fun openFileChooser() {
        runCatching { filePicker.launch(arrayOf("*/*")) }
    }

    override fun onDestroy() {
        App.activity = null
        super.onDestroy()
    }
}
