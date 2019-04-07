package net.kourlas.voipms_sms.signin

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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.didsConfigured
import net.kourlas.voipms_sms.preferences.setEmail
import net.kourlas.voipms_sms.preferences.setPassword
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.services.RetrieveDidsService
import net.kourlas.voipms_sms.sms.services.VerifyCredentialsService
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
                // Show error if one occurred
                val valid = intent?.getBooleanExtra(
                    getString(
                        R.string.verify_credentials_complete_valid), false)
                val error = intent?.getStringExtra(
                    getString(
                        R.string.verify_credentials_complete_error))


                when {
                    error != null -> {
                        toggleControls(enabled = true)
                        showSnackbar(
                            this@SignInActivity,
                            R.id.coordinator_layout,
                            error)
                    }
                    valid == false -> {
                        toggleControls(enabled = true)
                        showSnackbar(
                            this@SignInActivity,
                            R.id.coordinator_layout,
                            getString(
                                R.string.verify_credentials_error_unknown))
                    }
                    valid == true -> {
                        val username = findViewById<TextInputEditText>(
                            R.id.username)
                        val password = findViewById<TextInputEditText>(
                            R.id.password)
                        setEmail(applicationContext,
                                 username.text?.toString() ?: "")
                        setPassword(applicationContext,
                                    password.text?.toString() ?: "")

                        RetrieveDidsService.startService(
                            this@SignInActivity, autoAdd = true)
                    }
                }
            }
        }
    private val didRetrievalCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                toggleControls(enabled = true)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_in)
        onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        // Register dynamic receivers for this fragment
        registerReceiver(
            verifyCredentialsCompleteReceiver,
            IntentFilter(getString(
                R.string.verify_credentials_complete_action)))
        registerReceiver(
            didRetrievalCompleteReceiver,
            IntentFilter(getString(
                R.string.retrieve_dids_complete_action)))
    }

    override fun onPause() {
        super.onPause()

        // Unregister dynamic receivers for this fragment
        safeUnregisterReceiver(
            this,
            verifyCredentialsCompleteReceiver)
        safeUnregisterReceiver(
            this,
            didRetrievalCompleteReceiver)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        setupToolbar()
        setupTextView()
        setupButton()
    }

    /**
     * Sets up the activity toolbar.
     */
    private fun setupToolbar() {
        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            if (!blockFinish()) {
                actionBar.setHomeButtonEnabled(true)
                actionBar.setDisplayHomeAsUpEnabled(true)
            }
        }
    }

    private fun setupTextView() {
        val textView = findViewById<TextView>(R.id.text_view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            textView.text = Html.fromHtml(
                getString(R.string.sign_in_info), 0)
        } else {
            @Suppress("DEPRECATION")
            textView.text = Html.fromHtml(
                getString(R.string.sign_in_info))
        }
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupButton() {
        val button = findViewById<MaterialButton>(R.id.sign_in_button)
        button.setOnClickListener {
            toggleControls(enabled = false)

            val username = findViewById<TextInputEditText>(R.id.username)
            val password = findViewById<TextInputEditText>(R.id.password)
            VerifyCredentialsService.startService(
                applicationContext,
                username.text?.toString() ?: "",
                password.text?.toString() ?: "")
        }
    }

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

    override fun onBackPressed() {
        if (blockFinish()) {
            return
        }
        super.onBackPressed()
    }

    private fun blockFinish(): Boolean {
        return !didsConfigured(applicationContext)
               && Database.getInstance(applicationContext).getDids().isEmpty()
               && !accountConfigured(applicationContext)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (blockFinish()) {
                    return true
                }
                return super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
