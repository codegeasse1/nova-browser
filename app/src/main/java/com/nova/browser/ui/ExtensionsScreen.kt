package com.nova.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.nova.browser.browser.BrowserCore
import com.nova.browser.ext.ExtensionManager
import com.nova.browser.ext.ExtensionUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ExtensionManager.installFromUri(context, uri)
    }
    var storeInput by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        HeaderRow(title = "Extensions", onBack = onBack)
        if (ExtensionManager.busy) {
            LinearProgressIndicator(progress = Float.NaN, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Nova runs on Mozilla GeckoView — the same engine as Firefox for Android — so it supports real Firefox add-ons.\n\n" +
                            "• One-tap install: open any add-on page on addons.mozilla.org inside Nova — an \"Install in Nova?\" banner appears and installs it.\n" +
                            "• Paste an add-on link or ID below to install it directly.\n" +
                            "• Chrome Web Store extensions run on Chrome's engine (Blink), not Gecko — those can only run on a full Chromium fork like Kiwi, which needs a desktop-class build farm. Nova's banner on Chrome store pages instead finds the add-on on the Firefox store.\n" +
                            "• Nova Shield (EasyList ad blocking) and the bundled tools are built-in extensions.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Install from Firefox Add-ons", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = storeInput,
                            onValueChange = { storeInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste add-on link or slug (e.g. ublock-origin)") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Storefront, null) },
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val input = storeInput.trim()
                                if (input.isNotBlank()) {
                                    val m = Regex("/(?:firefox/)?addon/([^/?]+)").find(input)
                                    if (m != null) {
                                        ExtensionManager.installFromAmo(context, m.groupValues[1])
                                    } else if (!input.contains(" ") && !input.contains("://")) {
                                        ExtensionManager.installFromAmo(context, input)
                                    } else {
                                        ExtensionManager.installFromUrl(context, input)
                                    }
                                    storeInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Storefront, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Install")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tip: open any add-on page on addons.mozilla.org in Nova, copy its link, and paste it here — or just let the banner install it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            BrowserCore.navigate("https://addons.mozilla.org/firefox/")
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Public, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add-ons store")
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/x-xpinstall", "application/zip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install file")
                    }
                }
            }
            if (ExtensionManager.extensions.isEmpty()) {
                item {
                    EmptyState("No extensions installed", "Install one from the Firefox Add-ons store, or from a .xpi file.")
                }
            } else {
                items(ExtensionManager.extensions, key = { it.id }) { ext ->
                    ExtCard(ext)
                }
            }
        }
    }
}

@Composable
private fun ExtCard(ext: ExtensionUi) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ext.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (ext.isBuiltIn) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    "Bundled",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Text("v${ext.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ext.enabled, onCheckedChange = { ExtensionManager.setEnabled(ext, it) })
            }
            if (ext.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(ext.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ext.permissions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ext.permissions.take(4).forEach { perm ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                perm,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (ext.permissions.size > 4) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                "+${ext.permissions.size - 4}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (!ext.isBuiltIn) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { ExtensionManager.uninstall(ext) }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
