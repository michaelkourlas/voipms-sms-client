/*
 * VoIP.ms SMS
 * Copyright (C) 2017 Michael Kourlas
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

package net.kourlas.voipms_sms.sms

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.google.firebase.crash.FirebaseCrash
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getEmail
import net.kourlas.voipms_sms.preferences.getPassword
import net.kourlas.voipms_sms.utils.getJson
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Service used to retrieve DIDs for a particular account from VoIP.ms.
 */
class RetrieveDidsService : IntentService(
    RetrieveDidsService::class.java.name) {
    var error: String? = null

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
     * @param intent The specified intent.
     * @return The DIDs associated with the configured VoIP.ms account, or null
     * if an error occurred.
     */
    fun handleRetrieveDids(intent: Intent?): Set<String>? {
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
        } catch (e: Exception) {
            FirebaseCrash.report(e)
        }
        return dids
    }

    /**
     * Gets the response of a getDIDsInfo call to the VoIP.ms API.
     *
     * @return The response of a getDIDsInfo call to the VoIP.ms API, or null
     * if an error occurred.
     */
    private fun getApiResponse(): JSONObject? {
        val retrieveDidsUrl =
            "https://www.voip.ms/api/v1/rest.php?" +
            "api_username=" +
            URLEncoder.encode(getEmail(applicationContext),
                              "UTF-8") + "&" +
            "api_password=" +
            URLEncoder.encode(getPassword(applicationContext),
                              "UTF-8") + "&" +
            "method=getDIDsInfo"
        val response: JSONObject
        try {
            response = getJson(applicationContext, retrieveDidsUrl)
        } catch (e: IOException) {
            error = applicationContext.getString(
                R.string.preferences_account_dids_error_api_request)
            return null
        } catch (e: JSONException) {
            FirebaseCrash.report(e)
            error = applicationContext.getString(
                R.string.preferences_account_dids_error_api_parse)
            return null
        } catch (e: Exception) {
            FirebaseCrash.report(e)
            error = applicationContext.getString(
                R.string.preferences_account_dids_error_unknown)
            return null
        }
        return response
    }

    /**
     * Parses the response of a getDIDsInfo from the VoIP.ms API to
     * extract all DIDs with SMS enabled.
     *
     * @param response The specified response.
     * @return The DIDs extracted from the response, or null if an error
     * occurred.
     */
    private fun getDidsFromResponse(response: JSONObject): Set<String>? {
        val dids = mutableListOf<String>()
        try {
            val status = response.getString("status")
            if (status != "success") {
                error = getString(
                    R.string.preferences_account_dids_error_api_error, status)
                return null
            }

            val rawDids = response.getJSONArray("dids")
            (0..rawDids.length() - 1)
                .map { rawDids.getJSONObject(it) }
                .filter {
                    it.getString("sms_available") == "1"
                    && it.getString("sms_enabled") == "1"
                }
                .mapTo(dids) { it.getString("did") }
        } catch (e: JSONException) {
            error = getString(
                R.string.preferences_account_dids_error_api_parse)
            return null
        }

        if (dids.size == 0) {
            error = getString(
                R.string.preferences_account_dids_error_no_dids)
            return null
        }
        return dids.toSet()
    }

    companion object {
        /**
         * Gets an intent which can be used to launch this service using the
         * specified context.
         *
         * @param context The specified context.
         * @return An intent which can be used to launch this service.
         */
        fun getIntent(context: Context): Intent {
            val intent = Intent(context, RetrieveDidsService::class.java)
            intent.action = context.getString(R.string.retrieve_dids_action)
            return intent
        }
    }
}