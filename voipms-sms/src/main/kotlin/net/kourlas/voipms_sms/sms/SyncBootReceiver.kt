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

/**
 * Receiver called on system startup and used to set up the synchronization
 * interval.
 */
class SyncBootReceiver : BroadcastReceiver() {
    /**
     * Sets up the synchronization interval using the specified context and
     * intent.
     *
     * @param context The specified context.
     * @param intent The specified intent.
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent != null &&
            (intent.action == "android.intent.action.BOOT_COMPLETED"
             || intent.action == "android.intent.action.ACTION_LOCKED_BOOT_COMPLETED")) {
            SyncIntervalService.startService(context)
        }
    }
}
