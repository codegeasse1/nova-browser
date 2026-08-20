#!/usr/bin/env bash
# Nova Browser branding overlay — cosmetic only. The engine, GeckoView and every
# feature are byte-identical to upstream IceRaven (iceraven-2.46.0). This script
# re-labels the visible product and adds three Nova options (pause history,
# study mode, clear tabs on close). Runs in the iceraven repo root.
set -euo pipefail

NOVA_PRIMARY="#0B7E78"
NOVA_SLATE="#17343C"

echo ">> Nova branding: product name"
# Upstream's CI already replaced "Firefox" -> "Iceraven"; we turn that into "Nova"
find app/src -path "*/res/*/*.xml" -type f -exec sed -i 's/Iceraven/Nova/g' {} +

# Launcher label: "Nova Browser"
sed -i 's#<string name="app_name" translatable="false">Nova</string>#<string name="app_name" translatable="false">Nova Browser</string>#' app/src/forkRelease/res/values/static_strings.xml

# About-page credit line (all locales)
sed -i 's#produced by @forkmaintainers#produced by the Nova Browser project#' app/src/*/res/values*/*strings.xml

# "What's new" must point at our repo, not the upstream one
sed -i 's#https://github.com/fork-maintainers/iceraven-browser/releases#https://github.com/codegeasse1/nova-browser/releases#' \
  app/src/main/java/org/mozilla/fenix/settings/SupportUtils.kt

echo ">> Nova branding: app identity"
sed -i 's/applicationId "io.github.forkmaintainers"/applicationId "com.nova.browser"/' app/build.gradle
sed -i 's/io.github.forkmaintainers.iceraven.sharedID/com.nova.browser.sharedID/g' app/build.gradle
sed -i 's/deepLinkSchemeValue = "iceraven-debug"/deepLinkSchemeValue = "nova-debug"/' app/build.gradle
sed -i 's/deepLinkSchemeValue = "iceraven"/deepLinkSchemeValue = "nova"/' app/build.gradle
sed -i 's/applicationIdSuffix "\.iceraven"/applicationIdSuffix ""/' app/build.gradle

echo ">> Nova branding: teal accent palette"
cat > app/src/forkRelease/res/values/colors.xml <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0B7E78</color>
    <color name="novaViolet0">#EAF6F5</color>
    <color name="novaViolet5">#D6EFEC</color>
    <color name="novaViolet10">#BFE5E1</color>
    <color name="novaViolet15">#A8DCD7</color>
    <color name="novaViolet20">#8FCFC9</color>
    <color name="novaViolet25">#76C2BB</color>
    <color name="novaViolet30">#5FB7B0</color>
    <color name="novaViolet40">#2E9E95</color>
    <color name="novaViolet50">#178D84</color>
    <color name="novaViolet60">#0B7E78</color>
    <color name="novaViolet70">#0B7E78</color>
    <color name="novaViolet80">#08564F</color>
    <color name="novaViolet90">#063B37</color>
    <color name="novaViolet100">#042622</color>
    <color name="novaVioletDesaturated10">#DCE7E6</color>
    <color name="novaVioletDesaturated30">#AFC4C1</color>
    <color name="novaVioletDesaturated50">#84A09D</color>
    <color name="novaVioletDesaturated70">#5C7572</color>
    <color name="novaVioletDesaturated90">#2E3E3C</color>
    <color name="novaVioletDesaturated90A70">#4D2E3E3C</color>
</resources>
XML

echo ">> Nova branding: launcher icons (teal + white N)"
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  mip=app/src/forkRelease/res/mipmap-$d
  cp nova-icons/mipmap-$d/ic_launcher.png $mip/ic_launcher.png
  cp nova-icons/mipmap-$d/ic_launcher.png $mip/ic_launcher_round.png
  cp nova-icons/mipmap-$d/ic_launcher_private.png $mip/ic_launcher_private.png
  cp nova-icons/mipmap-$d/ic_launcher_private.png $mip/ic_launcher_private_round.png
done
cp nova-icons/drawable-hdpi/fenix_search_widget.png app/src/forkRelease/res/drawable-hdpi/fenix_search_widget.png

echo ">> Nova branding: wordmarks"
cp nova-icons/drawable/ic_wordmark_logo.png app/src/forkRelease/res/drawable/ic_wordmark_logo.png
cp nova-icons/drawable/ic_wordmark_text_normal.png app/src/forkRelease/res/drawable/ic_wordmark_text_normal.png
cp nova-icons/drawable/ic_wordmark_text_private.png app/src/forkRelease/res/drawable/ic_wordmark_text_private.png
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  dw=app/src/forkRelease/res/drawable-$d
  cp nova-icons/drawable-$d/ic_logo_wordmark_normal.png $dw/ic_logo_wordmark_normal.png
  cp nova-icons/drawable-$d/ic_logo_wordmark_private.png $dw/ic_logo_wordmark_private.png
