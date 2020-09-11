/*
 * VoIP.ms SMS
 * Copyright (C) 2019-2020 Michael Kourlas
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
import androidx.core.app.JobIntentService
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.JobId
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Service used to test credentials for a particular account from VoIP.ms.
 */
class VerifyCredentialsService : JobIntentService() {
    private val okHttp = OkHttpClient()
    private val moshi: Moshi = Moshi.Builder().build()
    private var error: String? = null

    override fun onHandleWork(intent: Intent) {
        // Verify that credentials are valid
        val credentialsValid = handleVerifyCredentials(intent)

        // Send broadcast
        val verifyCredentialsCompleteIntent = Intent(
            applicationContext.getString(
                R.string.verify_credentials_complete_action))
        verifyCredentialsCompleteIntent.putExtra(getString(
            R.string.verify_credentials_complete_error), error)
        verifyCredentialsCompleteIntent.putExtra(
            getString(R.string.verify_credentials_complete_valid),
            credentialsValid)
        applicationContext.sendBroadcast(verifyCredentialsCompleteIntent)
    }

    /**
     * Verifies the credentials of the VoIP.ms account using the parameters
     * from the specified intent.
     */
    private fun handleVerifyCredentials(intent: Intent): Boolean {
        // Retrieve DIDs from VoIP.ms API
        try {
            // Terminate quietly if intent does not exist or does not contain
            // the sync action
            if (intent.action != applicationContext.getString(
                    R.string.verify_credentials_action)) {
                return false
            }

            val email = intent.extras?.getString(
                applicationContext.getString(
                    R.string.verify_credentials_email))
            val password = intent.extras?.getString(
                applicationContext.getString(
                    R.string.verify_credentials_password))
            if (email == null || password == null) {
                return false
            }

            val response = getApiResponse(email, password)
            if (response != null) {
                return verifyResponse(response)
            }
        } catch (e: Exception) {
            logException(e)
        }
        return false
    }

    @JsonClass(generateAdapter = true)
    data class VerifyCredentialsResponse(val status: String)

    /**
     * Verifies the response of a getDIDsInfo call to the VoIP.ms API.
     *
     * @return Null if an error occurred.
     */
    private fun getApiResponse(email: String,
                               password: String): VerifyCredentialsResponse? {
        try {
            return httpPostWithMultipartFormData(
                applicationContext, okHttp, moshi,
                "https://www.voip.ms/api/v1/rest.php",
                mapOf("api_username" to email,
                      "api_password" to password,
                      "method" to "getDIDsInfo"))
        } catch (e: IOException) {
            error = applicationContext.getString(
                R.string.verify_credentials_error_api_request)
            return null
        } catch (e: JsonDataException) {
            logException(e)
            error = applicationContext.getString(
                R.string.verify_credentials_error_api_parse)
            return null
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.verify_credentials_error_unknown)
            return null
        }
    }

    /**
     * Parses the response of a getDIDsInfo from the VoIP.ms API to verify that
     * the response is valid.
     */
    private fun verifyResponse(response: VerifyCredentialsResponse): Boolean {
        if (response.status != "success") {
            error = when (response.status) {
                "invalid_credentials" -> getString(
                    R.string.verify_credentials_error_api_error_invalid_credentials)
                "missing_credentials" -> getString(
                    R.string.verify_credentials_error_api_error_missing_credentials)
                else -> getString(
                    R.string.verify_credentials_error_api_error,
                    response.status)
            }
            return false
        }
        return true
    }

    companion object {
        /**
         * Verify credentials for a VoIP.ms account.
         */
        fun startService(context: Context, email: String, password: String) {
            val intent = Intent(context, VerifyCredentialsService::class.java)
            intent.action = context.getString(
                R.string.verify_credentials_action)
            intent.putExtra(
                context.getString(R.string.verify_credentials_email), email)
            intent.putExtra(
                context.getString(R.string.verify_credentials_password),
                password)

            enqueueWork(context, VerifyCredentialsService::class.java,
                        JobId.VerifyCredentialsService.ordinal, intent)
        }
    }
}