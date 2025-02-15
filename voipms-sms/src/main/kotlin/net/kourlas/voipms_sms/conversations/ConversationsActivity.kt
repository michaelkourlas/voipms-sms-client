/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.newConversation.NewConversationActivity
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.accountConfigured
import net.kourlas.voipms_sms.preferences.activities.AccountPreferencesActivity
import net.kourlas.voipms_sms.preferences.activities.MarkdownPreferencesActivity
import net.kourlas.voipms_sms.preferences.activities.PreferencesActivity
import net.kourlas.voipms_sms.preferences.activities.SynchronizationPreferencesActivity
import net.kourlas.voipms_sms.preferences.didsConfigured
import net.kourlas.voipms_sms.preferences.firstRun
import net.kourlas.voipms_sms.preferences.getActiveDid
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.preferences.getEmail
import net.kourlas.voipms_sms.preferences.getFirstSyncAfterSignIn
import net.kourlas.voipms_sms.preferences.getSetupCompletedForVersion
import net.kourlas.voipms_sms.preferences.getStartDate
import net.kourlas.voipms_sms.preferences.setActiveDid
import net.kourlas.voipms_sms.preferences.setFirstSyncAfterSignIn
import net.kourlas.voipms_sms.preferences.setSetupCompletedForVersion
import net.kourlas.voipms_sms.signIn.SignInActivity
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.sms.workers.SyncWorker
import net.kourlas.voipms_sms.utils.applyCircularMask
import net.kourlas.voipms_sms.utils.enablePushNotifications
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber
import net.kourlas.voipms_sms.utils.registerNonExportedReceiver
import net.kourlas.voipms_sms.utils.safeUnregisterReceiver
import net.kourlas.voipms_sms.utils.showPermissionSnackbar
import net.kourlas.voipms_sms.utils.showSnackbar
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Activity that contains a generic list of conversations.
 */
