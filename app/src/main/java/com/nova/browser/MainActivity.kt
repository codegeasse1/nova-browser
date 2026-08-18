package com.nova.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nova.browser.ui.BrowserApp
import com.nova.browser.ui.NovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.data?.toString()
        setContent {
            NovaTheme {
                BrowserApp(initialUrl = url)
            }
        }
    }
}
