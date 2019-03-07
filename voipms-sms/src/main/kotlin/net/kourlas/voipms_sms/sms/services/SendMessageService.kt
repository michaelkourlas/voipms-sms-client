/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2019 Michael Kourlas
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

package net.kourlas.voipms_sms.sms.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.core.app.RemoteInput
import com.crashlytics.android.Crashlytics
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.getEmail
import net.kourlas.voipms_sms.preferences.getPassword
import net.kourlas.voipms_sms.preferences.isAccountActive
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.JobId
import net.kourlas.voipms_sms.utils.getJson
import net.kourlas.voipms_sms.utils.isNetworkConnectionAvailable
import net.kourlas.voipms_sms.utils.validatePhoneNumber
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.*

/**
 * Service used to send an SMS message to the specified contact using the
 * specified DID with the VoIP.ms API.
 */
class SendMessageService : JobIntentService() {
    private var error: String? = null

    override fun onHandleWork(intent: Intent) {
        val rand = Random().nextInt().toString(16)
        Log.i(
            SendMessageService::class.java.name,
            "[$rand] sending message")

        val conversationId = handleSendMessage(intent) ?: return

        // Cancel any existing notification with this conversation ID
        Notifications.getInstance(application)
            .cancelNotification(conversationId)

        // Send a broadcast indicating that the messages have been sent
        // (or that an attempt to send them has been made)
        val sentMessageBroadcastIntent = Intent(
            applicationContext.getString(
                R.string.sent_message_action,
                conversationId.did, conversationId.contact))
        if (error != null) {
            sentMessageBroadcastIntent.putExtra(applicationContext.getString(
                R.string.sent_message_error), error)
        }
        applicationContext.sendBroadcast(sentMessageBroadcastIntent)

        Log.i(
            SendMessageService::class.java.name,
            "[$rand] sent message")
    }

    private fun handleSendMessage(intent: Intent): ConversationId? {
        try {
            // Terminate quietly if intent does not exist or does not contain
            // the send SMS action
            if (intent.action != applicationContext.getString(
                    R.string.send_message_action)) {
                return null
            }

            // Terminate quietly if account inactive
            if (!isAccountActive(applicationContext)) {
                return null
            }

            // Retrieve the DID, contact, and list of message texts from the
            // intent
            val (did, contact, messageTexts, databaseId) = getIntentData(intent)

            val messages = mutableListOf<OutgoingMessage>()
            if (databaseId != null) {
                val message = Database.getInstance(
                    applicationContext)
                                  .getMessageDatabaseId(databaseId)
                              ?: throw Exception("No message with database" +
                                                 " ID found")
                Database.getInstance(
                    applicationContext)
                    .markMessageDeliveryInProgress(databaseId)
                messages.add(
                    OutgoingMessage(
                        databaseId, did, contact,
                        message.text))
            } else if (messageTexts != null) {
                // Try adding the messages to the database; if this fails,
                // terminate with a toast and try to remove existing added
                // messages
                for (messageText in messageTexts) {
                    try {
                        val newDatabaseId = Database.getInstance(
                            applicationContext)
                            .insertMessageDeliveryInProgress(
                                ConversationId(did,
                                               contact), messageText)
                        messages.add(
                            OutgoingMessage(
                                newDatabaseId, did,
                                contact, messageText))
                    } catch (e: Exception) {
                        Crashlytics.logException(e)
                        error = applicationContext.getString(
                            R.string.send_message_error_database)
                        for ((newDatabaseId) in messages) {
                            try {
                                Database.getInstance(
                                    applicationContext)
                                    .removeMessage(newDatabaseId)
                            } catch (e: Exception) {
                                Crashlytics.logException(e)
                            }
                        }
                        return ConversationId(did,
                                              contact)
                    }
                }
            }

            // Send a broadcast indicating that the messages are about to be
            // sent
            val sendingMessageBroadcastIntent = Intent(
                applicationContext.getString(
                    R.string.sending_message_action, did, contact))
            applicationContext.sendBroadcast(sendingMessageBroadcastIntent)

            // Send each message using the VoIP.ms API
            for (message in messages) {
                sendMessage(message)
            }

            return ConversationId(did, contact)
        } catch (e: Exception) {
            Crashlytics.logException(e)
            error = applicationContext.getString(
                R.string.send_message_error_unknown)
        }

        return null
    }

    /**
     * Extracts the DID, contact, and message texts from the specified intent.
     */
    private fun getIntentData(intent: Intent): IntentData {
        // Extract the DID and contact from the intent
        val did = intent.getStringExtra(
            applicationContext.getString(R.string.send_message_did))
                  ?: throw Exception("DID missing")
        val contact = intent.getStringExtra(
            applicationContext.getString(R.string.send_message_contact))
                      ?: throw Exception("Contact phone number missing")

        val databaseId = intent.getLongExtra(getString(
            R.string.send_message_database_id), -1)
        if (databaseId != -1L) {
            return IntentData(
                did, contact, null, databaseId)
        }

        // Extract the message text provided by inline reply if it exists;
        // otherwise, use the manually specified message text
        var messageText: String
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        messageText = remoteInput?.getCharSequence(
            applicationContext.getString(
                R.string.notifications_reply_key))?.toString()
                      ?: (intent.getStringExtra(
                          applicationContext.getString(
                              R.string.send_message_text))
                          ?: throw Exception(
                              "Message text missing"))

        // If the message text exceeds the maximum length of an SMS message,
        // split it into multiple message texts
        val length = applicationContext.resources.getInteger(
            R.integer.sms_max_length)
        val messageTexts = mutableListOf<String>()
        do {
            val sublength: Int = if (messageText.length > length) {
                length
            } else {
                messageText.length
            }
            messageTexts.add(messageText.substring(0, sublength))
            messageText = messageText.substring(sublength)
        } while (messageText.isNotEmpty())

        return IntentData(
            did, contact, messageTexts, null)
    }

