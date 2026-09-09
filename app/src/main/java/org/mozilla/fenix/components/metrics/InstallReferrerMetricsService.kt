/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.content.Context
import android.os.RemoteException
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.utils.Settings
import java.util.concurrent.TimeUnit

/**
 * A metrics service used to derive the UTM parameters with the Google Play Install Referrer library.
 *
 * This service delegates all attribution retrieval to [InstallReferrerWorker], which handles
 * the initial attempt and any necessary retries with exponential backoff.
 */
class InstallReferrerMetricsService(
    private val context: Context,
    private val settings: Settings,
) : MetricsService {
    private val logger = Logger("InstallReferrerMetricsService")
    override val type = MetricServiceType.Data

    override fun start() {
        if (settings.utmParamsKnown) {
            logger.debug("UTM params already known, skipping")
            return
        }
    }

    override fun stop() {
    }

    override fun track(event: Event) = Unit

    override fun shouldTrack(event: Event): Boolean = false

    companion object {
        internal const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_DELAY_SECONDS = 5L
        private const val WORK_NAME = "INSTALL_REFERRER"
    }
}
