/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2016 Michael Kourlas
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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.adapters.ConversationsRecyclerViewAdapter;
import net.kourlas.voipms_sms.billing.Billing;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.notifications.PushNotifications;
import net.kourlas.voipms_sms.preferences.Preferences;
import net.kourlas.voipms_sms.receivers.SynchronizationIntervalReceiver;
import net.kourlas.voipms_sms.utils.Utils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ConversationsActivity
    extends AppCompatActivity
    implements ActionMode.Callback, View.OnClickListener,
               View.OnLongClickListener,
               ActivityCompat.OnRequestPermissionsResultCallback
{
    private static final int PERM_REQ_CONTACTS = 0;

    private final ConversationsActivity conversationsActivity = this;

    private PushNotifications pushNotifications;
    private Billing billing;
    private Database database;
    private Preferences preferences;

    private RecyclerView recyclerView;
    private ConversationsRecyclerViewAdapter adapter;

    private Menu menu;

    private ActionMode actionMode;
    private boolean actionModeEnabled;

    private AlertDialog firstRunDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversations);

        pushNotifications = PushNotifications
            .getInstance(getApplicationContext());
        billing = Billing.getInstance(getApplicationContext());
        database = Database.getInstance(getApplicationContext());
        preferences = Preferences.getInstance(getApplicationContext());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources()
            .getDimension(R.dimen.toolbar_elevation));
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

        SwipeRefreshLayout swipeRefreshLayout =
            (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout
            .setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    ConversationsActivity.this.preFullUpdate();
                }
            });
        swipeRefreshLayout.setColorSchemeResources(R.color.accent);

        FloatingActionButton button =
            (FloatingActionButton) findViewById(R.id.new_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!preferences.getEmail().equals("") && !preferences
                    .getPassword().equals("") &&
                    !preferences.getDid().equals(""))
                {
                    Intent intent = new Intent(conversationsActivity,
                                               NewConversationActivity.class);
                    ConversationsActivity.this.startActivity(intent);
                }
            }
        });

        SynchronizationIntervalReceiver
            .setupSynchronizationInterval(getApplicationContext());

        if (ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(
                this,
                new String[] {android.Manifest.permission.READ_CONTACTS},
                PERM_REQ_CONTACTS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityMonitor.getInstance().deleteReferenceToActivity(this);
    }

    /**
     * Initiates a full update of the message database. This update follows
     * all synchronization rules set in the
     * application's settings.
     */
    private void preFullUpdate() {
        adapter.refresh();
        pushNotifications
            .registerForFcm(conversationsActivity, null, false, false);
        database.synchronize(conversationsActivity, true, false, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data)
    {
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            try {
                String purchaseData =
                    data.getStringExtra("INAPP_PURCHASE_DATA");
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
        } else if (menu != null) {
            MenuItem searchItem = menu.findItem(R.id.search_button);
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (!searchView.isIconified()) {
                searchItem.collapseActionView();
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ActivityMonitor.getInstance().deleteReferenceToActivity(this);

        if (firstRunDialog != null) {
            firstRunDialog.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ActivityMonitor.getInstance().setCurrentActivity(this);

        if (!(preferences.getEmail().equals("") || preferences.getPassword()
                                                              .equals("") ||
              preferences.getDid().equals("")))
        {
            preRecentUpdate();

            if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED)
            {
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            }
        } else {
            firstRunDialog = Utils.showAlertDialog(
                this, null,
                getString(
                    R.string.conversations_first_run_dialog_text),
                getString(R.string.preferences_name),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Intent preferencesIntent =
                            new Intent(conversationsActivity,
                                       PreferencesActivity
                                           .class);
                        ConversationsActivity.this
                            .startActivity(preferencesIntent);
                    }
                }, getString(R.string.help_name),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Intent helpIntent =
                            new Intent(conversationsActivity,
                                       HelpActivity.class);
                        ConversationsActivity.this.startActivity(helpIntent);
                    }
                });
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions,
                                         grantResults);
        if (requestCode == PERM_REQ_CONTACTS) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(
                    android.Manifest.permission.READ_CONTACTS))
                {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        adapter.notifyItemRangeChanged(0,
                                                       adapter.getItemCount());
                    } else {
                        Utils.showPermissionSnackbar(
                            this,
                            R.id.new_button,
                            getString(
                                R.string.conversations_perm_denied_contacts));
                    }
                }
            }
        }
    }

    /**
     * Initiates a partial update of the message database. This update only
     * retrieves messages dated after the most
     * recent message.
     */
    private void preRecentUpdate() {
        adapter.refresh();
        pushNotifications
            .registerForFcm(conversationsActivity, null, false, false);
        database.synchronize(conversationsActivity, false, true, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversations, menu);
        this.menu = menu;

        SearchView searchView =
            (SearchView) menu.findItem(R.id.search_button).getActionView();
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
                Intent preferencesIntent =
                    new Intent(this, PreferencesActivity.class);
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
                        Message message = adapter.getItem(i);
                        if (item.getTitle().equals(getResources().getString(
                            R.string.conversations_action_mark_unread)))
                        {
                            database.markConversationAsUnread(
                                preferences.getDid(),
                                message.getContact());
                        } else {
                            database.markConversationAsRead(
                                preferences.getDid(),
                                message.getContact());
                        }
                    }
                }
                adapter.refresh();
                mode.finish();
                return true;
            case R.id.delete_button:
                return deleteButtonHandler(mode);
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

    private boolean deleteButtonHandler(ActionMode mode) {
        final List<String> contacts = new ArrayList<>();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                contacts.add(adapter.getItem(i).getContact());
            }
        }

        Utils.showAlertDialog(
            this,
            getString(R.string.conversations_delete_confirm_title),
            getString(R.string.conversations_delete_confirm_message),
            getString(R.string.delete),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    for (String contact : contacts) {
                        database.deleteMessages(preferences.getDid(), contact);
                    }
                    adapter.refresh();
                }
            },
            getString(R.string.cancel),
            null);

        mode.finish();
        return true;
    }

    /**
     * Called when any item in the RecyclerView is short-clicked.
     * <p/>
     * This method only toggles the selected item (if the action mode is
     * enabled) or opens the ConversationActivity
     * for that item (if the action mode is not enabled).
     *
     * @param view The item to toggle or open.
     */
    @Override
    public void onClick(View view) {
        if (actionModeEnabled) {
            toggleItem(view);
        } else {
            Message message =
                adapter.getItem(recyclerView.getChildAdapterPosition(view));
            String contact = message.getContact();

            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra(getString(R.string.conversation_extra_contact),
                            contact);
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
     * Toggles the item associated with the specified view. Activates and
     * deactivates the action mode depending on the
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
     * Updates this activity's user interface after a database update.
     * <p/>
     * Called by the Api class after updating the SMS database if this
     * activity made the update request or, if the
     * update request was initiated by the GCM service, if this activity is
     * currently visible to the user.
     */
    public void postUpdate() {
        SwipeRefreshLayout swipeRefreshLayout =
            (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setRefreshing(false);

        adapter.refresh();
    }

    /**
     * Switches between "mark as read" and "mark as unread" buttons for the
     * action mode depending on which items in
     * the RecyclerView are selected.
     */
    private void updateButtons() {
        int read = 0;
        int unread = 0;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                if (adapter.getItem(i).isUnread()) {
                    unread++;
                } else {
                    read++;
                }
            }
        }

        MenuItem item =
            actionMode.getMenu().findItem(R.id.mark_read_unread_button);
        if (read > unread) {
            item.setIcon(R.drawable.ic_markunread_white_24dp);
            item.setTitle(R.string.conversations_action_mark_unread);
        } else {
            item.setIcon(R.drawable.ic_drafts_white_24dp);
            item.setTitle(R.string.conversations_action_mark_read);
        }
    }


}
