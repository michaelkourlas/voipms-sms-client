/*
 * VoIP.ms SMS
 * Copyright (C) 2018-2023 Michael Kourlas
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

import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getNotificationSound
import net.kourlas.voipms_sms.preferences.setNotificationSound
import net.kourlas.voipms_sms.utils.preferences

class NotificationsPreferencesFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val ringtoneActivityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            context?.let {
                @Suppress("DEPRECATION")
                val uri =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        result
                            ?.data
                            ?.getParcelableArrayListExtra(
                                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                Uri::class.java
                            )
                    } else {
                        result
                            ?.data
                            ?.getParcelableArrayListExtra<Uri?>(
                                RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                            )
                    }
                if (uri != null) {
                    @Suppress("DEPRECATION")
                    setNotificationSound(it, uri.toString())
                } else {
                    @Suppress("DEPRECATION")
                    setNotificationSound(it, "")
                }
            }
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        context?.let {
            // Add preferences
            addPreferencesFromResource(R.xml.preferences_notifications)

            // Add listener for preference changes
            preferenceScreen
                .sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(this)

            // Add listeners to preferences
            for (preference in preferenceScreen.preferences) {
                when (preference.key) {
                    getString(R.string.preferences_notifications_sound_key) ->
                        preference.onPreferenceClickListener =
                            Preference.OnPreferenceClickListener {
                                context?.let {
                                    val intent = Intent(
                                        RingtoneManager.ACTION_RINGTONE_PICKER
                                    )
                                    intent.putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                        RingtoneManager.TYPE_NOTIFICATION
                                    )

                                    @Suppress("DEPRECATION")
                                    val uri = getNotificationSound(it)
                                    if (uri != "") {
                                        @Suppress("DEPRECATION")
                                        intent.putExtra(
                                            RingtoneManager
                                                .EXTRA_RINGTONE_EXISTING_URI,
                                            Uri.parse(
                                                getNotificationSound(it)
                                            )
                                        )
                                    }
                                    ringtoneActivityResultLauncher.launch(
                                        intent
                                    )
                                }
                                true
                            }
                }
            }

            // Update preference summaries
            updateSummaries()
        }
    }

    override fun onResume() {
        super.onResume()

        // Update preference summaries
        updateSummaries()
    }

    /**
     * Updates the summaries for all preferences.
     */
    fun updateSummaries() {
        for (preference in preferenceScreen.preferences) {
            updateSummary(preference)
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
            updateSummary(findPreference(key))
        }
    }

    /**
     * Updates the summary for the specified preference.
     */
    private fun updateSummary(preference: Preference?) {
        context?.let {
            if (preference?.key == getString(
                    R.string.preferences_notifications_sound_key
                )
            ) {
                // Display selected notification sound as summary text for
                // notification setting
                @Suppress("DEPRECATION")
                val notificationSound = getNotificationSound(it)
                if (notificationSound == "") {
                    preference.summary =
                        getString(
                            R.string.preferences_notifications_sound_silent
                        )
                } else {
                    try {
                        val ringtone = RingtoneManager.getRingtone(
                            activity, Uri.parse(notificationSound)
                        )
                        if (ringtone != null) {
                            preference.summary = ringtone.getTitle(
                                activity
                            )
                        } else {
                            preference.summary = getString(
                                R.string.preferences_notifications_sound_unknown
                            )
                        }
                    } catch (ex: SecurityException) {
                        preference.summary = getString(
                            R.string
                                .preferences_notifications_sound_unknown_perm
                        )
                    }
                }
            }
        }
    }
}