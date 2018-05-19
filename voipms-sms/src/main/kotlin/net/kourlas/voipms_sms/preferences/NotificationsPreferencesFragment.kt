/*
 * VoIP.ms SMS
 * Copyright (C) 2018 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences

import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.support.v7.preference.Preference
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers
import com.takisoft.fix.support.v7.preference.RingtonePreference
import net.kourlas.voipms_sms.R

class NotificationsPreferencesFragment : PreferenceFragmentCompatDividers(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_notifications)

        // Add listener for preference changes
        preferenceScreen.sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)

        updateSummaries()
    }

    override fun onResume() {
        super.onResume()

        updateSummaries()
    }

    /**
     * Updates the summary text for all preferences.
     */
    fun updateSummaries() {
        if (preferenceScreen != null) {
            for (i in 0 until preferenceScreen.preferenceCount) {
                val subPreference = preferenceScreen.getPreference(i)
                updateSummaryTextForPreference(subPreference)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                           key: String) {
        // It's not clear why onSharedPreferenceChanged is called before the
        // fragment is actually added to the activity, but it apparently is;
        // this check is therefore required to prevent a crash
        if (isAdded) {
            // Update summary text for changed preference
            updateSummaryTextForPreference(findPreference(key))
        }
    }

    /**
     * Updates the summary text for the specified preference.
     *
     * @param preference The specified preference.
     */
    private fun updateSummaryTextForPreference(preference: Preference?) {
        val context = context ?: return
        if (preference is RingtonePreference) {
            // Display selected notification sound as summary text for
            // notification setting
            @Suppress("DEPRECATION")
            val notificationSound = getNotificationSound(context)
            if (notificationSound == "") {
                preference.summary = "None"
            } else {
                try {
                    val ringtone = RingtoneManager.getRingtone(
                        activity, Uri.parse(notificationSound))
                    if (ringtone != null) {
                        preference.summary = ringtone.getTitle(
                            activity)
                    } else {
                        preference.summary = getString(
                            R.string.preferences_notifications_sound_unknown)
                    }
                } catch (ex: SecurityException) {
                    preference.summary = getString(
                        R.string.preferences_notifications_sound_unknown_perm)
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