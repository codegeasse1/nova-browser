#!/usr/bin/env bash
# Nova Developer Options — adds inspect element, console, about:config and a
# remote-debugging toggle to the browser's three-dot menu.
#
# Run after nova-branding.sh, inside the iceraven source root.
# Self-contained: defines its own patch/read/write helpers and BASE path.
set -euo pipefail

echo ">> Nova developer options: applying patches"

python3 <<'PY'
import os, re

BASE = "app/src/main/java/org/mozilla/fenix/"

def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write(path, s):
    with open(path, "w", encoding="utf-8") as f:
        f.write(s)

def patch(path, old, new):
    s = read(path)
    if old not in s:
        raise SystemExit("PATCH FAILED in %s:\npattern not found:\n%s" % (path, old[:200]))
    write(path, s.replace(old, new, 1))

# --- strings.xml: Developer options menu items ----------------------------------
patch(
    "app/src/main/res/values/strings.xml",
    '    <string name="browser_menu_view_page_source_hint">Show the raw HTML source of this page in a new tab.</string>',
    """    <string name="browser_menu_view_page_source_hint">Show the raw HTML source of this page in a new tab.</string>
    <string name="browser_menu_dev_tools">Inspect page (DevTools)</string>
    <string name="browser_menu_dev_tools_hint">Open the Gecko developer tools — inspector, console, network.</string>
    <string name="browser_menu_about_config">about:config</string>
    <string name="browser_menu_about_config_hint">Advanced Gecko preferences.</string>
    <string name="browser_menu_java_console">Browser console</string>
    <string name="browser_menu_java_console_hint">View Nova debug log output.</string>""",
)

# --- preference_keys.xml: developer mode + remote debugging ----------------------
patch(
    "app/src/main/res/values/preference_keys.xml",
    '    <string name="pref_key_nova_adblock" translatable="false">pref_key_nova_adblock</string>',
    """    <string name="pref_key_nova_adblock" translatable="false">pref_key_nova_adblock</string>
    <string name="pref_key_nova_developer_mode" translatable="false">pref_key_nova_developer_mode</string>
    <string name="pref_key_nova_remote_debugging" translatable="false">pref_key_nova_remote_debugging</string>""",
)

# --- Settings.kt: developer mode + remote debugging preferences -----------------
patch(
    BASE + "utils/Settings.kt",
    """    var closeTabsOnExitLastTask by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit_last_task),
        default = 0,
    )""",
    """    var closeTabsOnExitLastTask by intPreference(
        appContext.getPreferenceKey(R.string.pref_key_close_tabs_on_exit_last_task),
        default = 0,
    )

    // Nova: developer mode — when on, enables GeckoView remote debugging so the
    // built-in DevTools (inspect element, console, network) can inspect the
    // current tab via about:debugging.
    var novaDeveloperMode by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_developer_mode),
        default = false,
    )

    // Nova: remote debugging toggle — flips GeckoRuntime remote debugging on/off.
    var novaRemoteDebugging by booleanPreference(
        appContext.getPreferenceKey(R.string.pref_key_nova_remote_debugging),
        default = false,
    )""",
)

# --- Core.kt: enable GeckoView remote debugging when developer mode is on -------
# The upstream line is:
#   remoteDebuggingEnabled = context.components.settings.isRemoteDebuggingEnabled,
# We OR it with novaDeveloperMode so flipping the Nova developer toggle in
# Settings is enough to let about:debugging inspect the current tab.
patch(
    BASE + "components/Core.kt",
    "remoteDebuggingEnabled = context.components.settings.isRemoteDebuggingEnabled,",
    "remoteDebuggingEnabled = context.components.settings.novaDeveloperMode || context.components.settings.isRemoteDebuggingEnabled,",
)

# --- MainMenu.kt: params for Developer options ----------------------------------
patch(
    BASE + "components/menu/compose/MainMenu.kt",
    """    onNovaAllowBackgroundToggle: () -> Unit = {},
    onNovaViewSource: () -> Unit = {},
    canGoBack: Boolean,""",
    """    onNovaAllowBackgroundToggle: () -> Unit = {},
    onNovaViewSource: () -> Unit = {},
    onNovaDevTools: () -> Unit = {},
    onNovaAboutConfig: () -> Unit = {},
    onNovaBrowserConsole: () -> Unit = {},
    canGoBack: Boolean,""",
)

