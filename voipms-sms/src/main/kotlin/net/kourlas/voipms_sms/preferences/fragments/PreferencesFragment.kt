/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2022 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.preference.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.activities.AccountPreferencesActivity
import net.kourlas.voipms_sms.preferences.activities.NotificationsPreferencesActivity
import net.kourlas.voipms_sms.signIn.SignInActivity
import net.kourlas.voipms_sms.utils.preferences

/**
 * Fragment used to display the app's preferences.
 */
class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences)

        context?.let {
            for (preference in preferenceScreen.preferences) {
                if (preference.title == getString(
                        R.string.preferences_notifications_category_name
                    )
                ) {
                    // Set the behaviour of the notifications preference; this
                    // is different depending on whether the system supports
                    // notification customization natively
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val activity = activity ?: return
                        Notifications.getInstance(activity.applicationContext)
                            .createDefaultNotificationChannel()

                        val intent = Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        )
                        intent.putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            it.packageName
                        )
                        preference.intent = intent
                    } else {
                        preference.intent = Intent(
                            context,
                            NotificationsPreferencesActivity::class.java
                        )
                    }
                } else if (preference.title == getString(
                        R.string.preferences_account_category_name
                    )
                ) {
                    // Set the behaviour of the account preference
                    if (accountConfigured(it)) {
                        preference.intent = Intent(
                            context, AccountPreferencesActivity::class.java
                        )
                    } else {
                        preference.intent = Intent(
                            context,
                            SignInActivity::class.java
                        )
                    }
                }
            }
        }
    }
}
