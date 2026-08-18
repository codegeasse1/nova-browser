package com.nova.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.nova.browser.ext.ExtManifest
import com.nova.browser.ext.ExtRuntime
import com.nova.browser.ext.InstalledExtension

@Composable
fun ExtensionsScreen(runtime: ExtRuntime) {
    val exts = remember { mutableStateOf(runtime.manager.list()) }
    var popupExt by remember { mutableStateOf<ExtManifest?>(null) }

    fun reload() {
        exts.value = runtime.manager.list()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runtime.manager.installFromUri(uri)
            reload()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
                Text("Install from .zip / .crx", fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                runtime.manager.installSample()
                reload()
            }) {
                Text("Load sample", fontSize = 12.sp)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (exts.value.isEmpty()) {
                item {
                    Text(
                        "No extensions installed yet.\nTap \"Install from .zip / .crx\" to pick an extension archive, or \"Load sample\" to try the bundled one.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
            itemsIndexed(exts.value) { _, ext ->
                ExtCard(ext, runtime, { reload() }, { popupExt = ext.manifest })
            }
        }
    }

    popupExt?.let { mf ->
        ExtensionPopup(mf, runtime) { popupExt = null }
    }
}

@Composable
private fun ExtCard(
    ext: InstalledExtension,
    runtime: ExtRuntime,
    reload: () -> Unit,
    onPopup: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ext.manifest.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "v${ext.manifest.version}" +
                            (if (ext.manifest.manifestVersion >= 3) " · MV3 (partial)" else " · MV2") +
                            (runtime.badgeText(ext.manifest.id)?.let { " · badge: $it" } ?: ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ext.enabled,
                    onCheckedChange = { on ->
                        runtime.manager.setEnabled(ext.manifest.id, on)
                        if (on) runtime.ensureBackground(ext.manifest)
                        else runtime.destroyBackground(ext.manifest.id)
                        reload()
                    }
                )
            }
            if (ext.manifest.description.isNotBlank()) {
                Text(
                    ext.manifest.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ext.manifest.popup != null) {
                    OutlinedButton(onClick = onPopup, modifier = Modifier.heightIn(min = 32.dp)) {
                        Text("Open popup", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = {
                    runtime.manager.remove(ext.manifest.id)
                    runtime.destroyBackground(ext.manifest.id)
                    reload()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ExtensionPopup(mf: ExtManifest, runtime: ExtRuntime, onClose: () -> Unit) {
    val html = remember(mf.id) { runtime.buildPopupHtml(mf) }
    val wv = remember(mf.id) { runtime.makeExtensionWebView() }

    LaunchedEffect(mf.id) {
        html?.let { wv.loadDataWithBaseURL("file://${mf.dir}/", it, "text/html", "utf-8", null) }
    }

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 480.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                if (html != null) {
                    AndroidView(
                        factory = { wv },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 460.dp)
                    )
                } else {
                    Text("This extension has no popup.", Modifier.padding(16.dp))
                }
            }
        }
    }
}
