/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2019 Michael Kourlas
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.preference.Preference
import com.takisoft.preferencex.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.activities.DidPreferencesActivity
import net.kourlas.voipms_sms.preferences.controls.MasterSwitchPreference
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.preferences.setDids
import net.kourlas.voipms_sms.preferences.setSetupCompletedForVersion
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber
import net.kourlas.voipms_sms.utils.preferences
import net.kourlas.voipms_sms.utils.safeUnregisterReceiver
import net.kourlas.voipms_sms.utils.showSnackbar

class DidsPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    // Sentinel used to prevent preferences from being loaded twice (once on
    // creation, once on resumption)
    private var beforeFirstPreferenceLoad: Boolean = true

    // DIDs, sorted by type
    private lateinit var dids: Set<String>
    private lateinit var activeDids: Set<String>
    private lateinit var retrievedDids: Set<String>
    private lateinit var databaseDids: Set<String>

    // Map between preferences and DIDs to use in listeners
    private lateinit var preferenceDidMap: Map<Preference, String>

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        activity?.let {
            // Load empty list of preferences
            addPreferencesFromResource(R.xml.preferences_dids)

            // Remove all preferences
            preferenceScreen.removeAll()

            // Retrieve all DIDs
            retrievedDids = arguments?.getStringArrayList(getString(
                R.string.preferences_dids_fragment_retrieved_dids_key))
                                ?.toSet()
                            ?: emptySet()
            databaseDids = arguments?.getStringArrayList(getString(
                R.string.preferences_dids_fragment_database_dids_key))
                               ?.toSet()
                           ?: emptySet()
            activeDids = getDids(it)

            // Transfer all DIDs into common set
            dids = mutableSetOf<String>()
                .plus(retrievedDids)
                .plus(databaseDids)
                .plus(activeDids)

            // Add DIDs to preferences screen
            val preferenceDidMap = mutableMapOf<Preference, String>()
            this.preferenceDidMap = preferenceDidMap
            for (did in dids.sorted()) {
                val preference = MasterSwitchPreference(it)
                preferenceDidMap[preference] = did
                preference.title = getFormattedPhoneNumber(did)
                if (did !in retrievedDids) {
                    preference.summary = getString(
                        R.string.preferences_dids_stored_locally)
                }
                preference.onPreferenceChangeListener = this
                preference.onPreferenceClickListener = this
                preference.isChecked = did in activeDids
                preferenceScreen.addPreference(preference)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Load DIDs and create preference for each
        if (!beforeFirstPreferenceLoad) {
            for (preference in preferenceScreen.preferences) {
                (preference as MasterSwitchPreference).let {
                    val did = preferenceDidMap[it]
                              ?: throw Exception("Unrecognized preference")
                    it.isChecked = did in activeDids
                }
            }
        }
        beforeFirstPreferenceLoad = false
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        // Show the DID preference activity associated with the selected
        // preference
        val did = preferenceDidMap[preference]
                  ?: throw Exception("Unrecognized preference")
        val intent = Intent(activity, DidPreferencesActivity::class.java)
        intent.putExtra(getString(R.string.preferences_did_did), did)
        startActivity(intent)

        return true
    }

    override fun onPreferenceChange(preference: Preference?,
                                    newValue: Any?): Boolean {
        activity?.let {
            // Enable the selected DID
            val did = preferenceDidMap[preference]
                      ?: throw Exception("Unrecognized preference")

            val dids = if (newValue as Boolean) {
                getDids(it).plus(did)
            } else {
                getDids(it).minus(did)
            }
            setDids(it, dids)
        }

        return true
    }
}
