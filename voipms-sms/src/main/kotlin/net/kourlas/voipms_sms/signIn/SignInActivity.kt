/*
 * VoIP.ms SMS
 * Copyright (C) 2019 Michael Kourlas
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

package net.kourlas.voipms_sms.signIn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.runBlocking
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.preferences.activities.AccountPreferencesActivity
import net.kourlas.voipms_sms.sms.workers.RetrieveDidsWorker
import net.kourlas.voipms_sms.sms.workers.VerifyCredentialsWorker
import net.kourlas.voipms_sms.utils.safeUnregisterReceiver
import net.kourlas.voipms_sms.utils.showSnackbar

/**
 * Activity used to sign-in to VoIP.ms SMS.
 */
class SignInActivity : AppCompatActivity() {
    // Broadcast receivers
    private val verifyCredentialsCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val valid = intent?.getBooleanExtra(
                    getString(
                        R.string.verify_credentials_complete_valid
                    ), false
                )
                val error = intent?.getStringExtra(
                    getString(
                        R.string.verify_credentials_complete_error
                    )
                )

                when {
                    error != null -> {
                        // Show error if one occurred
                        toggleControls(enabled = true)
                        showSnackbar(
                            this@SignInActivity,
                            R.id.coordinator_layout,
                            error
                        )
                    }
                    valid == false -> {
                        // If valid is false, then some error occurred
                        toggleControls(enabled = true)
                        showSnackbar(
                            this@SignInActivity,
                            R.id.coordinator_layout,
                            getString(
                                R.string.verify_credentials_error_unknown
                            )
                        )
                    }
                    valid == true -> {
                        // If we managed to verify the credentials, then we
                        // save them and try to enable all of the DIDs in the
                        // account
                        val username = findViewById<TextInputEditText>(
                            R.id.username
                        )
                        val password = findViewById<TextInputEditText>(
                            R.id.password
                        )
                        setEmail(
                            applicationContext,
                            username.text?.toString() ?: ""
                        )
                        setPassword(
                            applicationContext,
                            password.text?.toString() ?: ""
                        )

                        setFirstSyncAfterSignIn(this@SignInActivity, true)

                        RetrieveDidsWorker.retrieveDids(
                            this@SignInActivity, autoAdd = true
                        )
                    }
                }
            }
        }
    private val didRetrievalCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Regardless of whether this succeeded, we've successfully
                // signed in, so we exit this activity
                toggleControls(enabled = true)
                setFirstRun(applicationContext, false)
                finish()
            }
        }
    private var hasDids: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_in)
        onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        // If an account is already configured, then go to the account screen
        // instead
        if (accountConfigured(this)) {
            startActivity(Intent(this, AccountPreferencesActivity::class.java))
            setFirstRun(applicationContext, false)
            finish()
            return
        }

        // Register dynamic receivers for this fragment
        registerReceiver(
            verifyCredentialsCompleteReceiver,
            IntentFilter(
                getString(
                    R.string.verify_credentials_complete_action
                )
            )
        )
        registerReceiver(
            didRetrievalCompleteReceiver,
            IntentFilter(
                getString(
                    R.string.retrieve_dids_complete_action
                )
            )
        )
    }

    override fun onPause() {
        super.onPause()

        // Unregister dynamic receivers for this fragment
        safeUnregisterReceiver(
            this,
            verifyCredentialsCompleteReceiver
        )
        safeUnregisterReceiver(
            this,
            didRetrievalCompleteReceiver
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        hasDids = runBlocking {
            Database.getInstance(applicationContext).getDids().isNotEmpty()
        }

        setupBack()
        setupToolbar()
        setupTextView()
        setupButton()
    }

    /**
     * Sets up the back button handler.
     */
    private fun setupBack() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Block the back button if appropriate
                    if (blockFinish()) {
                        return
                    }

                    finish()
                }
            })
    }

    /**
     * Sets up the activity toolbar.
     */
    private fun setupToolbar() {
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            if (!blockFinish()) {
                it.setHomeButtonEnabled(true)
                it.setDisplayHomeAsUpEnabled(true)
            }
        }
    }

    /**
     * Sets up the info text view.
     */
    private fun setupTextView() {
        val textView = findViewById<TextView>(R.id.text_view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.text = Html.fromHtml(
                getString(R.string.sign_in_info), 0
            )
        } else {
            @Suppress("DEPRECATION")
            textView.text = Html.fromHtml(
                getString(R.string.sign_in_info)
            )
        }
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * Sets up the sign-in button.
     */
    private fun setupButton() {
        val button = findViewById<MaterialButton>(R.id.sign_in_button)
        button.setOnClickListener {
            toggleControls(enabled = false)

            val username = findViewById<TextInputEditText>(R.id.username)
            val password = findViewById<TextInputEditText>(R.id.password)
            VerifyCredentialsWorker.verifyCredentials(
                applicationContext,
                username.text?.toString() ?: "",
                password.text?.toString() ?: ""
            )
        }

        val skipButton = findViewById<MaterialButton>(R.id.skip_button)
        skipButton.setOnClickListener {
            setFirstRun(applicationContext, false)
            finish()
        }
        if (!blockFinish()) {
            skipButton.visibility = View.GONE
        }
    }

    /**
     * Enables and disables the controls during sign-in.
     */
    fun toggleControls(enabled: Boolean) {
        val button = findViewById<MaterialButton>(R.id.sign_in_button)
        button.isEnabled = enabled

        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        progressBar.visibility = if (enabled) View.INVISIBLE else View.VISIBLE

        val username = findViewById<TextInputEditText>(R.id.username)
        username.isEnabled = enabled

        val password = findViewById<TextInputEditText>(R.id.password)
        password.isEnabled = enabled
    }

    /**
     * Returns true if the user should not be allowed to leave this activity.
     */
    private fun blockFinish(): Boolean {
        return !didsConfigured(applicationContext)
            && !hasDids
            && !accountConfigured(applicationContext)
            && firstRun(applicationContext)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Block the back button if appropriate
                if (blockFinish()) {
                    return true
                }
                return super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
