/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2019 Michael Kourlas
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

package net.kourlas.voipms_sms.conversation

import android.Manifest
import android.animation.ValueAnimator
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversations.ConversationsActivity
import net.kourlas.voipms_sms.conversations.ConversationsArchivedActivity
import net.kourlas.voipms_sms.demo.demo
import net.kourlas.voipms_sms.demo.getDemoNotification
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.sms.services.SendMessageService
import net.kourlas.voipms_sms.ui.FastScroller
import net.kourlas.voipms_sms.utils.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity used to display messages in a single conversation.
 */
class ConversationActivity : AppCompatActivity(), ActionMode.Callback,
    View.OnLongClickListener, View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback {
    // UI elements
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationRecyclerViewAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var menu: Menu? = null
    private var actionMode: ActionMode? = null

    // The DID and contact associated with this conversation
    private var did: String = ""
    private var contact: String = ""
    val conversationId: ConversationId
        get() = ConversationId(did, contact)

    // Additional metadata associated with the DID and contact, including the
    // name and photo of the person associated with the DID or contact
    private var contactName: String? = null
    private var contactBitmap: Bitmap? = null

    // Broadcast receivers
    private val syncCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val error = intent?.getStringExtra(getString(
                    R.string.sync_complete_error))
                if (error != null) {
                    // Show error in snackbar if one occurred
                    showSnackbar(this@ConversationActivity,
                                 R.id.coordinator_layout, error)
                }

                adapter.refresh()
            }
        }
    private val sendingMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?,
                               intent: Intent?) =
        // Refresh adapter to show message being sent
            adapter.refresh()
    }
    private val sentMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val error = intent?.getStringExtra(getString(
                R.string.sent_message_error))
            if (error != null) {
                // Show error in snackbar if one occurred
                showSnackbar(this@ConversationActivity,
                             R.id.coordinator_layout, error)
            }

            adapter.refresh()

            // Scroll to the bottom of the adapter so that the message is in
            // view
            if (adapter.itemCount > 0) {
                layoutManager.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.conversation)
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent == null) {
            abortActivity(this, Exception("Intent is null"))
            return
        }

        setupDidAndContact(intent)
        setupToolbar()
        setupRecyclerView()
        setupMessageText()
        setupSendButton()

        @Suppress("ConstantConditionIf")
        if (demo) {
            Notifications.getInstance(application).showDemoNotification(
                getDemoNotification())
        }
    }

    /**
     * Retrieve and store the DID and contact from the intent, as well as
     * additional metadata such as the name and photo.
     *
     * @param intent The intent that this activity was launched with.
     */
    private fun setupDidAndContact(intent: Intent) {
        val action = intent.action
        val data = intent.dataString
        if (Intent.ACTION_VIEW == action && data != null) {
            // Firebase URL intent
            val uri = Uri.parse(data)
            val id = uri.getQueryParameter("id")
            val uriDid = uri.getQueryParameter("did")
            val contactDid = uri.getQueryParameter("contact")
            if (id != null) {
                // Firebase message index URL
                val message = Database.getInstance(this)
                    .getMessageDatabaseId(id.toLong())
                if (message == null) {
                    abortActivity(this, Exception("Invalid URI: '$data'"))
                    return
                } else {
                    did = message.did
                    contact = message.contact
                }
            } else if (uriDid != null && contactDid != null) {
                // Firebase conversation index URL
                did = uriDid
                contact = contactDid
            } else {
                abortActivity(this, Exception("Invalid URI: '$data'"))
                return
            }
        } else {
            // Standard intent
            val d = intent.getStringExtra(getString(R.string.conversation_did))
            val c = intent.getStringExtra(getString(
                R.string.conversation_contact))
            if (d == null || c == null) {
                abortActivity(this, Exception("No DID or contact specified:" +
                                              " did: '$d', contact: '$c'"))
                return
            } else {
                did = d
                contact = c
            }
        }

        if (did !in getDids(applicationContext)) {
            abortActivity(this, Exception("DID no longer exists"))
            return
        }

        if (contact.length == 11 && contact[0] == '1') {
            // Remove the leading one from a North American phone number
            // (e.g. +1 (123) 555-4567)
            contact = contact.substring(1)
        }
        // Get DID and contact name and photo for use in recycler view adapter
        @Suppress("ConstantConditionIf")
        contactName = if (!demo) {
            getContactName(this, contact)
        } else {
            net.kourlas.voipms_sms.demo.getContactName(contact)
        }
        contactBitmap = getContactPhotoBitmap(this, contact)
    }

    /**
     * Sets up the activity toolbar.
     */
    private fun setupToolbar() {
        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar ?: throw Exception(
            "Action bar cannot be null")
        // Show phone number under contact name if there is a contact name;
        // otherwise just show phone number
        if (contactName != null) {
            actionBar.title = contactName
            actionBar.subtitle = getFormattedPhoneNumber(contact)
        } else {
            actionBar.title = getFormattedPhoneNumber(contact)
        }
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionMode = null
    }

    /**
     * Sets up the activity recycler view.
     */
    private fun setupRecyclerView() {
        // Set up recycler view
        layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        // Most recent message at bottom of list
        layoutManager.stackFromEnd = true
        recyclerView = findViewById(R.id.list)
        adapter = ConversationRecyclerViewAdapter(this, recyclerView,
                                                  layoutManager,
                                                  conversationId,
                                                  contactName,
                                                  contactBitmap)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        FastScroller.addTo(
            recyclerView, FastScroller.POSITION_RIGHT_SIDE)
    }

    /**
     * Sets up the activity message text container.
     */
    private fun setupMessageText() {
        // Set up container for message text box
        val messageSection = findViewById<LinearLayout>(R.id.message_section)
        ViewCompat.setElevation(
            messageSection,
            resources.getDimension(R.dimen.send_message_elevation))
        applyRoundedCornersMask(messageSection)

        // Set up message text box
        val messageEditText = findViewById<EditText>(R.id.message_edit_text)
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int,
                                           count: Int,
                                           after: Int) = // Do nothing.
                Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int,
                                       count: Int) = // Do nothing.
                Unit

            override fun afterTextChanged(s: Editable) =
                onMessageTextChange(s.toString())
        })
        messageEditText.onFocusChangeListener =
            View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    adapter.refresh()
                }
            }
        val intentMessageText = intent.getStringExtra(
            getString(R.string.conversation_extra_message_text))
        if (intentMessageText != null) {
            messageEditText.setText(intentMessageText)
            messageEditText.setSelection(messageEditText.text.length)
        }
        val intentFocus = intent.getBooleanExtra(
            getString(R.string.conversation_extra_focus), false)
        if (intentFocus) {
            messageEditText.requestFocus()
        }
    }

    /**
     * Called when the message text box text changes.

     * @param str The new text.
     */
    private fun onMessageTextChange(str: String) {
        val maxLength = applicationContext.resources.getInteger(
            R.integer.sms_max_length)
        val charsRemainingTextView = findViewById<TextView>(
            R.id.chars_remaining_text)

        if (str.length <= maxLength) {
            if (str.length >= maxLength - 10) {
                // Show "N" when there are N characters left in the first
                // message and N <= 10
                charsRemainingTextView.visibility = View.VISIBLE
                charsRemainingTextView.text =
                    (maxLength - str.length).toString()
            } else {
                // Show nothing
                charsRemainingTextView.visibility = View.GONE
            }
        } else {
            // Show "N / M" when there are M messages and M >= 2; N is the
            // number of characters left in the current message
            charsRemainingTextView.visibility = View.VISIBLE

            val charsRemaining = if (str.length % maxLength != 0) {
                maxLength - str.length % maxLength
            } else 0
            val numMessages = str.length / maxLength + 1
            charsRemainingTextView.text = getString(
                R.string.conversation_char_rem,
                charsRemaining, numMessages)
        }

        runOnNewThread {
            Database.getInstance(this).insertMessageDraft(conversationId, str)
        }
    }

    /**
     * Sets up the activity send button.
     */
    private fun setupSendButton() {
        // Set up send button
        val sendButton = findViewById<ImageButton>(R.id.send_button)
        sendButton.setOnClickListener {
            this@ConversationActivity.sendMessage()
        }
    }

    /**
     * Called after the user clicks the send message button.
     */
    private fun sendMessage() {
        // Get message from UI
        val messageEditText = findViewById<EditText>(R.id.message_edit_text)
        val messageText = messageEditText.text.toString()

        if (messageText.trim() != ""
            && accountConfigured(applicationContext)
            && did in getDids(applicationContext)) {
            // Send the message using the SendMessageService
            SendMessageService.startService(this, did, contact, messageText)

            // Clear the message text box
            messageEditText.setText("")
        }
    }

    override fun onResume() {
        super.onResume()

        // Register all dynamic receivers for this activity
        registerReceiver(syncCompleteReceiver,
                         IntentFilter(getString(R.string.sync_complete_action)))
        registerReceiver(sendingMessageReceiver,
                         IntentFilter(applicationContext.getString(
                             R.string.sending_message_action, did, contact)))
        registerReceiver(sentMessageReceiver,
                         IntentFilter(applicationContext.getString(
                             R.string.sent_message_action, did, contact)))

        // Load draft message
        val messageText = findViewById<EditText>(R.id.message_edit_text)
        runOnNewThread {
            val draftMessage = Database.getInstance(this).getMessageDraft(
                conversationId)
            runOnUiThread {
                if (draftMessage != null) {
                    messageText.setText(draftMessage.text)
                    messageText.requestFocus()
                    messageText.setSelection(messageText.text.length)
                }
            }
        }

        // Mark the conversation as read and remove any open notifications
        // related to this conversation
        Notifications.getInstance(application).cancelNotification(
            conversationId)
        runOnNewThread {
            Database.getInstance(applicationContext).markConversationRead(
                conversationId)
        }
        adapter.refresh()

        // Track number of activities
        (application as CustomApplication).conversationActivityIncrementCount(
            conversationId)
    }

    override fun onPause() {
        super.onPause()

        // Unregister all dynamic receivers for this activity
        safeUnregisterReceiver(this, syncCompleteReceiver)
        safeUnregisterReceiver(this, sendingMessageReceiver)
        safeUnregisterReceiver(this, sentMessageReceiver)

        // Track number of activities
        (application as CustomApplication).conversationActivityDecrementCount(
            conversationId)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Create standard options menu
        val inflater = menuInflater
        inflater.inflate(R.menu.conversation, menu)
        this.menu = menu

        // Replace DID menu item with DID
        val didMenuItem = menu.findItem(R.id.did_button)
        didMenuItem.title = getString(R.string.conversation_action_did,
                                      getFormattedPhoneNumber(did))

        // Hide the call button on devices without telephony support
        if (!packageManager
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            val phoneMenuItem = menu.findItem(R.id.call_button)
            phoneMenuItem.isVisible = false
        }

        // Hide the notifications button on devices running versions of Android
        // prior to Oreo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val notificationsItem = menu.findItem(R.id.notifications_button)
            notificationsItem.isVisible = false
        }

        // Configure the search box to trigger adapter filtering when the
        // text changes
        val searchView = menu.findItem(
            R.id.search_button).actionView as SearchView
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = false

                override fun onQueryTextChange(newText: String): Boolean {
                    adapter.refresh(newText)
                    return true
                }
            })

        // Set cursor color and hint text
        val searchAutoComplete = searchView.findViewById<
            SearchView.SearchAutoComplete>(
            androidx.appcompat.R.id.search_src_text)
        searchAutoComplete.hint = getString(R.string.conversation_action_search)
        searchAutoComplete.setHintTextColor(ContextCompat.getColor(
            applicationContext, R.color.search_hint))
        try {
            val field = TextView::class.java.getDeclaredField(
                "mCursorDrawableRes")
            field.isAccessible = true
            field.set(searchAutoComplete, R.drawable.search_cursor)
        } catch (_: java.lang.Exception) {
        }

        updateButtons()

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> return onUpButtonClick()
            R.id.call_button -> return onCallButtonClick()
            R.id.archive_button -> return onArchiveButtonClick()
            R.id.unarchive_button -> return onUnarchiveButtonClick()
            R.id.delete_button -> return onDeleteAllButtonClick()
            R.id.notifications_button -> return onNotificationsButtonClick()
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Handles the up button.
     *
     * @return Always returns true.
     */
    private fun onUpButtonClick(): Boolean {
        // Override standard "up" behaviour because this activity
        // has multiple parents
        runOnNewThread {
            val clazz = if (Database.getInstance(this)
                    .isConversationArchived(conversationId)) {
                ConversationsArchivedActivity::class.java
            } else {
                ConversationsActivity::class.java
            }

            runOnUiThread {
                val intent = Intent(this, clazz)
                // Simulate normal behaviour of up button with these particular
                // flags
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
        return true
    }

    /**
     * Handles the call button.
     *
     * @return Always returns true.
     */
    private fun onCallButtonClick(): Boolean {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$contact")

        // Before trying to call the contact's phone number, request the
        // CALL_PHONE permission
        val permission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't yet have the permission, so request it; if granted,
            // this method will be called again
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                0)
        } else {
            // We have the permission
            try {
                startActivity(intent)
            } catch (ignored: SecurityException) {
                // Do nothing.
            }
        }
        return true
    }

    /**
     * Handles the archive button.
     *
     * @return Always returns true.
     */
    private fun onArchiveButtonClick(): Boolean {
        runOnNewThread {
            Database.getInstance(applicationContext)
                .markConversationArchived(conversationId)
            runOnUiThread {
                finish()
            }
        }
        return true
    }

    /**
     * Handles the unarchive button.
     *
     * @return Always returns true.
     */
    private fun onUnarchiveButtonClick(): Boolean {
        runOnNewThread {
            Database.getInstance(applicationContext)
                .markConversationUnarchived(conversationId)
            runOnUiThread {
                adapter.refresh()
                updateButtons()
            }
        }
        return true
    }

    /**
     * Handles the delete all messages button.
     *
     * @return Always returns true.
     */
    private fun onDeleteAllButtonClick(): Boolean {
        // Show a confirmation prompt; if the user accepts, delete all messages
        // and return to the previous activity
        showAlertDialog(
            this,
            getString(R.string.conversation_delete_confirm_title),
            getString(R.string.conversation_delete_confirm_message),
            getString(R.string.delete),
            DialogInterface.OnClickListener { _, _ ->
                runOnNewThread {
                    Database.getInstance(this).deleteMessages(conversationId)
                    runOnUiThread {
                        finish()
                    }
                }
            },
            getString(R.string.cancel), null)

        return true
    }

    /**
     * Handles the notifications button.
     *
     * @return Always returns true.
     */
    private fun onNotificationsButtonClick(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notifications.getInstance(application).createDidNotificationChannel(
                did, contact)

            val intent = Intent(
                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE,
                            packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID,
                            getString(
                                R.string.notifications_channel_contact,
                                did, contact))
            startActivity(intent)
        }
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.conversation_secondary, menu)

        val colorAnimation = ValueAnimator.ofArgb(
            ContextCompat.getColor(applicationContext,
                                   R.color.colorPrimaryDark),
            ContextCompat.getColor(applicationContext,
                                   R.color.colorSecondaryDark))
        colorAnimation.duration = applicationContext.resources.getInteger(
            android.R.integer.config_longAnimTime).toLong()
        colorAnimation.addUpdateListener { animator ->
            window.statusBarColor = animator.animatedValue as Int
        }
        colorAnimation.start()

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
        false

    override fun onActionItemClicked(mode: ActionMode,
                                     item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.resend_button -> return onResendButtonClick(mode)
            R.id.info_button -> return onInfoButtonClick(mode)
            R.id.copy_button -> return onCopyButtonClick(mode)
            R.id.share_button -> return onShareButtonClick(mode)
            R.id.delete_button -> return onDeleteButtonClick(mode)
        }
        return false
    }

    override fun onDestroyActionMode(actionMode: ActionMode) {
        for (i in 0 until adapter.itemCount) {
            adapter[i].setChecked(false, i)
        }

        val colorAnimation = ValueAnimator.ofArgb(
            ContextCompat.getColor(applicationContext,
                                   R.color.colorSecondaryDark),
            ContextCompat.getColor(applicationContext,
                                   R.color.colorPrimaryDark))
        colorAnimation.duration = applicationContext.resources.getInteger(
            android.R.integer.config_longAnimTime).toLong()
        colorAnimation.addUpdateListener { animator ->
            window.statusBarColor = animator.animatedValue as Int
        }
        colorAnimation.start()

        this.actionMode = null
    }

    /**
     * Handles the resend button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onResendButtonClick(mode: ActionMode): Boolean {
        // Resends all checked items
        for (messageItem in adapter.messageItems) {
            if (messageItem.checked) {
                SendMessageService.startService(
                    applicationContext, conversationId,
                    messageItem.message.databaseId)
                break
            }
        }

        mode.finish()
        return true
    }

    /**
     * Handles the info button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onInfoButtonClick(mode: ActionMode): Boolean {
        // Get first checked item
        val message: Message? = adapter.messageItems
            .firstOrNull { it.checked }
            ?.message

        // Display info dialog for that item
        if (message != null) {
            val dateFormat = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dialogText = StringBuilder()
            if (message.voipId != null) {
                dialogText.append(getString(
                    R.string.conversation_info_id, message.voipId))
                dialogText.append("\n")
            }
            if (message.isIncoming) {
                dialogText.append(getString(
                    R.string.conversation_info_to, getFormattedPhoneNumber(
                    message.did)))
                dialogText.append("\n")
                dialogText.append(getString(
                    R.string.conversation_info_from, getFormattedPhoneNumber(
                    message.contact)))
                dialogText.append("\n")
            } else {
                dialogText.append(getString(
                    R.string.conversation_info_to, getFormattedPhoneNumber(
                    message.contact)))
                dialogText.append("\n")
                dialogText.append(getString(
                    R.string.conversation_info_from, getFormattedPhoneNumber(
                    message.did)))
                dialogText.append("\n")
            }
            dialogText.append(getString(
                R.string.conversation_info_date, dateFormat.format(
                message.date)))
            showAlertDialog(
                this,
                getString(R.string.conversation_info_title),
                dialogText.toString(),
                getString(R.string.ok), null, null, null)
        }

        mode.finish()
        return true
    }

    /**
     * Handles the copy button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onCopyButtonClick(mode: ActionMode): Boolean {
        // Get first checked item
        val message: Message? = adapter.messageItems
            .firstOrNull { it.checked }
            ?.message

        // Copy text of message to clipboard
        if (message != null) {
            val clipboard = getSystemService(
                Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Text message", message.text)
            clipboard.primaryClip = clip
        }

        mode.finish()
        return true
    }

    /**
     * Handles the share button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onShareButtonClick(mode: ActionMode): Boolean {
        // Get first checked item
        val message: Message? = adapter.messageItems
            .firstOrNull { it.checked }
            ?.message

        // Send a share intent with the text of the message
        if (message != null) {
            val intent = Intent(android.content.Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(android.content.Intent.EXTRA_TEXT,
                            message.text)
            startActivity(Intent.createChooser(intent, null))
        }

        mode.finish()
        return true
    }

    /**
     * Handles the delete button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onDeleteButtonClick(mode: ActionMode): Boolean {
        // Get the messages that are checked
        val messages = adapter.messageItems
            .filter { it.checked }
            .map { it.message }

        // Show a confirmation dialog
        showAlertDialog(
            this,
            getString(R.string.conversation_delete_confirm_title),
            getString(R.string.conversation_delete_confirm_message),
            getString(R.string.delete),
            DialogInterface.OnClickListener { _, _ ->
                runOnNewThread {
                    // Delete each message
                    for (message in messages) {
                        Database.getInstance(applicationContext)
                            .deleteMessage(message.did, message.databaseId,
                                           message.voipId)
                    }

                    // Go back to the previous activity if no messages remain
                    if (!Database.getInstance(applicationContext)
                            .isConversationEmpty(conversationId)) {
                        runOnUiThread {
                            finish()
                        }
                    } else {
                        runOnUiThread {
                            adapter.refresh()
                            mode.finish()
                        }
                    }
                }
            },
            getString(R.string.cancel), null)
        return true
    }

    override fun onClick(view: View) {
        if (actionMode != null) {
            // Check or uncheck item when action mode is enabled
            toggleItem(getRecyclerViewItem(view))
        } else {
            // Resend message if has not yet been sent
            val position = recyclerView.getChildAdapterPosition(
                getRecyclerViewItem(view))
            if (position != RecyclerView.NO_POSITION) {
                val messageItem = adapter[position]
                val message = messageItem.message
                if (!message.isDelivered && !message.isDeliveryInProgress) {
                    SendMessageService.startService(
                        this, conversationId, message.databaseId)
                }
            }

            val date = getRecyclerViewItem(view).findViewById<TextView>(
                R.id.date)
            if (date.visibility == View.GONE) {
                date.visibility = View.VISIBLE
            } else {
                date.visibility = View.GONE
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        // On long click, toggle selected item
        toggleItem(getRecyclerViewItem(view))
        return true
    }

    override fun onBackPressed() {
        // Close action mode if visible
        if (actionMode != null) {
            actionMode?.finish()
            return
        }

        // Close the search box if visible
        menu?.let {
            val searchItem = it.findItem(R.id.search_button)
            val searchView = searchItem.actionView as SearchView
            if (!searchView.isIconified) {
                searchItem.collapseActionView()
                return
            }
        }

        // Otherwise, do normal back button behaviour
        super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions,
                                         grantResults)
        if (requestCode == 0) {
            permissions.indices
                .filter { permissions[it] == Manifest.permission.CALL_PHONE }
                .forEach {
                    if (grantResults[it] == PackageManager.PERMISSION_GRANTED) {
                        // If the permission request was granted, try calling
                        // again
                        onCallButtonClick()
                    } else {
                        // Otherwise, show a warning
                        showPermissionSnackbar(
                            this,
                            R.id.coordinator_layout,
                            getString(R.string.conversation_perm_denied_call))
                    }
                }
        }
    }

    /**
     * Toggles the checked status of the specified view.

     * @param view The view to toggle.
     */
    private fun toggleItem(view: View) {
        // Inform the adapter that the item should be checked
        val position = recyclerView.getChildAdapterPosition(view)
        if (position != RecyclerView.NO_POSITION) {
            adapter[position].toggle(position)
        }

        // Turn on or off the action mode depending on how many items are
        // checked
        if (adapter.getCheckedItemCount() == 0) {
            actionMode?.finish()
            actionMode = null
            return
        }
        if (actionMode == null) {
            actionMode = startSupportActionMode(this)
        }

        // If the action mode is enabled, update the visible buttons to match
        // the number of checked items
        updateActionModeButtons()
    }

    /**
     * Update the visible menu buttons to match whether or not the conversation
     * is archived.
     */
    private fun updateButtons() = runOnNewThread {
        if (Database.getInstance(this)
                .isConversationArchived(conversationId)) {
            runOnUiThread {
                // If conversation archived, only show unarchive button
                menu?.let {
                    it.findItem(R.id.archive_button).isVisible = false
                    it.findItem(R.id.unarchive_button).isVisible = true
                }
            }
        } else {
            runOnUiThread {
                // If conversation unarchived, only show archive button
                menu?.let {
                    it.findItem(R.id.archive_button).isVisible = true
                    it.findItem(R.id.unarchive_button).isVisible = false
                }
            }
        }
    }

    /**
     * Update the visible action mode buttons to match the number of checked
     * items.
     */
    private fun updateActionModeButtons() {
        val actionMode = actionMode ?: return

        // The resend button should only be visible if there is a single item
        // checked and that item is in the failed to deliver state
        val resendAction = actionMode.menu.findItem(R.id.resend_button)
        val count = adapter.getCheckedItemCount()
        resendAction.isVisible = adapter.singleOrNull {
            it.checked && !it.message.isDelivered
            && !it.message.isDeliveryInProgress
        } != null

        // Certain buttons should not be visible if there is more than one
        // item visible
        val copyAction = actionMode.menu.findItem(R.id.copy_button)
        val shareAction = actionMode.menu.findItem(R.id.share_button)
        val infoAction = actionMode.menu.findItem(R.id.info_button)
        if (count >= 2) {
            infoAction.isVisible = false
            copyAction.isVisible = false
            shareAction.isVisible = false
        } else {
            infoAction.isVisible = true
            copyAction.isVisible = true
            shareAction.isVisible = true
        }
    }

    private fun getRecyclerViewItem(view: View): View {
        return if (view.parent is RecyclerView) {
            view
        } else {
            getRecyclerViewItem(view.parent as View)
        }
    }
}
