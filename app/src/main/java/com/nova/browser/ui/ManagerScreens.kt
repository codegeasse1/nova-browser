package com.nova.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.browser.browser.BrowserCore
import com.nova.browser.store.Settings
import com.nova.browser.store.Store

@Composable
fun BookmarksScreen(store: Store, core: BrowserCore, onOpenWeb: () -> Unit) {
    val entries = remember { mutableStateOf(store.loadBookmarks()) }
    fun reload() {
        entries.value = store.loadBookmarks()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (entries.value.isEmpty()) {
            item {
                Text(
                    "No bookmarks yet. Tap the star in the address bar to save the current page.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(entries.value) { _, e ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable {
                    core.navigate(e.url)
                    onOpenWeb()
                }
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title.ifBlank { e.url }, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(e.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = {
                        store.removeBookmark(e.url)
                        reload()
                    }) {
                        Icon(Icons.Rounded.Delete, "Delete bookmark", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(store: Store, core: BrowserCore, onOpenWeb: () -> Unit) {
    val entries = remember { mutableStateOf(store.loadHistory()) }
    fun reload() {
        entries.value = store.loadHistory()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                store.clearHistory()
                reload()
            }) {
                Text("Clear history", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (entries.value.isEmpty()) {
                item {
                    Text(
                        "No history yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(entries.value) { _, e ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            core.navigate(e.url)
                            onOpenWeb()
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Column {
                        Text(e.title.ifBlank { e.url }, fontSize = 14.sp, maxLines = 1)
                        Text(e.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(core: BrowserCore, store: Store) {
    var homeUrl by remember { mutableStateOf(core.settings.homeUrl) }
    var searchEngine by remember { mutableStateOf(core.settings.searchEngine) }
    var privateMode by remember { mutableStateOf(core.settings.privateMode) }
    var javaScript by remember { mutableStateOf(core.settings.javaScriptEnabled) }
    var showImages by remember { mutableStateOf(core.settings.showImages) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Homepage", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = homeUrl,
            onValueChange = { homeUrl = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Text("Search engine", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { menuOpen = true }) {
                Text(searchEngineName(searchEngine))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf("google", "duckduckgo", "bing").forEach { e ->
                    DropdownMenuItem(
                        text = { Text(searchEngineName(e)) },
                        onClick = {
                            searchEngine = e
                            menuOpen = false
                        }
                    )
                }
            }
        }

        SettingSwitch("Private browsing (don't save history)", privateMode) { privateMode = it }
        SettingSwitch("JavaScript", javaScript) { javaScript = it }
        SettingSwitch("Load images", showImages) { showImages = it }

        Spacer(Modifier.height(4.dp))
        Button(onClick = {
            store.saveSettings(
                Settings(
                    homeUrl = homeUrl.trim().ifBlank { "https://www.google.com" },
                    searchEngine = searchEngine,
                    privateMode = privateMode,
                    javaScriptEnabled = javaScript,
                    showImages = showImages
                )
            )
            core.updateSettings()
        }) {
            Text("Save settings")
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private fun searchEngineName(key: String): String = when (key) {
    "duckduckgo" -> "DuckDuckGo"
    "bing" -> "Bing"
    else -> "Google"
}
