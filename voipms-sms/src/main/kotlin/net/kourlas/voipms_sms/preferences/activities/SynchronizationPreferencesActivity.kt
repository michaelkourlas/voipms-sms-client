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

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.fragments.SynchronizationPreferencesFragment
import net.kourlas.voipms_sms.preferences.getStartDate

/**
 * Activity that houses a PreferencesFragment that displays the
 * synchronization preferences.
 */
class SynchronizationPreferencesActivity : AppCompatActivity() {
    // Preferences fragment for this preferences activity
    private lateinit var fragment: SynchronizationPreferencesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load activity layout
        setContentView(R.layout.preferences_synchronization)

        // Configure toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        // Load info message
        val textView = findViewById<TextView>(R.id.info_text_view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.text = Html.fromHtml(
                getString(R.string.preferences_sync_info), 0
            )
        } else {
            @Suppress("DEPRECATION")
            textView.text = Html.fromHtml(
                getString(R.string.preferences_sync_info)
            )
        }
        textView.movementMethod = LinkMovementMethod.getInstance()

        // Force start date to be set to a string
        getStartDate(this)

        // Load preferences fragment
        if (savedInstanceState == null) {
            fragment = SynchronizationPreferencesFragment()
            supportFragmentManager.beginTransaction().replace(
                R.id.preferences_fragment_layout, fragment
            ).commit()
        }
    }
}