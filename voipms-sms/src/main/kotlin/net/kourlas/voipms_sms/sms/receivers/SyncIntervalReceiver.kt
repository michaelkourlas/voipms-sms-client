/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getLastCompleteSyncTime
import net.kourlas.voipms_sms.preferences.getSyncInterval
import net.kourlas.voipms_sms.sms.workers.SyncWorker
import net.kourlas.voipms_sms.utils.logException

/**
 * Receiver called when a database synchronization is requested, either
 * automatically or triggered by a particular action.
 */
class SyncIntervalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) {
                throw Exception("No context or intent provided")
            }
            if (intent.action != context.getString(
                    R.string.sync_interval_action)) {
                throw Exception("Unrecognized action " + intent.action)
            }
            SyncWorker.performSynchronization(context)
        } catch (e: Exception) {
            logException(e)
        }
    }

    companion object {
        /**
         * Set up an alarm to trigger database synchronization.
         */
        fun setInterval(context: Context) {
            val alarmManager = context.getSystemService(
                Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context, SyncIntervalReceiver::class.java.hashCode(),
                getIntent(context),
                0)
            alarmManager.cancel(pendingIntent)

            val syncInterval = (getSyncInterval(context)
                                * (24 * 60 * 60 * 1000)).toLong()
            // Only setup interval if periodic synchronization is enabled
            if (syncInterval != 0L) {
                val nextSyncTime = getLastCompleteSyncTime(context) +
                                   syncInterval

                val now = System.currentTimeMillis()
                if (nextSyncTime <= now) {
                    pendingIntent.send()
                } else {
                    AlarmManagerCompat.setAndAllowWhileIdle(
                        alarmManager, AlarmManager.RTC_WAKEUP, nextSyncTime,
                        pendingIntent)
                }
            }
        }

        /**
         * Gets an intent which can be used to trigger this receiver.
         */
        private fun getIntent(context: Context): Intent {
            val intent = Intent(context, SyncIntervalReceiver::class.java)
            intent.action = context.getString(R.string.sync_interval_action)
            return intent
        }
    }
}
