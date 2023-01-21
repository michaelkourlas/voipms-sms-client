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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.utils.*

class DidPreferencesFragment : PreferenceFragmentCompat() {
    private lateinit var did: String

    // Broadcast receivers
    private val pushNotificationsRegistrationCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                activity?.let { activity ->
                    // Show error if one occurred
                    intent?.getStringArrayListExtra(
                        getString(
                            R.string
                                .push_notifications_reg_complete_failed_dids
                        )
                    )
                        ?.let {
                            if (it.isNotEmpty()) {
                                // Some DIDs failed registration
                                showSnackbar(
                                    activity, R.id.coordinator_layout,
                                    getString(
                                        R.string.push_notifications_fail_register
                                    )
                                )
                            }
                        } ?: run {
                        // Unknown error
                        showSnackbar(
                            activity, R.id.coordinator_layout,
                            getString(R.string.push_notifications_fail_unknown)
                        )

                        // Regardless of whether an error occurred, mark setup
                        // as complete
                        setSetupCompletedForVersion(activity, 134)
                    }
                }
            }
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        activity?.let {
            // Set DID
            did = arguments?.getString(
                getString(
                    R.string.preferences_did_fragment_argument_did
                )
            ) ?: run {
                abortActivity(it, Exception("Missing DID argument"))
                return
            }

            // Add preferences
            addPreferencesFromResource(R.xml.preferences_did)

            // Set keys and update checked status for each preference
            setupPreferences()
            updatePreferences()

            // Add listener for enabled switch
            val enabledSwitch = it.findViewById<SwitchCompat>(
                R.id.enabled_switch
            )
            enabledSwitch.setOnCheckedChangeListener(
                object :
                    CompoundButton.OnCheckedChangeListener {
                    override fun onCheckedChanged(
                        buttonView: CompoundButton?,
                        isChecked: Boolean
                    ) {
                        if (!::did.isInitialized) {
                            return
                        }

                        // When enabled switch is checked or unchecked, enable
                        // or disable and check or uncheck the additional
                        // switches as well
                        val dids = if (isChecked) {
                            getDids(it).plus(did)
                        } else {
                            getDids(it).minus(did)
                        }
                        setDids(it, dids)

                        if (dids.isNotEmpty()) {
                            // Re-register for push notifications when DIDs
                            // change
                            enablePushNotifications(
                                it.applicationContext,
                                activityToShowError = it
                            )
                        }
                        lifecycleScope.launch(Dispatchers.Default) {
                            replaceIndex(it)
                        }

                        updatePreferences()
                    }
                })
        }
    }

    override fun onResume() {
        super.onResume()

        activity?.let {
            // Set DID
            did = arguments?.getString(
                getString(
                    R.string.preferences_did_fragment_argument_did
                )
            ) ?: run {
                abortActivity(it, Exception("Missing DID argument"))
                return
            }

            // Register dynamic receivers for this fragment
            it.registerReceiver(
                pushNotificationsRegistrationCompleteReceiver,
                IntentFilter(
                    getString(
                        R.string.push_notifications_reg_complete_action
                    )
                )
            )

            // Update checked status for each preference
            updatePreferences()
        }
    }

    override fun onPause() {
        super.onPause()

        activity?.let {
            // Unregister dynamic receivers for this fragment
            safeUnregisterReceiver(
                it,
                pushNotificationsRegistrationCompleteReceiver
            )
        }
    }

    /**
     * Set the keys for each preference.
     */
    private fun setupPreferences() {
        val showInConversationsViewPreference =
            preferenceScreen.getPreference(0) as SwitchPreference
        showInConversationsViewPreference.key =
            getString(
                R.string.preferences_did_show_in_conversations_view_key,
                did
            )

        val retrieveMessagesPreference =
            preferenceScreen.getPreference(1) as SwitchPreference
        retrieveMessagesPreference.key =
            getString(
                R.string.preferences_did_retrieve_messages_key, did
            )

        val showNotificationsPreference =
            preferenceScreen.getPreference(2) as SwitchPreference
        showNotificationsPreference.key =
            getString(
                R.string.preferences_did_show_notifications_key, did
            )
        showNotificationsPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ ->
                context?.let {
                    lifecycleScope.launch(Dispatchers.Default) {
                        Database.getInstance(it).updateShortcuts()
                        replaceIndex(it)
                    }
                }
                true
            }
    }

    /**
     * Update checked status for each preference.
     */
    private fun updatePreferences() {
        activity?.let {
            val enabledSwitch = it.findViewById<SwitchCompat>(
                R.id.enabled_switch
            )
            enabledSwitch.isChecked = did in getDids(it)

            val showInConversationsViewPreference =
                preferenceScreen.getPreference(0) as SwitchPreference
            showInConversationsViewPreference.isEnabled =
                enabledSwitch.isChecked
            showInConversationsViewPreference.isChecked =
                getDidShowInConversationsView(it, did)

            val retrieveMessagesPreference =
                preferenceScreen.getPreference(1) as SwitchPreference
            retrieveMessagesPreference.isEnabled =
                enabledSwitch.isChecked
            retrieveMessagesPreference.isChecked =
                getDidRetrieveMessages(it, did)

            val showNotificationsPreference =
                preferenceScreen.getPreference(2) as SwitchPreference
            showNotificationsPreference.isEnabled =
                enabledSwitch.isChecked
            showNotificationsPreference.isChecked = getDidShowNotifications(
                it, did
            )
        }
    }
}
