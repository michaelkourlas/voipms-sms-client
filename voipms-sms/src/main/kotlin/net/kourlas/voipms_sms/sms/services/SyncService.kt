/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
 *
 * Portions Copyright (C) 2008 The Android Open Source Project
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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.network.NetworkManager
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import net.kourlas.voipms_sms.utils.toBoolean
import net.kourlas.voipms_sms.utils.validatePhoneNumber
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/**
 * Service used to synchronize the database with VoIP.ms.
 *
 * SyncService is a Service rather than a JobIntentService because
 * it is a foreground service that uses a notification to indicate
 * synchronization progress. This is mainly to prevent Android from killing
 * the service.
 */
class SyncService : Service() {
    @Volatile
    private lateinit var serviceLooper: Looper

    @Volatile
    private lateinit var serviceHandler: ServiceHandler

    private var error: String? = null
    private var progress: Int = 0

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            onHandleIntent(msg.obj as Intent)
            stopSelfResult(msg.arg1)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread(SyncService::class.java.name)
        thread.start()
        serviceLooper = thread.looper
        serviceHandler = ServiceHandler(serviceLooper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int,
                                startId: Int): Int {
        // Void any pending cancellation request
        requestCancellation.set(false)

        // Show notification during synchronization to prevent phone from
        // going to sleep
        synchronized(this) {
            // Use the existing progress value since the service may already
            // be running
            val notification = Notifications.getInstance(applicationContext)
                .getSyncNotification(progress)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Notifications.SYNC_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(
                    Notifications.SYNC_NOTIFICATION_ID,
                    notification)
            }
        }

        val msg: Message = serviceHandler.obtainMessage()
        msg.arg1 = startId
        msg.obj = intent
        serviceHandler.sendMessage(msg)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceLooper.quit()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @WorkerThread
    fun onHandleIntent(intent: Intent?) {
        val rand = Random().nextInt().toString(16)
        Log.i(SyncService::class.java.name, "[$rand] starting synchronization")

        synchronized(this) {
            progress = 0
            val notification = Notifications.getInstance(applicationContext)
                .getSyncNotification(progress)
            NotificationManagerCompat.from(applicationContext).notify(
                Notifications.SYNC_NOTIFICATION_ID, notification)
        }

        // Perform synchronization
        handleSync(intent)

        // Send a broadcast indicating that the database has been
        // synchronized (or an attempt has been made to synchronize it)
        val syncCompleteBroadcastIntent = Intent(
            applicationContext.getString(
                R.string.sync_complete_action))
        syncCompleteBroadcastIntent.putExtra(getString(
            R.string.sync_complete_error), error)
        if (intent?.extras?.get(getString(
                R.string.sync_force_recent)) != true) {
            syncCompleteBroadcastIntent.putExtra(getString(
                R.string.sync_complete_full), true)
        }
        applicationContext.sendBroadcast(syncCompleteBroadcastIntent)

        Log.i(SyncService::class.java.name, "[$rand] completed synchronization")

        progress = 0
    }

