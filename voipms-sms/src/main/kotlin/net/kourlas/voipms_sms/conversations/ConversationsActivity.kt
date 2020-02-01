/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2020 Michael Kourlas
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
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.newConversation.NewConversationActivity
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.preferences.activities.AccountPreferencesActivity
import net.kourlas.voipms_sms.preferences.activities.PreferencesActivity
import net.kourlas.voipms_sms.preferences.activities.SynchronizationPreferencesActivity
import net.kourlas.voipms_sms.signIn.SignInActivity
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.services.SyncService
import net.kourlas.voipms_sms.utils.*
import java.text.SimpleDateFormat
import java.util.*

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

    // Mapping between items in the navigation menu and their corresponding DIDs
    private lateinit var navViewMenuItemDidMap: HashMap<MenuItem, String>

    // Broadcast receivers
    private val syncCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Show error in snackbar if one occurred
                intent?.getStringExtra(getString(
                    R.string.sync_complete_error))?.let {
                    showSnackbar(this@ConversationsActivity,
                                 R.id.coordinator_layout, it)
                }

                // Turn off refresh icon if this was a complete sync
                if (intent?.getBooleanExtra(getString(
                        R.string.sync_complete_full), false) == true) {
                    val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
                        R.id.swipe_refresh_layout)
                    swipeRefreshLayout.isRefreshing = false
                }

                // Refresh adapter to show new messages
                if (::adapter.isInitialized) {
                    adapter.refresh()
                }
            }
        }
    private val pushNotificationsRegistrationCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringArrayListExtra(getString(
                    R.string.push_notifications_reg_complete_failed_dids))?.let {
                    if (it.isNotEmpty()) {
                        // Some DIDs failed registration
                        showSnackbar(
                            this@ConversationsActivity,
                            R.id.coordinator_layout,
                            getString(
                                R.string.push_notifications_fail_register))
                    }
                } ?: run {
                    // Unknown error
                    showSnackbar(
                        this@ConversationsActivity,
                        R.id.coordinator_layout,
                        getString(R.string.push_notifications_fail_unknown))
                }

                // Regardless of whether an error occurred, mark setup as
                // complete
                setSetupCompletedForVersion(this@ConversationsActivity, 114)
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
        setupPermissions()
        setupNavigationView()
    }

    /**
     * Sets up the activity toolbar.
     */
    open fun setupToolbar() {
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_menu_toolbar_24dp)
        }
    }

    /**
     * Sets up the activity recycler view and swipe refresh layout.
     */
    private fun setupRecyclerViewAndSwipeRefreshLayout() {
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        recyclerView = findViewById(R.id.list)
        adapter = ConversationsRecyclerViewAdapter(this, recyclerView,
                                                   layoutManager)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
            R.id.swipe_refresh_layout)
        swipeRefreshLayout.setOnRefreshListener {
            adapter.refresh()
            SyncService.startService(this, forceRecent = false)
        }
        swipeRefreshLayout.setColorSchemeResources(R.color.swipe_refresh_icon)
    }

    /**
     * Sets up the new conversation floating action button.
     */
    open fun setupNewConversationButton() {
        findViewById<FloatingActionButton>(R.id.chat_button).let {
            if (!didsConfigured(applicationContext)) {
                (it as View).visibility = View.GONE
            } else {
                (it as View).visibility = View.VISIBLE
            }
            it.setOnClickListener {
                val newConversationIntent = Intent(
                    this, NewConversationActivity::class.java)
                startActivity(newConversationIntent)
            }
        }
    }

    /**
     * Requests the contacts permission.
     */
    private fun setupPermissions() {
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
     * Sets up the navigation view.
     */
    private fun setupNavigationView() {
        navViewMenuItemDidMap = HashMap()
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // Filter the list of conversations to the selected DID
            drawerLayout.closeDrawer(GravityCompat.START)
            navViewMenuItemDidMap[menuItem]?.let {
                setActiveDid(applicationContext, it)
                menuItem.isChecked = true
                adapter.refresh()
            }
            true
        }
        navigationView.getHeaderView(0).setOnClickListener {
            // If an account is configured, show the account screen; otherwise
            // show the sign-in screen
            if (accountConfigured(applicationContext)) {
                startActivity(
                    Intent(this, AccountPreferencesActivity::class.java))
            } else {
                startActivity(Intent(this, SignInActivity::class.java))
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
        registerReceiver(
            pushNotificationsRegistrationCompleteReceiver,
            IntentFilter(getString(
                R.string.push_notifications_reg_complete_action)))

        // Perform initial setup as well as account and DID check
        performAccountDidCheck()
        performInitialSetup()

        // Refresh and perform limited synchronization
        adapter.refresh()
        SyncService.startService(this, forceRecent = true)

        // Refresh on resume just in case the contacts permission was newly
        // granted and we need to add the contact names and photos
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        // Delete any notification channels and groups that are no longer
        // needed and rename existing channels if necessary
        runOnNewThread {
            Notifications.getInstance(application)
                .createDefaultNotificationChannel()
            Notifications.getInstance(application)
                .deleteNotificationChannelsAndGroups()
            Notifications.getInstance(application).renameNotificationChannels()
        }

        // Update navigation view
        updateNavigationView()
    }

    /**
     * Performs a check for a configured account and DIDs, and forces the user
     * to configure an account where appropriate.
     */
    private fun performAccountDidCheck() {
        // If there are no DIDs available and the user has not configured an
        // account, then force the user to configure an account
        if (!didsConfigured(applicationContext)
            && Database.getInstance(applicationContext).getDids().isEmpty()
            && !accountConfigured(applicationContext)) {
            startActivity(Intent(this, SignInActivity::class.java))
        }
    }

    /**
     * Performs initial setup following sign-in or upgrade.
     */
    private fun performInitialSetup() {
        // After the user configures an account, do an initial synchronization
        // with the SwipeRefreshLayout refresh icon
        if (getFirstSyncAfterSignIn(this)) {
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
                R.id.swipe_refresh_layout)
            swipeRefreshLayout.isRefreshing = true
            SyncService.startService(this, forceRecent = false)

            val format = SimpleDateFormat("MMM d, yyyy",
                                          Locale.getDefault())
            val date = format.format(getStartDate(this))
            showSnackbar(
                this, R.id.coordinator_layout,
                getString(R.string.conversations_sync_date_suggestion, date),
                getString(R.string.change),
                View.OnClickListener {
                    startActivity(
                        Intent(this,
                               SynchronizationPreferencesActivity::class.java))
                },
                Snackbar.LENGTH_INDEFINITE)

            setFirstSyncAfterSignIn(this, false)
        }

        // Perform special setup for version 114: need to re-enable push
        // notifications
        if (getSetupCompletedForVersion(this) < 114) {
            enablePushNotifications(this.application,
                                    activityToShowError = this)
        }
    }

    /**
     * Updates the navigation view.
     */
    private fun updateNavigationView() {
        val navigationView = findViewById<NavigationView>(R.id.nav_view)

        // Apply circular mask to and remove overlay from picture
        // to match Android Messages aesthetic
        val photo = navigationView.getHeaderView(0).findViewById<ImageView>(
            R.id.photo)
        applyCircularMask(photo)

        // Set navigation bar email address
        val email = navigationView.getHeaderView(0).findViewById<TextView>(
            R.id.email)
        if (accountConfigured(applicationContext)) {
            email.text = getEmail(applicationContext)
        } else {
            email.text = getString(R.string.conversations_no_account)
        }

        // Set navigation bar DIDs
        val dids = getDids(applicationContext,
                           onlyShowInConversationsView = true)
        navigationView.menu.clear()
        navViewMenuItemDidMap.clear()
        if (dids.isNotEmpty()) {
            var activeDid = getActiveDid(applicationContext)
            if (activeDid !in dids) {
                activeDid = ""
                setActiveDid(applicationContext, activeDid)
            }

            for (did in listOf("").union(dids)) {
                val menuItem = navigationView.menu.add(
                    if (did == "") getString(R.string.conversations_all_dids)
                    else getFormattedPhoneNumber(did))
                navViewMenuItemDidMap[menuItem] = did
                menuItem.isCheckable = true
                if (activeDid == did) {
                    menuItem.isChecked = true
                }
            }
        } else {
            setActiveDid(applicationContext, "")
        }
    }

    override fun onPause() {
        super.onPause()

        // Unregister all dynamic receivers for this activity
        safeUnregisterReceiver(this, syncCompleteReceiver)
        @Suppress("ConstantConditionIf")
        safeUnregisterReceiver(
            this,
            pushNotificationsRegistrationCompleteReceiver)

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
        searchAutoComplete.hint = getString(R.string.conversations_text_hint)
        searchAutoComplete.setTextColor(ContextCompat.getColor(
            applicationContext, android.R.color.white))
        searchAutoComplete.setHintTextColor(ContextCompat.getColor(
            applicationContext, R.color.search_hint))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val field = TextView::class.java.getDeclaredField(
                    "mCursorDrawableRes")
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
            android.R.id.home -> {
                val drawerLayout = findViewById<DrawerLayout>(
                    R.id.drawer_layout)
                drawerLayout.openDrawer(GravityCompat.START)
                return true
            }
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
                try {
                    val intent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse(getString(R.string.help_url)))
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    showSnackbar(this, R.id.coordinator_layout,
                                 getString(
                                     R.string.conversations_fail_web_browser))
                }
                return true
            }
            R.id.privacy_button -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse(getString(
                                            R.string.privacy_url)))
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    showSnackbar(this, R.id.coordinator_layout,
                                 getString(
                                     R.string.conversations_fail_web_browser))
                }
                return true
            }
            R.id.license_button -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse(getString(
                                            R.string.license_url)))
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    showSnackbar(this, R.id.coordinator_layout,
                                 getString(
                                     R.string.conversations_fail_web_browser))
                }
                return true
            }
            R.id.credits_button -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse(getString(
                                            R.string.credits_url)))
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    showSnackbar(this, R.id.coordinator_layout,
                                 getString(
                                     R.string.conversations_fail_web_browser))
                }
                return true
            }
            R.id.donate_button -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW,
                                        Uri.parse(getString(
                                            R.string.donate_url)))
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    showSnackbar(this, R.id.coordinator_layout,
                                 getString(
                                     R.string.conversations_fail_web_browser))
                }
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

    override fun onPrepareActionMode(mode: ActionMode,
                                     menu: Menu): Boolean = false

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

    override fun onDestroyActionMode(mode: ActionMode) {
        // Uncheck all items
        for (i in 0 until adapter.itemCount) {
            adapter[i].setChecked(false, i)
        }
        actionMode = null
    }

    /**
     * Handles the archive button.
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
                runOnNewThread {
                    for (message in messages) {
                        Database.getInstance(applicationContext)
                            .deleteMessages(message.conversationId)
                    }
                    runOnUiThread {
                        mode.finish()
                        adapter.refresh()
                    }
                }
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
            val position = recyclerView.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) {
                return
            }

            val conversationItem = adapter[position]
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
                            R.id.chat_button,
                            getString(
                                R.string.conversations_perm_denied_contacts))
                    }
                }
        }
    }

    /**
     * Toggles the item associated with the specified view. Activates and
     * deactivates the action mode depending on the checked item count.
     */
    private fun toggleItem(view: View) {
        // Inform the adapter that the item should be checked
        val index = recyclerView.getChildAdapterPosition(view)
        if (index != RecyclerView.NO_POSITION) {
            adapter[index].toggle(index)
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
