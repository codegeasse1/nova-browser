package com.nova.browser

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.activity = this
        App.init(applicationContext)
        enableEdgeToEdge()
        setContent { BrowserApp() }
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
