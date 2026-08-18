package com.nova.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nova.browser.App
import com.nova.browser.browser.BrowserCore
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenExtensions: () -> Unit, onOpenDownloads: () -> Unit) {
    var note by remember { mutableStateOf<String?>(null) }
    var safeBrowsing by remember { mutableStateOf(Store.safeBrowsing) }
    var historyEnabled by remember { mutableStateOf(Store.historyEnabled) }
    var autoClearHistory by remember { mutableStateOf(Store.autoClearHistory) }
    var quickAccess by remember { mutableStateOf(Store.quickAccessEnabled) }
    var blockedOpen by remember { mutableStateOf(false) }
    var allowedOpen by remember { mutableStateOf(false) }

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
                    subtitle = "Blocks ads, trackers, analytics, social widgets and cryptominers using GeckoView's Enhanced Tracking Protection plus the EasyList, EasyPrivacy, annoyance and Nova AdHost lists (the same community lists used by uBlock Origin). The number in the address bar is what it blocked on the current page.",
                ) {
                    RadioRow(
                        options = listOf("Off", "Standard", "Strict"),
                        values = listOf("off", "standard", "strict"),
                        selected = Store.adblockLevel,
                    ) { v ->
                        Store.adblockLevel = v
                        ExtensionManager.setShieldEnabled(v != "off")
                        BrowserCore.applyShieldToAll()
                        note = if (v == "off") "Ad blocking is now off. Reload pages to apply." else "Ad blocking updated for all tabs. Reload pages to apply."
                    }
                }
            }

            item { StatRow("Requests blocked this session", "${BrowserCore.totalBlocked}") }

            item {
                ButtonRow("Blocked domains", Icons.Rounded.PlaylistRemove) { blockedOpen = true }
            }

            item {
                ButtonRow("Allowed domains", Icons.Rounded.Lock) { allowedOpen = true }
            }

            item {
                SettingBlock(
                    title = "Secure DNS (DNS-over-HTTPS)",
                    subtitle = "Encrypts every domain lookup, browser-wide. Nova's own resolver choice — no need for a system VPN or Private DNS. Pages fall back to your normal DNS if a DoH server is unreachable.",
                ) {
                    RadioRow(
                        options = listOf("Off", "Cloudflare", "Google", "Quad9"),
                        values = listOf("off", "cloudflare", "google", "quad9"),
                        selected = Store.dnsMode,
                    ) { v ->
                        Store.dnsMode = v
                        App.recreateGeckoRuntime()
                        note = if (v == "off") "DNS-over-HTTPS turned off." else "DNS switched to ${v.replaceFirstChar { it.uppercase() }} — applied now."
                    }
                }
            }

            item {
                SettingBlock(
                    title = "Safe browsing",
                    subtitle = "Uses GeckoView's built-in Safe Browsing (Mozilla + Google lists) to warn on dangerous sites.",
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = safeBrowsing,
                            onCheckedChange = {
                                safeBrowsing = it
                                Store.safeBrowsing = it
                                App.recreateGeckoRuntime()
                                note = "Safe browsing updated — applied now."
                            },
                        )
                    }
                }
            }

            item { SectionHeader("History") }

            item {
                SettingBlock(
                    title = "Pause history",
                    subtitle = "While paused, no pages are added to history.",
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (historyEnabled) "Recording on" else "Recording paused", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = historyEnabled,
                            onCheckedChange = {
                                historyEnabled = it
                                Store.historyEnabled = it
                            },
                        )
                    }
                }
            }

            item {
                SettingBlock(
                    title = "Auto-clear history on open",
                    subtitle = "Wipes all history every time Nova starts.",
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (autoClearHistory) "On" else "Off", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = autoClearHistory,
                            onCheckedChange = {
                                autoClearHistory = it
                                Store.autoClearHistory = it
                            },
                        )
                    }
                }
            }

            item {
                SettingBlock(
                    title = "Replace history with study items",
                    subtitle = "Wipes your real history and fills it with random study/search topics — great for covering your tracks.",
                ) {
                    ButtonRow("Replace history now", Icons.Rounded.School) {
                        Store.randomizeHistory()
                        note = "History replaced with ${Store.history().size} random study topics."
                    }
                }
            }

            item { SectionHeader("Start page") }

            item {
                SettingBlock(
                    title = "Quick access",
                    subtitle = "Show the shortcut grid on the start page.",
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (quickAccess) "Visible" else "Hidden", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = quickAccess,
                            onCheckedChange = {
                                quickAccess = it
                                Store.quickAccessEnabled = it
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
                ButtonRow("Clear bookmarks, history & shortcuts", Icons.Rounded.Delete) {
                    Store.clearAllData()
                    note = "Bookmarks, history and shortcuts cleared."
                }
            }

            item { SectionHeader("About") }

            item {
                Text(
                    "Nova Browser 3.0\n\n" +
                        "Engine: Mozilla GeckoView — the same engine that powers Firefox for Android, with native HTML5 video, fullscreen and rotation support.\n" +
                        "Ad blocking (EasyList + Nova AdHost) · DNS-over-HTTPS · Safe browsing · Firefox add-ons · Private mode · Downloads · Desktop mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (blockedOpen) {
        DomainListDialog(
            title = "Blocked domains",
            hint = "Sites on this list are always blocked, even outside ad lists.",
            domains = Store.blockedDomains(),
            onAdd = { Store.addBlockedDomain(it) },
            onRemove = { Store.removeBlockedDomain(it) },
            onDismiss = { blockedOpen = false },
        )
    }
    if (allowedOpen) {
        DomainListDialog(
            title = "Allowed domains",
            hint = "Sites on this list are never blocked, even when ad blocking is on.",
            domains = Store.whitelistedDomains(),
            onAdd = { Store.addWhitelistedDomain(it) },
            onRemove = { Store.removeWhitelistedDomain(it) },
            onDismiss = { allowedOpen = false },
        )
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

@Composable
private fun DomainListDialog(
    title: String,
    hint: String,
    domains: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Domain, e.g. ads.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                if (domains.isEmpty()) {
                    Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    domains.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(d, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onRemove(d) }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = input.trim().lowercase().removePrefix("www.")
                if (d.isNotEmpty() && d.contains('.')) {
                    onAdd(d)
                    input = ""
                }
            }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
