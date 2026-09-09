/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.logins

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.compose.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginsStorage
import mozilla.components.feature.password.importer.PasswordsImporterResult
import org.mozilla.fenix.ext.requireComponents

internal class ImportPasswordsDialogFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = content {
        NovaPasswordsImporter(
            loginsStorage = requireComponents.core.passwordsStorage,
            onFinished = { result ->
                parentFragmentManager.setFragmentResult(REQUEST_KEY, encodeResult(result))
                dismiss()
            },
        )
    }

    companion object {
        const val REQUEST_KEY = "import_passwords_request"
        const val TAG = "import_passwords_dialog"

        private const val KEY_RESULT = "result"
        private const val KEY_COUNT = "count"
        private const val RESULT_SUCCESS = "success"
        private const val RESULT_FAILURE = "failure"
        private const val RESULT_CANCELLED = "cancelled"

        fun decodeResult(bundle: Bundle): PasswordsImporterResult? =
            when (bundle.getString(KEY_RESULT)) {
                RESULT_SUCCESS -> PasswordsImporterResult.Success(importCount = bundle.getInt(KEY_COUNT))
                RESULT_FAILURE -> PasswordsImporterResult.Failure
                RESULT_CANCELLED -> PasswordsImporterResult.Canceled
                else -> null
            }

        private fun encodeResult(result: PasswordsImporterResult): Bundle = Bundle().apply {
            when (result) {
                is PasswordsImporterResult.Success -> {
                    putString(KEY_RESULT, RESULT_SUCCESS)
                    putInt(KEY_COUNT, result.importCount)
                }
                PasswordsImporterResult.Failure -> putString(KEY_RESULT, RESULT_FAILURE)
                PasswordsImporterResult.Canceled -> putString(KEY_RESULT, RESULT_CANCELLED)
            }
        }
    }
}

@Composable
private fun NovaPasswordsImporter(
    loginsStorage: LoginsStorage,
    onFinished: (PasswordsImporterResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onFinished(PasswordsImporterResult.Canceled)
        } else {
            importing = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Cannot read file")
                        val logins = parseLoginCsv(bytes.toString(Charsets.UTF_8))
                        if (logins.isEmpty()) {
                            PasswordsImporterResult.Success(importCount = 0)
                        } else {
                            PasswordsImporterResult.Success(
                                importCount = loginsStorage.addMany(logins).count { it.isSuccess },
                            )
                        }
                    }.getOrElse { e ->
                        if (e is CancellationException) throw e
                        PasswordsImporterResult.Failure
                    }
                }
                onFinished(result)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            launcher.launch(arrayOf("*/*"))
        }
    }

    Column(
        modifier = Modifier
            .width(280.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (importing) {
                "Importing passwords..."
            } else {
                "Choose the CSV file that contains your passwords"
            },
        )
        CircularProgressIndicator()
    }
}

/** Column names recognised in passwords CSV exports (Firefox, Chrome, Quetta...). */
private val CSV_COLUMN_FIELDS = mapOf(
    "url" to "origin",
    "login_uri" to "origin",
    "website" to "origin",
    "username" to "username",
    "login_username" to "username",
    "password" to "password",
    "login_password" to "password",
    "httprealm" to "httpRealm",
    "formactionorigin" to "formActionOrigin",
    "name" to "name",
    "note" to "note",
)

