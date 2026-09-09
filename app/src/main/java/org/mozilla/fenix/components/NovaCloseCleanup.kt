/* This Source Code Form is subject to the terms of the Mozilla Public
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
 *  - "Delete browsing data on quit" (Nova's own setting): runs the same
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