# --- MainMenu.kt: the "Developer options" menu rows ------------------------------
# Inserts three new items below "View page source":
#   - Inspect page (DevTools)  -> opens about:debugging
#   - about:config             -> opens about:config
#   - Browser console          -> opens the nova-debug.log
patch(
    BASE + "components/menu/compose/MainMenu.kt",
    """        if (accessPoint == MenuAccessPoint.Browser) {
            MenuGroup {
                MenuItem(
                    label = stringResource(id = R.string.browser_menu_view_page_source),
                    description = stringResource(id = R.string.browser_menu_view_page_source_hint),
                    beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_settings_24),
                    onClick = onNovaViewSource,
                )
            }
        }

        LibraryMenuGroup(
            isDownloadHighlighted = isDownloadHighlighted,""",
    """        if (accessPoint == MenuAccessPoint.Browser) {
            MenuGroup {
                MenuItem(
                    label = stringResource(id = R.string.browser_menu_view_page_source),
                    description = stringResource(id = R.string.browser_menu_view_page_source_hint),
                    beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_settings_24),
                    onClick = onNovaViewSource,
                )
                MenuItem(
                    label = stringResource(id = R.string.browser_menu_dev_tools),
                    description = stringResource(id = R.string.browser_menu_dev_tools_hint),
                    beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_settings_24),
                    onClick = onNovaDevTools,
                )
                MenuItem(
                    label = stringResource(id = R.string.browser_menu_about_config),
                    description = stringResource(id = R.string.browser_menu_about_config_hint),
                    beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_settings_24),
                    onClick = onNovaAboutConfig,
                )
                MenuItem(
                    label = stringResource(id = R.string.browser_menu_java_console),
                    description = stringResource(id = R.string.browser_menu_java_console_hint),
                    beforeIconPainter = painterResource(id = iconsR.drawable.mozac_ic_settings_24),
                    onClick = onNovaBrowserConsole,
                )
            }
        }

        LibraryMenuGroup(
            isDownloadHighlighted = isDownloadHighlighted,""",
)

# --- MenuDialogFragment.kt: developer-options handlers --------------------------
patch(
    BASE + "components/menu/MenuDialogFragment.kt",
    """                                val onNovaViewSource = {
                                    if (novaCurrentUrl.isNotEmpty()) {
                                        requireComponents.useCases.tabsUseCases.addTab(
                                            url = "view-source:$novaCurrentUrl",
                                            selectTab = true,
                                        )
                                    }
                                }""",
    """                                val onNovaViewSource = {
                                    if (novaCurrentUrl.isNotEmpty()) {
                                        requireComponents.useCases.tabsUseCases.addTab(
                                            url = "view-source:$novaCurrentUrl",
                                            selectTab = true,
                                        )
                                    }
                                }

                                // Nova: open the built-in Gecko DevTools page. This shows
                                // all open tabs and lets you inspect each one — DOM inspector,
                                // console, network monitor, style editor.
                                val onNovaDevTools = {
                                    requireComponents.useCases.tabsUseCases.addTab(
                                        url = "about:debugging#/runtime/this-firefox",
                                        selectTab = true,
                                    )
                                }

                                // Nova: open about:config — advanced Gecko preferences.
                                val onNovaAboutConfig = {
                                    requireComponents.useCases.tabsUseCases.addTab(
                                        url = "about:config",
                                        selectTab = true,
                                    )
                                }

                                // Nova: open the browser console — opens the Nova debug
                                // log (nova-debug.log) as a readable text tab.
                                val onNovaBrowserConsole = {
                                    val logFile = requireContext().getExternalFilesDir(null)
                                        ?.resolve("nova-debug.log")
                                    val url = if (logFile?.exists() == true) {
                                        "file://" + logFile.absolutePath
                                    } else {
                                        "data:text/plain,No debug log yet. Nova writes here " +
                                        "when something goes wrong (crashes, cleanup, " +
                                        "background playback)."
                                    }
                                    requireComponents.useCases.tabsUseCases.addTab(
                                        url = url,
                                        selectTab = true,
                                    )
                                }""",
)

# --- MenuDialogFragment.kt: pass dev options into MainMenu ----------------------
patch(
    BASE + "components/menu/MenuDialogFragment.kt",
    """                                    onNovaAllowBackgroundToggle = onNovaAllowBackgroundToggle,
                                    onNovaViewSource = onNovaViewSource,""",
    """                                    onNovaAllowBackgroundToggle = onNovaAllowBackgroundToggle,
                                    onNovaViewSource = onNovaViewSource,
                                    onNovaDevTools = onNovaDevTools,
                                    onNovaAboutConfig = onNovaAboutConfig,
                                    onNovaBrowserConsole = onNovaBrowserConsole,""",
)

print("Nova developer options patches applied.")
PY

echo ">> Nova developer options: done"
