/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginsStorage
import org.mozilla.fenix.R
import java.io.File

/**
 * Nova: exports all saved passwords to a CSV file (the same format Firefox
 * exports, which Chrome, Edge, Brave and other browsers can import back), then
 * opens the share sheet so the file can be saved anywhere or sent to another
 * device or browser.
 */
object NovaPasswordExport {
    fun export(context: Context, storage: LoginsStorage, activity: Activity) {
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val logins = storage.list()
                    if (logins.isEmpty()) {
                        throw EmptyLogins()
                    }
                    val dir = File(context.cacheDir, "nova_exports").apply { mkdirs() }
                    val file = File(dir, "nova-passwords.csv")
                    file.writeText(buildCsv(logins))
                    ExportOutcome(file, logins.size)
                }
            }
            outcome.fold(
                onSuccess = { o ->
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            o.file,
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        activity.startActivity(
                            Intent.createChooser(send, context.getString(R.string.nova_passwords_export_title)),
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.nova_passwords_export_success, o.count),
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.nova_passwords_export_error, Toast.LENGTH_LONG).show()
                    }
                },
                onFailure = { e ->
                    if (e is EmptyLogins) {
                        Toast.makeText(context, R.string.nova_passwords_export_empty, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, R.string.nova_passwords_export_error, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
    }

    private class EmptyLogins : Exception()

    private data class ExportOutcome(val file: File, val count: Int)

    private fun buildCsv(logins: List<Login>): String {
        val sb = StringBuilder()
        sb.append("url,username,password,httpRealm,formActionOrigin,guid,timeCreated,timeLastUsed,timePasswordChanged\n")
        for (login in logins) {
            sb.append(csv(login.origin)).append(',')
            sb.append(csv(login.username)).append(',')
            sb.append(csv(login.password)).append(',')
            sb.append(csv(login.httpRealm.orEmpty())).append(',')
            sb.append(csv(login.formActionOrigin.orEmpty())).append(',')
            sb.append(csv(login.guid)).append(',')
            sb.append(login.timeCreated).append(',')
            sb.append(login.timeLastUsed).append(',')
            sb.append(login.timePasswordChanged).append('\n')
        }
        return sb.toString()
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
