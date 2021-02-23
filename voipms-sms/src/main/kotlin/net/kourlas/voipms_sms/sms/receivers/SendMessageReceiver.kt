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
import androidx.core.app.RemoteInput
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.services.SendMessageService
import net.kourlas.voipms_sms.utils.getMessageTexts
import net.kourlas.voipms_sms.utils.logException
import net.kourlas.voipms_sms.utils.runOnNewThread

/**
 * Broadcast receiver used to forward send message requests from a PendingIntent
 * to the SendMessageService.
 */
class SendMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null || intent == null) {
                return
            }
            if (intent.action != context.getString(
                    R.string.send_message_receiver_action)) {
                return
            }

            val did = intent.getStringExtra(context.getString(
                R.string.send_message_receiver_did)) ?: return
            val contact = intent.getStringExtra(context.getString(
                R.string.send_message_receiver_contact)) ?: return

            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val messageText = remoteInput?.getCharSequence(
                context.getString(
                    R.string.notifications_reply_key))?.toString()
                              ?: throw Exception(
                                  "Message text missing")

            runOnNewThread {
                Database.getInstance(context)
                    .insertMessageDeliveryInProgress(
                        ConversationId(did, contact),
                        getMessageTexts(context, messageText))
                SendMessageService.startService(context,
                                                ConversationId(did, contact),
                                                inlineReply = true)
            }
        } catch (e: Exception) {
            logException(e)
        }
    }


    companion object {
        /**
         * Gets an intent which can be used to send a message to the
         * specified contact and from the specified DID.
         */
        fun getIntent(context: Context, did: String, contact: String): Intent {
            val intent = Intent()
            intent.action =
                context.getString(R.string.send_message_receiver_action)
            intent.putExtra(context.getString(
                R.string.send_message_receiver_did), did)
            intent.putExtra(context.getString(
                R.string.send_message_receiver_contact), contact)
            return intent
        }
    }
}
