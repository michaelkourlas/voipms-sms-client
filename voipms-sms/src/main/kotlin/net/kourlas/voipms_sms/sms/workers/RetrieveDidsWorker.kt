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
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.preferences.getEmail
import net.kourlas.voipms_sms.preferences.getPassword
import net.kourlas.voipms_sms.preferences.setDids
import net.kourlas.voipms_sms.utils.enablePushNotifications
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import net.kourlas.voipms_sms.utils.replaceIndex
import java.io.IOException

/**
 * Worker used to retrieve DIDs for a particular account from VoIP.ms.
 */
class RetrieveDidsWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private var error: String? = null

    override suspend fun doWork(): Result {
        // Retrieve DIDs.
        val dids = retrieveDids()

        // Send broadcast with DIDs.
        val retrieveDidsCompleteIntent = Intent(
            applicationContext.getString(
                R.string.retrieve_dids_complete_action
            )
        )
        retrieveDidsCompleteIntent.putExtra(
            applicationContext.getString(
                R.string.retrieve_dids_complete_error
            ), error
        )
        retrieveDidsCompleteIntent.putStringArrayListExtra(
            applicationContext.getString(R.string.retrieve_dids_complete_dids),
            if (dids != null) ArrayList<String>(dids.toList()) else null
        )
        applicationContext.sendBroadcast(retrieveDidsCompleteIntent)

        return if (error == null) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notifications.getInstance(applicationContext)
            .getSyncRetrieveDidsNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Notifications.SYNC_RETRIEVE_DIDS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                Notifications.SYNC_RETRIEVE_DIDS_NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Retrieves and returns the DIDs associated with the configured VoIP.ms
     * account using the parameters from the specified intent.
     *
     * @return Null if an error occurred.
     */
    private suspend fun retrieveDids(): Set<String>? = coroutineScope {
        // Retrieve DIDs from VoIP.ms API
        var dids: Set<String>? = null
        try {
            // Terminate quietly if email and password are undefined.
            if (getEmail(applicationContext) == ""
                || getPassword(applicationContext) == ""
            ) {
                return@coroutineScope dids
            }

            val response = getApiResponse()
            if (response != null) {
                dids = getDidsFromResponse(response)
            }

            val autoAdd = inputData.getBoolean(
                applicationContext.getString(R.string.retrieve_dids_auto_add),
                false
            )
            if (autoAdd && dids?.isNotEmpty() == true) {
                setDids(
                    applicationContext,
                    getDids(applicationContext).plus(dids)
                )
                enablePushNotifications(applicationContext)
                launch {
                    replaceIndex(applicationContext)
                }
            }
        } catch (e: Exception) {
            logException(e)
        }
        return@coroutineScope dids
    }

    @JsonClass(generateAdapter = true)
    data class DidResponse(
        @Json(name = "sms_enabled") val smsEnabled: String?,
        val did: String
    )

    @JsonClass(generateAdapter = true)
    data class DidsResponse(
        val status: String,
        @Suppress("ArrayInDataClass") val dids: List<DidResponse>?
    )

    /**
     * Gets the response of a getDIDsInfo call to the VoIP.ms API.
     *
     * @return Null if an error occurred.
     */
    private suspend fun getApiResponse(): DidsResponse? {
        try {
            repeat(3) {
                try {
                    return httpPostWithMultipartFormData(
                        applicationContext,
                        "https://www.voip.ms/api/v1/rest.php",
                        mapOf(
                            "api_username" to getEmail(applicationContext),
                            "api_password" to getPassword(applicationContext),
                            "method" to "getDIDsInfo"
                        )
                    )
                } catch (e: IOException) {
                    // Try again...
                }
            }
            error = applicationContext.getString(
                R.string.preferences_dids_error_api_request
            )
            return null
        } catch (e: JsonDataException) {
            logException(e)
            error = applicationContext.getString(
                R.string.preferences_dids_error_api_parse
            )
            return null
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.preferences_dids_error_unknown
            )
            return null
        }
    }

    /**
     * Parses the response of a getDIDsInfo from the VoIP.ms API to
     * extract all DIDs with SMS enabled.
     *
     * @return Null if an error occurred.
     */
    private fun getDidsFromResponse(response: DidsResponse): Set<String>? {
        if (response.status != "success") {
            if (response.status == "no_did") {
                return emptySet()
            }
            error = when (response.status) {
                "invalid_credentials" -> applicationContext.getString(
                    R.string.preferences_dids_error_api_error_invalid_credentials
                )
                else -> applicationContext.getString(
                    R.string.preferences_dids_error_api_error,
                    response.status
                )
            }
            return null
        }

        return response.dids
            ?.filter { it.smsEnabled == "1" }
            ?.map { it.did }
            ?.toSet()
    }

    companion object {
        /**
         * Retrieve DIDs for a particular account from VoIP.ms.
         */
        fun retrieveDids(context: Context, autoAdd: Boolean = false) {
            val work = OneTimeWorkRequestBuilder<RetrieveDidsWorker>()
                .setInputData(
                    workDataOf(
                        context.getString(
                            R.string.retrieve_dids_auto_add
                        ) to autoAdd
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                context.getString(R.string.retrieve_dids_work_id),
                ExistingWorkPolicy.REPLACE, work
            )
        }
    }
}