done

echo ">> Nova branding: vector layers"
cat > app/src/forkRelease/res/drawable/ic_launcher_foreground.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M33,75 L33,36 L75,72 L75,36"
      android:strokeColor="#FFFFFF"
      android:strokeWidth="16"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
XML
cp app/src/forkRelease/res/drawable/ic_launcher_foreground.xml app/src/forkRelease/res/drawable-v24/ic_launcher_foreground.xml

cat > app/src/forkRelease/res/drawable/ic_launcher_monochrome.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M33,75 L33,36 L75,72 L75,36"
      android:strokeColor="#FF000000"
      android:strokeWidth="16"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
XML

cat > app/src/main/res/drawable/ic_launcher_private_background.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M0,0 L108,0 L108,108 L0,108 Z"
      android:fillColor="#17343C"/>
</vector>
XML
cat > app/src/main/res/drawable/ic_launcher_private_foreground.xml <<'XML'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
  <path
      android:pathData="M33,75 L33,36 L75,72 L75,36"
      android:strokeColor="#FFFFFF"
      android:strokeWidth="16"
      android:strokeLineCap="round"
      android:strokeLineJoin="round"/>
</vector>
XML

echo ">> Nova branding: splash screen"
cat > app/src/forkRelease/res/drawable/animated_splash_screen.xml <<'XML'
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="432dp"
            android:height="432dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
            <path
                android:pathData="M0,0 L108,0 L108,108 L0,108 Z"
                android:fillColor="#0B7E78"/>
            <group
                android:name="nova_n">
                <path
                    android:pathData="M38,80 L38,40 L74,72 L74,40"
                    android:strokeColor="#FFFFFF"
                    android:strokeWidth="10"
                    android:strokeLineCap="round"
                    android:strokeLineJoin="round"/>
            </group>
        </vector>
    </aapt:attr>
    <target android:name="nova_n">
        <aapt:attr name="android:animation">
            <set android:interpolator="@android:interpolator/decelerate_cubic"
                android:ordering="together"
                android:repeatMode="reverse"
                android:repeatCount="infinite">
                <objectAnimator
                    android:propertyName="scaleX"
                    android:duration="500"
                    android:valueFrom="0.75"
                    android:valueTo="1"/>
                <objectAnimator
                    android:propertyName="scaleY"
                    android:duration="500"
                    android:valueFrom="0.75"
                    android:valueTo="1"/>
                <objectAnimator
                    android:propertyName="alpha"
                    android:duration="500"
                    android:valueFrom="0.35"
                    android:valueTo="1"/>
            </set>
        </aapt:attr>
    </target>
</animated-vector>
XML

echo ">> Nova branding: pause history / study mode / clear tabs on close"
python3 <<'PY'
import io

def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()

def write(path, content):
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(content)

def patch(path, old, new):
    s = read(path)
    if old not in s:
        raise SystemExit("PATCH FAILED in %s:\npattern not found:\n%s" % (path, old[:200]))
    write(path, s.replace(old, new, 1))

BASE = "app/src/main/java/org/mozilla/fenix/"

# --- New file: study list storage -------------------------------------------
write(BASE + "components/NovaStudyStorage.kt", r'''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A private "study list" of visited sites. When Study mode is on, visited pages
 * are recorded here instead of the normal browser history, and the History screen
 * shows this list instead. Everything is kept in a small SharedPreferences file.
 */
class NovaStudyStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordVisit(url: String, title: String?) {
        val now = System.currentTimeMillis()
        val newList = JSONArray().apply {
            readAll().forEach {
                if (it.optString("url") != url) put(it)
            }
            put(
                JSONObject()
                    .put("url", url)
                    .put("title", title ?: "")
                    .put("visitedAt", now),
            )
        }
        prefs.edit().putString(KEY_ITEMS, newList.toString()).apply()
    }

    fun recordTitle(url: String, title: String) {
        val items = readAll()
        var changed = false
        val newList = JSONArray().apply {
            items.forEach {
                if (it.optString("url") == url) {
                    changed = true
                    put(
                        JSONObject()
                            .put("url", url)
                            .put("title", title)
                            .put("visitedAt", it.optLong("visitedAt")),
                    )
                } else {
                    put(it)
                }
            }
        }
        if (changed) {
            prefs.edit().putString(KEY_ITEMS, newList.toString()).apply()
        }
    }

    fun readAll(): List<JSONObject> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun delete(url: String) {
        val newList = JSONArray().apply {
            readAll().forEach {
                if (it.optString("url") != url) put(it)
            }
        }
        prefs.edit().putString(KEY_ITEMS, newList.toString()).apply()
    }

    fun deleteVisitsBetween(startTime: Long, endTime: Long) {
        val newList = JSONArray().apply {
            readAll().forEach {
                val visitedAt = it.optLong("visitedAt")
                if (visitedAt < startTime || visitedAt > endTime) put(it)
            }
        }
        prefs.edit().putString(KEY_ITEMS, newList.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    companion object {
        const val PREFS_NAME = "nova_study"
        private const val KEY_ITEMS = "study_items"
    }
}
''')