    /**
     * Sends the specified message using the VoIP.ms API and updates the
     * database accordingly.
     */
    private fun sendMessage(message: OutgoingMessage) {
        // Terminate if no network connection is available
        if (!isNetworkConnectionAvailable(applicationContext)) {
            error = applicationContext.getString(
                R.string.send_message_error_network)
            Database.getInstance(
                applicationContext).markMessageNotSent(
                message.databaseId)
            return
        }

        // Send the message using the VoIP.ms API
        val voipId = sendMessageWithVoipMsApi(message)

        // If the message was sent, mark it as sent and update it with the
        // retrieved VoIP.ms ID; if not, mark it as failed to send
        if (voipId != null) {
            Database.getInstance(
                applicationContext).markMessageSent(
                message.databaseId, voipId)
        } else {
            Database.getInstance(
                applicationContext).markMessageNotSent(
                message.databaseId)
        }
    }

    /**
     * Sends the specified message using the VoIP.ms API
     *
     * @return Null if the message could not be sent.
     */
    private fun sendMessageWithVoipMsApi(message: OutgoingMessage): Long? {
        // Get encoded versions of URI values
        val email = URLEncoder.encode(getEmail(applicationContext), "UTF-8")
        val password = URLEncoder.encode(getPassword(applicationContext),
                                         "UTF-8")
        val did = URLEncoder.encode(message.did, "UTF-8")
        val contact = URLEncoder.encode(message.contact, "UTF-8")
        val text = URLEncoder.encode(message.text, "UTF-8")

        // Get JSON response from API
        val sendMessageUrl = "https://www.voip.ms/api/v1/rest.php?" +
                             "api_username=$email&api_password=$password" +
                             "&method=sendSMS&did=$did&dst=$contact" +
                             "&message=$text"
        val response: JSONObject
        try {
            response = getJson(applicationContext, sendMessageUrl)
        } catch (e: IOException) {
            error = applicationContext.getString(
                R.string.send_message_error_api_request)
            return null
        } catch (e: JSONException) {
            Crashlytics.logException(e)
            error = applicationContext.getString(
                R.string.send_message_error_api_parse)
            return null
        } catch (e: Exception) {
            Crashlytics.logException(e)
            error = applicationContext.getString(
                R.string.send_message_error_unknown)
            return null
        }

        // Get VoIP.ms ID from response
        val status = response.getString("status")
        if (status == "") {
            error = applicationContext.getString(
                R.string.send_message_error_api_parse)
            return null
        }
        if (status != "success") {
            error = applicationContext.getString(
                R.string.send_message_error_api_error, status)
            return null
        }
        val voipId = response.optLong("sms")
        if (voipId == 0L) {
            error = applicationContext.getString(
                R.string.send_message_error_api_parse)
            return null
        }
        return voipId
    }

    /**
     * Represents the data in the intent sent to this service.
     */
    data class IntentData(val did: String, val contact: String,
                          val messageTexts: List<String>?,
                          val databaseId: Long?)

    /**
     * Represents a message that has been input into the database but has not
     * yet been sent.
     */
    data class OutgoingMessage(val databaseId: Long, val did: String,
                               val contact: String, val text: String) {
        init {
            validatePhoneNumber(did)
            validatePhoneNumber(contact)
        }
    }

    companion object {
        /**
         * Gets an intent which can be used to send a message to the
         * specified contact and from the specified DID.
         */
        fun getIntent(context: Context, did: String, contact: String): Intent {
            val intent = Intent()
            intent.action = context.getString(R.string.send_message_action)
            intent.putExtra(context.getString(
                R.string.send_message_did), did)
            intent.putExtra(context.getString(
                R.string.send_message_contact), contact)
            return intent
        }

        /**
         * Sends the specified message to the specified contact and from the
         * specified DID.
         */
        fun startService(context: Context, did: String, contact: String,
                         text: String) {
            val intent = getIntent(
                context, did, contact)
            intent.putExtra(context.getString(R.string.send_message_text), text)
            startService(
                context, intent)
        }

        /**
         * Sends the message associated with the specified database ID to the
         * contact and from the DID associated with the specified conversation
         * ID.
         */
        fun startService(context: Context, conversationId: ConversationId,
                         databaseId: Long) {
            val intent = getIntent(
                context, conversationId.did,
                conversationId.contact)
            intent.putExtra(context.getString(
                R.string.send_message_database_id), databaseId)
            startService(
                context, intent)
        }

        /**
         * Sends a message.
         */
        fun startService(context: Context, intent: Intent) {
            enqueueWork(context, SendMessageService::class.java,
                        JobId.SendMessageService.ordinal, intent)
        }
    }
}
