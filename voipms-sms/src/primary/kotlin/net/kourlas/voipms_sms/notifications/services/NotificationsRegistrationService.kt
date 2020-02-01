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

package net.kourlas.voipms_sms.notifications.services

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.google.gson.JsonSyntaxException
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.utils.getJson
import net.kourlas.voipms_sms.utils.logException
import java.io.IOException
import java.net.URLEncoder

/**
 * Service that registers a VoIP.ms callback for each DID.
 *
 * This service is an IntentService rather than a JobIntentService because it
 * does not need to be run in the background.
 */
class NotificationsRegistrationService : IntentService(
    NotificationsRegistrationService::class.java.name) {
    override fun onHandleIntent(intent: Intent?) {
        // Terminate quietly if intent does not exist or does not contain
        // the correct action
        if (intent == null || intent.action != applicationContext.getString(
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

    data class RegisterResponse(val status: String)

    /**
     * Gets the response of a setSMS call to the VoIP.ms API for each DID.
     */
    private fun getVoipMsApiCallbackResponses(
        dids: Set<String>): Map<String, RegisterResponse?> {
        val responses = mutableMapOf<String, RegisterResponse?>()
        for (did in dids) {
            try {
                val registerVoipCallbackUrl =
                    "https://www.voip.ms/api/v1/rest.php?" +
                    "api_username=" + URLEncoder.encode(
                        getEmail(applicationContext), "UTF-8") + "&" +
                    "api_password=" + URLEncoder.encode(
                        getPassword(applicationContext), "UTF-8") + "&" +
                    "method=setSMS" + "&" +
                    "did=" + URLEncoder.encode(
                        did, "UTF-8") + "&" +
                    "enable=1" + "&" +
                    "url_callback_enable=1" + "&" +
                    "url_callback=" + URLEncoder.encode(
                        "https://us-central1-voip-ms-sms-9ee2b" +
                        ".cloudfunctions.net/notify?did={TO}", "UTF-8") + "&" +
                    "url_callback_retry=0"
                responses[did] = getJson(applicationContext,
                                         registerVoipCallbackUrl)
            } catch (e: IOException) {
                // Do nothing.
            } catch (e: JsonSyntaxException) {
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
