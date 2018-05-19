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
import android.os.Bundle
import android.support.v7.preference.Preference
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.takisoft.fix.support.v7.preference.EditTextPreference
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers
import net.kourlas.voipms_sms.R

class NetworkPreferencesFragment : PreferenceFragmentCompatDividers(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_network)

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
    private fun updateSummaries() {
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
        if (preference is EditTextPreference) {
            // Display value of preference as summary text
            if (preference.key == getString(
                    R.string.preferences_network_connect_timeout_key)
                || preference.key == getString(
                    R.string.preferences_network_read_timeout_key)) {
                try {
                    if (preference.text.toInt() == 0) {
                        preference.summary = "Infinite"
                    } else {
                        preference.summary = preference.text + " seconds"
                    }
                } catch (e: NumberFormatException) {
                    preference.summary = "Infinite"
                }
            } else {
                preference.summary = preference.text
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