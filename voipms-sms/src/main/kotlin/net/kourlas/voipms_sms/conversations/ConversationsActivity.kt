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

package net.kourlas.voipms_sms.conversations

import android.animation.ValueAnimator
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.newconversation.NewConversationActivity
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.preferences.activities.AccountPreferencesActivity
import net.kourlas.voipms_sms.preferences.activities.PreferencesActivity
import net.kourlas.voipms_sms.signin.SignInActivity
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.services.SyncService
import net.kourlas.voipms_sms.utils.*
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
    private var menu: Menu? = null
    private lateinit var navMenuItemDidMap: HashMap<MenuItem, String>
    private var actionMode: ActionMode? = null

    // Broadcast receivers
    private val syncCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val error = intent?.getStringExtra(getString(
                    R.string.sync_complete_error))
                if (error != null) {
                    // Show error in snackbar if one occurred
                    showSnackbar(this@ConversationsActivity,
                                 R.id.coordinator_layout, error)
                }

                if (intent?.getBooleanExtra(getString(
                        R.string.sync_complete_full), false) == true) {
                    // Turn off refresh icon
                    val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
                        R.id.swipe_refresh_layout)
                    swipeRefreshLayout.isRefreshing = false
                }

                adapter.refresh()
            }
        }
    private val pushNotificationsRegistrationCompleteReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val failedDids = intent?.getStringArrayListExtra(getString(
                    R.string.push_notifications_reg_complete_voip_ms_api_callback_failed_dids))
                if (failedDids == null) {
                    // Unknown error
                    showSnackbar(
                        this@ConversationsActivity,
                        R.id.coordinator_layout,
                        getString(R.string.push_notifications_fail_unknown))
                } else if (!failedDids.isEmpty()) {
                    // Some DIDs failed registration
                    showSnackbar(
                        this@ConversationsActivity,
                        R.id.coordinator_layout,
                        getString(R.string.push_notifications_fail_register))
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

        // Contacts permission is required to get contact names and photos
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.READ_CONTACTS),
                PermissionIndex.CONTACTS.ordinal)
        }

        navMenuItemDidMap = HashMap()

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            navMenuItemDidMap[menuItem]?.let {
                setActiveDid(applicationContext, it)
                menuItem.isChecked = true
                adapter.refresh()
            }
            true
        }

        navigationView.getHeaderView(0).setOnClickListener {
            if (accountConfigured(applicationContext)) {
                startActivity(
                    Intent(this, AccountPreferencesActivity::class.java))
            } else {
                startActivity(Intent(this, SignInActivity::class.java))
            }
        }
    }

    /**
     * Sets up the activity toolbar.
     */
    open fun setupToolbar() {
        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
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
        swipeRefreshLayout.setColorSchemeResources(R.color.colorSecondary)
    }

    /**
     * Sets up the new conversation floating action button.
     */
    open fun setupNewConversationButton() {
        val button = findViewById<FloatingActionButton>(R.id.chat_button)

        if (!didsConfigured(applicationContext)) {
            button.visibility = View.GONE
        } else {
            button.visibility = View.VISIBLE
        }

        button.setOnClickListener {
            val newConversationIntent = Intent(
                this, NewConversationActivity::class.java)
            startActivity(newConversationIntent)
        }
    }

    override fun onResume() {
        super.onResume()

        // Track number of activities
        (application as CustomApplication).conversationsActivityIncrementCount()

        // Register dynamic receivers for this activity
        registerReceiver(syncCompleteReceiver,
                         IntentFilter(getString(R.string.sync_complete_action)))
        registerReceiver(pushNotificationsRegistrationCompleteReceiver,
                         IntentFilter(getString(
                             R.string.push_notifications_reg_complete_action)))

        // Perform special setup if there are no DIDs available and the user
        // has not configured an account
        if (!didsConfigured(applicationContext)
            && Database.getInstance(applicationContext).getDids().isEmpty()
            && !accountConfigured(applicationContext)) {
            startActivity(Intent(this, SignInActivity::class.java))
        }

        // Perform special setup for version 114: need to re-enable push
        // notifications
        if (getSetupCompletedForVersion(this) < 114) {
            if (GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(
                        this) != ConnectionResult.SUCCESS) {
                showSnackbar(this, R.id.coordinator_layout,
                             this.getString(
                                 R.string.push_notifications_fail_google_play))
            }
            Notifications.getInstance(application).enablePushNotifications(this)
        }

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
        navMenuItemDidMap.clear()
        if (dids.isNotEmpty()) {
            var activeDid = getActiveDid(applicationContext)
            if (activeDid !in dids) {
                activeDid = dids.first()
                setActiveDid(applicationContext, activeDid)
            }

            for (did in dids) {
                val menuItem = navigationView.menu.add(
                    getFormattedPhoneNumber(did))
                navMenuItemDidMap[menuItem] = did
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
        safeUnregisterReceiver(this,
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
        searchAutoComplete.setHintTextColor(ContextCompat.getColor(
            applicationContext, R.color.search_hint))
        try {
            val field = TextView::class.java.getDeclaredField(
                "mCursorDrawableRes")
            field.isAccessible = true
            field.set(searchAutoComplete, R.drawable.search_cursor)
        } catch (_: java.lang.Exception) {
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

    override fun onPrepareActionMode(mode: ActionMode,
                                     menu: Menu): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) {
        for (i in 0 until adapter.itemCount) {
            adapter[i].setChecked(i, false)
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
     *
     * @param view The specified view.
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
