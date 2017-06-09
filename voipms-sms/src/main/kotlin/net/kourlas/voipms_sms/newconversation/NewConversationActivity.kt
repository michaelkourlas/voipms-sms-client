/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2017 Michael Kourlas
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

package net.kourlas.voipms_sms.newconversation

import android.content.Intent
import android.os.Bundle
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.futuremind.recyclerviewfastscroll.FastScroller
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.ui.CustomScrollerViewProvider
import net.kourlas.voipms_sms.utils.getDigitsOfString

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
    }

    /**
     * Retrieves and stores the message text from the intent.
     */
    fun getMessageTextFromIntent() {
        val intent = intent
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null
            && type == "text/plain") {
            this.messageText = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }

    /**
     * Sets up the activity toolbar.
     */
    fun setupToolbar() {
        // Set up toolbar
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        ViewCompat.setElevation(toolbar, resources
            .getDimension(R.dimen.toolbar_elevation))
        setSupportActionBar(toolbar)

        val actionBar = supportActionBar ?:
                        throw Exception("Action bar cannot be null")
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setDisplayShowTitleEnabled(false)
        actionBar.setCustomView(R.layout.new_conversation_toolbar)
        actionBar.setDisplayShowCustomEnabled(true)

        // Configure the search box to trigger adapter filtering when the
        // text changes
        val searchView = actionBar.customView.findViewById(
            R.id.search_view) as SearchView
        searchView.inputType = InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        searchView
            .setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        return false
                    }

                    override fun onQueryTextChange(
                        newText: String): Boolean {
                        val phoneNumber = newText.replace(
                            "[^0-9]".toRegex(), "")
                        adapter.typedInPhoneNumber = phoneNumber
                        adapter.refresh(newText)
                        return true
                    }
                })
        searchView.requestFocus()

        // Hide search icon
        val searchMagIcon = searchView.findViewById(
            R.id.search_mag_icon) as ImageView
        searchMagIcon.layoutParams = LinearLayout.LayoutParams(0, 0)
    }

    /**
     * Sets up the activity recycler view.
     */
    fun setupRecyclerView() {
        // Set up recycler view
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        recyclerView = findViewById(R.id.list) as RecyclerView
        adapter = NewConversationRecyclerViewAdapter(this, recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        val fastScroller = findViewById(R.id.fastscroll) as FastScroller
        fastScroller.setRecyclerView(recyclerView)
        fastScroller.setViewProvider(
            CustomScrollerViewProvider())
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
     *
     * @return Always returns true.
     */
    fun onDialpadButtonClick(item: MenuItem): Boolean {
        val actionBar = supportActionBar ?:
                        throw Exception("Action bar cannot be null")
        val searchView = actionBar.customView
            .findViewById(R.id.search_view) as SearchView
        searchView.inputType = InputType.TYPE_CLASS_PHONE
        item.isVisible = false
        menu.findItem(R.id.keyboard_button).isVisible = true
        return true
    }

    /**
     * Handles the keyboard button.
     *
     * @return Always returns true.
     */
    fun onKeyboardButtonClick(item: MenuItem): Boolean {
        val actionBar = supportActionBar ?:
                        throw Exception("Action bar cannot be null")
        val searchView = actionBar.customView
            .findViewById(R.id.search_view) as SearchView
        searchView.inputType = InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        item.isVisible = false
        menu.findItem(R.id.dialpad_button).isVisible = true
        return true
    }

    override fun onClick(v: View?) {
        val contactItem = adapter[recyclerView.getChildAdapterPosition(v)]

        if (contactItem is NewConversationRecyclerViewAdapter.ContactItem) {
            // If the selected contact has multiple phone numbers, allow the
            // user to select one of the numbers
            if (contactItem.secondaryPhoneNumbers.isNotEmpty()) {
                val phoneNumbers = mutableListOf<String>()
                phoneNumbers.add(contactItem.primaryPhoneNumber)
                phoneNumbers.addAll(contactItem.secondaryPhoneNumbers)

                var selectedIndex: Int = 0
                AlertDialog.Builder(this, R.style.DialogTheme).apply {
                    setTitle("Select phone number")
                    setSingleChoiceItems(phoneNumbers.toTypedArray(),
                                         selectedIndex, {
                                             _, which ->
                                             selectedIndex = which
                                         })
                    setPositiveButton(context.getString(R.string.ok),
                                      { _, _ ->
                                          startConversationActivity(
                                              phoneNumbers[selectedIndex])
                                      })
                    setNegativeButton(context.getString(R.string.cancel),
                                      null)
                    setCancelable(false)
                    show()
                }
            } else {
                startConversationActivity(contactItem.primaryPhoneNumber)
            }
        } else if (contactItem
            is NewConversationRecyclerViewAdapter.TypedInContactItem) {
            startConversationActivity(contactItem.primaryPhoneNumber)
        } else {
            throw Exception("Unrecognized contact item type")
        }
    }

    /**
     * Starts a conversation activity with the specified contact.
     *
     * @param contact The specified contact.
     */
    fun startConversationActivity(contact: String) {
        val intent = Intent(this, ConversationActivity::class.java)
        intent.putExtra(this.getString(R.string.conversation_contact),
                        getDigitsOfString(contact))
        if (messageText != null) {
            intent.putExtra(
                this.getString(
                    R.string.conversation_extra_message_text),
                messageText)
        }
        intent.putExtra(this.getString(R.string.conversation_extra_focus), true)

        // If the user has multiple DIDs, allow the user to select the one
        // they want to use with the conversation
        val dids = getDids(this).toList()
        if (dids.size > 1) {
            var selectedIndex: Int = 0
            AlertDialog.Builder(this, R.style.DialogTheme).apply {
                setTitle("Select DID")
                setSingleChoiceItems(dids.toTypedArray(), selectedIndex, {
                    _, which ->
                    selectedIndex = which
                })
                setPositiveButton(getString(R.string.ok),
                                  { _, _ ->
                                      intent.putExtra(
                                          getString(R.string.conversation_did),
                                          dids[selectedIndex])
                                      startActivity(intent)
                                  })
                setNegativeButton(getString(R.string.cancel),
                                  null)
                setCancelable(false)
                show()
            }
        } else {
            intent.putExtra(getString(R.string.conversation_did),
                            getDids(this).first())
            this.startActivity(intent)
        }
    }
}
