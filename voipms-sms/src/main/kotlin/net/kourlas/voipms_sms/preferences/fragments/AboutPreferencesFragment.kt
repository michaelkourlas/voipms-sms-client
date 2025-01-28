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

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.launch
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.getInstallationId
import net.kourlas.voipms_sms.utils.preferences

class AboutPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_about)
    }
    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            val firebaseInstallationId = getInstallationId()

            for (preference in preferenceScreen.preferences) {
                if (preference.key == getString(R.string.preferences_about_firebase_installation_id_key)) {
                    preference.summary = firebaseInstallationId
                }
            }
        }
    }
}
