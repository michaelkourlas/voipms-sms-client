/*
 * VoIP.ms SMS
 * Copyright (C) 2018-2019 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.fragments.NotificationsPreferencesFragment
import net.kourlas.voipms_sms.utils.showPermissionSnackbar

/**
 * Activity that houses a PreferencesFragment that displays the notifications
 * preferences.
 */
class NotificationsPreferencesActivity : AppCompatActivity() {
    // Preferences fragment for this preferences activity
    private lateinit var fragment: NotificationsPreferencesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load activity layout
        setContentView(R.layout.preferences_notifications)

        // Configure toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        // Load preferences fragment
        if (savedInstanceState == null) {
            fragment = NotificationsPreferencesFragment()
            supportFragmentManager.beginTransaction().replace(
                R.id.preferences_fragment_layout, fragment
            ).commit()
        }

        // Ask for external storage permission (required to display
        // information associated with ringtones on external storage)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PermissionIndex.EXTERNAL_STORAGE.ordinal
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions,
            grantResults
        )
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
                            this,
                            R.id.coordinator_layout,
                            getString(
                                R.string
                                    .preferences_perm_denied_external_storage
                            )
                        )
                    } else {
                        // Otherwise, continue updating summary and handlers
                        fragment.updateSummaries()
                    }
                }
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