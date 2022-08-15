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

package net.kourlas.voipms_sms.notifications.workers

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CancellationException
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import java.io.IOException

/**
 * Service that registers a VoIP.ms callback for each DID.
 */
class NotificationsRegistrationWorker(
    context: Context,
    params: WorkerParameters
) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Terminate quietly if notifications are not enabled
        if (!Notifications.getInstance(applicationContext)
                .getNotificationsEnabled()
        ) {
            return Result.success()
        }

        // Terminate quietly if account or DIDs are not configured
        if (!didsConfigured(applicationContext)
            || !accountConfigured(applicationContext)
        ) {
            return Result.success()
        }

        val dids = getDids(applicationContext, onlyShowNotifications = true)
        var callbackFailedDids: Set<String>? = null
        try {
            // Try to register URL callbacks with VoIP.ms for DIDs
            val responses = getVoipMsApiCallbackResponses(dids)
            callbackFailedDids = parseVoipMsApiCallbackResponses(
                dids,
                responses
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logException(e)
        }

        // Send broadcast with DIDs that failed registration
        val registrationCompleteIntent = Intent(
            applicationContext.getString(
                R.string.push_notifications_reg_complete_action
            )
        )
        registrationCompleteIntent.putStringArrayListExtra(
            applicationContext.getString(
                R.string.push_notifications_reg_complete_failed_dids
            ),
            if (callbackFailedDids != null)
                ArrayList<String>(
                    callbackFailedDids.toList()
                ) else null
        )
        applicationContext.sendBroadcast(registrationCompleteIntent)

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notifications.getInstance(applicationContext)
            .getSyncRegisterPushNotificationsNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Notifications.SYNC_REGISTER_PUSH_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                Notifications.SYNC_REGISTER_PUSH_NOTIFICATION_ID,
                notification
            )
        }
    }

    @JsonClass(generateAdapter = true)
    data class RegisterResponse(val status: String)

    /**
     * Gets the response of a setSMS call to the VoIP.ms API for each DID.
     */
    private suspend fun getVoipMsApiCallbackResponses(
        dids: Set<String>
    ): Map<String, List<RegisterResponse?>> {
        val responses = mutableMapOf<String, MutableList<RegisterResponse?>>()
        for (did in dids) {
            try {
                // Work around a bug in the VoIP.ms API by disabling SMS for
                // the DID before changing the URL callback.
                val didResponses = mutableListOf<RegisterResponse?>()
                didResponses.add(
                    httpPostWithMultipartFormData(
                        applicationContext,
                        "https://www.voip.ms/api/v1/rest.php",
                        mapOf(
                            "api_username" to getEmail(applicationContext),
                            "api_password" to getPassword(applicationContext),
                            "method" to "setSMS",
                            "did" to did,
                            "enable" to "0",
                            "url_callback_enable" to "0",
                            "url_callback" to "",
                            "url_callback_retry" to "0"
                        )
                    )
                )
                didResponses.add(
                    httpPostWithMultipartFormData(
                        applicationContext,
                        "https://www.voip.ms/api/v1/rest.php",
                        mapOf(
                            "api_username" to getEmail(applicationContext),
                            "api_password" to getPassword(applicationContext),
                            "method" to "setSMS",
                            "did" to did,
                            "enable" to "1",
                            "url_callback_enable" to "1",
                            ("url_callback"
                                to "https://us-south.functions.appdomain.cloud/"
                                + "api/v1/web/michael%40kourlas.com_dev/default/"
                                + "voipmssms-notify?did={TO}"),
                            "url_callback_retry" to "0"
                        )
                    )
                )
                responses[did] = didResponses
            } catch (e: IOException) {
                // Do nothing.
            } catch (e: JsonDataException) {
                logException(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logException(e)
            }
        }
        return responses
    }

    /**
     * Parses the specified responses from several setSMS calls to the VoIP.ms
     * API to ensure that they all succeeded.
     *
     * @return A list of any DIDs that failed the setSMS call.
     */
    private fun parseVoipMsApiCallbackResponses(
        dids: Set<String>,
        responses: Map<String, List<RegisterResponse?>>
    ): Set<String> {
        val failedDids = mutableSetOf<String>()
        for (did in dids) {
            if (did !in responses) {
                failedDids.add(did)
                continue
            }

            val didResponses = responses[did]
            if (didResponses != null) {
                for (response in didResponses) {
                    if (response == null || response.status != "success") {
                        failedDids.add(did)
                        continue
                    }
                }
            }
        }
        return failedDids
    }

    companion object {
        /**
         * Registers a VoIP.ms callback for each DID.
         */
        fun registerForPushNotifications(context: Context) {
            val work =
                OneTimeWorkRequestBuilder<NotificationsRegistrationWorker>().setExpedited(
                    OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
                )
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                context.getString(R.string.push_notifications_work_id),
                ExistingWorkPolicy.REPLACE, work
            )
        }
    }
}