private fun parseLoginCsv(text: String): List<LoginEntry> {
    val rows = readCsvRows(if (text.isNotEmpty() && text[0].code == 0xFEFF) text.substring(1) else text)
    if (rows.isEmpty()) return emptyList()

    val header = rows[0]
    val columns = mutableMapOf<String, Int>()
    header.forEachIndexed { index, cell ->
        val field = CSV_COLUMN_FIELDS[cell.trim().lowercase()] ?: return@forEachIndexed
        if (field != "name" && field != "note" && !columns.containsKey(field)) {
            columns[field] = index
        }
    }

    val hasHeader = columns.containsKey("origin") &&
        columns.containsKey("username") &&
        columns.containsKey("password")
    if (!hasHeader) return parsePositional(rows)

    val originIdx = columns["origin"] ?: return emptyList()
    val usernameIdx = columns["username"]
    val passwordIdx = columns["password"]
    val httpRealmIdx = columns["httpRealm"]
    val formActionIdx = columns["formActionOrigin"]

    val logins = mutableListOf<LoginEntry>()
    for (rowIndex in 1 until rows.size) {
        val row = rows[rowIndex]
        if (passwordIdx == null || passwordIdx >= row.size) continue
        val origin = row[originIdx].trim()
        val password = row[passwordIdx]
        if (origin.isEmpty() || password.isEmpty()) continue
        val httpRealm = httpRealmIdx?.let { row.getOrNull(it)?.takeIf(String::isNotEmpty) }
        val formActionOrigin = formActionIdx?.let { row.getOrNull(it)?.takeIf(String::isNotEmpty) } ?: origin
        logins += LoginEntry(
            origin = origin,
            formActionOrigin = formActionOrigin,
            httpRealm = httpRealm,
            username = usernameIdx?.let { row.getOrNull(it) }.orEmpty(),
            password = password,
        )
    }
    return logins
}

/** Fallback for CSVs without a recognised header: guess columns from the data. */
private fun parsePositional(rows: List<List<String>>): List<LoginEntry> {
    var originIdx = -1
    outer@ for (row in rows) {
        for ((index, cell) in row.withIndex()) {
            if (cell.contains("://")) {
                originIdx = index
                break@outer
            }
        }
    }
    if (originIdx < 0) return emptyList()

    val width = rows.maxOf { it.size }
    val lastNonEmpty = IntArray(width)
    for (row in rows) {
        var last = -1
        for ((index, cell) in row.withIndex()) if (cell.isNotEmpty()) last = index
        if (last >= 0) lastNonEmpty[last]++
    }
    var passwordIdx = -1
    var best = 0
    lastNonEmpty.forEachIndexed { index, count ->
        if (index != originIdx && count > best) {
            best = count
            passwordIdx = index
        }
    }
    if (passwordIdx < 0) return emptyList()
    var usernameIdx = passwordIdx - 1
    if (usernameIdx == originIdx) usernameIdx = passwordIdx - 2

    val logins = mutableListOf<LoginEntry>()
    for (row in rows) {
        val origin = row[originIdx].trim()
        val password = row[passwordIdx]
        if (origin.isEmpty() || password.isEmpty()) continue
        logins += LoginEntry(
            origin = origin,
            formActionOrigin = origin,
            httpRealm = null,
            username = if (usernameIdx in row.indices) row[usernameIdx] else "",
            password = password,
        )
    }
    return logins
}

// ---------------------------------------------------------------------------
// RFC 4180 CSV reader.
// ---------------------------------------------------------------------------

private fun readCsvRows(text: String): List<List<String>> {
    val cursor = CsvCursor(text)
    val rows = mutableListOf<List<String>>()
    cursor.skipLineBreaks()
    while (!cursor.atEnd()) {
        val row = cursor.readRow()
        if (row.isNotEmpty()) rows.add(row)
    }
    return rows
}

private class CsvCursor(private val text: String) {
    private var i = 0
    private val n = text.length

    fun atEnd(): Boolean = i >= n

    fun skipLineBreaks() {
        while (i < n && (text[i] == '\r' || text[i] == '\n')) i++
    }

    fun readRow(): List<String> {
        val row = mutableListOf<String>()
        while (!atEnd() && !atLineBreak()) {
            row.add(readField())
            if (!atEnd() && text[i] == ',') {
                i++
                if (atEnd() || atLineBreak()) row.add("")
            }
        }
        skipLineBreaks()
        return if (row.size == 1 && row[0].isEmpty()) emptyList() else row
    }

