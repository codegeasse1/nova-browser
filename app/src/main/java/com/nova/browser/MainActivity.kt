package com.nova.browser

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
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
            App.finishFilePrompt(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.activity = this
        App.init(applicationContext)
        enableEdgeToEdge()
        setContent { BrowserApp() }
    }

    fun setFullscreenUi(on: Boolean) {
        runCatching {
            if (on) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun launchPermissionRequest(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    fun openFileChooser() {
        runCatching { filePicker.launch(arrayOf("*/*")) }
    }

    override fun onDestroy() {
        App.markCleanShutdown()
        App.activity = null
        super.onDestroy()
    }
}
