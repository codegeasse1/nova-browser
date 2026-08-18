package com.nova.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.browser.browser.BrowserCore
import com.nova.browser.browser.DownloadItem
import com.nova.browser.browser.Downloads
import com.nova.browser.store.Store
import java.util.concurrent.TimeUnit

object ManagerScreens {

    @Composable
    fun BookmarksScreen(onBack: () -> Unit) {
        var list by remember { mutableStateOf(Store.bookmarks()) }
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HeaderRow(title = "Bookmarks", onBack = onBack)
            if (list.isEmpty()) {
                EmptyState("No bookmarks yet", "Tap the bookmark icon in the toolbar to save a page.")
            } else {
                LazyColumn {
                    items(list, key = { it.second }) { (title, url) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    BrowserCore.newTab(url)
                                    onBack()
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FaviconImage(url, fallbackLabel = title, size = 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                Store.removeBookmark(url)
                                list = Store.bookmarks()
                            }) {
                                Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryScreen(onBack: () -> Unit) {
        var list by remember { mutableStateOf(Store.history()) }
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HeaderRow(
                title = "History",
                onBack = onBack,
                trailing = {
                    if (list.isNotEmpty()) {
                        TextButton(onClick = {
                            Store.clearHistory()
                            list = Store.history()
                        }) { Text("Clear") }
                    }
                },
            )
            if (list.isEmpty()) {
                EmptyState("No history yet", "Pages you visit will appear here.")
            } else {
                LazyColumn {
                    items(list, key = { it.second }) { (title, url, time) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    BrowserCore.newTab(url)
                                    onBack()
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FaviconImage(url, fallbackLabel = title, size = 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(relativeTime(time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    private fun relativeTime(timeMs: Long): String {
        val diff = System.currentTimeMillis() - timeMs
        val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            mins < 1 -> "now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            else -> "${mins / (60 * 24)}d ago"
        }
    }

    private fun fileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    @Composable
    fun DownloadsScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        val downloads = Downloads.items
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HeaderRow(
                title = "Downloads",
                onBack = onBack,
                trailing = {
                    if (downloads.isNotEmpty()) {
                        TextButton(onClick = { Downloads.clearAll(context) }) { Text("Clear all") }
                    }
                },
            )
            if (downloads.isEmpty()) {
                EmptyState("No downloads yet", "Files you download will appear here.")
            } else {
                LazyColumn {
                    items(downloads, key = { it.id }) { item ->
                        DownloadRow(item, context)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    @Composable
    private fun DownloadRow(item: DownloadItem, context: android.content.Context) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { Downloads.open(context, item) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (item.state) {
                    "done" -> Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
                    "downloading" -> LinearProgressIndicator(progress = Float.NaN, modifier = Modifier.size(22.dp))
                    else -> Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "${fileSize(item.size)} · ${item.filename} · ${relativeTime(item.date)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { Downloads.share(context, item) }) {
                Icon(Icons.Rounded.Share, "Share", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { Downloads.delete(context, item) }) {
                Icon(Icons.Rounded.Delete, "Delete", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
