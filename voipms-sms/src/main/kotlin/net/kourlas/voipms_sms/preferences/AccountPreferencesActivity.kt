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

import android.os.Build
import android.os.Bundle
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import net.kourlas.voipms_sms.R

/**
 * Activity that houses a [PreferencesFragment] that displays the account
 * preferences.
 */
class AccountPreferencesActivity : AppCompatActivity() {
    // Preferences fragment for this preferences activity
    private lateinit var fragment: AccountPreferencesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load activity layout
        setContentView(R.layout.preferences_account)

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

        fragment = AccountPreferencesFragment()
        supportFragmentManager.beginTransaction().replace(
            R.id.preferences_fragment_layout, fragment).commit()

        val textView = findViewById<TextView>(R.id.text_view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.text = Html.fromHtml(
                getString(R.string.preferences_account_info), 0)
        } else {
            @Suppress("DEPRECATION")
            textView.text = Html.fromHtml(
                getString(R.string.preferences_account_info))
        }
        textView.movementMethod = LinkMovementMethod.getInstance()
    }
}