# --- New file: debug logging that survives process death ----------------------
write(BASE + "components/NovaDebugLog.kt", r'''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes Nova Browser diagnostics to logcat (tag "NovaDebug") and to a plain-text
 * log file at <external files dir>/nova-debug.log so the user can inspect what the
 * app actually did without adb. Every write goes to disk asynchronously so it is
 * safe to call from lifecycle callbacks.
 */
object NovaDebugLog {
    fun log(context: Context, message: String) {
        Log.i("NovaDebug", message)
        try {
            val file = context.getExternalFilesDir(null)?.resolve("nova-debug.log") ?: return
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    file.appendText(java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()) + " " + message + "\n")
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    fun clear(context: Context) {
        try {
            context.getExternalFilesDir(null)?.resolve("nova-debug.log")?.delete()
        } catch (_: Exception) {
        }
    }
}
''')

# --- New file: shared close-cleanup (tabs + browsing data + session snapshot) --
write(BASE + "components/NovaCloseCleanup.kt", r'''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.DataStorage
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.DeleteDataUseCases
import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.Stores

/**
 * Applies the "the app was closed" cleanup: if "Clear tabs on close" is on, removes
 * every tab AND deletes the persisted session snapshot so the tabs cannot come back
 * on the next launch; if "Delete browsing data on quit" is on, runs IceRaven's own
 * delete-on-quit controller (the same one the Quit menu item uses).
 *
 * A deliberately small shared helper so the exact same cleanup runs whether the close
 * was detected while the process was alive (Application.onTaskRemoved) or at the next
 * launch (task id mismatch, e.g. after the process was killed on swipe).
 */
object NovaCloseCleanup {
    fun run(context: Context, components: Components) {
        val settings = components.settings
        try {
            if (settings.novaClearTabsOnExit) {
                NovaDebugLog.log(context, "NovaCloseCleanup: clearing all tabs")
                components.useCases.tabsUseCases.removeAllTabs.invoke(false)
                // The session snapshot on disk would otherwise restore the tabs on the
                // next launch (especially when the process is killed on swipe before
                // the empty state is saved), so delete it explicitly.
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        components.core.sessionStorage.clear()
                        NovaDebugLog.log(context, "NovaCloseCleanup: session snapshot deleted")
                    } catch (_: Exception) {
                    }
                }
            }
            if (settings.shouldDeleteBrowsingDataOnQuit) {
                // The same controller (and settings) used by the Quit menu item, so the
                // user's "Delete browsing data on quit" choices are honoured here too.
                val controller = DefaultDeleteBrowsingDataController(
                    deleteDataUseCases = DeleteDataUseCases(
                        removeAllTabs = components.useCases.tabsUseCases.removeAllTabs,
                        removeAllDownloads = components.useCases.downloadUseCases.removeAllDownloads,
                    ),
                    dataStorage = DataStorage(
                        history = components.core.historyStorage,
                        permissions = components.core.permissionStorage,
                    ),
                    stores = Stores(
                        appStore = components.appStore,
                        browserStore = components.core.store,
                    ),
                    engine = components.core.engine,
                    settings = settings,
                )
                NovaDebugLog.log(context, "NovaCloseCleanup: delete browsing data on quit running")
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        controller.clearBrowsingDataOnQuit { }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        // Visible confirmation for every cleanup path (delayed background check,
        // task-removal consume at next launch, and restore-time drop).
        try {
            val msg = when {
                settings.novaClearTabsOnExit && settings.shouldDeleteBrowsingDataOnQuit ->
                    "Nova cleared your tabs and browsing data on close."
                settings.novaClearTabsOnExit -> "Nova cleared your tabs on close."
                else -> "Nova cleared your browsing data on close."
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
    }
}
''')

