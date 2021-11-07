/*
 * VoIP.ms SMS
 * Copyright (C) 2018-2021 Michael Kourlas
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.workers.SendMessageWorker
import net.kourlas.voipms_sms.utils.getMessageTexts
import net.kourlas.voipms_sms.utils.logException

/**
 * Broadcast receiver used to forward send message requests from a notification
 * PendingIntent to a SendMessageWorker.
 */
class SendMessageReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            // Collect the required state.
            if (context == null || intent == null) {
                throw Exception("No context or intent provided")
            }
            if (intent.action != context.getString(
                    R.string.send_message_receiver_action
                )
            ) {
                throw Exception("Unrecognized action " + intent.action)
            }
            val did = intent.getStringExtra(
                context.getString(
                    R.string.send_message_receiver_did
                )
            ) ?: throw Exception(
                "No DID provided"
            )
            val contact = intent.getStringExtra(
                context.getString(
                    R.string.send_message_receiver_contact
                )
            ) ?: throw Exception(
                "No contact provided"
            )
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val messageText = remoteInput?.getCharSequence(
                context.getString(
                    R.string.notifications_reply_key
                )
            )?.toString()
                ?: throw Exception("No message text provided")

            val pendingResult =
                goAsync() ?: throw Exception("No PendingResult returned")
            GlobalScope.launch(Dispatchers.Default) {
                // Insert the messages into the database, then tell the
                // SendMessageWorker to send them.
                try {
                    val databaseIds = Database.getInstance(context)
                        .insertConversationMessagesDeliveryInProgress(
                            ConversationId(did, contact),
                            getMessageTexts(context, messageText)
                        )
                    for (id in databaseIds) {
                        SendMessageWorker.sendMessage(
                            context, id,
                            inlineReplyConversationId = ConversationId(
                                did, contact
                            )
                        )
                    }
                } catch (e: Exception) {
                    logException(e)
                } finally {
                    pendingResult.finish()
                }
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
            intent.putExtra(
                context.getString(
                    R.string.send_message_receiver_did
                ), did
            )
            intent.putExtra(
                context.getString(
                    R.string.send_message_receiver_contact
                ), contact
            )
            return intent
        }
    }
}
