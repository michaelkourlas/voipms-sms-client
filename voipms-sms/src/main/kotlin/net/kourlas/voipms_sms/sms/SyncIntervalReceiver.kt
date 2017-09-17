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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.crash.FirebaseCrash
import net.kourlas.voipms_sms.R

/**
 * Receiver called when a database synchronization is requested (automatically
 * or triggered by a particular action).
 */
class SyncIntervalReceiver : BroadcastReceiver() {
    /**
     * Performs database synchronization using the specified context and
     * intent.
     *
     * @param context The specified context.
     * @param intent The specified intent.
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) {
                return
            }
            if (intent.action != context.getString(
                R.string.sync_interval_action)) {
                return
            }
            val forceRecent = intent.extras.get(
                context.getString(
                    R.string.sync_interval_force_recent)) as Boolean?
                              ?: throw Exception("Force recent missing")
            SyncService.startService(context, forceRecent)
        } catch (e: Exception) {
            FirebaseCrash.report(e)
        }
    }

    companion object {
        /**
         * Gets an intent which can be used to trigger this receiver using the
         * specified context.
         *
         * @param context The specified context.
         * @param forceRecent If true, retrieves only the most recent messages
         * regardless of the app configuration.
         * @return An intent which can be used to launch this service.
         */
        fun getIntent(context: Context, forceRecent: Boolean = true): Intent {
            val intent = Intent(context, SyncIntervalReceiver::class.java)
            intent.action = context.getString(R.string.sync_interval_action)
            intent.putExtra(context.getString(
                R.string.sync_interval_force_recent),
                            forceRecent)
            return intent
        }
    }
}
