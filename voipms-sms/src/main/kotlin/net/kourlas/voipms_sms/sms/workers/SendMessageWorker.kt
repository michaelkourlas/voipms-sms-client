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

package net.kourlas.voipms_sms.sms.workers

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.network.NetworkManager
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.getEmail
import net.kourlas.voipms_sms.preferences.getPassword
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import java.io.IOException

/**
 * Worker used to send an SMS message using the VoIP.ms API.
 */
class SendMessageWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private var conversationId: ConversationId? = null
    private var error: String? = null
    private var markedAsSent: Boolean = false
    private var markedAsNotSent: Boolean = false

    override suspend fun doWork(): Result {
        // Terminate quietly if we cannot find the message we are supposed
        // to send.
        val databaseId =
            inputData.getLong(
                applicationContext.getString(
                    R.string.send_message_database_id
                ), -1
            )
        if (databaseId == -1L) {
            return Result.failure()
        }

        try {
            // Send the message we were asked to send.
            sendMessage(databaseId)

            // Send a broadcast indicating that the message has been sent, or that
            // an attempt to send it was made.
            conversationId?.let {
                val sentMessageBroadcastIntent = Intent(
                    applicationContext.getString(
                        R.string.sent_message_action, it.did, it.contact
                    )
                )
                if (error != null) {
                    sentMessageBroadcastIntent.putExtra(
                        applicationContext.getString(
                            R.string.sent_message_error
                        ), error
                    )
                }
                applicationContext.sendBroadcast(sentMessageBroadcastIntent)
            }

            return if (error == null) {
                Result.success()
            } else {
                Result.failure()
            }
        } finally {
            // We are not going to try sending this message again unless the
            // user triggers it by clicking on the message, so ensure the
            // database is correctly updated even on failure.
            if (!markedAsSent && !markedAsNotSent) {
                try {
                    Database.getInstance(
                        applicationContext
                    ).markMessageNotSent(
                        databaseId
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notifications.getInstance(applicationContext)
            .getSyncMessageSendNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Notifications.SYNC_SEND_MESSAGE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                Notifications.SYNC_SEND_MESSAGE_NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Sends the message we were asked to send using the VoIP.ms API and
     * updates the database accordingly.
     */
    private suspend fun sendMessage(databaseId: Long) {
        try {
            // Terminate quietly if it is impossible to send messages due to
            // account configuration.
            if (!accountConfigured(applicationContext)) {
                return
            }

            // Retrieve the message we are supposed to send. Terminate quietly
            // if it is not in a valid state.
            val message = Database.getInstance(applicationContext)
                .getMessageDatabaseId(databaseId)
            conversationId = message?.conversationId
            if (message == null || !message.isDeliveryInProgress) {
                return
            }

            // Terminate if no network connection is available.
            if (!NetworkManager.getInstance().isNetworkConnectionAvailable(
                    applicationContext
                )
            ) {
                error = applicationContext.getString(
                    R.string.send_message_error_network
                )
                Database.getInstance(
                    applicationContext
                ).markMessageNotSent(
                    message.databaseId
                )
                return
            }

            // Send the message using the VoIP.ms API. If we succeed, mark the
            // the message was sent. If not, mark it as not having been sent.
            val voipId = sendMessageWithVoipMsApi(message)
            if (voipId != null) {
                Database.getInstance(
                    applicationContext
                ).markMessageSent(
                    message.databaseId, voipId
                )
                markedAsSent = true
            } else {
                Database.getInstance(
                    applicationContext
                ).markMessageNotSent(
                    message.databaseId
                )
                markedAsNotSent = true
            }

            // If this request came from an inline reply, refresh the
            // notification.
            val inlineReply = inputData.getBoolean(
                applicationContext.getString(
                    R.string.send_message_inline_reply
                ), false
            )
            if (inlineReply) {
                val inlineReplyDid = inputData.getString(
                    applicationContext.getString(
                        R.string.send_message_inline_reply_did
                    )
                ) ?: return
                val inlineReplyContact = inputData.getString(
                    applicationContext.getString(
                        R.string.send_message_inline_reply_contact
                    )
                ) ?: return
                Notifications.getInstance(applicationContext).showNotifications(
                    setOf(ConversationId(inlineReplyDid, inlineReplyContact)),
                    inlineReplyMessages = listOf(message)
                )
            }
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.send_message_error_unknown
            )
        }
    }

    @JsonClass(generateAdapter = true)
    data class MessageResponse(val status: String, val sms: Long?)

    /**
     * Sends the specified message using the VoIP.ms API
     *
     * @return Null if the message could not be sent.
     */
    private suspend fun sendMessageWithVoipMsApi(message: Message): Long? {
        // Get JSON response from API
        val response: MessageResponse? = getMessageResponse(message)

        // Get VoIP.ms ID from response
        if (response?.status == "") {
            error = applicationContext.getString(
                R.string.send_message_error_api_parse
            )
            return null
        }
        if (response?.status != "success") {
            error = when (response?.status) {
                "invalid_credentials" -> applicationContext.getString(
                    R.string.send_message_error_api_error_invalid_credentials
                )
                "invalid_dst" -> applicationContext.getString(
                    R.string.send_message_error_api_error_invalid_dst
                )
                "invalid_sms" -> applicationContext.getString(
                    R.string.send_message_error_api_error_invalid_sms
                )
                "limit_reached" -> applicationContext.getString(
                    R.string.send_message_error_api_error_limit_reached
                )
                "message_empty" -> applicationContext.getString(
                    R.string.send_message_error_api_error_message_empty
                )
                "missing_sms" -> applicationContext.getString(
                    R.string.send_message_error_api_error_missing_sms
                )
                "sms_failed" -> applicationContext.getString(
                    R.string.send_message_error_api_error_sms_failed
                )
                "sms_toolong" -> applicationContext.getString(
                    R.string.send_message_error_api_error_sms_toolong
                )
                else -> applicationContext.getString(
                    R.string.send_message_error_api_error, response?.status
                )
            }
            return null
        }
        if (response.sms == 0L) {
            error = applicationContext.getString(
                R.string.send_message_error_api_parse
            )
            return null
        }
        return response.sms
    }

    private suspend fun getMessageResponse(message: Message): MessageResponse? {
        try {
            repeat(3) {
                try {
                    return httpPostWithMultipartFormData(
                        applicationContext,
                        "https://www.voip.ms/api/v1/rest.php",
                        mapOf(
                            "api_username" to getEmail(applicationContext),
                            "api_password" to getPassword(applicationContext),
                            "method" to "sendSMS",
                            "did" to message.did,
                            "dst" to message.contact,
                            "message" to message.text
                        )
                    )
                } catch (e: IOException) {
                    // Try again...
                }
            }
            error = applicationContext.getString(
                R.string.send_message_error_api_request
            )
            return null
        } catch (e: JsonDataException) {
            logException(e)
            error = applicationContext.getString(
                R.string.send_message_error_api_parse
            )
            return null
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.send_message_error_unknown
            )
            return null
        }
    }

    companion object {
        /**
         * Sends the message associated with the specified database ID.
         */
        fun sendMessage(
            context: Context, databaseId: Long,
            inlineReplyConversationId: ConversationId? = null
        ) {
            val work = OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setInputData(
                    workDataOf(
                        context.getString(
                            R.string.send_message_database_id
                        ) to databaseId,
                        context.getString(
                            R.string.send_message_inline_reply
                        )
                            to (inlineReplyConversationId != null),
                        context.getString(
                            R.string.send_message_inline_reply_did
                        )
                            to inlineReplyConversationId?.did,
                        context.getString(
                            R.string.send_message_inline_reply_contact
                        )
                            to inlineReplyConversationId?.contact
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                context.getString(R.string.send_message_work_id, databaseId),
                ExistingWorkPolicy.KEEP, work
            )
        }
    }
}
