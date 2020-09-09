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

package net.kourlas.voipms_sms.sms.services

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.preferences.getEmail
import net.kourlas.voipms_sms.preferences.getPassword
import net.kourlas.voipms_sms.preferences.setDids
import net.kourlas.voipms_sms.utils.enablePushNotifications
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import net.kourlas.voipms_sms.utils.replaceIndexOnNewThread
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Service used to retrieve DIDs for a particular account from VoIP.ms.
 *
 * This service is an IntentService rather than a JobIntentService because it
 * does not need to be run in the background.
 */
class RetrieveDidsService : IntentService(
    RetrieveDidsService::class.java.name) {
    private val okHttp = OkHttpClient()
    private val moshi: Moshi = Moshi.Builder().build()
    private var error: String? = null

    override fun onHandleIntent(intent: Intent?) {
        // Retrieve DIDs
        val dids = handleRetrieveDids(intent)

        // Send broadcast with DIDs
        val retrieveDidsCompleteIntent = Intent(
            applicationContext.getString(
                R.string.retrieve_dids_complete_action))
        retrieveDidsCompleteIntent.putExtra(getString(
            R.string.retrieve_dids_complete_error), error)
        retrieveDidsCompleteIntent.putStringArrayListExtra(
            getString(R.string.retrieve_dids_complete_dids),
            if (dids != null) ArrayList<String>(dids.toList()) else null)
        applicationContext.sendBroadcast(retrieveDidsCompleteIntent)
    }

    /**
     * Retrieves and returns the DIDs associated with the configured VoIP.ms
     * account using the parameters from the specified intent.
     *
     * @return Null if an error occurred.
     */
    private fun handleRetrieveDids(intent: Intent?): Set<String>? {
        // Retrieve DIDs from VoIP.ms API
        var dids: Set<String>? = null
        try {
            // Terminate quietly if intent does not exist or does not contain
            // the send SMS action
            if (intent == null || intent.action != applicationContext.getString(
                    R.string.retrieve_dids_action)) {
                return dids
            }

            // Terminate quietly if email and password are undefined
            if (getEmail(applicationContext) == ""
                || getPassword(applicationContext) == "") {
                return dids
            }

            val response = getApiResponse()
            if (response != null) {
                dids = getDidsFromResponse(response)
            }

            val autoAdd = intent.extras?.get(
                applicationContext.getString(R.string.retrieve_dids_auto_add))
                              as Boolean?
                          ?: throw Exception("Auto add missing")
            if (autoAdd && dids?.isNotEmpty() == true) {
                setDids(applicationContext,
                        getDids(applicationContext).plus(dids))
                enablePushNotifications(application)
                replaceIndexOnNewThread(applicationContext)
            }
        } catch (e: Exception) {
            logException(e)
        }
        return dids
    }

    @JsonClass(generateAdapter = true)
    data class DidResponse(
        @Json(name = "sms_enabled") val smsEnabled: String?,
        val did: String)

    @JsonClass(generateAdapter = true)
    data class DidsResponse(
        val status: String,
        @Suppress("ArrayInDataClass") val dids: List<DidResponse>?)

    /**
     * Gets the response of a getDIDsInfo call to the VoIP.ms API.
     *
     * @return Null if an error occurred.
     */
    private fun getApiResponse(): DidsResponse? {
        try {
            return httpPostWithMultipartFormData(
                applicationContext, okHttp, moshi,
                "https://www.voip.ms/api/v1/rest.php",
                mapOf("api_username" to getEmail(applicationContext),
                      "api_password" to getPassword(applicationContext),
                      "method" to "getDIDsInfo"))
        } catch (e: IOException) {
            error = applicationContext.getString(
                R.string.preferences_dids_error_api_request)
            return null
        } catch (e: JsonDataException) {
            logException(e)
            error = applicationContext.getString(
                R.string.preferences_dids_error_api_parse)
            return null
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.preferences_dids_error_unknown)
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
            error = when (response.status) {
                "invalid_credentials" -> applicationContext.getString(
                    R.string.preferences_dids_error_api_error_invalid_credentials)
                else -> applicationContext.getString(
                    R.string.preferences_dids_error_api_error,
                    response.status)
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
        fun startService(context: Context, autoAdd: Boolean = false) {
            val intent = Intent(context, RetrieveDidsService::class.java)
            intent.action = context.getString(R.string.retrieve_dids_action)
            intent.putExtra(context.getString(R.string.retrieve_dids_auto_add),
                            autoAdd)

            context.startService(intent)
        }
    }
}