    private fun atLineBreak(): Boolean = text[i] == '\r' || text[i] == '\n'

    private fun readField(): String {
        if (text[i] == '"') {
            i++
            val quoted = readQuoted()
            if (!atEnd() && text[i] != ',' && !atLineBreak()) {
                throw IllegalArgumentException("Value after closing quote")
            }
            return quoted
        }
        return readUnquoted()
    }

    private fun readQuoted(): String {
        val sb = StringBuilder()
        while (i < n) {
            if (text[i] == '"') {
                if (i + 1 < n && text[i + 1] == '"') {
                    sb.append('"')
                    i += 2
                    continue
                }
                i++
                return sb.toString()
            }
            sb.append(text[i])
            i++
        }
        throw IllegalArgumentException("Unterminated quoted value")
    }

    private fun readUnquoted(): String {
        val start = i
        while (i < n && text[i] != ',' && !atLineBreak()) i++
        return text.substring(start, i)
    }
}

@Composable
private fun NovaPasswordsImporter(
    loginsStorage: LoginsStorage,
    onFinished: (PasswordsImporterResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onFinished(PasswordsImporterResult.Canceled)
        } else {
            importing = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Cannot read file")
                        val logins = parseLoginCsv(bytes.toString(Charsets.UTF_8))
                        if (logins.isEmpty()) {
                            PasswordsImporterResult.Success(importCount = 0)
                        } else {
                            PasswordsImporterResult.Success(
                                importCount = loginsStorage.addMany(logins).count { it.isSuccess },
                            )
                        }
                    }.getOrElse { e ->
                        if (e is CancellationException) throw e
                        PasswordsImporterResult.Failure
                    }
                }
                onFinished(result)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            launcher.launch(arrayOf("*/*"))
        }
    }

    Column(
        modifier = Modifier
            .width(280.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (importing) {
                "Importing passwords..."
            } else {
                "Choose the CSV file that contains your passwords"
            },
        )
        CircularProgressIndicator()
    }
}

/** Column names recognised in passwords CSV exports (Firefox, Chrome, Quetta...). */
private val CSV_COLUMN_FIELDS = mapOf(
    "url" to "origin",
    "login_uri" to "origin",
    "website" to "origin",
    "username" to "username",
    "login_username" to "username",
    "password" to "password",
    "login_password" to "password",
    "httprealm" to "httpRealm",
    "formactionorigin" to "formActionOrigin",
    "name" to "name",
    "note" to "note",
)

private fun parseLoginCsv(text: String): List<LoginEntry> {
    val rows = readCsvRows(if (text.isNotEmpty() && text[0].code == 0xFEFF) text.substring(1) else text)
    if (rows.isEmpty()) return emptyList()

    val header = rows[0]
    val columns = mutableMapOf<String, Int>()
    header.forEachIndexed { index, cell ->
        val field = CSV_COLUMN_FIELDS[cell.trim().lowercase()] ?: return@forEachIndexed
        if (field != "name" && field != "note" && !columns.containsKey(field)) {
            columns[field] = index
        }
    }

    val hasHeader = columns.containsKey("origin") &&
        columns.containsKey("username") &&
        columns.containsKey("password")
    if (!hasHeader) return parsePositional(rows)

    val originIdx = columns["origin"] ?: return emptyList()
    val usernameIdx = columns["username"]
    val passwordIdx = columns["password"]
    val httpRealmIdx = columns["httpRealm"]
    val formActionIdx = columns["formActionOrigin"]

    val logins = mutableListOf<LoginEntry>()
    for (rowIndex in 1 until rows.size) {
        val row = rows[rowIndex]
        if (passwordIdx == null || passwordIdx >= row.size) continue
        val origin = row[originIdx].trim()
        val password = row[passwordIdx]
        if (origin.isEmpty() || password.isEmpty()) continue
        val httpRealm = httpRealmIdx?.let { row.getOrNull(it)?.takeIf(String::isNotEmpty) }
        val formActionOrigin = formActionIdx?.let { row.getOrNull(it)?.takeIf(String::isNotEmpty) } ?: origin
        logins += LoginEntry(
            origin = origin,
            formActionOrigin = formActionOrigin,
            httpRealm = httpRealm,
            username = usernameIdx?.let { row.getOrNull(it) }.orEmpty(),
            password = password,
        )
    }
    return logins
}

