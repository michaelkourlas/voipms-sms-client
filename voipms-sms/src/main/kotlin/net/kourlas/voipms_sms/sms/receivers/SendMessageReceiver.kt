/*
 * VoIP.ms SMS
 * Copyright (C) 2018 Michael Kourlas
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
import com.crashlytics.android.Crashlytics
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.services.SendMessageService

/**
 * Broadcast receiver used to forward send message requests from a PendingIntent
 * to the SendMessageService.
 */
class SendMessageReceiver : BroadcastReceiver() {
    /**
     * Forwards a send message request using the specified context and
     * intent.
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) {
                return
            }
            if (intent.action != context.getString(
                    R.string.send_message_action)) {
                return
            }

            // Forward intent to SendMessageService
            intent.setClass(context, SendMessageService::class.java)
            SendMessageService.sendMessage(context, intent)
        } catch (e: Exception) {
            Crashlytics.logException(e)
        }
    }
}