# --- New file: history hook that understands the Nova options ----------------
write(BASE + "components/NovaHistoryTrackingDelegate.kt", r'''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.content.Context
import mozilla.components.concept.engine.history.HistoryTrackingDelegate
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.feature.session.HistoryDelegate
import org.mozilla.fenix.ext.components

/**
 * The browser's history hook, extended with two Nova options:
 *
 *  - **Pause history**: stops recording any browsing history while enabled.
 *  - **Study mode**: records visited sites into a private "study list" instead
 *    of the normal browser history.
 *
 * When neither option is active this behaves exactly like the stock
 * [HistoryDelegate], so the built-in history features are untouched.
 */
class NovaHistoryTrackingDelegate(
    private val context: Context,
    historyStorage: Lazy<HistoryStorage>,
) : HistoryTrackingDelegate {

    private val delegate = HistoryDelegate(historyStorage)

    private val settings get() = context.components.settings

    private val studyStorage by lazy { NovaStudyStorage(context.applicationContext) }

    override suspend fun onVisited(uri: String, visit: PageVisit) {
        NovaDebugLog.log(context, "visited ${uri.take(80)} study=${settings.novaStudyMode} pause=${settings.novaPauseHistory}")
        if (settings.novaStudyMode) {
            settings.novaVisitsStudy = settings.novaVisitsStudy + 1
            studyStorage.recordVisit(uri, title = null)
            return
        }
        if (settings.novaPauseHistory) {
            settings.novaVisitsPaused = settings.novaVisitsPaused + 1
            return
        }
        settings.novaVisitsForwarded = settings.novaVisitsForwarded + 1
        delegate.onVisited(uri, visit)
    }

    override suspend fun onTitleChanged(uri: String, title: String) {
        NovaDebugLog.log(context, "title ${uri.take(80)} -> ${title.take(40)}")
        if (settings.novaStudyMode) {
            studyStorage.recordTitle(uri, title)
            return
        }
        if (settings.novaPauseHistory) return
        delegate.onTitleChanged(uri, title)
    }

    override suspend fun onPreviewImageChange(uri: String, previewImageUrl: String) {
        if (settings.novaStudyMode) return
        if (settings.novaPauseHistory) return
        delegate.onPreviewImageChange(uri, previewImageUrl)
    }

    override suspend fun getVisited(uris: List<String>): List<Boolean> =
        delegate.getVisited(uris)

    override suspend fun getVisited(): List<String> =
        delegate.getVisited()

    override fun shouldStoreUri(uri: String): Boolean = delegate.shouldStoreUri(uri)
}
''')

# --- New file: study list shown in the History screen ------------------------
write(BASE + "components/history/StudyPagedHistoryProvider.kt", r'''/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.history

import android.content.Context
import mozilla.components.support.ktx.kotlin.tryGetHostFromUrl
import org.mozilla.fenix.components.NovaStudyStorage

/**
 * A paginated list of "study" items. When Study mode is on, the History screen
 * uses this provider instead of the normal one, so it shows the study list.
 */
class StudyPagedHistoryProvider(
    context: Context,
) : PagedHistoryProvider {

    private val storage = NovaStudyStorage(context.applicationContext)

    override suspend fun getHistory(offset: Int, numberOfItems: Int): List<HistoryDB> {
        return storage.readAll()
            .sortedByDescending { it.optLong("visitedAt") }
            .drop(offset)
            .take(numberOfItems)
            .map { item ->
                HistoryDB.Regular(
                    title = item.optString("title")
                        .takeIf(String::isNotEmpty)
                        ?: item.optString("url").tryGetHostFromUrl(),
                    url = item.optString("url"),
                    visitedAt = item.optLong("visitedAt"),
                )
            }
    }
}
''')

# --- Core.kt: use the Nova history hook for the engine -----------------------
patch(
    BASE + "components/Core.kt",
    "            historyTrackingDelegate = HistoryDelegate(lazyHistoryStorage),",
    "            historyTrackingDelegate = NovaHistoryTrackingDelegate(context, lazyHistoryStorage),",
)
patch(
    BASE + "components/Core.kt",
    "import mozilla.components.feature.session.HistoryDelegate\n",
    "",
)