/** Fallback for CSVs without a recognised header: guess columns from the data. */
private fun parsePositional(rows: List<List<String>>): List<LoginEntry> {
    var originIdx = -1
    outer@ for (row in rows) {
        for ((index, cell) in row.withIndex()) {
            if (cell.contains("://")) {
                originIdx = index
                break@outer
            }
        }
    }
    if (originIdx < 0) return emptyList()

    val width = rows.maxOf { it.size }
    val lastNonEmpty = IntArray(width)
    for (row in rows) {
        var last = -1
        for ((index, cell) in row.withIndex()) if (cell.isNotEmpty()) last = index
        if (last >= 0) lastNonEmpty[last]++
    }
    var passwordIdx = -1
    var best = 0
    lastNonEmpty.forEachIndexed { index, count ->
        if (index != originIdx && count > best) {
            best = count
            passwordIdx = index
        }
    }
    if (passwordIdx < 0) return emptyList()
    var usernameIdx = passwordIdx - 1
    if (usernameIdx == originIdx) usernameIdx = passwordIdx - 2

    val logins = mutableListOf<LoginEntry>()
    for (row in rows) {
        val origin = row[originIdx].trim()
        val password = row[passwordIdx]
        if (origin.isEmpty() || password.isEmpty()) continue
        logins += LoginEntry(
            origin = origin,
            formActionOrigin = origin,
            httpRealm = null,
            username = if (usernameIdx in row.indices) row[usernameIdx] else "",
            password = password,
        )
    }
    return logins
}

// ---------------------------------------------------------------------------
// RFC 4180 CSV reader.
// ---------------------------------------------------------------------------

private fun readCsvRows(text: String): List<List<String>> {
    val cursor = CsvCursor(text)
    val rows = mutableListOf<List<String>>()
    cursor.skipLineBreaks()
    while (!cursor.atEnd()) {
        val row = cursor.readRow()
        if (row.isNotEmpty()) rows.add(row)
    }
    return rows
}

private class CsvCursor(private val text: String) {
    private var i = 0
    private val n = text.length

    fun atEnd(): Boolean = i >= n

    fun skipLineBreaks() {
        while (i < n && (text[i] == '\r' || text[i] == '\n')) i++
    }

    fun readRow(): List<String> {
        val row = mutableListOf<String>()
        while (!atEnd() && !atLineBreak()) {
            row.add(readField())
            if (!atEnd() && text[i] == ',') {
                i++
                if (atEnd() || atLineBreak()) row.add("")
            }
        }
        skipLineBreaks()
        return if (row.size == 1 && row[0].isEmpty()) emptyList() else row
    }

    private fun atLineBreak(): Boolean = text[i] == '\r' || text[i] == '\n'

    private fun readField(): String {
        if (text[i] == '"') {
            i++
            val quoted = readQuoted()
            if (!atEnd() && text[i] != ',' && !atLineBreak()) {
                throw IllegalArgumentException("Value after closing quote")
            }
            return quoted
        }
        return readUnquoted()
    }

    private fun readQuoted(): String {
        val sb = StringBuilder()
        while (i < n) {
            if (text[i] == '"') {
                if (i + 1 < n && text[i + 1] == '"') {
                    sb.append('"')
                    i += 2
                    continue
                }
                i++
                return sb.toString()
            }
            sb.append(text[i])
            i++
        }
        throw IllegalArgumentException("Unterminated quoted value")
    }

    private fun readUnquoted(): String {
        val start = i
        while (i < n && text[i] != ',' && !atLineBreak()) i++
        return text.substring(start, i)
    }
}
