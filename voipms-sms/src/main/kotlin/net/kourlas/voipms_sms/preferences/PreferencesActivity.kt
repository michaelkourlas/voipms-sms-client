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
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.showPermissionSnackbar

/**
 * Activity that houses a [PreferencesFragment] that displays the app's
 * preferences.
 */
class PreferencesActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var fragment: PreferencesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load activity layout
        setContentView(R.layout.preferences)

        // Configure toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setElevation(toolbar, resources
            .getDimension(R.dimen.toolbar_elevation))
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }

        // Load instance of PreferencesFragment
        fragment = PreferencesFragment()
        fragmentManager.beginTransaction().replace(
            R.id.preference_fragment_content, fragment).commit()

        // Ask for external storage permission (required to display
        // information associated with ringtones on external storage)
        if (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PermissionIndex.EXTERNAL_STORAGE.ordinal)
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
                            this,
                            R.id.coordinator_layout,
                            getString(
                                R.string
                                    .preferences_perm_denied_external_storage))
                    } else {
                        // Otherwise, continue updating summary and handlers
                        fragment.updateSummaryAndHandlers()
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
