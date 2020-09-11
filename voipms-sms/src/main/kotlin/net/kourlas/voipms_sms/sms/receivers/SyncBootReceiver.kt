/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2020 Michael Kourlas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import net.kourlas.voipms_sms.sms.workers.SyncWorker
import net.kourlas.voipms_sms.utils.logException

/**
 * Receiver called on system startup and used to set up the synchronization
 * interval.
 */
class SyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) {
                return
            }
            if (intent.action != "android.intent.action.BOOT_COMPLETED"
                && intent.action != "android.intent.action.ACTION_LOCKED_BOOT_COMPLETED") {
                return
            }

            // We want to start the periodic synchronization if it hasn't
            // already
            SyncWorker.startPeriodicWorker(
                context, existingWorkPolicy = ExistingWorkPolicy.KEEP)
        } catch (e: Exception) {
            logException(e)
        }
    }
}
