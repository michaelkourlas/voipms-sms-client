/*
 * VoIP.ms SMS
 * Copyright (C) 2018-2020 Michael Kourlas
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
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.takisoft.preferencex.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.workers.SyncWorker
import net.kourlas.voipms_sms.utils.preferences

class SynchronizationPreferencesFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    // Preference change handlers
    private val syncIntervalPreferenceChangeListener =
        Preference.OnPreferenceChangeListener { _, newValue ->
            activity?.let {
                SyncWorker.performFullSynchronization(
                    it,
                    customPeriod = (newValue as String).toDouble(),
                    scheduleOnly = true
                )
            }
            true
        }

    override fun onCreatePreferencesFix(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_synchronization)

        // Add listener for preference changes
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(
            this
        )

        // Update preference summaries and handlers
        updateSummariesAndHandlers()
    }

    override fun onResume() {
        super.onResume()

        // Update preference summaries and handlers
        updateSummariesAndHandlers()
    }

    /**
     * Updates the summaries and handlers for all preferences.
     */
    private fun updateSummariesAndHandlers() {
        for (preference in preferenceScreen.preferences) {
            updateSummaryTextForPreference(preference)
            updateHandlersForPreference(preference)
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String
    ) {
        // It's not clear why onSharedPreferenceChanged is called before the
        // fragment is actually added to the activity, but it apparently is;
        // this check is therefore required to prevent a crash
        if (isAdded) {
            // Update summary text for changed preference
            updateSummaryTextForPreference(findPreference(key))
        }
    }

    /**
     * Updates the summary text and handler for the specified preference.
     */
    private fun updateSummaryTextForPreference(preference: Preference?) {
        if (preference is ListPreference) {
            // Display value of selected element as summary text
            preference.summary = preference.entry
        }
    }

    /**
     * Updates the handlers for the specified preference.
     */
    private fun updateHandlersForPreference(preference: Preference) {
        if (preference.key == getString(
                R.string.preferences_sync_interval_key
            )
        ) {
            preference.onPreferenceChangeListener =
                syncIntervalPreferenceChangeListener
        }
    }
}