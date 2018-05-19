/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2018 Michael Kourlas
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

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.crashlytics.android.Crashlytics
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.getJson
import net.kourlas.voipms_sms.utils.isNetworkConnectionAvailable
import net.kourlas.voipms_sms.utils.toBoolean
import net.kourlas.voipms_sms.utils.validatePhoneNumber
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service used to synchronize the database with VoIP.ms.
 *
 * SyncService is an IntentService rather than a JobIntentService because
 * it is a foreground service that uses a notification to indicate
 * synchronization progress. This is mainly to prevent Android from killing
 * the service.
 */
class SyncService : IntentService(
    SyncService::class.java.name) {
    private var error: String? = null

    override fun onHandleIntent(intent: Intent?) {
        // Terminate quietly if intent does not exist or does not contain
        // the sync action
        if (intent == null || intent.action != applicationContext.getString(
                R.string.sync_action)) {
            return
        }

        // Terminate quietly if account inactive
        if (!isAccountActive(applicationContext)) {
            return
        }

        val rand = Random().nextInt().toString(16)
        Log.i(SyncService::class.java.name, "[$rand] starting synchronization")

        // Show notification during synchronization to prevent phone from
        // going to sleep
        val notification = Notifications.getInstance(application)
            .getSyncNotification()
        startForeground(Notifications.SYNC_NOTIFICATION_ID, notification)

        // Perform synchronization
        handleSync(intent)

        // Send a broadcast indicating that the database has been
        // synchronized (or an attempt has been made to synchronize it)
        val syncCompleteBroadcastIntent = Intent(
            applicationContext.getString(
                R.string.sync_complete_action))
        syncCompleteBroadcastIntent.putExtra(getString(
            R.string.sync_complete_error), error)
        if (intent.extras?.get(getString(
                R.string.sync_force_recent)) != true) {
            syncCompleteBroadcastIntent.putExtra(getString(
                R.string.sync_complete_full), true)
        }
        applicationContext.sendBroadcast(syncCompleteBroadcastIntent)

        Log.i(SyncService::class.java.name, "[$rand] completed synchronization")

        stopForeground(true)
    }

    /**
     * Perform synchronization.
     */
    private fun handleSync(intent: Intent) {
        try {
            // Extract the boolean properties from the intent
            val forceRecent = intent.extras.get(
                applicationContext.getString(R.string.sync_force_recent))
                                  as Boolean?
                              ?: throw Exception("Force recent missing")

            // Terminate with a toast if no network connection is available
            if (!isNetworkConnectionAvailable(applicationContext)) {
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
            Crashlytics.logException(e)
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

        val encodedEmail = URLEncoder.encode(getEmail(applicationContext),
                                             "UTF-8")
        val encodedPassword = URLEncoder.encode(getPassword(applicationContext),
                                                "UTF-8")
        val encodedDids = getDids(applicationContext)
            .map { URLEncoder.encode(it, "UTF-8") }

        // Get number of days between now and the message retrieval start
        // date or when the most recent message was received, as appropriate;
        // note that EDT is used throughout because the VoIP.ms API only works
        // with EDT
        val mostRecentMessage = Database.getInstance(
            applicationContext)
            .getMessageMostRecent(
                getDids(applicationContext))
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
        val daysDifference = Math.ceil(
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
            val encodedDateFrom = URLEncoder.encode(sdf.format(period.first),
                                                    "UTF-8")
            val encodedDateTo = URLEncoder.encode(sdf.format(period.second),
                                                  "UTF-8")
            encodedDids
                .map {
                    "https://www.voip.ms/api/v1/rest.php?" +
                    "api_username=$encodedEmail" +
                    "&api_password=$encodedPassword&method=getSMS" +
                    "&did=$it&limit=1000000" +
                    "&from=$encodedDateFrom&to=$encodedDateTo" +
                    "&timezone=-5" // -5 corresponds to EDT
                }
                .mapTo(retrievalRequests) {
                    RetrievalRequest(
                        it, period)
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
        for (i in 0 until retrievalRequests.size) {
            val nextIncomingMessages = processRetrievalRequest(
                retrievalRequests[i])
            if (nextIncomingMessages != null) {
                incomingMessages.addAll(nextIncomingMessages)
            } else {
                return
            }

            val notification = Notifications.getInstance(application)
                .getSyncNotification(((i + 1) * 100) / retrievalRequests.size)
            NotificationManagerCompat.from(applicationContext).notify(
                Notifications.SYNC_NOTIFICATION_ID, notification)
        }

        // Add new messages from the server
        val newConversationIds: Set<ConversationId>
        try {
            newConversationIds = Database.getInstance(
                applicationContext)
                .insertMessagesVoipMsApi(incomingMessages,
                                         retrieveDeletedMessages)
        } catch (e: Exception) {
            Crashlytics.logException(e)
            error = applicationContext.getString(
                R.string.sync_error_database)
            return
        }

        // Show notifications for new messages
        if (newConversationIds.isNotEmpty()) {
            Notifications.getInstance(application).showNotifications(
                newConversationIds)
        }
    }

    /**
     * Processes the specified retrieval request using the VoIP.ms API.
     *
     * @return Null if the request failed.
     */
    private fun processRetrievalRequest(
        request: RetrievalRequest): List<IncomingMessage>? {
        val response = sendRequestWithVoipMsApi(request.url) ?: return null

        // Extract messages from the VoIP.ms API response
        val incomingMessages = mutableListOf<IncomingMessage>()
        val status = response.optString("status")
        if (status != "success" && status != "no_sms") {
            error = applicationContext.getString(R.string.sync_error_api_error,
                                                 status)
            return null
        }

        if (status != "no_sms") {
            val rawMessages = response.optJSONArray("sms")
            if (rawMessages == null) {
                error = applicationContext.getString(
                    R.string.sync_error_api_parse)
                return null
            }
            for (i in 0 until rawMessages.length()) {
                val rawSms = rawMessages.optJSONObject(i)
                if (rawSms == null) {
                    error = applicationContext.getString(
                        R.string.sync_error_api_parse)
                    return null
                }

                val rawDate = rawSms.getString("date")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("America/New_York")

                try {
                    val incomingMessage = IncomingMessage(
                        rawSms.getString("id").toLong(), sdf.parse(rawDate),
                        toBoolean(rawSms.getString("type")),
                        rawSms.getString("did"), rawSms.getString("contact"),
                        rawSms.getString("message"))
                    incomingMessages.add(incomingMessage)
                } catch (e: Exception) {
                    Crashlytics.logException(e)
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
    private fun sendRequestWithVoipMsApi(url: String): JSONObject? {
        val response: JSONObject
        try {
            response = getJson(applicationContext, url)
        } catch (e: IOException) {
            error = applicationContext.getString(
                R.string.sync_error_api_request)
            return null
        } catch (e: JSONException) {
            Crashlytics.logException(e)
            error = applicationContext.getString(R.string.sync_error_api_parse)
            return null
        } catch (e: Exception) {
            Crashlytics.logException(e)
            error = applicationContext.getString(R.string.sync_error_unknown)
            return null
        }

        return response
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
    data class RetrievalRequest(val url: String,
                                private val period: Pair<Date, Date>)

    companion object {
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
    }
}
