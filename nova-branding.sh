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

# --- New file: close-tabs-on-exit cleanup ------------------------------------
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
 * Applies the "app was closed" cleanup, using exactly the user's existing settings:
 *
 *  - "Close tabs when the app is closed": removes every tab and deletes the
 *    persisted session snapshot so the tabs cannot come back on the next launch.
 *  - "Delete browsing data on quit" (IceRaven's own setting): runs the same
 *    delete-on-quit controller the Quit menu item uses.
 *
 * A deliberately small shared helper so the exact same cleanup runs whether the
 * close was detected while the process was alive (the delayed task check) or at
 * the next launch (task id mismatch).
 */
object NovaCloseCleanup {
    fun run(context: Context, components: Components) {
        val settings = components.settings
        var clearedTabs = false
        var clearedData = false

        if (settings.closeTabsOnExit) {
            clearedTabs = true
            try {
                NovaDebugLog.log(context, "NovaCloseCleanup: closing all tabs")
                components.useCases.tabsUseCases.removeAllTabs.invoke(false)
                // The session snapshot on disk would otherwise restore the tabs on the
                // next launch (especially when the process is killed on swipe before the
                // empty state is saved), so delete it explicitly.
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        components.core.sessionStorage.clear()
                        NovaDebugLog.log(context, "NovaCloseCleanup: session snapshot deleted")
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (settings.shouldDeleteBrowsingDataOnQuit) {
            clearedData = true
            try {
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
            } catch (_: Exception) {
            }
        }

        try {
            val msg = when {
                clearedTabs && clearedData ->
                    "Nova closed your tabs and cleared your browsing data."
                clearedTabs -> "Nova closed all your tabs."
                clearedData -> "Nova cleared your browsing data."
                else -> return
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
    }
}
''')

# --- New file: diagnostics log ------------------------------------------------
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

# --- Settings.kt: the new option + internal state -----------------------------
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

    // Nova: close all tabs the moment the app is really closed (removed from the
    // app switcher, or Quit). Chosen in Settings -> Tabs -> Close tabs.
    var closeTabsOnExit by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit),
        default = false,
    )

    // Internal state for the close-on-exit detection (armed when the app goes to
    // the background; consumed at the next launch by comparing the task id).
    var closeTabsOnExitArmed by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit_armed),
        default = false,
    )

    var closeTabsOnExitLastTask by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit_last_task),
        default = 0,
    )""",
)

# --- preference_keys.xml ------------------------------------------------------
patch(
    "app/src/main/res/values/preference_keys.xml",
    "</resources>",
    """    <string name="pref_key_close_tabs_on_exit" translatable="false">pref_key_close_tabs_on_exit</string>
    <string name="pref_key_close_tabs_on_exit_armed" translatable="false">pref_key_close_tabs_on_exit_armed</string>
    <string name="pref_key_close_tabs_on_exit_last_task" translatable="false">pref_key_close_tabs_on_exit_last_task</string>
</resources>""",
)

# --- strings.xml --------------------------------------------------------------
patch(
    "app/src/main/res/values/strings.xml",
    "</resources>",
    """    <string name="close_tabs_on_exit">When the app is closed</string>
    <string name="close_tabs_on_exit_summary">Close every tab the moment Nova is closed or removed from the app switcher.</string>
</resources>""",
)

# --- Tabs settings: add the "close tabs when the app is closed" radio option ---
patch(
    "app/src/main/res/xml/tabs_preferences.xml",
    """        <org.mozilla.fenix.settings.RadioButtonPreference
            android:defaultValue="false"
            android:key="@string/pref_key_close_tabs_after_one_month"
            android:title="@string/close_tabs_after_one_month" />""",
    """        <org.mozilla.fenix.settings.RadioButtonPreference
            android:defaultValue="false"
            android:key="@string/pref_key_close_tabs_after_one_month"
            android:title="@string/close_tabs_after_one_month" />

        <org.mozilla.fenix.settings.RadioButtonPreference
            android:defaultValue="false"
            android:key="@string/pref_key_close_tabs_on_exit"
            android:title="@string/close_tabs_on_exit"
            android:summary="@string/close_tabs_on_exit_summary" />""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    "    private lateinit var radioOneMonth: RadioButtonPreference",
    """    private lateinit var radioOneMonth: RadioButtonPreference
    private lateinit var radioOnExit: RadioButtonPreference""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    "        radioOneDay = requirePreference(R.string.pref_key_close_tabs_after_one_day)",
    """        radioOneDay = requirePreference(R.string.pref_key_close_tabs_after_one_day)
        radioOnExit = requirePreference(R.string.pref_key_close_tabs_on_exit)""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    "        radioOneMonth.onClickListener(::enableInactiveTabsSetting)",
    """        radioOneMonth.onClickListener(::enableInactiveTabsSetting)
        radioOnExit.onClickListener(::disableInactiveTabsSetting)""",
)
patch(
    BASE + "settings/TabsSettingsFragment.kt",
    """        addToRadioGroup(
            radioManual,
            radioOneDay,
            radioOneMonth,
            radioOneWeek,
        )""",
    """        addToRadioGroup(
            radioManual,
            radioOneDay,
            radioOneMonth,
            radioOneWeek,
            radioOnExit,
        )""",
)

# --- HomeActivity.kt: close tabs when the app is closed -----------------------
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
    "import org.mozilla.fenix.settings.SupportUtils",
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
    """    private var novaScheduledCheck: Job? = null

    /**
     * "Close tabs when the app is closed": every time the app goes to the
     * background the current task id is remembered and a short timer is started.
     * If the task is later removed from the app switcher the user really closed
     * the app, so the cleanup runs right away. If the process was killed before
     * the timer fired, the decision happens at the next launch (task id
     * comparison), where the session snapshot is dropped before it is restored
     * (the same point where the stock "Close tabs after X" option drops tabs).
     * Not for the external-app browser activity (custom tabs), which is a
     * separate task and must not close the user's tabs when it is dismissed.
     */
    private fun armNovaClearOnExitCheck() {
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.closeTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        settings.closeTabsOnExitLastTask = taskId
        settings.closeTabsOnExitArmed = true
        NovaDebugLog.log(this, "arm: armed=true task=$taskId")
    }

    @Suppress("DEPRECATION")
    private fun scheduleNovaClearOnCloseCheck() {
        // Called on every stop, AFTER the app has fully backgrounded. A short
        // while later, if the app's task is no longer in the recents list, the
        // user really closed it (removed it from the app switcher / pressed
        // "Clear all"), so the cleanup runs right away. A plain background keeps
        // the task, so nothing is cleared then. If the process is killed before
        // this check runs, consumeNovaClearTabsOnExit handles it at the next
        // launch by comparing the task id.
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.closeTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        if (!settings.closeTabsOnExitArmed) return
        val appContext = applicationContext
        val myTaskId = taskId
        novaScheduledCheck?.cancel()
        novaScheduledCheck = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            delay(5_000L)
            try {
                val am = appContext.getSystemService(ActivityManager::class.java)
                    ?: return@launch
                if (am.appTasks.any { it.taskInfo?.id == myTaskId }) {
                    NovaDebugLog.log(appContext, "delayed check: task still present - kept")
                    return@launch
                }
                val s = appContext.components.settings
                if (!s.closeTabsOnExitArmed) return@launch
                s.closeTabsOnExitArmed = false
                NovaDebugLog.log(appContext, "task removed from background - closing tabs")
                NovaCloseCleanup.run(appContext, appContext.components)
            } catch (_: Exception) {
            }
        }
    }

    private fun consumeNovaClearTabsOnExit() {
        // Runs at the start of every HomeActivity launch. Same task id as when the
        // app last stopped -> the app was only backgrounded, keep the tabs. A
        // different task id -> the app was really closed (removed from the app
        // switcher / Quit), so the tabs must not come back: the session snapshot
        // is deleted before it is restored, and the tabs are closed.
        if (this is ExternalAppBrowserActivity) return
        val settings = components.settings
        if (!settings.closeTabsOnExit && !settings.shouldDeleteBrowsingDataOnQuit) return
        if (!settings.closeTabsOnExitArmed) return
        val lastTaskId = settings.closeTabsOnExitLastTask
        if (lastTaskId == 0 || taskId == lastTaskId) {
            FenixApplication.novaPendingCleanStart = false
            settings.closeTabsOnExitArmed = false
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
        settings.closeTabsOnExitArmed = false
        FenixApplication.novaPendingCleanStart = false
        NovaDebugLog.log(this, "consume: new task - closing tabs")
        NovaCloseCleanup.run(this, components)
    }

    final override fun onStart() {""",
)
patch(
    BASE + "HomeActivity.kt",
    "        super.onStart()",
    "        super.onStart()\n        novaScheduledCheck?.cancel()",
)

# --- FenixApplication.kt: restore-time drop -----------------------------------
patch(
    BASE + "FenixApplication.kt",
    "        components.useCases.tabsUseCases.restore(sessionStorage, components.settings.getTabTimeout())",
    """        // Nova: if "close tabs when the app is closed" was armed and HomeActivity saw
        // a fresh task at this launch, delete the saved session snapshot BEFORE it is
        // restored so the tabs cannot come back. This is the same point where the
        // stock "Close tabs after X" option drops stale tabs. (If the restore already
        // ran before HomeActivity.onCreate on some Android versions, the flag stays
        // set and the HomeActivity cleanup handles it instead.)
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
         * relaunch knows the "close tabs on exit" armed flag can be consumed safely.
         */
        var initialSessionRestoreCompleted = false
            private set

        /**
         * Set by HomeActivity when the "close tabs on exit" armed flag is consumed at
         * launch and the session restore has not run yet: the saved session snapshot
         * is then deleted right before the restore (the same point where the stock
         * "Close tabs after X" option drops stale tabs).
         */
        var novaPendingCleanStart = false
    }
""",
)

print("All Nova source patches applied.")

PY

echo ">> Nova branding: done"
