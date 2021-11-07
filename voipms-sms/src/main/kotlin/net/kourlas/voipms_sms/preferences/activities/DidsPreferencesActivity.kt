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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.runBlocking
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.network.NetworkManager
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.fragments.DidsPreferencesFragment
import net.kourlas.voipms_sms.sms.workers.RetrieveDidsWorker
import net.kourlas.voipms_sms.utils.safeUnregisterReceiver
import net.kourlas.voipms_sms.utils.showSnackbar

/**
 * Activity that houses a PreferencesFragment that displays the DID selection
 * preferences.
 */
class DidsPreferencesActivity : AppCompatActivity() {
    // Preferences fragment for this preferences activity
    private lateinit var fragment: DidsPreferencesFragment

    // Saved instance state
    private var savedInstanceState: Bundle? = null

    // Broadcast receivers
    private val didRetrievalCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Show error if one occurred
                val retrievedDids = intent?.getStringArrayListExtra(
                    getString(
                        R.string.retrieve_dids_complete_dids
                    )
                )
                val error = intent?.getStringExtra(
                    getString(
                        R.string.retrieve_dids_complete_error
                    )
                )
                if (error != null) {
                    showSnackbar(
                        this@DidsPreferencesActivity,
                        R.id.coordinator_layout,
                        error
                    )
                }

                loadPreferences(retrievedDids)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.savedInstanceState = savedInstanceState

        // Load activity layout
        setContentView(R.layout.preferences_dids)

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
                getString(R.string.preferences_dids_info), 0
            )
        } else {
            @Suppress("DEPRECATION")
            textView.text = Html.fromHtml(
                getString(R.string.preferences_dids_info)
            )
        }
        textView.movementMethod = LinkMovementMethod.getInstance()

        retrieveDids()
    }

    override fun onResume() {
        super.onResume()

        // Register dynamic receivers for this fragment
        registerReceiver(
            didRetrievalCompleteReceiver,
            IntentFilter(getString(R.string.retrieve_dids_complete_action))
        )
    }

    override fun onPause() {
        super.onPause()

        // Unregister dynamic receivers for this fragment
        safeUnregisterReceiver(this, didRetrievalCompleteReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()

        this.savedInstanceState = null
    }

    /**
     * Starts the [RetrieveDidsWorker], which will retrieve the DIDs to
     * show in this activity.
     */
    private fun retrieveDids() {
        // Verify email and password are set and that Internet connection is
        // available to avoid lengthy timeout
        if (!accountConfigured(this)) {
            loadPreferences(null)
            return
        }
        if (!NetworkManager.getInstance().isNetworkConnectionAvailable(this)) {
            showSnackbar(
                this, R.id.coordinator_layout, getString(
                    R.string.preferences_dids_error_network
                )
            )
            loadPreferences(null)
            return
        }

        // Pass control to RetrieveDidsService
        RetrieveDidsWorker.retrieveDids(this)
    }

    /**
     * Creates a preference for each of the specified DIDs, which were
     * retrieved from VoIP.ms, as well as DIDs from the database, and loads
     * them into this activity.
     *
     * @param retrievedDids The retrieved DIDs to show.
     */
    private fun loadPreferences(retrievedDids: ArrayList<String>?) {
        // Hide progress bar
        val preloadLayout = findViewById<View>(R.id.preferences_preload_layout)
        val postloadLayout = findViewById<View>(
            R.id.preferences_postload_layout
        )
        preloadLayout.visibility = View.GONE
        postloadLayout.visibility = View.VISIBLE

        // Load preferences fragment
        val bundle = Bundle()
        bundle.putStringArrayList(
            getString(
                R.string
                    .preferences_dids_fragment_retrieved_dids_key
            ),
            retrievedDids
        )
        val databaseDids = runBlocking {
            Database.getInstance(applicationContext)
                .getDids()
        }
        bundle.putStringArrayList(
            getString(
                R.string
                    .preferences_dids_fragment_database_dids_key
            ),
            ArrayList(databaseDids)
        )

        if (this.savedInstanceState == null) {
            fragment = DidsPreferencesFragment()
            fragment.arguments = bundle
            supportFragmentManager.beginTransaction().replace(
                R.id.preferences_fragment_layout, fragment
            ).commit()
        }
    }
}