open class ConversationsActivity(val archived: Boolean = false) :
    AppCompatActivity(),
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
                intent?.getStringExtra(
                    getString(
                        R.string.sync_complete_error
                    )
                )?.let {
                    showSnackbar(
                        this@ConversationsActivity,
                        R.id.coordinator_layout, it
                    )
                }

                // Turn off refresh icon if this was a complete sync
                if (intent?.getBooleanExtra(
                        getString(
                            R.string.sync_complete_full
                        ), false
                    ) == true
                ) {
                    val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
                        R.id.swipe_refresh_layout
                    )
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
                intent?.getStringArrayListExtra(
                    getString(
                        R.string.push_notifications_reg_complete_failed_dids
                    )
                )
                    ?.let {
                        if (it.isNotEmpty()) {
                            // Some DIDs failed registration
                            showSnackbar(
                                this@ConversationsActivity,
                                R.id.coordinator_layout,
                                getString(
                                    R.string.push_notifications_fail_register
                                )
                            )
                        }
                    } ?: run {
                    // Unknown error
                    showSnackbar(
                        this@ConversationsActivity,
                        R.id.coordinator_layout,
                        getString(R.string.push_notifications_fail_unknown)
                    )
                }

                // Regardless of whether an error occurred, mark setup as
                // complete
                setSetupCompletedForVersion(
                    this@ConversationsActivity,
                    BuildConfig.VERSION_CODE.toLong()
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.conversations)
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        setupBack()
        setupToolbar()
        setupRecyclerViewAndSwipeRefreshLayout()
        setupNewConversationButton()
        setupPermissions()
        setupNavigationView()
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
    private fun setupToolbar() {
        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            if (archived) {
                it.title = getString(R.string.conversations_archived_name)
            }
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
        adapter = ConversationsRecyclerViewAdapter(
            this, recyclerView,
            layoutManager
        )
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    c, recyclerView, viewHolder,
                    dX, dY, actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(
                        ContextCompat.getColor(
                            applicationContext,
                            R.color.archive_swipe
                        )
                    )
                    .addSwipeLeftActionIcon(
                        if (archived) R.drawable.ic_unarchive_toolbar_24dp
                        else R.drawable.ic_archive_toolbar_24dp
                    )
                    .addSwipeRightBackgroundColor(
                        ContextCompat.getColor(
                            applicationContext,
                            R.color.delete_swipe
                        )
                    )
                    .addSwipeRightActionIcon(R.drawable.ic_delete_toolbar_24dp)
                    .create()
                    .decorate()
                super.onChildDraw(
                    c, recyclerView, viewHolder, dX, dY,
                    actionState, isCurrentlyActive
                )
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(
                viewHolder: RecyclerView.ViewHolder,
                direction: Int
            ) {
                if (viewHolder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                    return
                }
                when (direction) {
                    ItemTouchHelper.LEFT ->
                        if (archived)
                            unarchiveConversations(
                                listOf(
                                    adapter[viewHolder.bindingAdapterPosition].message
                                )
                            )
                        else
                            archiveConversations(
                                listOf(
                                    adapter[viewHolder.bindingAdapterPosition].message
                                )
                            )

                    ItemTouchHelper.RIGHT -> deleteConversations(
                        listOf(
                            adapter[viewHolder.bindingAdapterPosition].message
                        )
                    )
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(
            recyclerView
        )

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, windowInsets ->
            val systemBarsInsets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                systemBarsInsets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
            R.id.swipe_refresh_layout
        )
        swipeRefreshLayout.setOnRefreshListener {
            adapter.refresh()
            SyncWorker.performFullSynchronization(applicationContext)
        }
        swipeRefreshLayout.setColorSchemeResources(R.color.swipe_refresh_icon)
    }

    /**
     * Sets up the new conversation floating action button.
     */
    private fun setupNewConversationButton() {
        if (archived) {
            // Remove new conversation button
            findViewById<View>(R.id.chat_button).visibility = View.GONE
        } else {
            findViewById<FloatingActionButton>(R.id.chat_button).let {
                if (!didsConfigured(
                        applicationContext
                    ) && !BuildConfig.IS_DEMO
                ) {
                    (it as View).visibility = View.GONE
                } else {
                    (it as View).visibility = View.VISIBLE
                }
                it.setOnClickListener {
                    val newConversationIntent = Intent(
                        this, NewConversationActivity::class.java
                    )
                    startActivity(newConversationIntent)
                }
                ViewCompat.setOnApplyWindowInsetsListener(it) { v, windowInsets ->
                    val insets =
                        windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.updateLayoutParams<MarginLayoutParams> {
                        bottomMargin =
                            insets.bottom + resources.getDimensionPixelSize(
                                R.dimen.margin
                            )
                    }
                    WindowInsetsCompat.CONSUMED
                }
            }
        }
    }

    /**
     * Requests the contacts permission.
     */
    private fun setupPermissions() {
        // Notifications permission is required to send notifications
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Contacts permission is required to get contact names and photos
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(android.Manifest.permission.READ_CONTACTS)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), 0
            )
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
                    Intent(this, AccountPreferencesActivity::class.java)
                )
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
        registerNonExportedReceiver(
            this,
            syncCompleteReceiver,
            IntentFilter(getString(R.string.sync_complete_action))
        )
        registerNonExportedReceiver(
            this,
            pushNotificationsRegistrationCompleteReceiver,
            IntentFilter(
                getString(
                    R.string.push_notifications_reg_complete_action
                )
            )
        )

        // Perform initial setup as well as account and DID check
        performAccountDidCheck()
        val firstSyncRequired = performInitialSetup()

        // Refresh and perform limited synchronization
        if (!firstSyncRequired) {
            adapter.refresh()
            SyncWorker.performPartialSynchronization(applicationContext)
        }

        // Refresh on resume just in case the contacts permission was newly
        // granted and we need to add the contact names and photos
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            CustomApplication.getApplication().applicationScope.launch(
                Dispatchers.Default
            ) {
                Database.getInstance(applicationContext).updateShortcuts()
            }
        }

        // Delete any notification channels and groups that are no longer
        // needed and rename existing channels if necessary
        CustomApplication.getApplication().applicationScope.launch(Dispatchers.Default) {
            Notifications.getInstance(applicationContext)
                .createDefaultNotificationChannel()
            Notifications.getInstance(applicationContext)
                .deleteNotificationChannelsAndGroups()
            Notifications.getInstance(applicationContext)
                .renameNotificationChannels()
        }

        // Update navigation view
        updateNavigationView()

        // Turn off the refreshing indicator in the SwipeRefreshLayout in
        // case we missed a "sync complete" notification
        if (!firstSyncRequired) {
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
                R.id.swipe_refresh_layout
            )
            swipeRefreshLayout.isRefreshing = false
        }
    }

    /**
     * Performs a check for a configured account and DIDs, and forces the user
     * to configure an account where appropriate.
     */
    private fun performAccountDidCheck() {
        // If there are no DIDs available and the user has not configured an
        // account, then force the user to configure an account
        runBlocking {
            if (!didsConfigured(applicationContext)
                && Database.getInstance(applicationContext).getDids().isEmpty()
                && !accountConfigured(applicationContext)
                && firstRun(applicationContext)
            ) {
                startActivity(
                    Intent(applicationContext, SignInActivity::class.java)
                )
            }
        }

        // Update chat button visibility
        findViewById<FloatingActionButton>(R.id.chat_button).let {
            if (archived || !didsConfigured(applicationContext) && !BuildConfig.IS_DEMO) {
                (it as View).visibility = View.GONE
            } else {
                (it as View).visibility = View.VISIBLE
            }
        }
    }

    /**
     * Performs initial setup following sign-in or upgrade.
     */
    private fun performInitialSetup(): Boolean {
        // After the user configures an account, do an initial synchronization
        // with the SwipeRefreshLayout refresh icon
        val firstSyncRequired = getFirstSyncAfterSignIn(this)
        if (getFirstSyncAfterSignIn(this)) {
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(
                R.id.swipe_refresh_layout
            )
            swipeRefreshLayout.isRefreshing = true
            SyncWorker.performFullSynchronization(applicationContext)

            val emptyTextView = findViewById<TextView>(
                R.id.empty_text
            )
            emptyTextView.text = getString(R.string.conversations_first_sync)

            val format = SimpleDateFormat(
                "MMM d, yyyy",
                Locale.getDefault()
            )
            val date = format.format(getStartDate(this))
            showSnackbar(
                this, R.id.coordinator_layout,
                getString(R.string.conversations_sync_date_suggestion, date),
                getString(R.string.change),
                {
                    startActivity(
                        Intent(
                            this,
                            SynchronizationPreferencesActivity::class.java
                        )
                    )
                },
                Snackbar.LENGTH_INDEFINITE
            )

            setFirstSyncAfterSignIn(this, false)
        }

        // Perform special setup for version 145: need to re-enable push
        // notifications
        if (getSetupCompletedForVersion(this) < 145) {
            enablePushNotifications(
                this.applicationContext,
                activityToShowError = this
            )
        }

        return firstSyncRequired
    }

    /**
     * Updates the navigation view.
     */
    private fun updateNavigationView() {
        val navigationView = findViewById<NavigationView>(R.id.nav_view)

        // Apply circular mask to and remove overlay from picture
        // to match Android Messages aesthetic
        val photo = navigationView.getHeaderView(0).findViewById<ImageView>(
            R.id.photo
        )
        applyCircularMask(photo)

        // Set navigation bar email address
        val email = navigationView.getHeaderView(0).findViewById<TextView>(
            R.id.email
        )
        if (accountConfigured(applicationContext)) {
            email.text = getEmail(applicationContext)
        } else {
            email.text = getString(R.string.conversations_no_account)
        }

        // Set navigation bar DIDs
        val dids = getDids(
            applicationContext,
            onlyShowInConversationsView = true
        )
        navigationView.menu.clear()
        navViewMenuItemDidMap.clear()
        if (dids.isNotEmpty()) {
            var activeDid = getActiveDid(applicationContext)
            if (activeDid !in dids) {
                activeDid = ""
                setActiveDid(applicationContext, activeDid)
            }

            for (did in listOf("") + dids.sorted()) {
                val menuItem = navigationView.menu.add(
                    if (did == "") getString(R.string.conversations_all_dids)
                    else getFormattedPhoneNumber(did)
                )
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
        safeUnregisterReceiver(
            this,
            pushNotificationsRegistrationCompleteReceiver
        )

        // Track number of activities
        (application as CustomApplication).conversationsActivityDecrementCount()
    }

    @SuppressLint("DiscouragedPrivateApi")
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
            TextView>(
            androidx.appcompat.R.id.search_src_text
        )
        searchAutoComplete.hint = getString(R.string.conversations_text_hint)
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

        if (archived) {
            menu.findItem(R.id.archived_button).isVisible = false
            menu.findItem(R.id.preferences_button).isVisible = false
            menu.findItem(R.id.help_button).isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                val drawerLayout = findViewById<DrawerLayout>(
                    R.id.drawer_layout
                )
                drawerLayout.openDrawer(GravityCompat.START)
                return true
            }

            R.id.archived_button -> {
                val intent = Intent(
                    this,
                    ConversationsArchivedActivity::class.java
                )
                startActivity(intent)
                return true
            }

            R.id.preferences_button -> {
                val intent = Intent(this, PreferencesActivity::class.java)
                startActivity(intent)
                return true
            }

            R.id.help_button -> {
                val intent =
                    Intent(this, MarkdownPreferencesActivity::class.java)
                intent.putExtra(
                    getString(R.string.preferences_markdown_extra),
                    "HELP"
                )
                startActivity(intent)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.conversations_secondary, menu)

        if (archived) {
            // Remove the archive button and replace it with an unarchive button
            menu.findItem(R.id.archive_button).isVisible = false
            menu.findItem(R.id.unarchive_button).isVisible = true
        }

        return true
    }

    override fun onPrepareActionMode(
        mode: ActionMode,
        menu: Menu
    ): Boolean = false

    override fun onActionItemClicked(
        mode: ActionMode,
        item: MenuItem
    ): Boolean {
        when (item.itemId) {
            R.id.archive_button -> {
                onArchiveButtonClick(mode)
                return true
            }

            R.id.unarchive_button -> {
                onUnarchiveButtonClick(mode)
                return true
            }

            R.id.mark_read_button -> {
                onMarkReadButtonClick(mode)
                return true
            }

            R.id.mark_unread_button -> {
                onMarkUnreadButtonClick(mode)
                return true
            }

            R.id.delete_button -> {
                onDeleteButtonClick()
                return true
            }
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
    private fun onArchiveButtonClick(mode: ActionMode) {
        val messages = adapter
            .filter { it.checked }
            .map { it.message }

        archiveConversations(messages, mode)
    }

    /**
     * Handles the mark read button.
     */
    private fun onMarkReadButtonClick(mode: ActionMode) {
        val messages = adapter
            .filter { it.checked }
            .map { it.message }
        mode.finish()

        // Mark all selected conversations as read
        CustomApplication.getApplication().applicationScope.launch(Dispatchers.Default) {
            messages.forEach {
                    Database.getInstance(applicationContext)
                        .markConversationRead(it.conversationId)
                }

            lifecycleScope.launch(Dispatchers.Main) {
                adapter.refresh()
            }
        }
    }

    /**
     * Handles the mark unread button.
     */
    private fun onMarkUnreadButtonClick(mode: ActionMode) {
        val messages = adapter
            .filter { it.checked }
            .map { it.message }
        mode.finish()

        // Mark all selected conversations as unread
        CustomApplication.getApplication().applicationScope.launch(Dispatchers.Default) {
            messages.forEach {
                    Database.getInstance(applicationContext)
                        .markConversationUnread(it.conversationId)
                }

            lifecycleScope.launch(Dispatchers.Main) {
                adapter.refresh()
            }
        }
    }

    /**
     * Handles the delete button.
     */
    private fun onDeleteButtonClick() {
        val messages = adapter
            .filter { it.checked }
            .map { it.message }

        deleteConversations(messages)
    }

    /**
     * Handles the unarchive button.
     */
    private fun onUnarchiveButtonClick(mode: ActionMode) {
        val messages = adapter
            .filter { it.checked }
            .map { it.message }

        unarchiveConversations(messages, mode)
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
            intent.putExtra(
                getString(R.string.conversation_did),
                conversationItem.message.did
            )
            intent.putExtra(
                getString(R.string.conversation_contact),
                conversationItem.message.contact
            )
            startActivity(intent)
        }
    }

    override fun onLongClick(view: View): Boolean {
        // On long click, toggle selected item
        toggleItem(view)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.indices.filter {
                permissions[it] == android.Manifest.permission.POST_NOTIFICATIONS
            }.forEach {
                if (grantResults[it] != PackageManager.PERMISSION_GRANTED) {
                    // Otherwise, show a warning
                    showPermissionSnackbar(
                        this, R.id.coordinator_layout, getString(
                            R.string.conversations_perm_denied_notifications
                        )
                    )
                }
            }
        }

        permissions.indices
            .filter {
                permissions[it] == android.Manifest.permission.READ_CONTACTS
            }
            .forEach {
                if (grantResults[it] == PackageManager.PERMISSION_GRANTED) {
                    // If the permission request was granted, try refreshing
                    // and loading the contact name and photo
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    CustomApplication.getApplication().applicationScope.launch(
                        Dispatchers.Default
                    ) {
                        Database.getInstance(applicationContext)
                            .updateShortcuts()
                    }
                } else {
                    // Otherwise, show a warning
                    showPermissionSnackbar(
                        this,
                        R.id.coordinator_layout,
                        getString(
                            R.string.conversations_perm_denied_contacts
                        )
                    )
                }
            }
    }

    /**
     * Archives the conversations represented by the specified messages.
     */
    private fun archiveConversations(
        messages: List<Message>,
        mode: ActionMode? = null
    ) {
        mode?.finish()

        CustomApplication.getApplication().applicationScope.launch(Dispatchers.Default) {
            // Archive the conversations.
            for (message in messages) {
                Database.getInstance(applicationContext)
                    .markConversationArchived(message.conversationId)
            }

            lifecycleScope.launch(Dispatchers.Main) {
                adapter.refresh()

                showSnackbar(
                    this@ConversationsActivity,
                    R.id.coordinator_layout,
                    resources.getQuantityString(
                        R.plurals.conversations_archived,
                        messages.size,
                        messages.size
                    ),
                    getString(R.string.undo),
                    {
                        CustomApplication.getApplication().applicationScope.launch(
                            Dispatchers.Default
                        ) {
                            for (message in messages) {
                                Database.getInstance(applicationContext)
                                    .markConversationUnarchived(
                                        message.conversationId
                                    )

                                lifecycleScope.launch(Dispatchers.Main) {
                                    adapter.refresh()
                                }
                            }
                        }
                    })
            }
        }
    }

    /**
     * Unarchives the conversations represented by the specified messages.
     */
    private fun unarchiveConversations(
        messages: List<Message>,
        mode: ActionMode? = null
    ) {
        mode?.finish()

        CustomApplication.getApplication().applicationScope.launch(Dispatchers.Default) {
            // Archive the conversations.
            for (message in messages) {
                Database.getInstance(applicationContext)
                    .markConversationUnarchived(message.conversationId)
            }

            lifecycleScope.launch(Dispatchers.Main) {
                adapter.refresh()

                showSnackbar(
                    this@ConversationsActivity,
                    R.id.coordinator_layout,
                    resources.getQuantityString(
                        R.plurals.conversations_unarchived,
                        messages.size,
                        messages.size
                    ),
                    getString(R.string.undo),
                    {
                        CustomApplication.getApplication().applicationScope.launch(
                            Dispatchers.Default
                        ) {
                            for (message in messages) {
                                Database.getInstance(applicationContext)
                                    .markConversationArchived(
                                        message.conversationId
                                    )

                                lifecycleScope.launch(Dispatchers.Main) {
                                    adapter.refresh()
                                }
                            }
                        }
                    })
            }
        }
    }

    /**
     * Deletes the conversations represented by the specified messages.
     */
    private fun deleteConversations(
        messages: List<Message>,
        mode: ActionMode? = null
    ) {
        mode?.finish()

        CustomApplication.getApplication().applicationScope.launch(Dispatchers.Default) {
            // Collect existing state in case we need to undo this.
            val conversations = messages.map {
                Database.getInstance(applicationContext)
                    .getConversationMessagesUnsorted(it.conversationId)
            }
            val archived = mutableMapOf<ConversationId, Boolean>()
            for (message in messages) {
                archived[message.conversationId] =
                    Database.getInstance(applicationContext)
                        .isConversationArchived(message.conversationId)
            }
            val drafts = mutableMapOf<ConversationId, Message?>()
            for (message in messages) {
                drafts[message.conversationId] =
                    Database.getInstance(applicationContext)
                        .getConversationDraft(message.conversationId)
            }

            // Delete the conversations.
            for (message in messages) {
                Database.getInstance(applicationContext)
                    .deleteConversation(message.conversationId)
            }

            lifecycleScope.launch(Dispatchers.Main) {
                adapter.refresh()

                // Show a snackbar to allow the user to undo the action.
                showSnackbar(
                    this@ConversationsActivity,
                    R.id.coordinator_layout,
                    resources.getQuantityString(
                        R.plurals.conversations_deleted,
                        messages.size,
                        messages.size
                    ),
                    getString(R.string.undo),
                    {
                        CustomApplication.getApplication().applicationScope.launch(
                            Dispatchers.Default
                        ) {
                            // Restore the conversations.
                            for (conversation in conversations) {
                                Database.getInstance(applicationContext)
                                    .insertMessagesDatabase(conversation)
                            }
                            for ((conversationId, isArchived) in archived) {
                                if (isArchived) {
                                    Database.getInstance(applicationContext)
                                        .markConversationArchived(
                                            conversationId
                                        )
                                }
                            }
                            for ((conversationId, draft) in drafts) {
                                if (draft != null) {
                                    Database.getInstance(applicationContext)
                                        .updateConversationDraft(
                                            conversationId,
                                            draft.text
                                        )
                                }
                            }

                            lifecycleScope.launch(Dispatchers.Main) {
                                adapter.refresh()
                            }
                        }
                    })
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
            R.id.mark_read_button
        )
        val markUnreadButton = actionMode.menu.findItem(
            R.id.mark_unread_button
        )
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
}
