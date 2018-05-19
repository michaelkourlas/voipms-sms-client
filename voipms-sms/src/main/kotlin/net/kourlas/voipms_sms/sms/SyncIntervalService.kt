/*
 * VoIP.ms SMS
 * Copyright (C) 2017 Michael Kourlas
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

package net.kourlas.voipms_sms.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.AlarmManagerCompat
import android.support.v4.app.JobIntentService
import android.util.Log
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getLastCompleteSyncTime
import net.kourlas.voipms_sms.preferences.getSyncInterval
import java.util.*

/**
 * Service used to set up an alarm to trigger database synchronization.
 */
class SyncIntervalService : JobIntentService() {
    override fun onHandleWork(intent: Intent) {
        // Terminate quietly if does not contain the sync interval action
        if (intent.action != applicationContext.getString(
                R.string.sync_interval_service_action)) {
            return
        }

        val rand = Random().nextInt().toString(16)
        Log.i(SyncIntervalService::class.java.name,
              "[$rand] setting sync interval")

        setInterval()

        Log.i(SyncIntervalService::class.java.name,
              "[$rand] sync interval set")
    }

    /**
     * Set up an alarm to trigger database synchronization.
     */
    private fun setInterval() {
        val alarmManager = applicationContext.getSystemService(
            Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext, SyncIntervalReceiver::class.java.hashCode(),
            SyncIntervalReceiver.getIntent(
                applicationContext, forceRecent = false),
            0)
        alarmManager.cancel(pendingIntent)

        val syncInterval = (getSyncInterval(applicationContext)
                            * (24 * 60 * 60 * 1000)).toLong()
        // Only setup interval if periodic synchronization is enabled
        if (syncInterval != 0L) {
            val nextSyncTime = getLastCompleteSyncTime(applicationContext) +
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

    companion object {
        /**
         * Starts the service as a job using the specified context.
         */
        fun startService(context: Context) {
            val intent = Intent()
            intent.action = context.getString(
                R.string.sync_interval_service_action)

            enqueueWork(context, SyncIntervalService::class.java,
                        SyncIntervalService::class.java.hashCode(), intent)
        }
    }
}
