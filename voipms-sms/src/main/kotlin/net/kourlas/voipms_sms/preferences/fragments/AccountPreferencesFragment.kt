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

import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.preference.Preference
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.takisoft.fix.support.v7.preference.EditTextPreference
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber
import net.kourlas.voipms_sms.utils.preferences

/**
 * Fragment used to display the account preferences.
 */
class AccountPreferencesFragment : PreferenceFragmentCompatDividers(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        try {
            return super.onCreateView(inflater, container, savedInstanceState)
        } finally {
            setDividerPreferences(DIVIDER_NONE)
        }
    }

    override fun onResume() {
        super.onResume()

        updateSummaries()
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_account)

        // Add listener for preference changes
        preferenceScreen.sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)

        updateSummaries()
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
     * Updates the summary text for all preferences.
     */
    private fun updateSummaries() {
        if (preferenceScreen != null) {
            for (preference in preferenceScreen.preferences) {
                updateSummaryTextForPreference(preference)
            }
        }
    }

    /**
     * Updates the summary text for the specified preference.
     *
     * @param preference The specified preference.
     */
    private fun updateSummaryTextForPreference(preference: Preference?) {
        val context = context ?: return
        if (preference?.key == getString(
                R.string.preferences_account_dids_key)) {
            // Display list of selected DIDs as summary text
            val formattedDids = getDids(
                context).map(::getFormattedPhoneNumber)
            preference.summary = formattedDids.joinToString(separator = ", ")
        } else if (preference is EditTextPreference) {
            // Display value of preference as summary text (except for
            // passwords, which should be masked, as well as the read and
            // connect timeouts, which should include the unit)
            if (preference.key == getString(
                    R.string.preferences_account_password_key)) {
                if (preference.text != "") {
                    preference.summary = getString(
                        R.string.preferences_account_password_placeholder)
                } else {
                    preference.summary = ""
                }
            } else {
                preference.summary = preference.text
            }
        }
    }
}
