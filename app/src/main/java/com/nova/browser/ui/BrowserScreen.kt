package com.nova.browser.ui

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nova.browser.browser.BrowserCore
import com.nova.browser.browser.TabState
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.store.Store
import kotlinx.coroutines.delay

enum class NovaScreen { BROWSER, TABS, EXTENSIONS, SETTINGS, BOOKMARKS, HISTORY, DOWNLOADS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf(NovaScreen.BROWSER) }
    var addressEditing by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("") }
    var shieldOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    val darkTheme = when (Store.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val incognito = BrowserCore.activeTab?.isPrivate == true

    LaunchedEffect(Unit) {
        if (BrowserCore.tabs.isEmpty()) BrowserCore.newTab()
    }

    LaunchedEffect(Unit) {
        while (true) {
            BrowserCore.lastDownloadMessage?.let { snackMsg = it; BrowserCore.lastDownloadMessage = null }
            ExtensionManager.message?.let { snackMsg = it; ExtensionManager.message = null }
            ExtensionManager.openAmoSearch?.let { q ->
                ExtensionManager.openAmoSearch = null
                BrowserCore.navigate("https://addons.mozilla.org/firefox/search/?q=${android.net.Uri.encode(q)}")
            }
            BrowserCore.pendingExternalIntent?.let {
                snackMsg = "No app found to open \"$it\""
                BrowserCore.pendingExternalIntent = null
            }
            delay(500)
        }
    }

    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            snackMsg = null
        }
    }

    BackHandler {
        when {
            BrowserCore.fullscreen -> BrowserCore.exitFullscreen()
            addressEditing -> {
                addressEditing = false
                addressText = ""
                context.hideKeyboard()
            }
            screen != NovaScreen.BROWSER -> screen = NovaScreen.BROWSER
            BrowserCore.activeTab?.canGoBack == true -> BrowserCore.back()
            else -> (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    NovaTheme(incognito = incognito, darkTheme = darkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { _ ->
            when (screen) {
                NovaScreen.BROWSER -> BrowserShell(
                    addressEditing = addressEditing,
                    setAddressEditing = { addressEditing = it },
                    addressText = addressText,
                    setAddressText = { addressText = it },
                    shieldOpen = shieldOpen,
                    setShieldOpen = { shieldOpen = it },
                    menuOpen = menuOpen,
                    setMenuOpen = { menuOpen = it },
                    onOpenTabs = { screen = NovaScreen.TABS },
                    onOpenExtensions = { screen = NovaScreen.EXTENSIONS },
                    onOpenSettings = { screen = NovaScreen.SETTINGS },
                    onOpenBookmarks = { screen = NovaScreen.BOOKMARKS },
                    onOpenHistory = { screen = NovaScreen.HISTORY },
                    onOpenDownloads = { screen = NovaScreen.DOWNLOADS },
                )
                NovaScreen.TABS -> TabSwitcherScreen(onClose = { screen = NovaScreen.BROWSER })
                NovaScreen.EXTENSIONS -> ExtensionsScreen(onBack = { screen = NovaScreen.BROWSER })
                NovaScreen.SETTINGS -> SettingsScreen(onBack = { screen = NovaScreen.BROWSER }, onOpenExtensions = { screen = NovaScreen.EXTENSIONS }, onOpenDownloads = { screen = NovaScreen.DOWNLOADS })
                NovaScreen.BOOKMARKS -> ManagerScreens.BookmarksScreen(onBack = { screen = NovaScreen.BROWSER })
                NovaScreen.HISTORY -> ManagerScreens.HistoryScreen(onBack = { screen = NovaScreen.BROWSER })
                NovaScreen.DOWNLOADS -> ManagerScreens.DownloadsScreen(onBack = { screen = NovaScreen.BROWSER })
            }
        }
    }
}

private fun Context.hideKeyboard() {
    val imm = getSystemService(InputMethodManager::class.java)
    val windowToken = (this as? android.app.Activity)?.currentFocus?.windowToken
    imm.hideSoftInputFromWindow(windowToken, 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserShell(
    addressEditing: Boolean,
    setAddressEditing: (Boolean) -> Unit,
    addressText: String,
    setAddressText: (String) -> Unit,
    shieldOpen: Boolean,
    setShieldOpen: (Boolean) -> Unit,
    menuOpen: Boolean,
    setMenuOpen: (Boolean) -> Unit,
    onOpenTabs: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val context = LocalContext.current
    val tab = BrowserCore.activeTab
    val loading = (tab?.progress ?: 0) in 1..99
    var showBookmarkDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!BrowserCore.fullscreen) {
                ToolbarArea(
                    tab = tab,
                    addressEditing = addressEditing,
                    setAddressEditing = setAddressEditing,
                    addressText = addressText,
                    setAddressText = setAddressText,
                    menuOpen = menuOpen,
                    setMenuOpen = setMenuOpen,
                    onOpenTabs = onOpenTabs,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenHistory = onOpenHistory,
                    onOpenExtensions = onOpenExtensions,
                    onOpenDownloads = onOpenDownloads,
                    onShieldClick = { setShieldOpen(true) },
                    onAddBookmark = { showBookmarkDialog = true },
                )

                if (loading) {
                    LinearProgressIndicator(
                        progress = (tab?.progress ?: 0) / 100f,
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val current = BrowserCore.activeTab
                if (current != null) {
                    if (current.isStartPage) {
                        StartPage(
                            onSearchClick = {
                                setAddressText("")
                                setAddressEditing(true)
                            },
                            onOpenTabs = onOpenTabs,
                            onOpenExtensions = onOpenExtensions,
                            onOpenSettings = onOpenSettings,
                            onOpenBookmarks = onOpenBookmarks,
                            onOpenHistory = onOpenHistory,
                            onOpenDownloads = onOpenDownloads,
                            onOpenPrivate = { BrowserCore.newTab(isPrivate = true) },
                        )
                    } else {
                        WebHost(current)
                    }
                }

                BrowserCore.storeOffer?.let { offer ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Install \"${offer.name}\" in Nova?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (offer.kind == "amo") "From the Firefox Add-ons store"
                                    else "Nova runs the Firefox engine — this is the Chrome Web Store",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (offer.kind == "amo") {
                                TextButton(onClick = {
                                    ExtensionManager.installFromAmo(context, offer.key)
                                    BrowserCore.storeOffer = null
                                }) { Text("Install", fontWeight = FontWeight.Bold) }
                            } else {
                                TextButton(onClick = {
                                    BrowserCore.navigate("https://addons.mozilla.org/firefox/search/?q=${android.net.Uri.encode(offer.name)}")
                                    BrowserCore.storeOffer = null
                                }) { Text("Find on Firefox Add-ons", fontWeight = FontWeight.Bold) }
                            }
                            TextButton(onClick = { BrowserCore.storeOffer = null }) { Text("Not now") }
                        }
                    }
                }
            }

            BottomBar(tab = tab, onOpenTabs = onOpenTabs, hidden = BrowserCore.fullscreen)
        }

        if (shieldOpen) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = interactionSource, indication = null) { setShieldOpen(false) },
            )
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                ShieldPanel(tab)
            }
        }
    }

    if (showBookmarkDialog && tab != null && tab.url.isNotBlank()) {
        val title = tab.title.ifBlank { tab.host }
        val url = tab.url
        val already = Store.isBookmarked(url)
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text(if (already) "Remove bookmark?" else "Add bookmark") },
            text = { Text("$title\n$url") },
            confirmButton = {
                TextButton(onClick = {
                    if (already) Store.removeBookmark(url) else Store.addBookmark(title, url)
                    showBookmarkDialog = false
                    context.hideKeyboard()
                }) { Text(if (already) "Remove" else "Add") }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarArea(
    tab: TabState?,
    addressEditing: Boolean,
    setAddressEditing: (Boolean) -> Unit,
    addressText: String,
    setAddressText: (String) -> Unit,
    menuOpen: Boolean,
    setMenuOpen: (Boolean) -> Unit,
    onOpenTabs: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShieldClick: () -> Unit,
    onAddBookmark: () -> Unit,
) {
    val context = LocalContext.current
    var previousText by remember { mutableStateOf<String?>(null) }

    val submit = {
        val q = addressText.trim()
        if (q.isNotEmpty()) BrowserCore.navigate(q)
        setAddressEditing(false)
        setAddressText("")
        previousText = null
        context.hideKeyboard()
    }

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onOpenTabs,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tab, contentDescription = "Tabs", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("${BrowserCore.tabs.size}", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.width(8.dp))

            AddressBar(
                tab = tab,
                editing = addressEditing,
                setEditing = setAddressEditing,
                text = addressText,
                setText = setAddressText,
                onGo = submit,
                onShieldClick = onShieldClick,
                onEditStart = { previousText = it },
            )

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onAddBookmark,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = "Bookmark",
                        tint = if (tab != null && Store.isBookmarked(tab.url)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { setMenuOpen(true) },
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { setMenuOpen(false) }) {
                    DropdownMenuItem(
                        text = { Text("Desktop site") },
                        leadingIcon = { Icon(Icons.Rounded.DesktopWindows, null) },
                        trailingIcon = {
                            if (tab?.desktopSite == true) {
                                Icon(Icons.Rounded.Check, "On", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = { setMenuOpen(false); BrowserCore.toggleDesktopSite() },
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, null) },
                        onClick = { setMenuOpen(false); onOpenBookmarks() },
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        leadingIcon = { Icon(Icons.Rounded.History, null) },
                        onClick = { setMenuOpen(false); onOpenHistory() },
                    )
                    DropdownMenuItem(
                        text = { Text("Downloads") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { setMenuOpen(false); onOpenDownloads() },
                    )
                    DropdownMenuItem(
                        text = { Text("Extensions") },
                        leadingIcon = { Icon(Icons.Rounded.Extension, null) },
                        onClick = { setMenuOpen(false); onOpenExtensions() },
                    )
                    DropdownMenuItem(
                        text = { Text("New private tab") },
                        leadingIcon = { Icon(Icons.Rounded.PrivacyTip, null) },
                        onClick = { setMenuOpen(false); BrowserCore.newTab(isPrivate = true) },
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = {
                            setMenuOpen(false)
                            tab?.let {
                                if (it.url.isNotBlank()) {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, it.url)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(send, "Share link")) }
                                }
                            }
                        },
                    )
                }
            }
        }

        if (addressEditing && addressText.isNotBlank()) {
            SuggestionsList(query = addressText, onPick = { label, url, isSearch ->
                context.hideKeyboard()
                setAddressEditing(false)
                if (isSearch) BrowserCore.navigate(label) else BrowserCore.navigate(url)
                setAddressText("")
            })
        }

        LaunchedEffect(tab?.url) {
            previousText = null
        }

        if (addressEditing && previousText != null) {
            Spacer(Modifier.height(6.dp))
            PreviousSearchRow(
                previous = previousText!!,
                onCopy = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    runCatching { cm?.setPrimaryClip(android.content.ClipData.newPlainText("link", previousText ?: "")) }
                    android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                },
                onRestore = {
                    previousText?.let { setAddressText(it) }
                    previousText = null
                },
            )
        }
    }
}

