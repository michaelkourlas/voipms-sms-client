/*
 * VoIP.ms SMS
 * Copyright (C) 2021 Michael Kourlas
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

package net.kourlas.voipms_sms.billing

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.showSnackbar

class Billing(private val context: Context) {
    suspend fun askForCoffee(activity: FragmentActivity) =
        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        context.getString(
                            R.string.coffee_url
                        )
                    )
                )
                activity.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                showSnackbar(
                    activity, R.id.coordinator_layout,
                    context.getString(
                        R.string.conversations_fail_web_browser
                    )
                )
            }
        }

    companion object {
        // It is not a leak to store an instance to the application context,
        // since it has the same lifetime as the application itself.
        @SuppressLint("StaticFieldLeak")
        private var instance: Billing? = null

        /**
         * Gets the sole instance of the Billing class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(context: Context): Billing =
            instance ?: synchronized(this) {
                instance ?: Billing(
                    context.applicationContext
                ).also { instance = it }
            }
    }
}