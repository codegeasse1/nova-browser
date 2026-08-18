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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nova.browser.browser.BrowserCore
import com.nova.browser.ext.ExtRuntime
import com.nova.browser.store.Store

private val NAV_ITEMS = listOf(
    "web" to "Tabs",
    "extensions" to "Extensions",
    "bookmarks" to "Bookmarks",
    "history" to "History",
    "settings" to "Settings"
)

@Composable
fun BrowserApp(initialUrl: String?) {
    val context = LocalContext.current
    val store = remember { Store(context.applicationContext) }
    val runtime = remember { ExtRuntime(context.applicationContext, store) }
    val core = remember { BrowserCore(context.applicationContext, store, runtime) }

    var screen by remember { mutableStateOf("web") }

    LaunchedEffect(Unit) {
        if (!initialUrl.isNullOrBlank()) core.navigate(initialUrl)
    }

    Column(Modifier.fillMaxSize()) {
        if (screen == "web") {
            AddressBar(core, store)
            TabStrip(core)
        } else {
            val title = NAV_ITEMS.firstOrNull { it.first == screen }?.second ?: ""
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                "extensions" -> ExtensionsScreen(runtime)
                "bookmarks" -> BookmarksScreen(store, core) { screen = "web" }
                "history" -> HistoryScreen(store, core) { screen = "web" }
                "settings" -> SettingsScreen(core, store)
                else -> WebSurface(core, store)
            }
        }
        NavigationBar {
            NAV_ITEMS.forEach { (key, label) ->
                NavigationBarItem(
                    selected = screen == key,
                    onClick = { screen = key },
                    icon = {
                        val icon = when (key) {
                            "extensions" -> Icons.Rounded.Extension
                            "bookmarks" -> Icons.Rounded.Bookmarks
                            "history" -> Icons.Rounded.History
                            "settings" -> Icons.Rounded.Settings
                            else -> Icons.Rounded.Language
                        }
                        Icon(icon, label)
                    },
                    label = { Text(label, fontSize = 10.sp) }
                )
            }
        }
    }
}

@Composable
private fun AddressBar(core: BrowserCore, store: Store) {
    val url = core.urlBarText
    val activeUrl = core.activeTab?.url ?: ""
    val bookmarked = activeUrl.isNotBlank() && store.isBookmarked(activeUrl)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { core.goBack() }, enabled = core.canGoBack()) {
            Icon(Icons.Rounded.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { core.goForward() }, enabled = core.canGoForward()) {
            Icon(Icons.Rounded.ArrowForward, "Forward", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { core.reload() }) {
            Icon(Icons.Rounded.Refresh, "Reload", tint = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(
            value = url,
            onValueChange = { core.setUrlBar(it) },
            modifier = Modifier.weight(1f).height(44.dp),
            singleLine = true,
            placeholder = { Text("Search or enter address", fontSize = 13.sp) },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { core.navigate(url) })
        )
        IconButton(onClick = {
            if (bookmarked) store.removeBookmark(activeUrl)
            else store.addBookmark(activeUrl, core.activeTab?.title ?: activeUrl)
        }) {
            Icon(
                if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                "Bookmark",
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TabStrip(core: BrowserCore) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(core.tabs) { index, tab ->
            val selected = index == core.activeIndex
            Surface(
                modifier = Modifier.clickable { core.switchTo(index) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (tab.url == BrowserCore.NEW_TAB) "New tab" else (tab.title.takeIf { it.isNotBlank() } ?: tab.url).take(14),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    IconButton(
                        onClick = { core.closeTab(index) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Rounded.Close, "Close tab", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.clickable { core.openNewTab(null) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    Icons.Rounded.Add, "New tab",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WebSurface(core: BrowserCore, store: Store) {
    val active = core.activeTab
    Box(Modifier.fillMaxSize()) {
        if (active == null || active.url == BrowserCore.NEW_TAB) {
            NewTabPage(core, store)
        } else {
            AndroidView(factory = { core.container }, modifier = Modifier.fillMaxSize())
            if (active.progress in 1 until 100) {
                LinearProgressIndicator(
                    progress = { active.progress / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun NewTabPage(core: BrowserCore, store: Store) {
    var search by remember { mutableStateOf("") }
    val activeId = core.activeTab?.id ?: -1
    val recent = remember(activeId) { store.loadHistory(20) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            "Nova Browser",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search or enter address", fontSize = 13.sp) },
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { core.navigate(search) })
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { core.navigate("google.com") }) { Text("Google") }
            OutlinedButton(onClick = { core.navigate("youtube.com") }) { Text("YouTube") }
            OutlinedButton(onClick = { core.navigate("wikipedia.org") }) { Text("Wikipedia") }
            OutlinedButton(onClick = { core.navigate("github.com") }) { Text("GitHub") }
        }
        Spacer(Modifier.height(26.dp))
        if (recent.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(recent.take(15)) { _, e ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { core.navigate(e.url) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.History, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(e.title, fontSize = 13.sp, maxLines = 1)
                            Text(e.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
