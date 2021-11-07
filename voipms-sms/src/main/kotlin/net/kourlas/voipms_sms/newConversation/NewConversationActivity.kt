/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2021 Michael Kourlas
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

package net.kourlas.voipms_sms.newConversation

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.activities.DidsPreferencesActivity
import net.kourlas.voipms_sms.preferences.didsConfigured
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.signIn.SignInActivity
import net.kourlas.voipms_sms.ui.FastScroller
import net.kourlas.voipms_sms.utils.getDigitsOfString
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber

/**
 * Activity that contains a list of contacts to select to create a new
 * conversation.
 */
class NewConversationActivity : AppCompatActivity(), View.OnClickListener {
    // UI elements
    private lateinit var adapter: NewConversationRecyclerViewAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var menu: Menu

    // Relay used to forward message text from intent to new conversation
    private var messageText: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_conversation)
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        getMessageTextFromIntent()
        setupToolbar()
        setupRecyclerView()

        adapter.refresh()

        ShortcutManagerCompat.reportShortcutUsed(
            applicationContext,
            "new_conversation"
        )
    }

    /**
     * Retrieves and stores the message text from the intent.
     */
    private fun getMessageTextFromIntent() {
        val intent = intent
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null
            && type == "text/plain"
        ) {
            this.messageText = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }

    /**
     * Sets up the activity toolbar.
     */
    private fun setupToolbar() {
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowTitleEnabled(false)
            it.setCustomView(R.layout.new_conversation_toolbar)
            it.setDisplayShowCustomEnabled(true)

            // Configure the search box to trigger adapter filtering when the
            // text changes
            val searchView = it.customView.findViewById<SearchView>(
                R.id.search_view
            )
            searchView.inputType = InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            searchView
                .setOnQueryTextListener(
                    object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String): Boolean =
                            false

                        override fun onQueryTextChange(
                            newText: String
                        ): Boolean {
                            val phoneNumber = newText.replace(
                                "[^0-9]".toRegex(), ""
                            )
                            adapter.typedInPhoneNumber = phoneNumber
                            adapter.refresh(newText)
                            return true
                        }
                    })
            searchView.requestFocus()

            // Hide search icon
            val searchMagIcon = searchView.findViewById<ImageView>(
                R.id.search_mag_icon
            )
            searchMagIcon.layoutParams = LinearLayout.LayoutParams(0, 0)

            // Set cursor color and hint text
            val searchAutoComplete = searchView.findViewById<
                SearchView.SearchAutoComplete>(
                androidx.appcompat.R.id.search_src_text
            )
            searchAutoComplete.hint = getString(
                R.string.new_conversation_text_hint
            )
            searchAutoComplete.setTextColor(
                ContextCompat.getColor(
                    applicationContext, android.R.color.white
                )
            )
            searchAutoComplete.setHintTextColor(
                ContextCompat.getColor(
                    applicationContext, R.color.search_hint
                )
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                try {
                    @SuppressLint("DiscouragedPrivateApi")
                    val field = TextView::class.java.getDeclaredField(
                        "mCursorDrawableRes"
                    )
                    field.isAccessible = true
                    field.set(searchAutoComplete, R.drawable.search_cursor)
                } catch (_: java.lang.Exception) {
                }
            } else {
                searchAutoComplete.setTextCursorDrawable(
                    R.drawable.search_cursor
                )
            }
        }
    }

    /**
     * Sets up the activity recycler view.
     */
    private fun setupRecyclerView() {
        // Set up recycler view
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        recyclerView = findViewById(R.id.list)
        adapter = NewConversationRecyclerViewAdapter(this, recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        // Set up fast scroller
        FastScroller.addTo(recyclerView, FastScroller.POSITION_RIGHT_SIDE)
    }

    override fun onResume() {
        super.onResume()

        // Perform account and DID check
        performAccountDidCheck()
    }

    /**
     * Performs a check for a configured account and DIDs, and forces the user
     * to configure an account or DIDs where appropriate.
     */
    private fun performAccountDidCheck() {
        // If there are no DIDs available or the user has not configured an
        // account, then force the user to configure an account or DIDs
        if (!accountConfigured(applicationContext) && !BuildConfig.IS_DEMO) {
            startActivity(Intent(this, SignInActivity::class.java))
        } else if (!didsConfigured(
                applicationContext
            ) && !BuildConfig.IS_DEMO
        ) {
            startActivity(Intent(this, DidsPreferencesActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.new_conversation, menu)
        this.menu = menu

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dialpad_button -> return onDialpadButtonClick(item)
            R.id.keyboard_button -> return onKeyboardButtonClick(item)
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Handles the dialpad button.
     */
    private fun onDialpadButtonClick(item: MenuItem): Boolean {
        supportActionBar?.let {
            val searchView = it.customView.findViewById<SearchView>(
                R.id.search_view
            )
            searchView.inputType = InputType.TYPE_CLASS_PHONE
            item.isVisible = false
            menu.findItem(R.id.keyboard_button)?.isVisible = true
        }
        return true
    }

    /**
     * Handles the keyboard button.
     */
    private fun onKeyboardButtonClick(item: MenuItem): Boolean {
        supportActionBar?.let {
            val searchView = it.customView.findViewById<SearchView>(
                R.id.search_view
            )
            searchView.inputType = InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            item.isVisible = false
            menu.findItem(R.id.dialpad_button)?.isVisible = true
        }
        return true
    }

    override fun onClick(v: View) {
        val position = recyclerView.getChildAdapterPosition(v)
        if (position == RecyclerView.NO_POSITION) {
            return
        }

        val contactItem = adapter[position]
        if (contactItem is NewConversationRecyclerViewAdapter.ContactItem) {
            // If the selected contact has multiple phone numbers, allow the
            // user to select one of the numbers
            if (contactItem.phoneNumbersAndTypes.size > 1) {
                val phoneNumbers = contactItem
                    .phoneNumbersAndTypes
                    .map { phoneNumberAndType ->
                        "${phoneNumberAndType.phoneNumber} " +
                            "(${phoneNumberAndType.type})"
                    }

                var selectedIndex = 0
                AlertDialog.Builder(this).apply {
                    setTitle("Select phone number")
                    setSingleChoiceItems(
                        phoneNumbers.toTypedArray(),
                        selectedIndex
                    ) { _, which ->
                        selectedIndex = which
                    }
                    setPositiveButton(
                        context.getString(R.string.ok)
                    ) { _, _ ->
                        startConversationActivity(
                            phoneNumbers[selectedIndex]
                        )
                    }
                    setNegativeButton(
                        context.getString(R.string.cancel),
                        null
                    )
                    setCancelable(false)
                    show()
                }
            } else {
                startConversationActivity(contactItem.primaryPhoneNumber)
            }
        } else if (contactItem is
                NewConversationRecyclerViewAdapter.TypedInContactItem
        ) {
            startConversationActivity(contactItem.primaryPhoneNumber)
        } else {
            throw Exception("Unrecognized contact item type")
        }
    }

    /**
     * Starts a conversation activity with the specified contact.
     */
    private fun startConversationActivity(contact: String) {
        val intent = Intent(this, ConversationActivity::class.java)
        intent.putExtra(
            this.getString(R.string.conversation_contact),
            getDigitsOfString(contact)
        )
        if (messageText != null) {
            intent.putExtra(
                this.getString(
                    R.string.conversation_extra_message_text
                ),
                messageText
            )
        }
        intent.putExtra(this.getString(R.string.conversation_extra_focus), true)

        // If the user has multiple DIDs, allow the user to select the one
        // they want to use with the conversation
        val dids = getDids(this).toList()
        when {
            dids.isEmpty() -> // Silently fail if no DID set
                return
            dids.size > 1 -> {
                var selectedIndex = 0
                AlertDialog.Builder(this).apply {
                    setTitle("Select DID")
                    setSingleChoiceItems(
                        dids.map(::getFormattedPhoneNumber).toTypedArray(),
                        selectedIndex
                    ) { _, which ->
                        selectedIndex = which
                    }
                    setPositiveButton(getString(R.string.ok)) { _, _ ->
                        intent.putExtra(
                            getString(
                                R.string.conversation_did
                            ),
                            dids[selectedIndex]
                        )
                        startActivity(intent)
                    }
                    setNegativeButton(
                        getString(R.string.cancel),
                        null
                    )
                    setCancelable(false)
                    show()
                }
            }
            else -> {
                intent.putExtra(
                    getString(R.string.conversation_did),
                    dids.first()
                )
                this.startActivity(intent)
            }
        }
    }
}
