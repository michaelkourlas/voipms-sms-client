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

package net.kourlas.voipms_sms.conversations

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.newconversation.NewConversationActivity
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.SyncService
import net.kourlas.voipms_sms.utils.*

/**
 * Activity that contains a generic list of conversations.
 */
open class ConversationsActivity : AppCompatActivity(),
    ActionMode.Callback, View.OnClickListener,
    View.OnLongClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback {
    // UI elements
    private lateinit var recyclerView: RecyclerView
    protected lateinit var adapter: ConversationsRecyclerViewAdapter<
        ConversationsActivity>
    private lateinit var menu: Menu
    private var actionMode: ActionMode? = null

    // Dialog to show on first run of application
    private var firstRunDialog: AlertDialog? = null

    // Broadcast receivers
    val syncCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val error = intent?.getStringExtra(getString(
                    R.string.sync_complete_error))
                if (error != null) {
                    // Show error in snackbar if one occurred
                    showSnackbar(this@ConversationsActivity,
                                 R.id.coordinator_layout, error)
                }

                // Turn off refresh icon
                val swipeRefreshLayout = findViewById(
                    R.id.swipe_refresh_layout) as SwipeRefreshLayout
                swipeRefreshLayout.isRefreshing = false

                adapter.refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.conversations)
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        setupToolbar()
        setupRecyclerViewAndSwipeRefreshLayout()
        setupNewConversationButton()

        // Contacts permission is required to get contact names and photos
        if (ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.READ_CONTACTS),
                PermissionIndex.CONTACTS.ordinal)
        }
    }

    /**
     * Sets up the activity toolbar.
     */
    open fun setupToolbar() {
        // Set up toolbar
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        ViewCompat.setElevation(toolbar, resources
            .getDimension(R.dimen.toolbar_elevation))
        setSupportActionBar(toolbar)
    }

    /**
     * Sets up the activity recycler view and swipe refresh layout.
     */
    fun setupRecyclerViewAndSwipeRefreshLayout() {
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        recyclerView = findViewById(R.id.list) as RecyclerView
        adapter = ConversationsRecyclerViewAdapter(this, recyclerView,
                                                   layoutManager)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        val swipeRefreshLayout = findViewById(
            R.id.swipe_refresh_layout) as SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            adapter.refresh()
            startService(SyncService.getIntent(this, forceRecent = false))
        }
        swipeRefreshLayout.setColorSchemeResources(R.color.accent)
    }

    /**
     * Sets up the new conversation floating action button.
     */
    open fun setupNewConversationButton() {
        val button = findViewById(R.id.new_button) as FloatingActionButton
        button.setOnClickListener {
            if (isAccountActive(this)) {
                val newConversationIntent = Intent(
                    this, NewConversationActivity::class.java)
                startActivity(newConversationIntent)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Track number of activities
        (application as CustomApplication).conversationsActivityIncrementCount()

        // Register dynamic receivers for this activity
        registerReceiver(syncCompleteReceiver,
                         IntentFilter(getString(R.string.sync_complete_action)))

        // Perform special setup for the first time running this app
        if (!isAccountActive(this)) {
            onFirstRun()
            return
        }

        // Perform special setup for version 114
        if (getSetupCompletedForVersion(this) < 114) {
            setSetupCompletedForVersion(this@ConversationsActivity, 114)
        }

        // Refresh and perform limited synchronization
        adapter.refresh()
        startService(SyncService.getIntent(this, forceRecent = true))

        // Refresh on resume just in case the contacts permission was newly
        // granted and we need to add the contact names and photos
        if (ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    /**
     * Show a dialog with help information when the app is running for the
     * first time.
     */
    fun onFirstRun() {
        // Show a dialog with help information on first run of the app
        firstRunDialog = showAlertDialog(
            this,
            getString(R.string.conversations_first_run_dialog_title),
            getString(R.string.conversations_first_run_dialog_text),
            getString(R.string.conversations_action_settings),
            DialogInterface.OnClickListener { _, _ ->
                startActivity(Intent(this, PreferencesActivity::class.java))
            },
            getString(R.string.conversations_action_help),
            DialogInterface.OnClickListener { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW,
                                     Uri.parse(getString(R.string.help_url))))
            })
    }

    override fun onPause() {
        super.onPause()

        // Dismiss first run dialog if it is visible
        firstRunDialog?.dismiss()

        // Unregister all dynamic receivers for this activity
        unregisterReceiver(syncCompleteReceiver)
        unregisterReceiver(pushNotificationsRegistrationCompleteReceiver)

        // Track number of activities
        (application as CustomApplication).conversationsActivityDecrementCount()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Create standard options menu
        val inflater = menuInflater
        inflater.inflate(R.menu.conversations, menu)
        this.menu = menu

        // Configure the search box to trigger adapter filtering when the
        // text changes
        val searchView = menu.findItem(R.id.search_button).actionView
            as SearchView
        searchView.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    adapter.refresh(newText)
                    return true
                }
            })

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.archived_button -> {
                val intent = Intent(this,
                                    ConversationsArchivedActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.preferences_button -> {
                val intent = Intent(this, PreferencesActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.help_button -> {
                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse(getString(R.string.help_url)))
                startActivity(intent)
                return true
            }
            R.id.privacy_button -> {
                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse(getString(R.string.privacy_url)))
                startActivity(intent)
                return true
            }
            R.id.license_button -> {
                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse(getString(R.string.license_url)))
                startActivity(intent)
                return true
            }
            R.id.credits_button -> {
                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse(getString(R.string.credits_url)))
                startActivity(intent)
                return true
            }
            R.id.donate_button -> {
                val intent = Intent(Intent.ACTION_VIEW,
                                    Uri.parse(getString(R.string.donate_url)))
                startActivity(intent)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.conversations_secondary, menu)
        return true
    }

    override fun onActionItemClicked(mode: ActionMode,
                                     item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.archive_button -> return onArchiveButtonClick(mode)
            R.id.mark_read_button -> return onMarkReadButtonClick(mode)
            R.id.mark_unread_button -> return onMarkUnreadButtonClick(mode)
            R.id.delete_button -> return onDeleteButtonClick(mode)
        }
        return false
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        for (i in 0..adapter.itemCount - 1) {
            adapter[i].setChecked(i, false)
        }
        actionMode = null
    }

    /**
     * Handles the archive button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onArchiveButtonClick(mode: ActionMode): Boolean {
        // Mark all selected conversations as archived
        runOnNewThread {
            adapter
                .filter { it.checked }
                .map { it.message }
                .forEach {
                    Database.getInstance(applicationContext)
                        .markConversationArchived(it.conversationId)
                }
            runOnUiThread {
                mode.finish()
                adapter.refresh()
            }
        }
        return true
    }

    /**
     * Handles the mark read button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onMarkReadButtonClick(mode: ActionMode): Boolean {
        // Mark all selected conversations as read
        runOnNewThread {
            adapter
                .filter { it.checked }
                .map { it.message }
                .forEach {
                    Database.getInstance(applicationContext)
                        .markConversationRead(it.conversationId)
                }
            runOnUiThread {
                mode.finish()
                adapter.refresh()
            }
        }
        return true
    }

    /**
     * Handles the mark unread button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onMarkUnreadButtonClick(mode: ActionMode): Boolean {
        // Mark all selected conversations as unread
        runOnNewThread {
            adapter
                .filter { it.checked }
                .map { it.message }
                .forEach {
                    Database.getInstance(applicationContext)
                        .markConversationUnread(it.conversationId)
                }
            runOnUiThread {
                mode.finish()
                adapter.refresh()
            }
        }
        return true
    }

    /**
     * Handles the delete button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun onDeleteButtonClick(mode: ActionMode): Boolean {
        val messages = adapter
            .filter { it.checked }
            .map { it.message }

        // Request confirmation before deleting
        showAlertDialog(
            this,
            getString(R.string.conversations_delete_confirm_title),
            getString(R.string.conversations_delete_confirm_message),
            getString(R.string.delete),
            DialogInterface.OnClickListener { _, _ ->
                Thread(Runnable {
                    for (message in messages) {
                        Database.getInstance(applicationContext)
                            .deleteMessages(message.conversationId)
                    }
                    runOnUiThread {
                        mode.finish()
                        adapter.refresh()
                    }
                }).start()
            },
            getString(R.string.cancel), null)
        return true
    }

    override fun onClick(view: View) {
        if (actionMode != null) {
            // Check or uncheck item when action mode is enabled
            toggleItem(view)
        } else {
            // Open the conversation activity for the selected conversation
            val conversationItem =
                adapter[recyclerView.getChildAdapterPosition(view)]

            val intent = Intent(this, ConversationActivity::class.java)
            intent.putExtra(getString(R.string.conversation_did),
                            conversationItem.message.did)
            intent.putExtra(getString(R.string.conversation_contact),
                            conversationItem.message.contact)
            startActivity(intent)
        }
    }

    override fun onLongClick(view: View): Boolean {
        // On long click, toggle selected item
        toggleItem(view)
        return true
    }

    override fun onBackPressed() {
        // Close action mode if visible
        if (actionMode != null) {
            actionMode?.finish()
            return
        }

        // Close the search box if visible
        val searchItem = menu.findItem(R.id.search_button)
        val searchView = searchItem.actionView as SearchView
        if (!searchView.isIconified) {
            searchItem.collapseActionView()
            return
        }

        // Otherwise, do normal back button behaviour
        super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions,
                                         grantResults)
        if (requestCode == PermissionIndex.CONTACTS.ordinal) {
            permissions.indices
                .filter {
                    permissions[it] == android.Manifest.permission.READ_CONTACTS
                }
                .forEach {
                    if (grantResults[it] == PackageManager.PERMISSION_GRANTED) {
                        // If the permission request was granted, try refreshing
                        // and loading the contact name and photo
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    } else {
                        // Otherwise, show a warning
                        showPermissionSnackbar(
                            this,
                            R.id.new_button,
                            getString(
                                R.string.conversations_perm_denied_contacts))
                    }
                }
        }
    }

    /**
     * Toggles the item associated with the specified view. Activates and
     * deactivates the action mode depending on the checked item count.
     *
     * @param view The specified view.
     */
    private fun toggleItem(view: View) {
        // Inform the adapter that the item should be checked
        val index = recyclerView.getChildAdapterPosition(view)
        adapter[index].toggle(index)

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
        // the kind of checked items
        updateActionModeButtons()
    }

    /**
     * Switches between "mark as read" and "mark as unread" buttons for the
     * action mode depending on which items in the RecyclerView are selected.
     */
    private fun updateActionModeButtons() {
        var read = 0
        var unread = 0
        adapter
            .filter { it.checked }
            .forEach {
                if (it.message.isUnread) {
                    unread++
                } else {
                    read++
                }
            }

        val actionMode = actionMode ?: return
        val markReadButton = actionMode.menu.findItem(
            R.id.mark_read_button)
        val markUnreadButton = actionMode.menu.findItem(
            R.id.mark_unread_button)
        if (read > unread) {
            // Show mark unread button if there are more read buttons than
            // unread buttons
            markReadButton.isVisible = false
            markUnreadButton.isVisible = true
        } else {
            // Otherwise, show mark read button
            markReadButton.isVisible = true
            markUnreadButton.isVisible = false
        }
    }

    companion object {
        /**
         * Used to disambiguate between different permission requests.
         */
        private enum class PermissionIndex {
            CONTACTS
        }
    }
}
