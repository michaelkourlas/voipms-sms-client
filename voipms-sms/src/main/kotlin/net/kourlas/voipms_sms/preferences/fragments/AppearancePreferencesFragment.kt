/*
 * VoIP.ms SMS
 * Copyright (C) 2020 Michael Kourlas
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.takisoft.preferencex.EditTextPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.preferences

class AppearancePreferencesFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    // Preference change handlers
    private val themePreferenceChangeListener =
        Preference.OnPreferenceChangeListener { _, newValue ->
            activity?.let {
                when (newValue) {
                    SYSTEM_DEFAULT -> AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    )
                    LIGHT -> AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO
                    )
                    DARK -> AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_YES
                    )
                }
            }
            true
        }

    override fun onCreatePreferencesFix(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_appearance)

        // Add listener for preference changes
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(
            this
        )

        // Update preferences summaries
        updateSummaries()
    }

    override fun onResume() {
        super.onResume()

        // Update preferences summaries
        updateSummaries()
    }

    /**
     * Updates the summary text for all preferences.
     */
    private fun updateSummaries() {
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
     * Updates the summary text for the specified preference.
     */
    private fun updateSummaryTextForPreference(preference: Preference?) {
        if (preference is ListPreference) {
            // Display value of selected element as summary text
            preference.summary = preference.entry
        } else if (preference is EditTextPreference) {
            // Display value of preference as summary text
            if (preference.key == getString(
                    R.string.preferences_message_text_box_maximum_size_key
                )
            ) {
                preference.summary = preference.text + " lines"
            } else {
                preference.summary = preference.text
            }
        }
    }

    /**
     * Updates the handlers for the specified preference.
     */
    private fun updateHandlersForPreference(preference: Preference) {
        if (preference.key == getString(
                R.string.preferences_theme_key
            )
        ) {
            preference.onPreferenceChangeListener =
                themePreferenceChangeListener
        }
    }

    companion object {
        // When changing the values of these constants, make sure to update
        // arrays.xml
        const val SYSTEM_DEFAULT = "0"
        const val LIGHT = "1"
        const val DARK = "2"
    }
}