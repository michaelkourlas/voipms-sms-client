/*
 * VoIP.ms SMS
 * Copyright (C) 2019-2021 Michael Kourlas
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
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.utils.httpPostWithMultipartFormData
import net.kourlas.voipms_sms.utils.logException
import java.io.IOException

/**
 * Worker used to test credentials for a particular account from VoIP.ms.
 */
class VerifyCredentialsWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private var error: String? = null

    override suspend fun doWork(): Result {
        // Verify that credentials are valid.
        val valid = verifyCredentials()

        // Send broadcast.
        val verifyCredentialsCompleteIntent = Intent(
            applicationContext.getString(
                R.string.verify_credentials_complete_action
            )
        )
        verifyCredentialsCompleteIntent.putExtra(
            applicationContext.getString(
                R.string.verify_credentials_complete_error
            ), error
        )
        verifyCredentialsCompleteIntent.putExtra(
            applicationContext.getString(
                R.string.verify_credentials_complete_valid
            ),
            valid
        )
        applicationContext.sendBroadcast(verifyCredentialsCompleteIntent)

        return if (error == null) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notifications.getInstance(applicationContext)
            .getSyncVerifyCredentialsNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Notifications.SYNC_VERIFY_CREDENTIALS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                Notifications.SYNC_VERIFY_CREDENTIALS_NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Verifies the credentials of the VoIP.ms account using the parameters
     * from the specified intent.
     */
    private suspend fun verifyCredentials(): Boolean {
        // Retrieve DIDs from VoIP.ms API.
        try {
            val email = inputData.getString(
                applicationContext.getString(
                    R.string.verify_credentials_email
                )
            )
            val password = inputData.getString(
                applicationContext.getString(
                    R.string.verify_credentials_password
                )
            )
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
    private suspend fun getApiResponse(
        email: String,
        password: String
    ): VerifyCredentialsResponse? {
        try {
            repeat(3) {
                try {
                    return httpPostWithMultipartFormData(
                        applicationContext,
                        "https://www.voip.ms/api/v1/rest.php",
                        mapOf(
                            "api_username" to email,
                            "api_password" to password,
                            "method" to "getDIDsInfo"
                        )
                    )
                } catch (e: IOException) {
                    // Try again...
                }
            }
            error = applicationContext.getString(
                R.string.verify_credentials_error_api_request
            )
            return null
        } catch (e: JsonDataException) {
            logException(e)
            error = applicationContext.getString(
                R.string.verify_credentials_error_api_parse
            )
            return null
        } catch (e: Exception) {
            logException(e)
            error = applicationContext.getString(
                R.string.verify_credentials_error_unknown
            )
            return null
        }
    }

    /**
     * Parses the response of a getDIDsInfo from the VoIP.ms API to verify that
     * the response is valid.
     */
    private fun verifyResponse(response: VerifyCredentialsResponse): Boolean {
        if (response.status != "success") {
            if (response.status == "no_did") {
                return true
            }
            error = when (response.status) {
                "invalid_credentials" -> applicationContext.getString(
                    R.string.verify_credentials_error_api_error_invalid_credentials
                )
                "missing_credentials" -> applicationContext.getString(
                    R.string.verify_credentials_error_api_error_missing_credentials
                )
                else -> applicationContext.getString(
                    R.string.verify_credentials_error_api_error,
                    response.status
                )
            }
            return false
        }
        return true
    }

    companion object {
        /**
         * Verify credentials for a VoIP.ms account.
         */
        fun verifyCredentials(
            context: Context, email: String,
            password: String
        ) {
            val work = OneTimeWorkRequestBuilder<VerifyCredentialsWorker>()
                .setInputData(
                    workDataOf(
                        context.getString(
                            R.string.verify_credentials_email
                        ) to email,
                        context.getString(
                            R.string.verify_credentials_password
                        ) to password
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                context.getString(R.string.verify_credentials_work_id),
                ExistingWorkPolicy.REPLACE, work
            )
        }
    }
}