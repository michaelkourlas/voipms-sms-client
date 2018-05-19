/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2018 Michael Kourlas
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.activities.NotificationsPreferencesActivity

/**
 * Fragment used to display the app's preferences.
 */
class PreferencesFragment : PreferenceFragmentCompatDividers() {
    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        // Populate fragment with preferences defined in XML file
        addPreferencesFromResource(R.xml.preferences)

        for (i in 0 until preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(i)
            if (preference.title == getString(
                    R.string.preferences_notifications_category_name)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val activity = activity ?: return
                    Notifications.getInstance(activity.application)
                        .createDefaultNotificationChannel()

                    val intent = Intent(
                        Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE,
                                    context?.packageName)
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID,
                                    getString(
                                        R.string.notifications_channel_default))
                    preference.intent = intent
                } else {
                    preference.intent = Intent(
                        context, NotificationsPreferencesActivity::class.java)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        try {
            return super.onCreateView(inflater, container, savedInstanceState)
        } finally {
            setDividerPreferences(DIVIDER_NONE)
        }
    }
}
