package com.nova.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nova.browser.browser.BrowserCore
import com.nova.browser.store.Store

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenExtensions: () -> Unit) {
    var note by remember { mutableStateOf<String?>(null) }

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
                    subtitle = "Blocks ads, trackers, analytics, social widgets, cryptominers and fingerprinters (Firefox Enhanced Tracking Protection lists).",
                ) {
                    RadioRow(
                        options = listOf("Off", "Standard", "Strict"),
                        values = listOf("off", "standard", "strict"),
                        selected = Store.adblockLevel,
                    ) { v ->
                        Store.adblockLevel = v
                        BrowserCore.applyShieldToAll()
                        note = if (v == "strict") "Strict blocking lists take full effect after restarting Nova." else "Ad blocking updated for all tabs."
                    }
                }
            }

            item { StatRow("Requests blocked this session", "${BrowserCore.totalBlocked}") }

            item {
                SettingBlock(
                    title = "DNS over HTTPS",
                    subtitle = "Encrypted DNS lookups. Automatic uses DoH with a secure fallback; Strict forces DoH for every lookup.",
                ) {
                    RadioRow(
                        options = listOf("Off", "Automatic", "Strict"),
                        values = listOf("off", "first", "only"),
                        selected = Store.dohMode,
                    ) { v ->
                        Store.dohMode = v
                        note = "DNS over HTTPS takes effect after restarting Nova."
                    }
                    Spacer(Modifier.height(6.dp))
                    RadioRow(
                        options = listOf("Mozilla", "Cloudflare", "NextDNS"),
                        values = listOf(
                            "https://mozilla.cloudflare-dns.com/dns-query",
                            "https://cloudflare-dns.com/dns-query",
                            "https://dns.nextdns.io",
                        ),
                        selected = Store.dohProvider,
                    ) { v ->
                        Store.dohProvider = v
                        note = "DNS over HTTPS takes effect after restarting Nova."
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
                        "Engine: GeckoView (nightly) — the same engine behind Quetta and Firefox for Android.\n" +
                        "WebExtension support · Enhanced Tracking Protection · DNS over HTTPS · Private mode · Downloads",
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