# --- HistoryFragment.kt: use the study provider in study mode ----------------
patch(
    BASE + "library/history/HistoryFragment.kt",
    "import org.mozilla.fenix.components.history.DefaultPagedHistoryProvider",
    "import org.mozilla.fenix.components.NovaStudyStorage\n" +
    "import org.mozilla.fenix.components.history.DefaultPagedHistoryProvider\n" +
    "import org.mozilla.fenix.components.history.PagedHistoryProvider\n" +
    "import org.mozilla.fenix.components.history.StudyPagedHistoryProvider",
)
patch(
    BASE + "library/history/HistoryFragment.kt",
    "    private lateinit var historyProvider: DefaultPagedHistoryProvider",
    "    private lateinit var historyProvider: PagedHistoryProvider",
)
patch(
    BASE + "library/history/HistoryFragment.kt",
    "        historyProvider = DefaultPagedHistoryProvider(requireComponents.core.historyStorage)",
    "        historyProvider = if (requireContext().components.settings.novaStudyMode) {\n" +
    "            StudyPagedHistoryProvider(requireContext())\n" +
    "        } else {\n" +
    "            DefaultPagedHistoryProvider(requireComponents.core.historyStorage)\n" +
    "        }",
)
patch(
    BASE + "library/history/HistoryFragment.kt",
    "                    historyProvider.deleteMetadataSearchGroup(item)",
    "                    (historyProvider as? DefaultPagedHistoryProvider)?.deleteMetadataSearchGroup(item)",
)
patch(
    BASE + "library/history/HistoryFragment.kt",
    "                is History.Regular -> historyStorage.deleteVisitsFor(item.url)",
    "                is History.Regular -> {\n" +
    "                    historyStorage.deleteVisitsFor(item.url)\n" +
    "                    if (requireContext().components.settings.novaStudyMode) {\n" +
    "                        NovaStudyStorage(requireContext()).delete(item.url)\n" +
    "                    }\n" +
    "                }",
)
patch(
    BASE + "library/history/HistoryFragment.kt",
    "            if (selectedTimeFrame == null) {\n" +
    "                historyStorage.deleteEverything()\n" +
    "            } else {\n" +
    "                val longRange = selectedTimeFrame.toLongRange()\n" +
    "                historyStorage.deleteVisitsBetween(\n" +
    "                    startTime = longRange.first,\n" +
    "                    endTime = longRange.last,\n" +
    "                )\n" +
    "            }\n" +
    "            browserStore.dispatch(RecentlyClosedAction.RemoveAllClosedTabAction)",
    "            if (selectedTimeFrame == null) {\n" +
    "                historyStorage.deleteEverything()\n" +
    "            } else {\n" +
    "                val longRange = selectedTimeFrame.toLongRange()\n" +
    "                historyStorage.deleteVisitsBetween(\n" +
    "                    startTime = longRange.first,\n" +
    "                    endTime = longRange.last,\n" +
    "                )\n" +
    "            }\n" +
    "            if (requireContext().components.settings.novaStudyMode) {\n" +
    "                val studyStorage = NovaStudyStorage(requireContext())\n" +
    "                if (selectedTimeFrame == null) {\n" +
    "                    studyStorage.clear()\n" +
    "                } else {\n" +
    "                    val longRange = selectedTimeFrame.toLongRange()\n" +
    "                    studyStorage.deleteVisitsBetween(longRange.first, longRange.last)\n" +
    "                }\n" +
    "            }\n" +
    "            browserStore.dispatch(RecentlyClosedAction.RemoveAllClosedTabAction)",
)

# --- Settings.kt: new Nova options -------------------------------------------
patch(
    BASE + "utils/Settings.kt",
    """    var shouldDeleteBrowsingDataOnQuit by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_delete_browsing_data_on_quit),
        default = false,
    )""",
    """    var shouldDeleteBrowsingDataOnQuit by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_delete_browsing_data_on_quit),
        default = false,
    )

    // Nova Browser custom options
    var novaPauseHistory by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_pause_history),
        default = false,
    )

    var novaStudyMode by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_study_mode),
        default = false,
    )

    var novaClearTabsOnExit by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_clear_tabs_on_exit),
        default = false,
    )

    var novaClearTabsOnExitArmed by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_clear_tabs_on_exit_armed),
        default = false,
    )

    var novaLastTaskId by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_last_task_id),
        default = 0,
    )

    // Nova: diagnostic counters so the state can be read on screen (Settings -> Nova).
    var novaVisitsStudy by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_visits_study),
        default = 0,
    )

    var novaVisitsPaused by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_visits_paused),
        default = 0,
    )

    var novaVisitsForwarded by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_visits_forwarded),
        default = 0,
    )""",
)

