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

import android.os.Bundle
import android.support.v14.preference.SwitchPreference
import android.support.v7.widget.SwitchCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.utils.abortActivity

class DidPreferencesFragment : PreferenceFragmentCompatDividers(),
    CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(buttonView: CompoundButton?,
                                  isChecked: Boolean) {
        val activity = activity ?: return
        val context = context ?: return
        val did = arguments?.getString(getString(
            R.string.preferences_did_fragment_argument_did))
        if (did == null) {
            abortActivity(activity, Exception("Missing DID argument"))
            return
        }

        // When enabled switch is checked or unchecked, enable/disable and
        // check/uncheck the additional switches as well
        val dids = if (isChecked) {
            getDids(context).plus(did)
        } else {
            getDids(context).minus(did)
        }
        setDids(activity, dids)

        updatePreferences()
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        val activity = activity ?: return
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_did)

        // Set keys and update checked status for each preference
        setupPreferences()
        updatePreferences()

        // Add listener for enabled switch
        val enabledSwitch = activity.findViewById<SwitchCompat>(
            R.id.enabled_switch)
        enabledSwitch.setOnCheckedChangeListener(this)
    }

    override fun onResume() {
        super.onResume()

        // Update checked status for each preference
        updatePreferences()
    }

    /**
     * Set the keys for each preference.
     */
    private fun setupPreferences() {
        val activity = activity ?: return
        val did = arguments?.getString(getString(
            R.string.preferences_did_fragment_argument_did))
        if (did == null) {
            abortActivity(activity, Exception("Missing DID argument"))
        }

        val showInConversationsViewPreference =
            preferenceScreen.getPreference(0) as SwitchPreference
        showInConversationsViewPreference.key =
            getString(R.string.preferences_did_show_in_conversations_view, did)

        val retrieveMessagesPreference =
            preferenceScreen.getPreference(1) as SwitchPreference
        retrieveMessagesPreference.key =
            getString(R.string.preferences_did_retrieve_messages, did)

        val showNotificationsPreference =
            preferenceScreen.getPreference(2) as SwitchPreference
        showNotificationsPreference.key =
            getString(R.string.preferences_did_show_notifications, did)
    }

    /**
     * Update checked status for each preference.
     */
    private fun updatePreferences() {
        val activity = activity ?: return
        val context = context ?: return
        val did = arguments?.getString(getString(
            R.string.preferences_did_fragment_argument_did))
        if (did == null) {
            abortActivity(activity, Exception("Missing DID argument"))
            return
        }

        val enabledSwitch = activity.findViewById<SwitchCompat>(
            R.id.enabled_switch)
        enabledSwitch.isChecked = did in getDids(
            context)

        val showInConversationsViewPreference =
            preferenceScreen.getPreference(0) as SwitchPreference
        showInConversationsViewPreference.isEnabled =
            enabledSwitch.isChecked
        showInConversationsViewPreference.isChecked =
            getDidShowInConversationsView(
                context, did)

        val retrieveMessagesPreference =
            preferenceScreen.getPreference(1) as SwitchPreference
        retrieveMessagesPreference.isEnabled =
            enabledSwitch.isChecked
        retrieveMessagesPreference.isChecked =
            getDidRetrieveMessages(context,
                                   did)

        val showNotificationsPreference =
            preferenceScreen.getPreference(2) as SwitchPreference
        showNotificationsPreference.isEnabled =
            enabledSwitch.isChecked
        showNotificationsPreference.isChecked = getDidShowNotifications(
            context,
            did)
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
