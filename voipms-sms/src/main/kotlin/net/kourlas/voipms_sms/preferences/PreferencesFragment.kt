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

import android.Manifest
import android.app.ProgressDialog
import android.content.*
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.preference.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.RetrieveDidsService
import net.kourlas.voipms_sms.sms.SyncService
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber
import net.kourlas.voipms_sms.utils.isNetworkConnectionAvailable
import net.kourlas.voipms_sms.utils.showInfoDialog
import net.kourlas.voipms_sms.utils.showPermissionSnackbar
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment used to display the app's preferences.
 */
class PreferencesFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    ActivityCompat.OnRequestPermissionsResultCallback {
    var progressDialog: ProgressDialog? = null

    // Preference change handlers
    val syncIntervalPreferenceChangeListener =
        Preference.OnPreferenceChangeListener { _, _ ->
            SyncService.setupInterval(activity.applicationContext)
            true
        }

    // Broadcast receivers
    val pushNotificationsRegistrationCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                progressDialog?.hide()

                // Show error if one occurred
                val failedDids = intent?.getStringArrayListExtra(getString(
                    R.string.push_notifications_reg_complete_voip_ms_api_callback_failed_dids))
                if (failedDids == null) {
                    // Unknown error
                    showInfoDialog(activity, getString(
                        R.string.push_notifications_fail_unknown))
                } else if (!failedDids.isEmpty()) {
                    // Some DIDs failed registration
                    showInfoDialog(activity, getString(
                        R.string
                            .push_notifications_fail_register)
                        .replace("{dids}", failedDids.joinToString(", ")))
                }
            }
        }
    val didRetrievalCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                progressDialog?.hide()

                // Show error if one occurred
                val dids = intent?.getStringArrayListExtra(
                    activity.getString(
                        R.string.retrieve_dids_complete_dids))
                val error = intent?.getStringExtra(
                    activity.getString(
                        R.string.retrieve_dids_complete_error))
                if (dids == null) {
                    showInfoDialog(
                        activity,
                        activity.getString(
                            R.string.preferences_account_dids_error_unknown))
                } else if (error != null) {
                    showInfoDialog(activity, error)
                } else {
                    // Otherwise, show dialog to allow user to select DIDs
                    showSelectDidsDialog(dids)
                }
            }
        }

    /**
     * Shows a dialog that allows user to select a did from the specified DIDs.
     *
     * @param dids The specified DIDs.
     */
    private fun showSelectDidsDialog(dids: ArrayList<String>) {
        // Pre-select current DIDs
        val formattedDids = dids.map(::getFormattedPhoneNumber).toTypedArray()
        val activeDids = dids.map { getDids(activity).contains(it) }
            .toBooleanArray()
        val selectedDids = mutableSetOf<String>()
        selectedDids.addAll(getDids(activity))

        // Show dialog to allow user to select DIDs
        AlertDialog.Builder(activity, R.style.DialogTheme).apply {
            setTitle(context.getString(
                R.string.preferences_account_dids_dialog_title))
            setMultiChoiceItems(formattedDids, activeDids,
                                { _, which, isChecked ->
                                    if (isChecked) {
                                        selectedDids.add(dids[which])
                                    } else {
                                        selectedDids.remove(dids[which])
                                    }
                                })
            setPositiveButton(context.getString(R.string.ok),
                              { _, _ ->
                                  setDids(context, selectedDids)
                              })
            setNegativeButton(context.getString(R.string.cancel),
                              null)
            setCancelable(false)
            show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Populate fragment with preferences defined in XML file
        addPreferencesFromResource(R.xml.preferences)

        // Add listener for preference changes
        preferenceScreen.sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()

        if (ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED) {
            // If we have the external storage permission, we can just go
            // ahead and update the summary text and handlers
            updateSummaryAndHandlers()
        } else {
            // Ask for external storage permission (required to display
            // information associated with ringtones on external storage)
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PermissionIndex.EXTERNAL_STORAGE.ordinal)
        }

        // Register dynamic receivers for this fragment
        activity.registerReceiver(
            pushNotificationsRegistrationCompleteReceiver,
            IntentFilter(getString(
                R.string.push_notifications_reg_complete_action)))
        activity.registerReceiver(
            didRetrievalCompleteReceiver,
            IntentFilter(getString(R.string.retrieve_dids_complete_action)))
    }

    override fun onPause() {
        super.onPause()

        // Unregister dynamic receivers for this fragment
        activity.unregisterReceiver(
            pushNotificationsRegistrationCompleteReceiver)
        activity.unregisterReceiver(
            didRetrievalCompleteReceiver)
    }

    /**
     * Handler to update the summary text for each preference when one of the
     * preferences changes.
     */
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

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions,
                                         grantResults)
        if (requestCode == PermissionIndex.EXTERNAL_STORAGE.ordinal) {
            permissions.indices
                .filter {
                    permissions[it] == Manifest.permission
                        .READ_EXTERNAL_STORAGE
                }
                .forEach {
                    if (grantResults[it] != PackageManager.PERMISSION_GRANTED) {
                        // Show snackbar if permission denied
                        showPermissionSnackbar(
                            activity,
                            R.id.new_button,
                            getString(
                                R.string
                                    .preferences_perm_denied_external_storage))
                    } else {
                        // Otherwise, continue updating summary and handlers
                        updateSummaryAndHandlers()
                    }
                }
        }
    }

    fun retrieveDids() {
        // Verify email and password are set
        if (getEmail(activity) == "") {
            showInfoDialog(activity, activity.getString(
                R.string.preferences_account_dids_error_email))
            return
        }
        if (getPassword(activity) == "") {
            showInfoDialog(activity, activity.getString(
                R.string.preferences_account_dids_error_password))
            return
        }

        // Verify Internet connection is available (avoid lengthy timeout)
        if (!isNetworkConnectionAvailable(activity)) {
            showInfoDialog(activity, activity.getString(
                R.string.preferences_account_dids_error_network))
            return
        }

        // Show loading dialog
        val progressDialog = ProgressDialog(activity)
        with(progressDialog) {
            setMessage(activity.getString(
                R.string.preferences_account_dids_status))
            setCancelable(false)
            show()
        }
        this.progressDialog = progressDialog

        // Pass control to RetrieveDidsService
        activity.startService(RetrieveDidsService.getIntent(activity))
    }

    /**
     * Updates the summary text and handlers for all preferences.
     */
    private fun updateSummaryAndHandlers() {
        for (i in 0..preferenceScreen.preferenceCount - 1) {
            val preference = preferenceScreen.getPreference(i)
            if (preference is PreferenceGroup) {
                for (j in 0..preference.preferenceCount - 1) {
                    val subPreference = preference.getPreference(j)
                    updateHandlersForPreference(subPreference)
                    updateSummaryTextForPreference(subPreference)
                }
            }
        }
    }

    /**
     * Updates the summary text for the specified preference.
     *
     * @param preference The specified preference.
     */
    private fun updateSummaryTextForPreference(preference: Preference?) {
        if (preference is DidsPreference) {
            // Display list of selected DIDs as summary text
            val formattedDids = getDids(activity).map(::getFormattedPhoneNumber)
            preference.summary = formattedDids.joinToString(separator = ", ")
        } else if (preference is ListPreference) {
            // Display value of selected element as summary text
            preference.summary = preference.entry
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
            } else if (preference.key == getString(
                R.string.preferences_network_connect_timeout_key)
                       || preference.key == getString(
                R.string.preferences_network_read_timeout_key)) {
                if (preference.text.toInt() == 0) {
                    preference.summary = "Infinite"
                } else {
                    preference.summary = preference.text + " seconds"
                }
            } else {
                preference.summary = preference.text
            }
        } else if (preference is RingtonePreference) {
            // Display selected notification sound as summary text for
            // notification setting
            val notificationSound = getNotificationSound(
                activity.applicationContext)
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
                        preference.summary = "Unknown ringtone"
                    }
                } catch (ex: SecurityException) {
                    preference.summary = "Unknown ringtone (external storage" +
                                         " permission denied)"
                }
            }
        } else if (preference is StartDatePreference) {
            // Display formatted date as summary text
            val sdf = SimpleDateFormat("yyyy-MM-dd",
                                       Locale.getDefault())
            preference.summary = sdf.format(getStartDate(
                activity.applicationContext))
        }
    }

    /**
     * Updates the handlers for the specified preference.
     *
     * @param preference The specified preference.
     */
    private fun updateHandlersForPreference(preference: Preference) {
        if (preference.key == getString(
            R.string.preferences_sync_interval_key)) {
            preference.onPreferenceChangeListener =
                syncIntervalPreferenceChangeListener
        }
    }

    companion object {
        /**
         * Used to disambiguate between different permission requests.
         */
        private enum class PermissionIndex {
            EXTERNAL_STORAGE
        }
    }
}