    /**
     * Perform synchronization.
     */
    private fun handleSync(intent: Intent?) {
        try {
            // Terminate quietly if intent does not exist or does not contain
            // the sync action
            if (intent == null || intent.action != applicationContext.getString(
                    R.string.sync_action)) {
                return
            }

            // Terminate quietly if account inactive
            if (!accountConfigured(applicationContext) || !didsConfigured(
                    applicationContext)) {
                return
            }

            // Extract the properties from the intent
            val forceRecent = intent.extras?.get(
                applicationContext.getString(R.string.sync_force_recent))
                                  as Boolean?
                              ?: throw Exception("Force recent missing")

            // Terminate with a toast if no network connection is available
            if (!NetworkManager.getInstance().isNetworkConnectionAvailable(
                    applicationContext)) {
                error = applicationContext.getString(
                    R.string.sync_error_network)
                return
            }

            // Retrieve all messages from VoIP.ms, or only those messages
            // dated after the most recent message stored locally
            val retrieveOnlyRecentMessages =
                forceRecent || getRetrieveOnlyRecentMessages(applicationContext)
            // Retrieve messages from VoIP.ms that were deleted locally
            val retrieveDeletedMessages =
                !forceRecent && getRetrieveDeletedMessages(applicationContext)

            // Process retrieval requests separately
            val retrievalRequests = createRetrievalRequests(
                retrieveOnlyRecentMessages)
            processRequests(retrievalRequests, retrieveDeletedMessages)

            // If this was not an intentionally limited database
            // synchronization, set a new alarm for the next sync
            if (!forceRecent) {
                setLastCompleteSyncTime(applicationContext,
                                        System.currentTimeMillis())
                SyncIntervalService.startService(
                    applicationContext)
            }
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.sync_error_unknown)
        }
    }

    /**
     * Generates a set of requests for retrieving messages from VoIP.ms.
     *
     * @param retrieveOnlyRecentMessages Whether or not the constructed
     * retrieval requests should date back to the most recent message or the
     * message retrieval start date.
     */
    private fun createRetrievalRequests(
        retrieveOnlyRecentMessages: Boolean): List<RetrievalRequest> {
        val retrievalRequests = mutableListOf<RetrievalRequest>()

        val dids = getDids(applicationContext, onlyRetrieveMessages = true)

        val encodedDids = dids.map { URLEncoder.encode(it, "UTF-8") }

        // Get number of days between now and the message retrieval start
        // date or when the most recent message was received, as appropriate;
        // note that EDT is used throughout because the VoIP.ms API only works
        // with EDT
        val mostRecentMessage = Database.getInstance(applicationContext)
            .getMessageMostRecent(dids)
        val thenCalendar = Calendar.getInstance(
            TimeZone.getTimeZone("America/New_York"), Locale.US)
        thenCalendar.time = if (mostRecentMessage == null
                                || !retrieveOnlyRecentMessages) {
            getStartDate(applicationContext)
        } else {
            mostRecentMessage.date
        }
        thenCalendar.set(Calendar.HOUR_OF_DAY, 0)
        thenCalendar.set(Calendar.MINUTE, 0)
        thenCalendar.set(Calendar.SECOND, 0)
        thenCalendar.set(Calendar.MILLISECOND, 0)
        val then = thenCalendar.time
        val nowCalendar = Calendar.getInstance(
            TimeZone.getTimeZone("America/New_York"), Locale.US)
        nowCalendar.time = Date()
        nowCalendar.set(Calendar.HOUR_OF_DAY, 0)
        nowCalendar.set(Calendar.MINUTE, 0)
        nowCalendar.set(Calendar.SECOND, 0)
        nowCalendar.set(Calendar.MILLISECOND, 0)
        val now = nowCalendar.time
        val daysDifference = ceil(
            ((now.time - then.time) / (1000 * 60 * 60 * 24)).toDouble())
            .toLong()

        // Split this number into 90 day periods (which is approximately the
        // maximum supported by the VoIP.ms API); note that the first period
        // is intentionally the oldest
        var daysRemaining = daysDifference
        val periods = mutableListOf<Pair<Date, Date>>()
        while (daysRemaining > 90L) {
            val last = if (periods.isEmpty()) then else periods.last().second
            val calendar = Calendar.getInstance(
                TimeZone.getTimeZone("America/New_York"), Locale.US)
            calendar.time = last
            calendar.add(Calendar.DAY_OF_YEAR, 90)
            periods.add(Pair(last, calendar.time))
            daysRemaining -= 90L
        }
        if (periods.isEmpty()) {
            periods.add(Pair(then, now))
        } else {
            periods.add(Pair(periods.last().second, now))
        }

        // Create VoIP.ms API retrieval request for each of these periods
        // and for each DID
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("America/New_York")
        for (period in periods) {
            encodedDids
                .map {
                    mapOf("api_username" to getEmail(applicationContext),
                          "api_password" to getPassword(applicationContext),
                          "method" to "getSMS",
                          "did" to it,
                          "limit" to "1000000",
                          "from" to sdf.format(period.first),
                          "to" to sdf.format(period.second),
                          "timezone" to "-5") // -5 corresponds to EDT
                }
                .mapTo(retrievalRequests) {
                    RetrievalRequest(it, period)
                }
        }

        return retrievalRequests
    }

    /**
     * Processes the specified requests using the VoIP.ms API.
     *
     * @param retrievalRequests The retrieval requests to process.
     * @param retrieveDeletedMessages If true, messages are retrieved from
     * VoIP.ms even after being deleted locally.
     */
    private fun processRequests(retrievalRequests: List<RetrievalRequest>,
                                retrieveDeletedMessages: Boolean) {
        val incomingMessages = mutableListOf<IncomingMessage>()
        for (i in retrievalRequests.indices) {
            if (requestCancellation.get()) {
                break
            }

            val nextIncomingMessages = processRetrievalRequest(
                retrievalRequests[i])
            if (nextIncomingMessages != null) {
                incomingMessages.addAll(nextIncomingMessages)
            } else {
                return
            }

            synchronized(this) {
                progress = ((i + 1) * 100) / retrievalRequests.size
                val notification = Notifications.getInstance(applicationContext)
                    .getSyncNotification(progress)
                NotificationManagerCompat.from(applicationContext).notify(
                    Notifications.SYNC_NOTIFICATION_ID, notification)
            }
        }

        // Add new messages from the server
        val newConversationIds: Set<ConversationId>
        try {
            newConversationIds = Database.getInstance(
                applicationContext)
                .insertMessagesVoipMsApi(incomingMessages,
                                         retrieveDeletedMessages)
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.sync_error_database)
            return
        }

        // Show notifications for new messages
        if (newConversationIds.isNotEmpty()) {
            Notifications.getInstance(applicationContext).showNotifications(
                application as CustomApplication,
                newConversationIds)
        }
    }

    @JsonClass(generateAdapter = true)
    data class MessageResponse(
        val date: String,
        val id: String,
        val type: String,
        val did: String,
        val contact: String,
        val message: String?)

    @JsonClass(generateAdapter = true)
    data class MessagesResponse(
        val status: String,
        @Suppress("ArrayInDataClass") val sms: List<MessageResponse>?)

    /**
     * Processes the specified retrieval request using the VoIP.ms API.
     *
     * @return Null if the request failed.
     */
    private fun processRetrievalRequest(
        request: RetrievalRequest): List<IncomingMessage>? {
        val response = sendRequestWithVoipMsApi(request) ?: return null

        // Extract messages from the VoIP.ms API response
        val incomingMessages = mutableListOf<IncomingMessage>()
        if (response.status != "success" && response.status != "no_sms") {
            error = when (response.status) {
                "invalid_credentials" -> applicationContext.getString(
                    R.string.sync_error_api_error_invalid_credentials)
                else -> applicationContext.getString(
                    R.string.sync_error_api_error,
                    response.status)
            }
            return null
        }

        if (response.status != "no_sms") {
            for (message in response.sms ?: emptyList()) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("America/New_York")

                try {
                    if (message.message == null) {
                        // This is probably an MMS message, which we don't
                        // support yet.
                        continue
                    }
                    val incomingMessage = IncomingMessage(
                        message.id.toLong(),
                        sdf.parse(message.date) ?: throw Exception(
                            "Failed to parse date ${message.date}"),
                        toBoolean(message.type),
                        message.did,
                        message.contact,
                        message.message)
                    incomingMessages.add(incomingMessage)
                } catch (e: Exception) {
                    logException(e)
                    error = applicationContext.getString(
                        R.string.sync_error_api_parse)
                    return null
                }
            }
        }

        return incomingMessages
    }

    /**
     * Performs a GET request using the specified url and parses the response
     * as JSON.
     */
    private fun sendRequestWithVoipMsApi(
        request: RetrievalRequest): MessagesResponse? {
        try {
            return httpPostWithMultipartFormData(
                applicationContext,
                "https://www.voip.ms/api/v1/rest.php",
                request.formData)
        } catch (e: IOException) {
            error = applicationContext.getString(
                R.string.sync_error_api_request)
            return null
        } catch (e: JsonDataException) {
            logException(e)
            error = applicationContext.getString(R.string.sync_error_api_parse)
            return null
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(R.string.sync_error_unknown)
            return null
        }
    }

    /**
     * A message received from the VoIP.ms API.
     *
     * @param voipId The VoIP.ms ID associated with the message.
     * @param date The date that the message was received.
     * @param isIncoming Whether or not the message was received or sent by the
     * DID associated with the message.
     * @param did The DID associated with the message.
     * @param contact The contact associated with the message.
     * @param text The text of the message.
     */
    data class IncomingMessage(val voipId: Long, val date: Date,
                               val isIncoming: Boolean, val did: String,
                               val contact: String, val text: String) {
        init {
            validatePhoneNumber(did)
            validatePhoneNumber(contact)
        }
    }

    /**
     * A request to the VoIP.ms API to retrieve the messages from the specified
     * period using the specified URL.
     */
    data class RetrievalRequest(val formData: Map<String, String>,
                                private val period: Pair<Date, Date>)

    companion object {
        private val requestCancellation: AtomicBoolean = AtomicBoolean(false)

        /**
         * Synchronize the database with VoIP.ms.
         *
         * @param forceRecent If true, retrieves only the most recent messages
         * regardless of the app configuration.
         */
        fun startService(context: Context, forceRecent: Boolean = false) {
            val intent = Intent(context, SyncService::class.java)
            intent.action = context.getString(R.string.sync_action)
            intent.putExtra(context.getString(R.string.sync_force_recent),
                            forceRecent)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Gets an intent which can be used to cancel the current
         * synchronization.
         */
        fun getCancelIntent(context: Context): Intent {
            val intent = Intent()
            intent.action = context.getString(R.string.sync_cancel_action)
            return intent
        }

        /**
         * Requests that the current synchronization be cancelled.
         */
        fun requestCancellation() {
            requestCancellation.set(true)
        }
    }
}
