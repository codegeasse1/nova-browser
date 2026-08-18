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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.browser.browser.BrowserCore
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
}