@Composable
private fun RowScope.AddressBar(
    tab: TabState?,
    editing: Boolean,
    setEditing: (Boolean) -> Unit,
    text: String,
    setText: (String) -> Unit,
    onGo: () -> Unit,
    onShieldClick: () -> Unit,
    onEditStart: (String?) -> Unit = {},
) {
    if (editing) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val context = LocalContext.current
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        TextField(
            value = text,
            onValueChange = setText,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text("Search or type a URL") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { setText("") }) { Icon(Icons.Rounded.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                focusManager.clearFocus()
                context.hideKeyboard()
                onGo()
            }),
        )
    } else {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f).height(48.dp),
            onClick = {
                val prev = text.ifBlank { null } ?: if (tab != null && !tab.isStartPage) tab.url else null
                onEditStart(prev)
                setText("")
                setEditing(true)
            },
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val onPage = tab != null && !tab.isStartPage
                val secTint = if (tab?.secure == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    Modifier
                        .then(if (onPage) Modifier.clickable(onClick = onShieldClick) else Modifier)
                        .padding(end = 6.dp)
                        .size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        tab == null || tab.isStartPage -> Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        tab.secure -> Icon(Icons.Rounded.Lock, "Secure — tap for site settings", Modifier.size(16.dp), tint = secTint)
                        else -> Icon(Icons.Rounded.Shield, "Not secure — tap for site settings", Modifier.size(16.dp), tint = secTint)
                    }
                }
                if (onPage && (tab?.blocked ?: 0) > 0) {
                    Text(
                        "${tab!!.blocked}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(
                    text = when {
                        tab == null || tab.isStartPage -> "Search or type URL"
                        tab.host.isNotBlank() -> tab.host
                        else -> tab.url
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tab == null || tab.isStartPage) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private data class Sugg(val primary: String, val secondary: String?, val url: String?, val isSearch: Boolean)

@Composable
private fun PreviousSearchRow(previous: String, onCopy: () -> Unit, onRestore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Restore,
                "Previous",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Last entry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    previous,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, "Copy", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Restore", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SuggestionsList(query: String, onPick: (String, String, Boolean) -> Unit) {
    if (query.isBlank()) return
    val suggestions = remember(query) {
        buildList {
            add(Sugg("Search for \"$query\"", null, query, true))
            Store.history()
                .filter { it.second.contains(query, true) || it.first.contains(query, true) }
                .take(4)
                .forEach { add(Sugg(it.first, it.second, it.second, false)) }
            Store.bookmarks()
                .filter { it.second.contains(query, true) || it.first.contains(query, true) }
                .take(3)
                .forEach { add(Sugg(it.first, it.second, it.second, false)) }
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            suggestions.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(s.primary, s.url ?: s.primary, s.isSearch) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (s.isSearch) Icons.Rounded.Search else Icons.Rounded.History,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        if (!s.secondary.isNullOrBlank()) {
                            Text(
                                s.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebHost(tab: TabState) {
    key(tab.id) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> BrowserCore.attachView(ctx, tab.id) },
        )
    }
}

@Composable
private fun BottomBar(tab: TabState?, onOpenTabs: () -> Unit, hidden: Boolean = false) {
    if (hidden) return
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(enabled = tab?.canGoBack == true, onClick = { BrowserCore.back() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            IconButton(enabled = tab?.canGoForward == true, onClick = { BrowserCore.forward() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Forward")
            }
            IconButton(onClick = { BrowserCore.goHome() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Home, "Home")
            }
            IconButton(onClick = { BrowserCore.reload() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Refresh, "Reload")
            }
            IconButton(onClick = { BrowserCore.newTab() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, "New tab")
            }
            Surface(
                shape = CircleShape,
                color = if (tab?.isPrivate == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                onClick = onOpenTabs,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tab, "Tabs", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ShieldPanel(tab: TabState?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Site settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    tab?.host ?: "Start page",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Tracking protection", style = MaterialTheme.typography.bodyLarge)
                Text("Blocked on this page: ${tab?.blocked ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = tab?.shield == true,
                onCheckedChange = { BrowserCore.toggleShield() },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Desktop site", style = MaterialTheme.typography.bodyLarge)
                Text("Loads the desktop version of pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = tab?.desktopSite == true,
                onCheckedChange = { BrowserCore.toggleDesktopSite() },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "The same engine-level protection Firefox for Android and IceRaven use. For stronger ad blocking, install uBlock Origin from the Add-ons store.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { BrowserCore.reload() }) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reload page to apply")
        }
    }
}
