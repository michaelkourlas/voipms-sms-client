/*
 * VoIP.ms SMS
 * Copyright (C) 2017 Michael Kourlas
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v7.preference.Preference
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.notifications.NotificationsRegistrationService
import net.kourlas.voipms_sms.preferences.controls.MasterSwitchPreference
import net.kourlas.voipms_sms.sms.AppIndexingService
import net.kourlas.voipms_sms.utils.*

class DidsPreferencesFragment : PreferenceFragmentCompatDividers(),
    Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    // Sentinel use to prevent preferences from being loaded twice (once on
    // creation, once on resumption)
    private var beforeFirstPreferenceLoad: Boolean = true

    // DIDs, sorted by type
    private lateinit var dids: Set<String>
    private lateinit var activeDids: Set<String>
    private lateinit var retrievedDids: Set<String>
    private lateinit var databaseDids: Set<String>

    // Map between preferences and DIDs to use in listeners
    private lateinit var preferenceDidMap: Map<Preference, String>

    // Broadcast receivers
    private val pushNotificationsRegistrationCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val activity = activity ?: return
                // Show error if one occurred
                val failedDids = intent?.getStringArrayListExtra(
                    getString(
                        R.string.push_notifications_reg_complete_voip_ms_api_callback_failed_dids))
                if (failedDids == null) {
                    // Unknown error
                    showSnackbar(activity, R.id.coordinator_layout, getString(
                        R.string.push_notifications_fail_unknown))
                } else if (!failedDids.isEmpty()) {
                    // Some DIDs failed registration
                    showSnackbar(activity, R.id.coordinator_layout, getString(
                        R.string.push_notifications_fail_register))
                }

                // Regardless of whether an error occurred, mark setup as
                // complete
                setSetupCompletedForVersion(activity, 114)
            }
        }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        val activity = activity ?: return

        // Load empty list of preferences
        addPreferencesFromResource(R.xml.preferences_dids)

        // Remove all preferences
        preferenceScreen.removeAll()

        // Retrieve all DIDs
        retrievedDids = arguments?.getStringArrayList(getString(
            R.string.preferences_account_dids_fragment_retrieved_dids_key))
                            ?.toSet()
                        ?: emptySet()
        databaseDids = arguments?.getStringArrayList(getString(
            R.string.preferences_account_dids_fragment_database_dids_key))
                           ?.toSet()
                       ?: emptySet()
        activeDids = getDids(activity)

        // Transfer all DIDs into common set
        dids = mutableSetOf<String>()
            .plus(retrievedDids)
            .plus(databaseDids)
            .plus(activeDids)

        val preferenceDidMap = mutableMapOf<Preference, String>()
        this.preferenceDidMap = preferenceDidMap
        for (did in dids.sorted()) {
            val preference = MasterSwitchPreference(activity)
            preferenceDidMap[preference] = did
            preference.title = getFormattedPhoneNumber(did)
            if (did !in retrievedDids) {
                preference.summary = "Stored locally but not found in VoIP.ms account"
            }
            preference.onPreferenceChangeListener = this
            preference.onPreferenceClickListener = this
            preference.isChecked = did in activeDids
            preferenceScreen.addPreference(preference)
        }
    }

    override fun onResume() {
        super.onResume()
        val activity = activity ?: return

        // Register dynamic receivers for this fragment
        activity.registerReceiver(
            pushNotificationsRegistrationCompleteReceiver,
            IntentFilter(getString(
                R.string.push_notifications_reg_complete_action)))

        // Load DIDs and create preference for each
        if (!beforeFirstPreferenceLoad) {
            for (i in 0 until preferenceScreen.preferenceCount) {
                val preference = preferenceScreen.getPreference(i)
                    as MasterSwitchPreference
                val did = preferenceDidMap[preference]
                          ?: throw Exception("Unrecognized preference")
                preference.isChecked = did in activeDids
            }
        }
        beforeFirstPreferenceLoad = false
    }

    override fun onPause() {
        super.onPause()
        val activity = activity ?: return

        // Unregister dynamic receivers for this fragment
        safeUnregisterReceiver(
            activity,
            pushNotificationsRegistrationCompleteReceiver)
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        val did = preferenceDidMap[preference]
                  ?: throw Exception("Unrecognized preference")

        val intent = Intent(activity, DidPreferencesActivity::class.java)
        intent.putExtra(getString(R.string.preferences_did_did), did)
        startActivity(intent)

        return true
    }

    override fun onPreferenceChange(preference: Preference?,
                                    newValue: Any?): Boolean {
        val activity = activity ?: return true
        val context = context ?: return true
        val did = preferenceDidMap[preference]
                  ?: throw Exception("Unrecognized preference")

        val dids = if (newValue as Boolean) {
            getDids(context).plus(did)
        } else {
            getDids(context).minus(did)
        }
        setDids(activity, dids)

        if (dids.isNotEmpty()
            && Notifications.getInstance(activity.application)
                .getNotificationsEnabled()) {
            // Re-register for push notifications when
            // DIDs change
            enablePushNotifications()
        }
        runOnNewThread {
            AppIndexingService.replaceIndex(activity)
        }

        return true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        try {
            return super.onCreateView(inflater, container, savedInstanceState)
        } finally {
            setDividerPreferences(DIVIDER_NONE)
        }
    }

    /**
     * Enables push notifications by starting the push notifications
     * registration service.
     */
    private fun enablePushNotifications() {
        val activity = activity ?: return

        // Check if account is active and silently quit if not
        if (!isAccountActive(activity)) {
            setSetupCompletedForVersion(activity, 114)
            return
        }

        // Check if Google Play Services is available
        if (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(
                activity) != ConnectionResult.SUCCESS) {
            showSnackbar(activity, R.id.coordinator_layout, getString(
                R.string.push_notifications_fail_google_play))
            setSetupCompletedForVersion(activity, 114)
            return
        }

        // Subscribe to DID topics
        subscribeToDidTopics(activity)

        // Start push notifications registration service
        activity.startService(
            NotificationsRegistrationService.getIntent(activity))
    }
}
