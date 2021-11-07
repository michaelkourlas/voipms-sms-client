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

package net.kourlas.voipms_sms.preferences.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.fragments.PreferencesFragment

/**
 * Activity that houses a PreferencesFragment that displays the app's
 * preferences.
 */
class PreferencesActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback {
    // Preferences fragment for this preferences activity
    private lateinit var fragment: PreferencesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load activity layout
        setContentView(R.layout.preferences)

        // Configure toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        // Load preferences fragment
        if (savedInstanceState == null) {
            fragment = PreferencesFragment()
            supportFragmentManager.beginTransaction().replace(
                R.id.preference_fragment_content, fragment
            ).commit()
        }
    }
}