# --- preference_keys.xml ------------------------------------------------------
patch(
    "app/src/main/res/values/preference_keys.xml",
    "</resources>",
    """    <string name="pref_key_nova_pause_history" translatable="false">pref_key_nova_pause_history</string>
    <string name="pref_key_nova_study_mode" translatable="false">pref_key_nova_study_mode</string>
    <string name="pref_key_nova_clear_tabs_on_exit" translatable="false">pref_key_nova_clear_tabs_on_exit</string>
    <string name="pref_key_nova_clear_tabs_on_exit_armed" translatable="false">pref_key_nova_clear_tabs_on_exit_armed</string>
    <string name="pref_key_nova_last_task_id" translatable="false">pref_key_nova_last_task_id</string>
    <string name="pref_key_nova_visits_study" translatable="false">pref_key_nova_visits_study</string>
    <string name="pref_key_nova_visits_paused" translatable="false">pref_key_nova_visits_paused</string>
    <string name="pref_key_nova_visits_forwarded" translatable="false">pref_key_nova_visits_forwarded</string>
    <string name="pref_key_nova_status" translatable="false">pref_key_nova_status</string>
</resources>""",
)

# --- strings.xml --------------------------------------------------------------
patch(
    "app/src/main/res/values/strings.xml",
    "</resources>",
    """    <string name="preferences_category_nova">Nova Browser</string>
    <string name="pref_nova_pause_history_title">Pause history</string>
    <string name="pref_nova_pause_history_summary">Temporarily stop saving your browsing history.</string>
    <string name="pref_nova_study_mode_title">Study mode</string>
    <string name="pref_nova_study_mode_summary">Keep a study list of the sites you visit instead of normal history. The History screen shows this study list.</string>
    <string name="pref_nova_clear_tabs_title">Clear tabs on close</string>
    <string name="pref_nova_clear_tabs_summary">Close all tabs when you close Nova Browser: swipe it from the app switcher, quit it, or leave it closed in the background.</string>
    <string name="pref_nova_status_title">Nova status</string>
</resources>""",
)

# --- preferences.xml: new Nova Browser category -------------------------------
patch(
    "app/src/main/res/xml/preferences.xml",
    """    <androidx.preference.PreferenceCategory
        android:title="@string/preferences_category_about\"""",
    """    <androidx.preference.PreferenceCategory
        android:title="@string/preferences_category_nova"
        app:iconSpaceReserved="false"
        android:layout="@layout/preference_category_no_icon_style">
        <androidx.preference.SwitchPreferenceCompat
            android:key="@string/pref_key_nova_pause_history"
            android:title="@string/pref_nova_pause_history_title"
            android:summary="@string/pref_nova_pause_history_summary"
            app:iconSpaceReserved="false"
            android:defaultValue="false" />
        <androidx.preference.SwitchPreferenceCompat
            android:key="@string/pref_key_nova_study_mode"
            android:title="@string/pref_nova_study_mode_title"
            android:summary="@string/pref_nova_study_mode_summary"
            app:iconSpaceReserved="false"
            android:defaultValue="false" />
        <androidx.preference.SwitchPreferenceCompat
            android:key="@string/pref_key_nova_clear_tabs_on_exit"
            android:title="@string/pref_nova_clear_tabs_title"
            android:summary="@string/pref_nova_clear_tabs_summary"
            app:iconSpaceReserved="false"
            android:defaultValue="false" />
        <androidx.preference.Preference
            android:key="@string/pref_key_nova_status"
            android:title="@string/pref_nova_status_title"
            android:selectable="false"
            app:iconSpaceReserved="false" />
    </androidx.preference.PreferenceCategory>

    <androidx.preference.PreferenceCategory
        android:title="@string/preferences_category_about\"""",
)

