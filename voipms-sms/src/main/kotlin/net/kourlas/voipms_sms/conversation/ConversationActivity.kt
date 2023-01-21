/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2023 Michael Kourlas
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
import android.annotation.SuppressLint
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
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.*
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversations.ConversationsActivity
import net.kourlas.voipms_sms.conversations.ConversationsArchivedActivity
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.demo.getDemoNotification
import net.kourlas.voipms_sms.newConversation.NewConversationActivity
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.getDidShowInConversationsView
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.preferences.getMessageTextBoxMaximumSize
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.sms.workers.SendMessageWorker
import net.kourlas.voipms_sms.ui.FastScroller
import net.kourlas.voipms_sms.utils.*
import java.text.BreakIterator
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity used to display messages in a single conversation.
 */
open class ConversationActivity(val bubble: Boolean = false) :
    AppCompatActivity(), ActionMode.Callback,
    View.OnLongClickListener, View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback {
    // UI elements
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationRecyclerViewAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var menu: Menu
    private var actionMode: ActionMode? = null

    // The DID and contact associated with this conversation
    private lateinit var did: String
    private lateinit var contact: String
    val conversationId: ConversationId
        get() = ConversationId(did, contact)

    // Additional metadata associated with the DID and contact, including the
    // name and photo of the person associated with the DID or contact
    private var contactName: String? = null
    private lateinit var contactBitmap: Bitmap

    // Broadcast receivers
    private val syncCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Show error in snackbar if one occurred
                intent?.getStringExtra(
                    getString(
                        R.string.sync_complete_error
                    )
                )?.let {
                    showSnackbar(
                        this@ConversationActivity,
                        R.id.coordinator_layout, it
                    )
                }

                // Refresh adapter to show new messages
                if (::adapter.isInitialized) {
                    adapter.refresh()
                }
            }
        }
    private val sentMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Show error in snackbar if one occurred
            intent?.getStringExtra(
                getString(
                    R.string.sent_message_error
                )
            )?.let {
                showSnackbar(
                    this@ConversationActivity,
                    R.id.coordinator_layout, it
                )
            }

            if (::adapter.isInitialized) {
                // Refresh adapter to show message was sent
                adapter.refresh()

                // Scroll to the bottom of the adapter so that the message is
                // in view
                if (adapter.itemCount > 0) {
                    layoutManager.scrollToPosition(adapter.itemCount - 1)
                }
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
            abortActivity(this, Exception("No intent was provided"))
            return
        }

        if (!setupDidAndContact(intent)) {
            return
        }
        setupBack()
        setupToolbar()
        setupRecyclerView()
        setupMessageText()
        getMessageTextFromIntent()
        setupSendButton()
        setupDemo()

        ShortcutManagerCompat.reportShortcutUsed(
            applicationContext,
            conversationId.getId()
        )
    }

    /**
     * Retrieve and store the DID and contact from the intent, as well as
     * additional metadata such as the name and photo.
     *
     * @param intent The intent that this activity was launched with.
     */
    private fun setupDidAndContact(intent: Intent): Boolean {
        if (Intent.ACTION_SEND == intent.action
            && "text/plain" == intent.type
            && intent.getStringExtra(Intent.EXTRA_TEXT) != null
        ) {
            // Generic text share; forward to NewConversationActivity
            val shortcutId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
                } else {
                    intent.getStringExtra("android.intent.extra.shortcut.ID")
                }
            if (shortcutId == null) {
                val newIntent = Intent()
                newIntent.action = Intent.ACTION_SEND
                newIntent.type = "text/plain"
                newIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                )
                newIntent.component = ComponentName(
                    applicationContext,
                    NewConversationActivity::class.java
                )
                startActivity(newIntent)
                finish()
                return false
            } else {
                val components = shortcutId.split("_")
                if (components.size != 2) {
                    abortActivity(
                        this,
                        Exception("Invalid shortcut ID: '$shortcutId'")
                    )
                    return false
                }
                did = components[0]
                contact = components[1]
            }
        } else if (Intent.ACTION_VIEW == intent.action
            && intent.dataString != null
        ) {
            // Firebase URL intent
            val uri = Uri.parse(intent.dataString)
            val id = uri.getQueryParameter("id")
            val uriDid = uri.getQueryParameter("did")
            val contactDid = uri.getQueryParameter("contact")
            if (id != null) {
                // Firebase message index URL
                val message = runBlocking {
                    Database.getInstance(applicationContext)
                        .getMessageDatabaseId(id.toLong())
                }
                if (message == null) {
                    abortActivity(
                        this,
                        Exception("Invalid URI: '$intent.dataString'")
                    )
                    return false
                } else {
                    did = message.did
                    contact = message.contact
                }
            } else if (uriDid != null && contactDid != null) {
                // Firebase conversation index URL
                did = uriDid
                contact = contactDid
            } else {
                abortActivity(
                    this,
                    Exception("Invalid URI: '$intent.dataString'")
                )
                return false
            }
        } else {
            // Standard intent
            val d =
                intent.getStringExtra(getString(R.string.conversation_did))
            val c = intent.getStringExtra(
                getString(
                    R.string.conversation_contact
                )
            )
            if (d == null || c == null) {
                abortActivity(
                    this,
                    Exception(
                        "No DID or contact specified:" +
                            " did: '$d', contact: '$c'"
                    )
                )
                return false
            } else {
                did = d
                contact = c
            }
        }

        // We shouldn't show a conversation for a DID that is no longer
        // configured
        if (did !in getDids(applicationContext) && !BuildConfig.IS_DEMO) {
            abortActivity(this, Exception("DID '$did' no longer exists"))
            return false
        }

        // We shouldn't show a conversation for a DID that is not configured to
        // be displayed in the conversation view
        if (!getDidShowInConversationsView(
                applicationContext,
                did
            ) && !BuildConfig.IS_DEMO
        ) {
            abortActivity(
                this,
                Exception("DID '$did' not displayed in conversation view")
            )
            return false
        }

        // Remove the leading one from a North American phone number
        // (e.g. +1 (123) 555-4567)
        if (contact.length == 11 && contact[0] == '1') {
            contact = contact.substring(1)
        }

        // Get DID and contact name and photo for use in recycler view
        // adapter
        @Suppress("ConstantConditionIf")
        contactName = if (!BuildConfig.IS_DEMO) {
            getContactName(this, contact)
        } else {
            net.kourlas.voipms_sms.demo.getContactName(contact)
        }
        contactBitmap = getContactPhotoBitmap(
            this,
            contactName,
            contact,
            resources.getDimensionPixelSize(
                R.dimen.contact_badge
            )
        )

        return true
    }

    /**
     * Sets up the back button handler.
     */
    private fun setupBack() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Close action mode if visible
                    actionMode?.let {
                        it.finish()
                        return
                    }

                    // Close the search box if visible
                    if (::menu.isInitialized) {
                        val searchItem = menu.findItem(R.id.search_button)
                        val searchView = searchItem.actionView as SearchView
                        if (!searchView.isIconified) {
                            searchItem.collapseActionView()
                            return
                        }
                    }

                    finish()
                }
            })
    }

    /**
     * Sets up the activity toolbar.
     */
    open fun setupToolbar() {
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            // Show phone number under contact name if there is a contact name;
            // otherwise just show phone number
            contactName?.let { contactName ->
                it.title = contactName
                it.subtitle = getFormattedPhoneNumber(contact)
            } ?: run {
                it.title = getFormattedPhoneNumber(contact)
            }

            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
        }

        // Allow contact phone number to be copied by long clicking the toolbar
        findViewById<Toolbar>(R.id.toolbar).setOnLongClickListener {
            val clipboard = getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            val clip = ClipData.newPlainText(
                getString(R.string.conversation_contact_clipboard_description),
                getFormattedPhoneNumber(contact)
            )
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                this, getString(
                    R.string.conversation_copied_contact_toast_message
                ),
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        actionMode = null
    }

    /**
     * Sets up the activity recycler view.
     */
    private fun setupRecyclerView() {
        // Set up recycler view with most recent message at bottom of list
        layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        layoutManager.stackFromEnd = true
        recyclerView = findViewById(R.id.list)
        adapter = ConversationRecyclerViewAdapter(
            this,
            recyclerView,
            layoutManager,
            conversationId,
            contactName,
            contactBitmap
        )
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object :
                                             RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView, dx: Int,
                dy: Int
            ) {
                super.onScrolled(recyclerView, dx, dy)
                if (!adapter.loadingMoreItems
                    && layoutManager.findFirstVisibleItemPosition()
                    <= ConversationRecyclerViewAdapter.START_LOAD_INDEX
                ) {
                    adapter.loadMoreItems()
                }
            }

        })

        // Set up fast scroller
        FastScroller.addTo(
            recyclerView, FastScroller.POSITION_RIGHT_SIDE
        )
    }

    /**
     * Sets up the activity message text container.
     */
    private fun setupMessageText() {
        // Set up container for message text box
        val messageSection = findViewById<LinearLayout>(R.id.message_section)
        ViewCompat.setElevation(
            messageSection,
            resources.getDimension(R.dimen.send_message_elevation)
        )
        applyRoundedCornersMask(messageSection)

        // Set up message text box
        val messageEditText = findViewById<EditText>(R.id.message_edit_text)
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence, start: Int, before: Int,
                count: Int
            ) = Unit

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
            getString(R.string.conversation_extra_message_text)
        )
        if (intentMessageText != null) {
            messageEditText.setText(intentMessageText)
            messageEditText.setSelection(messageEditText.text.length)
        }
        val intentFocus = intent.getBooleanExtra(
            getString(R.string.conversation_extra_focus), false
        )
        if (intentFocus) {
            messageEditText.requestFocus()
        }
    }

    /**
     * Retrieves and stores the message text from the intent.
     */
    private fun getMessageTextFromIntent() {
        if (Intent.ACTION_SEND == intent.action
            && "text/plain" == intent.type
        ) {
            val messageText = findViewById<EditText>(
                R.id.message_edit_text
            )
            if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                messageText.setText(intent.getStringExtra(Intent.EXTRA_TEXT))
                messageText.requestFocus()
                messageText.setSelection(messageText.text.length)
            }
        }
    }

    /**
     * Called when the message text box text changes.
     */
    private fun onMessageTextChange(newText: String) {
        val maxLength = applicationContext.resources.getInteger(
            R.integer.sms_max_length
        )
        val charsRemainingTextView = findViewById<TextView>(
            R.id.chars_remaining_text
        )

        // VoIP.ms uses UTF-8 encoding for text messages; any message
        // exceeding N bytes when encoded using UTF-8 is too long
        var msgsCount = 1
        var bytesCount = 0
        val boundary = BreakIterator.getCharacterInstance(Locale.getDefault())
        boundary.setText(newText)
        var current = boundary.first()
        var next = boundary.next()
        while (next != BreakIterator.DONE) {
            val cluster = newText.substring(current, next)
            val clusterBytes = cluster.toByteArray(Charsets.UTF_8)
            if (bytesCount + clusterBytes.size > maxLength) {
                msgsCount += 1
                bytesCount = 0
            }
            bytesCount += clusterBytes.size
            current = next
            next = boundary.next()
        }

        if (msgsCount == 1) {
            if (bytesCount >= maxLength - 10) {
                // Show "N" when there are N characters left in the first
                // message and N <= 10
                charsRemainingTextView.visibility = View.VISIBLE
                charsRemainingTextView.text =
                    (maxLength - bytesCount).toString()
            } else {
                // Show nothing
                charsRemainingTextView.visibility = View.GONE
            }
        } else {
            // Show "N / M" when there are M messages and M >= 2; N is the
            // number of characters left in the current message
            charsRemainingTextView.visibility = View.VISIBLE
            charsRemainingTextView.text = getString(
                R.string.conversation_char_rem,
                maxLength - bytesCount, msgsCount
            )
        }

        lifecycleScope.launch(Dispatchers.Default) {
            Database.getInstance(applicationContext)
                .updateConversationDraft(
                    conversationId,
                    newText
                )
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
     * Sets up the conversation activity for demo mode.
     */
    private fun setupDemo() {
        @Suppress("ConstantConditionIf")
        if (BuildConfig.IS_DEMO) {
            Notifications.getInstance(applicationContext).showDemoNotification(
                getDemoNotification()
            )
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
            && did in getDids(applicationContext)
        ) {
            // Clear the message text box.
            messageEditText.setText("")

            // Send the message using the SendMessageService.
            lifecycleScope.launch(Dispatchers.Default) {
                val ids = Database.getInstance(
                    applicationContext
                )
                    .insertConversationMessagesDeliveryInProgress(
                        ConversationId(
                            did,
                            contact
                        ),
                        getMessageTexts(applicationContext, messageText)
                    )
                for (id in ids) {
                    SendMessageWorker.sendMessage(applicationContext, id)
                }

                ensureActive()

                lifecycleScope.launch(Dispatchers.Main) {
                    // Refresh adapter to show message being sent.
                    adapter.refresh()

                    // Scroll to the bottom of the adapter so that the message
                    // is in view.
                    if (adapter.itemCount > 0) {
                        layoutManager.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Register all dynamic receivers for this activity
        registerReceiver(
            syncCompleteReceiver,
            IntentFilter(getString(R.string.sync_complete_action))
        )
        registerReceiver(
            sentMessageReceiver,
            IntentFilter(
                applicationContext.getString(
                    R.string.sent_message_action, did, contact
                )
            )
        )

        // Load draft message
        val messageText = findViewById<EditText>(R.id.message_edit_text)
        lifecycleScope.launch(Dispatchers.Default) {
            val draftMessage =
                Database.getInstance(applicationContext).getConversationDraft(
                    conversationId
                )

            ensureActive()

            withContext(Dispatchers.Main) {
                if (draftMessage != null) {
                    messageText.setText(draftMessage.text)
                    messageText.requestFocus()
                    messageText.setSelection(messageText.text.length)
                }
            }
        }

        // Set max lines
        messageText.maxLines = getMessageTextBoxMaximumSize(this)

        // Mark the conversation as read and cancel any open notifications
        // related to this conversation (unless we are displaying in bubble
        // mode)
        if (!bubble) {
            Notifications.getInstance(applicationContext).cancelNotification(
                conversationId
            )
        } else {
            // If this is a bubble, still clear the notification state.
            Notifications.getInstance(applicationContext)
                .clearNotificationState(
                    conversationId
                )
        }
        lifecycleScope.launch(Dispatchers.Default) {
            Database.getInstance(applicationContext).markConversationRead(
                conversationId
            )
        }
        adapter.refresh()

        // Track number of activities
        if (!bubble) {
            (application as CustomApplication)
                .conversationActivityIncrementCount(conversationId)
        }
    }

    override fun onPause() {
        super.onPause()

        // Unregister all dynamic receivers for this activity
        safeUnregisterReceiver(this, syncCompleteReceiver)
        safeUnregisterReceiver(this, sentMessageReceiver)

        // Track number of activities
        if (!bubble) {
            (application as CustomApplication)
                .conversationActivityDecrementCount(conversationId)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Create standard options menu
        val inflater = menuInflater
        inflater.inflate(R.menu.conversation, menu)
        this.menu = menu

        // Replace DID menu item with DID
        val didMenuItem = menu.findItem(R.id.did_button)
        didMenuItem.title = getString(
            R.string.conversation_action_did,
            getFormattedPhoneNumber(did)
        )
        didMenuItem.setOnMenuItemClickListener {
            val clipboard = getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            val clip = ClipData.newPlainText(
                getString(R.string.conversation_did_clipboard_description),
                getFormattedPhoneNumber(did)
            )
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                this, getString(
                    R.string.conversation_copied_did_toast_message
                ),
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        // Hide the call button on devices without telephony support
        if (!packageManager
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        ) {
            val phoneMenuItem = menu.findItem(R.id.call_button)
            phoneMenuItem.isVisible = false
        }

        // Hide the notifications button on devices running versions of Android
        // prior to Oreo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val notificationsItem = menu.findItem(R.id.notifications_button)
            notificationsItem.isVisible = false
        }

        // Hide the bubble button if we cannot bubble this conversation, or we
        // are already in a bubble.
        if (bubble
            || !Notifications.getInstance(applicationContext)
                .canBubble(did, contact)
        ) {
            val bubbleItem = menu.findItem(R.id.bubble_button)
            bubbleItem.isVisible = false
        }

        // Hide certain menu options if we are in a bubble, but show a menu
        // option that opens the full view.
        if (bubble) {
            val notificationItem = menu.findItem(R.id.notifications_button)
            notificationItem.isVisible = false
            val exportItem = menu.findItem(R.id.export_button)
            exportItem.isVisible = false
        }

        // Configure the search box to trigger adapter filtering when the
        // text changes
        val searchView = menu.findItem(
            R.id.search_button
        ).actionView as SearchView
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
            androidx.appcompat.R.id.search_src_text
        )
        searchAutoComplete.hint = getString(R.string.conversation_action_search)
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
                val field = TextView::class.java.getDeclaredField(
                    "mCursorDrawableRes"
                )
                field.isAccessible = true
                field.set(searchAutoComplete, R.drawable.search_cursor)
            } catch (_: java.lang.Exception) {
            }
        } else {
            searchAutoComplete.setTextCursorDrawable(R.drawable.search_cursor)
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> return onUpButtonClick()
            R.id.call_button -> return onCallButtonClick()
            R.id.notifications_button -> return onNotificationsButtonClick()
            R.id.bubble_button -> return onBubbleButtonClick()
            R.id.export_button -> return onExportButtonClick()
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Handles the up button.
     */
    private fun onUpButtonClick(): Boolean {
        // Launch the conversation if we are in a bubble.
        if (bubble) {
            val intent = Intent(this, ConversationActivity::class.java)
            intent.putExtra(
                getString(
                    R.string.conversation_did
                ), did
            )
            intent.putExtra(
                getString(
                    R.string.conversation_contact
                ), contact
            )
            val stackBuilder = TaskStackBuilder.create(this)
            stackBuilder.addNextIntentWithParentStack(intent)
            stackBuilder.startActivities()
            return false
        }

        // Override standard "up" behaviour because this activity
        // has multiple parents
        lifecycleScope.launch(Dispatchers.Default) {
            val clazz = if (Database.getInstance(applicationContext)
                    .isConversationArchived(conversationId)
            ) {
                ConversationsArchivedActivity::class.java
            } else {
                ConversationsActivity::class.java
            }

            ensureActive()

            withContext(Dispatchers.Main) {
                val intent = Intent(applicationContext, clazz)
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
     */
    private fun onCallButtonClick(): Boolean {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$contact")

        // Before trying to call the contact's phone number, request the
        // CALL_PHONE permission
        val permission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CALL_PHONE
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't yet have the permission, so request it; if granted,
            // this method will be called again
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                0
            )
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
     * Handles the notifications button.
     */
    private fun onNotificationsButtonClick(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notifications.getInstance(applicationContext)
                .createDidNotificationChannel(
                    did, contact
                )

            val intent = Intent(
                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            )
            intent.putExtra(
                Settings.EXTRA_APP_PACKAGE,
                packageName
            )
            intent.putExtra(
                Settings.EXTRA_CHANNEL_ID,
                getString(
                    R.string.notifications_channel_contact,
                    did, contact
                )
            )
            startActivity(intent)
        }

        return true
    }

    /**
     * Handles the bubble button.
     */
    private fun onBubbleButtonClick(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            moveTaskToBack(true)
            lifecycleScope.launch(Dispatchers.Default) {
                Notifications.getInstance(applicationContext)
                    .showNotifications(
                        setOf(conversationId),
                        bubbleOnly = true,
                        autoLaunchBubble = true
                    )
            }
        }
        return true
    }

    /**
     * Handles the export button.
     */
    private fun onExportButtonClick(): Boolean {
        try {
            val messages = adapter.messageItems.map { it.message }
            val json = JsonParserManager.getInstance().parser.adapter(
                ExportableMessages::class.java
            )
                .toJson(ExportableMessages(messages))
            val clipboard = getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            val clip = ClipData.newPlainText(
                getString(
                    R.string.conversation_conversation_clipboard_description
                ),
                json
            )
            clipboard.setPrimaryClip(clip)
            Toast.makeText(
                this, getString(
                    R.string.conversation_copied_conversation_toast_message
                ),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this, getString(
                    R.string.conversation_copied_conversation_fail_toast_message
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.conversation_secondary, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
        false

    override fun onActionItemClicked(
        mode: ActionMode,
        item: MenuItem
    ): Boolean {
        when (item.itemId) {
            R.id.resend_button -> return onResendButtonClick(mode)
            R.id.info_button -> return onInfoButtonClick(mode)
            R.id.copy_button -> return onCopyButtonClick(mode)
            R.id.share_button -> return onShareButtonClick(mode)
            R.id.delete_button -> return onDeleteButtonClick(mode)
        }

        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        // Uncheck all items
        for (i in 0 until adapter.itemCount) {
            adapter[i].setChecked(false, i)
        }
        actionMode = null
    }

    /**
     * Handles the resend button.
     */
    private fun onResendButtonClick(mode: ActionMode): Boolean {
        // Resend all checked items.
        val databaseIds = adapter.messageItems.filter { it.checked }
            .map { it.message.databaseId }
        lifecycleScope.launch(Dispatchers.Default) {
            for (databaseId in databaseIds) {
                Database.getInstance(applicationContext)
                    .markMessageDeliveryInProgress(databaseId)
                SendMessageWorker.sendMessage(applicationContext, databaseId)
            }
        }
        mode.finish()
        return true
    }

    /**
     * Handles the info button.
     */
    private fun onInfoButtonClick(mode: ActionMode): Boolean {
        // Get first checked item
        val message: Message? = adapter.messageItems
            .firstOrNull { it.checked }
            ?.message

        // Display info dialog for that item
        if (message != null) {
            val dateFormat = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
            )
            val dialogText = StringBuilder()
            if (message.voipId != null) {
                dialogText.append(
                    getString(
                        R.string.conversation_info_id, message.voipId
                    )
                )
                dialogText.append("\n")
            }
            if (message.isIncoming) {
                dialogText.append(
                    getString(
                        R.string.conversation_info_to, getFormattedPhoneNumber(
                            message.did
                        )
                    )
                )
                dialogText.append("\n")
                dialogText.append(
                    getString(
                        R.string.conversation_info_from,
                        getFormattedPhoneNumber(
                            message.contact
                        )
                    )
                )
                dialogText.append("\n")
            } else {
                dialogText.append(
                    getString(
                        R.string.conversation_info_to, getFormattedPhoneNumber(
                            message.contact
                        )
                    )
                )
                dialogText.append("\n")
                dialogText.append(
                    getString(
                        R.string.conversation_info_from,
                        getFormattedPhoneNumber(
                            message.did
                        )
                    )
                )
                dialogText.append("\n")
            }
            dialogText.append(
                getString(
                    R.string.conversation_info_date, dateFormat.format(
                        message.date
                    )
                )
            )
            showAlertDialog(
                this,
                getString(R.string.conversation_info_title),
                dialogText.toString(),
                getString(R.string.ok), null, null, null
            )
        }

        mode.finish()
        return true
    }

    /**
     * Handles the copy button.
     */
    private fun onCopyButtonClick(mode: ActionMode): Boolean {
        // Get first checked item
        val message: Message? = adapter.messageItems
            .firstOrNull { it.checked }
            ?.message

        // Copy text of message to clipboard
        if (message != null) {
            val clipboard = getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            val clip = ClipData.newPlainText(
                getString(R.string.conversation_message_clipboard_description),
                message.text
            )
            clipboard.setPrimaryClip(clip)
        }

        mode.finish()
        return true
    }

    /**
     * Handles the share button.
     */
    private fun onShareButtonClick(mode: ActionMode): Boolean {
        // Get first checked item
        val message: Message? = adapter.messageItems
            .firstOrNull { it.checked }
            ?.message

        // Send a share intent with the text of the message
        if (message != null) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(
                Intent.EXTRA_TEXT,
                message.text
            )
            startActivity(Intent.createChooser(intent, null))
        }

        mode.finish()
        return true
    }

    /**
     * Handles the delete button.
     */
    private fun onDeleteButtonClick(mode: ActionMode): Boolean {
        // Get the messages that are checked.
        val messages = adapter.messageItems
            .filter { it.checked }
            .map { it.message }

        lifecycleScope.launch(Dispatchers.Default) {
            // Delete each message.
            for (message in messages) {
                Database.getInstance(applicationContext)
                    .deleteMessage(
                        message.did, message.databaseId,
                        message.voipId
                    )
            }

            ensureActive()

            withContext(Dispatchers.Main) {
                adapter.refresh()
                mode.finish()

                showSnackbar(
                    this@ConversationActivity,
                    R.id.coordinator_layout,
                    resources.getQuantityString(
                        R.plurals.conversation_message_deleted,
                        messages.size,
                        messages.size
                    ),
                    getString(R.string.undo),
                    {
                        lifecycleScope.launch(Dispatchers.Default) {
                            // Restore the messages.
                            Database.getInstance(applicationContext)
                                .insertMessagesDatabase(messages)

                            ensureActive()

                            withContext(Dispatchers.Main) {
                                adapter.refresh()
                            }
                        }
                    })
            }
        }

        return true
    }

    override fun onClick(view: View) {
        if (actionMode != null) {
            // Check or uncheck item when action mode is enabled
            toggleItem(getRecyclerViewContainingItem(view))
        } else {
            // Resend message if has not yet been sent
            val position = recyclerView.getChildAdapterPosition(
                getRecyclerViewContainingItem(view)
            )
            if (position != RecyclerView.NO_POSITION) {
                val messageItem = adapter[position]
                val message = messageItem.message
                if (!message.isDelivered && !message.isDeliveryInProgress) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        Database.getInstance(applicationContext)
                            .markMessageDeliveryInProgress(
                                messageItem.message.databaseId
                            )
                        SendMessageWorker.sendMessage(
                            applicationContext,
                            messageItem.message.databaseId
                        )
                    }
                }
            }

            // Toggle the date
            val date = getRecyclerViewContainingItem(
                view
            ).findViewById<TextView>(R.id.date)
            if (date.visibility == View.GONE) {
                date.visibility = View.VISIBLE
            } else {
                date.visibility = View.GONE
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        // On long click, toggle selected item
        toggleItem(getRecyclerViewContainingItem(view))
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions,
            grantResults
        )
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
                            getString(R.string.conversation_perm_denied_call)
                        )
                    }
                }
        }
    }

    /**
     * Toggles the checked status of the specified view.
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
     * Update the visible action mode buttons to match the number of checked
     * items.
     */
    private fun updateActionModeButtons() {
        actionMode?.let {
            // The resend button should only be visible if there is a single item
            // checked and that item is in the failed to deliver state
            val resendAction = it.menu.findItem(R.id.resend_button)
            val count = adapter.getCheckedItemCount()
            resendAction.isVisible = adapter.singleOrNull { item ->
                item.checked && !item.message.isDelivered
                    && !item.message.isDeliveryInProgress
            } != null

            // Certain buttons should not be visible if there is more than one
            // item visible
            val copyAction = it.menu.findItem(R.id.copy_button)
            val shareAction = it.menu.findItem(R.id.share_button)
            val infoAction = it.menu.findItem(R.id.info_button)

            infoAction.isVisible = count < 2
            copyAction.isVisible = count < 2
            shareAction.isVisible = count < 2
        }
    }

    /**
     * Gets the containing item for the specified view in the recycler view.
     */
    private fun getRecyclerViewContainingItem(view: View): View {
        return if (view.parent is RecyclerView) {
            view
        } else {
            getRecyclerViewContainingItem(view.parent as View)
        }
    }

    @JsonClass(generateAdapter = true)
    data class ExportableMessages(val messages: List<Message>)
}
