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
import androidx.appcompat.widget.Toolbar
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.fragments.DidPreferencesFragment
import net.kourlas.voipms_sms.utils.abortActivity
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber

/**
 * Activity that houses a PreferencesFragment that displays settings
 * associated with a particular DID.
 */
class DidPreferencesActivity : AppCompatActivity() {
    // Preferences fragment for this preferences activity
    private lateinit var fragment: DidPreferencesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get DID from intent extras
        val did = intent.getStringExtra(getString(R.string.preferences_did_did))
        if (did == null) {
            abortActivity(this, Exception("Missing DID extra"))
            return
        }

        // Load activity layout
        setContentView(R.layout.preferences_did)

        // Configure toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.title = getFormattedPhoneNumber(did)
        setSupportActionBar(toolbar)
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        // Load label and switch inside enabled toolbar
        val didToolbar = findViewById<Toolbar>(R.id.enabled_toolbar)
        didToolbar.inflateMenu(R.menu.preferences_did_enabled)

        // Load preferences fragment
        val bundle = Bundle()
        bundle.putString(
            getString(
                R.string.preferences_did_fragment_argument_did
            ), did
        )

        // Load preferences fragment
        if (savedInstanceState == null) {
            fragment = DidPreferencesFragment()
            fragment.arguments = bundle
            supportFragmentManager.beginTransaction().replace(
                R.id.preferences_fragment_layout, fragment
            ).commit()
        }
    }
}