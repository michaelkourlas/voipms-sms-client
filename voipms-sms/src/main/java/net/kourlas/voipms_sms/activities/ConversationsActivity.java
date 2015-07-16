/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas and other contributors
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

package net.kourlas.voipms_sms.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import net.kourlas.voipms_sms.*;
import net.kourlas.voipms_sms.adapters.ConversationsRecyclerViewAdapter;
import net.kourlas.voipms_sms.gcm.Gcm;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.receivers.SynchronizationIntervalReceiver;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ConversationsActivity
        extends AppCompatActivity
        implements ActionMode.Callback, View.OnClickListener, View.OnLongClickListener {
    private final ConversationsActivity conversationsActivity = this;

    private Gcm gcm;
    private Billing billing;
    private Database database;
    private Preferences preferences;

    private RecyclerView recyclerView;
    private ConversationsRecyclerViewAdapter adapter;

    private Menu menu;

    private ActionMode actionMode;
    private boolean actionModeEnabled;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversations);

        gcm = Gcm.getInstance(getApplicationContext());
        billing = Billing.getInstance(getApplicationContext());
        database = Database.getInstance(getApplicationContext());
        preferences = Preferences.getInstance(getApplicationContext());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources().getDimension(R.dimen.toolbar_elevation));
        setSupportActionBar(toolbar);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        adapter = new ConversationsRecyclerViewAdapter(this, layoutManager);
        recyclerView = (RecyclerView) findViewById(R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        actionMode = null;
        actionModeEnabled = false;

        SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                preFullUpdate();
            }
        });
        swipeRefreshLayout.setColorSchemeResources(R.color.accent);

        ImageButton button = (ImageButton) findViewById(R.id.new_button);
        ViewCompat.setElevation(button, getResources().getDimension(R.dimen.fab_elevation));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!preferences.getEmail().equals("") && !preferences.getPassword().equals("") &&
                        !preferences.getDid().equals("")) {
                    Intent intent = new Intent(conversationsActivity, NewConversationActivity.class);
                    startActivity(intent);
                }
            }
        });

        SynchronizationIntervalReceiver.setupSynchronizationInterval(getApplicationContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        ActivityMonitor.getInstance().setCurrentActivity(this);

        if (!(preferences.getEmail().equals("") || preferences.getPassword().equals("") ||
                preferences.getDid().equals(""))) {
            preRecentUpdate();
        }
        else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.conversations_first_run_dialog_text));
            builder.setPositiveButton(getString(R.string.preferences_name), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent preferencesIntent = new Intent(conversationsActivity, PreferencesActivity.class);
                    startActivity(preferencesIntent);
                }
            });
            builder.setNegativeButton(getString(R.string.help_name), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent helpIntent = new Intent(conversationsActivity, HelpActivity.class);
                    startActivity(helpIntent);
                }
            });
            builder.setCancelable(false);
            builder.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ActivityMonitor.getInstance().deleteReferenceToActivity(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityMonitor.getInstance().deleteReferenceToActivity(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            try {
                String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
                JSONObject json = new JSONObject(purchaseData);
                String token = json.getString("purchaseToken");
                billing.postDonation(token, this);
            } catch (Exception ignored) {
                // Do nothing.
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (actionModeEnabled) {
            actionMode.finish();
        }
        else if (menu != null) {
            MenuItem searchItem = menu.findItem(R.id.search_button);
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (!searchView.isIconified()) {
                searchItem.collapseActionView();
            }
            else {
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversations, menu);
        this.menu = menu;

        SearchView searchView = (SearchView) menu.findItem(R.id.search_button).getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.refresh(newText);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.preferences_button:
                Intent preferencesIntent = new Intent(this, PreferencesActivity.class);
                startActivity(preferencesIntent);
                return true;
            case R.id.help_button:
                Intent helpIntent = new Intent(this, HelpActivity.class);
                startActivity(helpIntent);
                return true;
            case R.id.credits_button:
                Intent creditsIntent = new Intent(this, CreditsActivity.class);
                startActivity(creditsIntent);
                return true;
            case R.id.donate_button:
                billing.preDonation(this);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.conversations_secondary, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    /**
     * Called when an action mode item is clicked.
     *
     * @param mode The action mode containing the item that is clicked.
     * @param item The item that is clicked.
     * @return Returns true if the method handles the item clicked.
     */
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mark_read_unread_button:
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    if (adapter.isItemChecked(i)) {
                        Message[] smses = adapter.getItem(i).getMessages();
                        for (Message message : smses) {
                            message.setUnread(item.getTitle().equals(getResources().getString(
                                    R.string.conversations_action_mark_unread)));
                            database.insertMessage(message);
                        }
                    }
                }
                adapter.refresh();
                mode.finish();
                return true;
            case R.id.delete_button:
                List<Long> databaseIds = new ArrayList<>();
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    if (adapter.isItemChecked(i)) {
                        for (Message message : adapter.getItem(i).getMessages()) {
                            if (message.getDatabaseId() != null) {
                                databaseIds.add(message.getDatabaseId());
                            }
                        }
                    }
                }

                Long[] databaseIdsArray = new Long[databaseIds.size()];
                databaseIds.toArray(databaseIdsArray);
                deleteMessages(databaseIdsArray);
                mode.finish();
                return true;
            default:
                return false;
        }
    }

    /**
     * Called when the action mode is destroyed.
     *
     * @param mode The action mode to be destroyed.
     */
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            adapter.setItemChecked(i, false);
        }
        actionModeEnabled = false;
    }

    /**
     * Toggles the item associated with the specified view. Activates and deactivates the action mode depending on the
     * checked item count.
     *
     * @param view The specified view.
     */
    private void toggleItem(View view) {
        adapter.toggleItemChecked(recyclerView.getChildAdapterPosition(view));

        if (adapter.getCheckedItemCount() == 0) {
            if (actionMode != null) {
                actionMode.finish();
            }
            actionModeEnabled = false;
            return;
        }

        if (!actionModeEnabled) {
            actionMode = startSupportActionMode(this);
            actionModeEnabled = true;
        }
        updateButtons();
    }

    /**
     * Switches between "mark as read" and "mark as unread" buttons for the action mode depending on which items in
     * the RecyclerView are selected.
     */
    private void updateButtons() {
        int read = 0;
        int unread = 0;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                if (adapter.getItem(i).isUnread()) {
                    unread++;
                }
                else {
                    read++;
                }
            }
        }

        MenuItem item = actionMode.getMenu().findItem(R.id.mark_read_unread_button);
        if (read > unread) {
            item.setIcon(R.drawable.ic_markunread_white_24dp);
            item.setTitle(R.string.conversations_action_mark_unread);
        }
        else {
            item.setIcon(R.drawable.ic_drafts_white_24dp);
            item.setTitle(R.string.conversations_action_mark_read);
        }
    }

    /**
     * Called when any item in the RecyclerView is short-clicked.
     * <p/>
     * This method only toggles the selected item (if the action mode is enabled) or opens the ConversationActivity
     * for that item (if the action mode is not enabled).
     *
     * @param view The item to toggle or open.
     */
    @Override
    public void onClick(View view) {
        if (actionModeEnabled) {
            toggleItem(view);
        }
        else {
            Conversation conversation = adapter.getItem(recyclerView.getChildAdapterPosition(view));
            String contact = conversation.getContact();

            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra(getString(R.string.conversation_extra_contact), contact);
            startActivity(intent);
        }
    }

    /**
     * Called when any item in the RecyclerView is long-clicked.
     * <p/>
     * This method only toggles the selected item.
     *
     * @param view The item to toggle.
     * @return Always returns true.
     */
    @Override
    public boolean onLongClick(View view) {
        toggleItem(view);
        return true;
    }

    /**
     * Deletes the messages with the specified database IDs.
     *
     * @param databaseIds The database IDs of the messages to delete.
     */
    public void deleteMessages(final Long[] databaseIds) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DialogTheme);
        builder.setTitle(getString(R.string.conversations_delete_confirm_title));
        builder.setMessage(getString(R.string.conversations_delete_confirm_message));
        builder.setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (long databaseId : databaseIds) {
                    Message message = database.getMessageWithDatabaseId(preferences.getDid(), databaseId);
                    if (message.getVoipId() == null) {
                        // Simply delete messages with no VoIP.ms ID from the database; no request to the VoIP.ms API
                        // will be necessary
                        database.removeMessage(databaseId);
                    }
                    else {
                        // Otherwise, keep the message in the database but set a deleted flag, so the message's VoIP.ms
                        // ID can be accessed later if local deletions are propagated to the VoIP.ms servers
                        message.setDeleted(true);
                        database.insertMessage(message);
                    }
                    adapter.refresh();
                }
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    /**
     * Initiates a partial update of the message database. This update only retrieves messages dated after the most
     * recent message.
     */
    public void preRecentUpdate() {
        adapter.refresh();
        gcm.registerForGcm(conversationsActivity, false, false);
        database.synchronize(true, false, conversationsActivity);
    }

    /**
     * Initiates a full update of the message database. This update follows all synchronization rules set in the
     * application's settings.
     */
    public void preFullUpdate() {
        adapter.refresh();
        gcm.registerForGcm(conversationsActivity, false, false);
        database.synchronize(false, true, conversationsActivity);
    }

    /**
     * Updates this activity's user interface after a database update.
     * <p/>
     * Called by the Api class after updating the SMS database if this activity made the update request or, if the
     * update request was initiated by the GCM service, if this activity is currently visible to the user.
     */
    public void postUpdate() {
        SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setRefreshing(false);

        adapter.refresh();
    }
}
