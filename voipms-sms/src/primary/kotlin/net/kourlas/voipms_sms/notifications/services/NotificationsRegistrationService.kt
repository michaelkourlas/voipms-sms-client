/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2020 Michael Kourlas
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

package net.kourlas.voipms_sms.notifications.services

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import java.io.IOException

/**
 * Service that registers a VoIP.ms callback for each DID.
 */
class NotificationsRegistrationService : JobIntentService() {
    private val moshi: Moshi = Moshi.Builder().build()

    override fun onHandleWork(intent: Intent) {
        // Terminate quietly if intent does not exist or does not contain
        // the correct action
        if (intent.action != applicationContext.getString(
                R.string.push_notifications_reg_action)) {
            return
        }

        // Terminate quietly if notifications are not enabled
        if (!Notifications.getInstance(application).getNotificationsEnabled()) {
            return
        }

        // Terminate quietly if account or DIDs are not configured
        if (!didsConfigured(applicationContext)
            || !accountConfigured(applicationContext)) {
            return
        }

        val dids = getDids(applicationContext, onlyShowNotifications = true)
        var callbackFailedDids: Set<String>? = null
        try {
            // Try to register URL callbacks with VoIP.ms for DIDs
            val responses = getVoipMsApiCallbackResponses(dids)
            callbackFailedDids = parseVoipMsApiCallbackResponses(dids,
                                                                 responses)
        } catch (e: Exception) {
            logException(e)
        }

        // Send broadcast with DIDs that failed registration
        val registrationCompleteIntent = Intent(
            applicationContext.getString(
                R.string.push_notifications_reg_complete_action))
        registrationCompleteIntent.putStringArrayListExtra(
            getString(
                R.string.push_notifications_reg_complete_failed_dids),
            if (callbackFailedDids != null)
                ArrayList<String>(
                    callbackFailedDids.toList()) else null)
        applicationContext.sendBroadcast(registrationCompleteIntent)
    }

    @JsonClass(generateAdapter = true)
    data class RegisterResponse(val status: String)

    /**
     * Gets the response of a setSMS call to the VoIP.ms API for each DID.
     */
    private fun getVoipMsApiCallbackResponses(
        dids: Set<String>): Map<String, RegisterResponse?> {
        val responses = mutableMapOf<String, RegisterResponse?>()
        for (did in dids) {
            try {
                responses[did] = httpPostWithMultipartFormData(
                    applicationContext,
                    (application as CustomApplication).okHttpClient, moshi,
                    "https://www.voip.ms/api/v1/rest.php",
                    mapOf("api_username" to getEmail(applicationContext),
                          "api_password" to getPassword(applicationContext),
                          "method" to "setSMS",
                          "did" to did,
                          "enable" to "1",
                          "url_callback_enable" to "1",
                          ("url_callback"
                              to "https://us-central1-voip-ms-sms-9ee2b"
                              + ".cloudfunctions.net/notify?did={TO}"),
                          "url_callback_retry" to "0"))
            } catch (e: IOException) {
                // Do nothing.
            } catch (e: JsonDataException) {
                logException(e)
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
        responses: Map<String, RegisterResponse?>): Set<String> {
        val failedDids = mutableSetOf<String>()
        for (did in dids) {
            if (did !in responses
                || responses[did]?.status != "success") {
                failedDids.add(did)
            }
        }
        return failedDids
    }

    companion object {
        /**
         * Registers a VoIP.ms callback for each DID.
         */
        fun startService(context: Context) {
            val intent = Intent(
                context,
                NotificationsRegistrationService::class.java)
            intent.action = context.getString(
                R.string.push_notifications_reg_action)
            context.startService(intent)
        }
    }
}