# --- SettingsFragment.kt: Nova toggle feedback + on-screen status readout ------
patch(
    BASE + "settings/SettingsFragment.kt",
    "        setPreferencesFromResource(R.xml.preferences, rootKey)",
    """        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Nova: confirm each Nova switch with a toast, so it is obvious the
        // settings are wired to real code.
        fun novaSwitchFeedback(keyRes: Int, onMsg: String, offMsg: String) {
            findPreference<SwitchPreferenceCompat>(getPreferenceKey(keyRes))?.setOnPreferenceChangeListener { _, newValue ->
                Toast.makeText(
                    requireContext(),
                    if (newValue == true) onMsg else offMsg,
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }
        novaSwitchFeedback(
            R.string.pref_key_nova_pause_history,
            "Pause history: ON - new visits are not saved.",
            "Pause history: OFF - history is saved again.",
        )
        novaSwitchFeedback(
            R.string.pref_key_nova_study_mode,
            "Study mode: ON - new visits go to your private study list.",
            "Study mode: OFF - normal history again.",
        )
        novaSwitchFeedback(
            R.string.pref_key_nova_clear_tabs_on_exit,
            "Clear tabs on close: ON - closing Nova Browser clears its tabs.",
            "Clear tabs on close: OFF - tabs are kept when you close Nova Browser.",
        )""",
)
patch(
    BASE + "settings/SettingsFragment.kt",
    "        // Consider finish of `onResume` to be the point at which we consider this fragment as 'created'.\n        creatingFragment = false",
    """        // Consider finish of `onResume` to be the point at which we consider this fragment as 'created'.
        creatingFragment = false

        // Nova: refresh the on-screen status readout in the "Nova Browser" category.
        runCatching {
            val s = requireComponents.settings
            val curTask = (activity as? HomeActivity)?.taskId ?: -1
            findPreference<Preference>(getPreferenceKey(R.string.pref_key_nova_status))?.summary =
                "Nova ${org.mozilla.fenix.BuildConfig.VERSION_NAME} | pause:${s.novaPauseHistory} | " +
                    "study:${s.novaStudyMode} | cleartabs:${s.novaClearTabsOnExit} | " +
                    "armed:${s.novaClearTabsOnExitArmed} | lastTask:${s.novaLastTaskId} | now:$curTask | " +
                    "studyList:${s.novaVisitsStudy} | paused:${s.novaVisitsPaused} | saved:${s.novaVisitsForwarded}"
        }""",
)

# --- HomeActivity.kt: clear tabs + browsing data when the app is closed -------
patch(
    BASE + "HomeActivity.kt",
    "import android.app.assist.AssistContent",
    "import android.app.ActivityManager\nimport android.app.assist.AssistContent",
)
patch(
    BASE + "HomeActivity.kt",
    "import kotlinx.coroutines.Dispatchers",
    "import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.Dispatchers",
)
patch(
    BASE + "HomeActivity.kt",
    "import kotlinx.coroutines.Job\n",
    "import kotlinx.coroutines.Job\nimport kotlinx.coroutines.SupervisorJob\n",
)
patch(
    BASE + "HomeActivity.kt",
    "import org.mozilla.fenix.settings.SupportUtils",
    "import org.mozilla.fenix.components.NovaCloseCleanup\n" +
    "import org.mozilla.fenix.components.NovaDebugLog\n" +
    "import org.mozilla.fenix.settings.SupportUtils\n" +
    "import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController\n" +
    "import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.DataStorage\n" +
    "import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.DeleteDataUseCases\n" +
    "import org.mozilla.fenix.settings.deletebrowsingdata.DefaultDeleteBrowsingDataController.Stores",
)
patch(
    BASE + "HomeActivity.kt",
    "        checkAndExitPiP()",
    "        checkAndExitPiP()\n        consumeNovaClearTabsOnExit()",
)
patch(
    BASE + "HomeActivity.kt",
    "        super.onStop()",
    "        super.onStop()\n        armNovaClearOnExitCheck()\n        scheduleNovaClearOnCloseCheck()",
)
patch(
    BASE + "HomeActivity.kt",
    "    final override fun onStart() {",
    """    /**
     * Persists the "close armed" flag plus the current task id every time the app
     * leaves the foreground. A relaunch compares its new task id against this
     * saved one: same task -> the app was only backgrounded (possibly killed in
     * the background); different task -> the app was really closed (swiped from
     * the app switcher, or Quit) and must start clean.
     */
    private var novaScheduledCheck: Job? = null

    private fun armNovaClearOnExitCheck() {
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.novaClearTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        settings.novaLastTaskId = taskId
        settings.novaClearTabsOnExitArmed = true
        NovaDebugLog.log(this, "arm: armed=true task=$taskId")
    }

    private fun scheduleNovaClearOnCloseCheck() {
        // Called on every stop, AFTER the app has fully backgrounded. If the app is
        // still in the background when the grace period elapses, the user has really
        // closed it (pressed Home and left it, swiped it from the app switcher, or
        // pressed "Clear all"), so the cleanup runs right away. Returning to the app
        // within the grace period cancels the timer and nothing is cleared.
        // Not for the external-app browser activity (custom tabs), which is a
        // separate task and must not clear the user's tabs when it is dismissed.
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.novaClearTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        if (!settings.novaClearTabsOnExitArmed) return
        val appContext = applicationContext
        novaScheduledCheck?.cancel()
        novaScheduledCheck = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            delay(10_000L)
            val s = appContext.components.settings
            if (!s.novaClearTabsOnExitArmed) return@launch
            s.novaClearTabsOnExitArmed = false
            NovaDebugLog.log(appContext, "still backgrounded after 10s - treating as closed, cleaning up")
            NovaCloseCleanup.run(appContext, appContext.components)
        }
    }

    private fun consumeNovaClearTabsOnExit() {
        // Runs at the start of every HomeActivity launch. Decides whether the tabs
        // restored at startup were supposed to come back:
        //  - same task id as when the app last stopped  -> the app was only
        //    backgrounded, keep the tabs;
        //  - different task id                          -> the app was really closed
        //    (swiped from the app switcher, Quit menu, force stop) and must start
        //    fresh. If the session restore has not run yet in this process, a flag
        //    is set so the snapshot is deleted right before the restore (the same
        //    point where the stock "Close tabs after X" setting drops stale tabs).
        // Not for the external-app browser activity (custom tabs), which opens in
        // its own task and must never clear the user's normal tabs.
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.novaClearTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        if (!settings.novaClearTabsOnExitArmed) return
        val lastTaskId = settings.novaLastTaskId
        if (lastTaskId == 0 || taskId == lastTaskId) {
            FenixApplication.novaPendingCleanStart = false
            settings.novaClearTabsOnExitArmed = false
            NovaDebugLog.log(this, "consume: same task ($taskId) - kept tabs")
            return
        }
        FenixApplication.novaPendingCleanStart = true
        if (!FenixApplication.initialSessionRestoreCompleted) {
            // The restore runs on the main thread shortly after this onCreate, so
            // wait for it before running the full cleanup (the snapshot deletion is
            // handled by restoreBrowserState).
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                var waited = 0L
                while (!FenixApplication.initialSessionRestoreCompleted && waited < 15000) {
                    delay(100)
                    waited += 100
                }
                if (FenixApplication.initialSessionRestoreCompleted) {
                    consumeNovaClearTabsOnExitInternal()
                }
            }
            return
        }
        consumeNovaClearTabsOnExitInternal()
    }

    private fun consumeNovaClearTabsOnExitInternal() {
        val settings = components.settings
        settings.novaClearTabsOnExitArmed = false
        FenixApplication.novaPendingCleanStart = false
        NovaDebugLog.log(this, "consume: new task - clearing tabs")
        NovaCloseCleanup.run(this, components)
    }

    final override fun onStart() {""",
)
patch(
    BASE + "HomeActivity.kt",
    "        super.onStart()",
    "        super.onStart()\n        novaScheduledCheck?.cancel()",
)

