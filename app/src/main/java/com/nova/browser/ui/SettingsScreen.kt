package com.nova.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nova.browser.browser.BrowserCore
import com.nova.browser.store.Store

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenExtensions: () -> Unit, onOpenDownloads: () -> Unit) {
    var note by remember { mutableStateOf<String?>(null) }
    var safeBrowsing by remember { mutableStateOf(Store.safeBrowsing) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        HeaderRow(title = "Settings", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SectionHeader("Privacy & Security") }

            item {
                SettingBlock(
                    title = "Ad & tracker blocking",
                    subtitle = "Blocks ads, trackers, analytics, social widgets, cryptominers and fingerprinters using the EasyList, EasyPrivacy and annoyance lists.",
                ) {
                    RadioRow(
                        options = listOf("Off", "Standard", "Strict"),
                        values = listOf("off", "standard", "strict"),
                        selected = Store.adblockLevel,
                    ) { v ->
                        Store.adblockLevel = v
                        BrowserCore.applyShieldToAll()
                        note = if (v == "off") "Ad blocking is now off. Reload pages to apply." else "Ad blocking updated for all tabs. Reload pages to apply."
                    }
                }
            }

            item { StatRow("Requests blocked this session", "${BrowserCore.totalBlocked}") }

            item {
                SettingBlock(
                    title = "Safe browsing",
                    subtitle = "Uses the Chromium engine's built-in Safe Browsing to warn on dangerous sites.",
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = safeBrowsing,
                            onCheckedChange = {
                                safeBrowsing = it
                                Store.safeBrowsing = it
                                note = "Safe browsing takes effect for pages loaded after this change."
                            },
                        )
                    }
                }
            }

            item { SectionHeader("Appearance") }

            item {
                SettingBlock(title = "Theme") {
                    RadioRow(
                        options = listOf("System", "Light", "Dark"),
                        values = listOf("auto", "light", "dark"),
                        selected = Store.theme,
                    ) { v ->
                        Store.theme = v
                        note = null
                    }
                }
            }

            item { SectionHeader("General") }

            item {
                SettingBlock(title = "Search engine") {
                    RadioRow(
                        options = listOf("Google", "DuckDuckGo", "Bing"),
                        values = listOf("google", "duckduckgo", "bing"),
                        selected = Store.searchEngine,
                    ) { v ->
                        Store.searchEngine = v
                    }
                }
            }

            item {
                ButtonRow("Downloads", Icons.Rounded.Download, onOpenDownloads)
            }

            item {
                ButtonRow("Extensions", Icons.Rounded.Extension, onOpenExtensions)
            }

            item {
                ButtonRow("Clear bookmarks, history & shortcuts", Icons.Rounded.Lock) {
                    Store.clearAllData()
                    note = "Bookmarks, history and shortcuts cleared."
                }
            }

            item { SectionHeader("About") }

            item {
                Text(
                    "Nova Browser 2.0\n\n" +
                        "Engine: Android System WebView — the Chromium (Blink + V8) engine that also powers Chrome on Android.\n" +
                        "Ad blocking · Safe browsing · Content-script extensions · Private mode · Downloads · Desktop mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }

    note?.let {
        AlertDialog(
            onDismissRequest = { note = null },
            title = { Text("Note") },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { note = null }) { Text("OK") }
            },
        )
    }
}
