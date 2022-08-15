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

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getEmail
import net.kourlas.voipms_sms.preferences.setEmail
import net.kourlas.voipms_sms.preferences.setPassword
import net.kourlas.voipms_sms.utils.preferences

/**
 * Fragment used to display the account preferences.
 */
class AccountPreferencesFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onResume() {
        super.onResume()

        // Update preference summaries
        updateSummaries()
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_account)

        // Add listeners for preference changes
        preferenceScreen
            .sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(this)

        // Assign listeners to preferences
        for (preference in preferenceScreen.preferences) {
            when (preference.key) {
                getString(
                    R.string.preferences_account_sign_out_key
                ) -> {
                    activity?.let { activity ->
                        preference.onPreferenceClickListener =
                            Preference.OnPreferenceClickListener {
                                setEmail(activity, "")
                                setPassword(activity, "")
                                activity.finish()
                                true
                            }
                    }
                }
            }
        }

        // Update preference summaries
        updateSummaries()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String
    ) {
        // It's not clear why onSharedPreferenceChanged is called before the
        // fragment is actually added to the activity, but it apparently is;
        // this check is therefore required to prevent a crash
        if (isAdded) {
            updateSummary(findPreference(key))
        }
    }

    /**
     * Updates the summaries for all preferences.
     */
    private fun updateSummaries() {
        for (preference in preferenceScreen.preferences) {
            // Update preference summary
            updateSummary(preference)
        }
    }

    /**
     * Updates the summary for the specified preference.
     */
    private fun updateSummary(preference: Preference?) {
        when (preference?.key) {
            getString(R.string.preferences_account_sign_out_key) -> {
                activity?.let { activity ->
                    preference.summary = getEmail(activity)
                }
            }
        }
    }
}