patch(
    BASE + "FenixApplication.kt",
    "        components.useCases.tabsUseCases.restore(sessionStorage, components.settings.getTabTimeout())",
    """        // Nova: if HomeActivity armed a "clear tabs on close" and saw a fresh task
        // at this launch, delete the saved session snapshot BEFORE it is restored so
        // the tabs cannot come back. This is the same point where the stock "Close
        // tabs after X" setting drops stale tabs. (If the restore already ran before
        // HomeActivity.onCreate on some Android versions, the flag stays set and the
        // HomeActivity cleanup handles it instead.)
        if (novaPendingCleanStart) {
            novaPendingCleanStart = false
            org.mozilla.fenix.components.NovaDebugLog.log(applicationContext, "restore: dropping saved tabs (pending clean start)")
            sessionStorage.clear()
        }
        components.useCases.tabsUseCases.restore(sessionStorage, components.settings.getTabTimeout())

        // Nova: HomeActivity decides whether this relaunch should start fresh by
        // comparing the current task id against the one saved when the app last
        // stopped (see consumeNovaClearTabsOnExit in HomeActivity.kt). We only mark
        // that the initial restore has finished here.
        initialSessionRestoreCompleted = true""",
)
patch(
    BASE + "FenixApplication.kt",
    "open class FenixApplication : Application(), Provider, ThemeProvider {",
    """open class FenixApplication : Application(), Provider, ThemeProvider {
    companion object {
        /**
         * Becomes true once the initial session restore has finished, so an in-process
         * relaunch knows the "clear tabs on close" armed flag can be consumed safely.
         */
        var initialSessionRestoreCompleted = false
            private set

        /**
         * Set by HomeActivity when the "Clear tabs on close" armed flag is consumed at
         * launch and the session restore has not run yet: the saved session snapshot is
         * then deleted right before the restore (the same point where the stock
         * "Close tabs after X" setting drops stale tabs).
         */
        var novaPendingCleanStart = false
    }
""",
)

print("All Nova source patches applied.")
PY

echo ">> Nova branding: done"
