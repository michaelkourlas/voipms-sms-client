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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.adapters.ConversationRecyclerViewAdapter;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.notifications.Notifications;
import net.kourlas.voipms_sms.preferences.Preferences;
import net.kourlas.voipms_sms.utils.Utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConversationActivity
    extends AppCompatActivity
    implements ActionMode.Callback, View.OnLongClickListener,
               View.OnClickListener,
               ActivityCompat.OnRequestPermissionsResultCallback
{
    private Database database;
    private Preferences preferences;

    private String contact;

    private Menu menu;
    private ActionMode actionMode;
    private boolean actionModeEnabled;

    private LinearLayoutManager layoutManager;
    private ConversationRecyclerViewAdapter adapter;
    private RecyclerView recyclerView;

    ///
    /// Accessors
    ///

    public String getContact() {
        return contact;
    }

    ///
    /// Lifecycle methods
    ///

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation);

        database = Database.getInstance(getApplicationContext());
        preferences = Preferences.getInstance(getApplicationContext());

        contact = getIntent()
            .getStringExtra(getString(R.string.conversation_extra_contact));
        if ((contact.length() == 11) && (contact.charAt(0) == '1')) {
            // Remove the leading one from a North American phone number
            // (e.g. +1 (123) 555-4567)
            contact = contact.substring(1);
        }

        // Set up toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources()
            .getDimension(R.dimen.toolbar_elevation));
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            String contactName = Utils.getContactName(this, contact);
            if (contactName != null) {
                actionBar.setTitle(contactName);
                actionBar.setSubtitle(Utils.getFormattedPhoneNumber(contact));
            } else {
                actionBar.setTitle(Utils.getFormattedPhoneNumber(contact));
            }
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        actionMode = null;
        actionModeEnabled = false;

        // Set up recycler view
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.setStackFromEnd(true);
        adapter = new ConversationRecyclerViewAdapter(this, layoutManager,
                                                      contact);
        recyclerView = (RecyclerView) findViewById(R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Set up container for message text box
        final RelativeLayout messageSection =
            (RelativeLayout) findViewById(R.id.message_section);
        ViewCompat.setElevation(
            messageSection,
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10,
                getResources().getDisplayMetrics()));

        // Set up message text box
        final EditText messageText =
            (EditText) findViewById(R.id.message_edit_text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            messageText.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(),
                                         view.getHeight(), 15);
                }
            });
            messageText.setClipToOutline(true);
        }
        messageText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after)
            {
                // Do nothing.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count)
            {
                // Do nothing.
            }

            @Override
            public void afterTextChanged(Editable s) {
                onMessageTextChange(s.toString());
            }
        });
        messageText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    adapter.refresh();
                }
            }
        });
        String intentMessageText = getIntent().getStringExtra(
            getString(R.string.conversation_extra_message_text));
        if (intentMessageText != null) {
            messageText.setText(intentMessageText);
            messageText.setSelection(messageText.getText().length());
        }
        boolean intentFocus = getIntent().getBooleanExtra(
            getString(R.string.conversation_extra_focus), false);
        if (intentFocus) {
            messageText.requestFocus();
        }

        // Set up personal photo
        QuickContactBadge photo = (QuickContactBadge) findViewById(R.id.photo);
        Utils.applyCircularMask(photo);
        photo.assignContactFromPhone(preferences.getDid(), true);
        String photoUri = Utils.getContactPhotoUri(getApplicationContext(),
                                                   preferences.getDid());
        if (photoUri != null) {
            photo.setImageURI(Uri.parse(photoUri));
        } else {
            photo.setImageToDefault();
        }

        // Set up send button
        final ImageButton sendButton =
            (ImageButton) findViewById(R.id.send_button);
        Utils.applyCircularMask(sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConversationActivity.this.preSendMessage();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityMonitor.getInstance().deleteReferenceToActivity(this);
    }

    /**
     * Called when the message text box text changes.
     *
     * @param str The new text.
     */
    private void onMessageTextChange(String str) {
        ViewSwitcher viewSwitcher =
            (ViewSwitcher) findViewById(R.id.view_switcher);
        if (str.equals("")
            && viewSwitcher.getDisplayedChild() == 1)
        {
            viewSwitcher.setDisplayedChild(0);
        } else if (viewSwitcher.getDisplayedChild() == 0) {
            viewSwitcher.setDisplayedChild(1);
        }

        Message previousDraftMessage =
            database.getDraftMessageForConversation(
                preferences.getDid(), contact);
        if (str.equals("")) {
            if (previousDraftMessage != null) {
                database.removeMessage(
                    previousDraftMessage.getDatabaseId());
            }
        } else {
            if (previousDraftMessage != null) {
                previousDraftMessage.setText(str);
                database.insertMessage(previousDraftMessage);
            } else {
                Message newDraftMessage = new Message(
                    preferences.getDid(), contact,
                    str);
                newDraftMessage.setDraft(true);
                database.insertMessage(newDraftMessage);
            }
        }

        TextView charsRemainingTextView = (TextView)
            findViewById(R.id.chars_remaining_text);
        if (str.length() >= 150 && str.length() <= 160) {
            charsRemainingTextView.setVisibility(View.VISIBLE);
            charsRemainingTextView.setText(String.valueOf(160 - str.length()));
        } else if (str.length() > 160) {
            charsRemainingTextView.setVisibility(View.VISIBLE);
            int charsRemaining;
            if (str.length() % 153 == 0) {
                charsRemaining = 0;
            } else {
                charsRemaining = 153 - (str.length() % 153);
            }
            charsRemainingTextView.setText(
                getString(
                    R.string.conversation_char_rem,
                    String.valueOf(charsRemaining),
                    String.valueOf((int) Math.ceil(str.length() / 153d))));
        } else {
            charsRemainingTextView.setVisibility(View.GONE);

        }
    }

    /**
     * Called after the user clicks the send message button.
     */
    private void preSendMessage() {
        EditText messageEditText =
            (EditText) findViewById(R.id.message_edit_text);
        String messageText = messageEditText.getText().toString();
        // Split up the message to be sent into 153-character chunks
        // (if character count greater than 160) and add them to the database
        if (messageText.length() > 160) {
            while (true) {
                if (messageText.length() > 153) {
                    long databaseId =
                        database.insertMessage(new Message(
                            preferences.getDid(),
                            contact,
                            messageText.substring(0, 153)));
                    messageText = messageText.substring(153);
                    adapter.refresh();
                    preSendMessage(databaseId);
                } else {
                    long databaseId =
                        database.insertMessage(new Message(
                            preferences.getDid(),
                            contact,
                            messageText));
                    adapter.refresh();
                    preSendMessage(databaseId);
                    break;
                }
            }
        } else {
            long databaseId =
                database.insertMessage(new Message(
                    preferences.getDid(),
                    contact,
                    messageText));
            adapter.refresh();
            preSendMessage(databaseId);
        }

        // Clear the message text box
        messageEditText.setText("");
    }

    ///
    /// Options menu
    ///

    /**
     * Called after the message to be sent has been added to the database.
     */
    private void preSendMessage(long databaseId) {
        database.markMessageAsSending(databaseId);
        adapter.refresh();
        database.sendMessage(this, databaseId);
    }

    /**
     * Creates the standard options menu.
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Create standard options menu
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation, menu);
        this.menu = menu;

        // Hide the call button on devices without telephony support
        if (!getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        {
            MenuItem phoneMenuItem = menu.findItem(R.id.call_button);
            phoneMenuItem.setVisible(false);
        }

        // Configure the search box to trigger adapter filtering when the
        // text changes
        SearchView searchView =
            (SearchView) menu.findItem(R.id.search_button).getActionView();
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

    /**
     * Dispatcher for options menu items.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            switch (item.getItemId()) {
                case R.id.call_button:
                    return onCallButtonClick();
                case R.id.delete_button:
                    return onDeleteAllButtonClick();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Handler for the call button.
     */
    private boolean onCallButtonClick() {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + contact));

        // Before trying to call the contact's phone number, request the
        // CALL_PHONE permission
        if (ContextCompat.checkSelfPermission(this,
                                              Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED)
        {
            // We don't yet have the permission, so request it; if granted,
            // this method will be called again
            ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.CALL_PHONE},
                0);
        } else {
            // We have the permission
            try {
                startActivity(intent);
            } catch (SecurityException ignored) {
                // Do nothing.
            }
        }

        return true;
    }

    ///
    /// Action mode menu
    ///

    /**
     * Handler for the delete all messages button.
     */
    private boolean onDeleteAllButtonClick() {
        // Show a confirmation prompt; if the user accepts, delete all messages
        // and return to the previous activity
        Utils.showAlertDialog(
            this,
            getString(R.string.conversation_delete_confirm_title),
            getString(R.string.conversation_delete_confirm_message),
            getString(R.string.delete),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    database.deleteMessages(preferences.getDid(), contact);

                    // Go back to the previous activity if no messages remain
                    if (!database.conversationHasMessages(preferences.getDid(),
                                                          contact))
                    {
                        NavUtils
                            .navigateUpFromSameTask(ConversationActivity.this);
                    } else {
                        adapter.refresh();
                    }
                }
            },
            getString(R.string.cancel),
            null);

        return true;
    }

    /**
     * Creates the action mode menu.
     */
    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.conversation_secondary, menu);
        return true;
    }

    /**
     * Unused method implemented due to interface requirements.
     */
    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    /**
     * Dispatcher for action mode items.
     */
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.resend_button:
                return onResendButtonClick(mode);
            case R.id.info_button:
                return onInfoButtonClick(mode);
            case R.id.copy_button:
                return onCopyButtonClick(mode);
            case R.id.share_button:
                return onShareButtonClick(mode);
            case R.id.delete_button:
                return onDeleteButtonClick(mode);
            default:
                return false;
        }
    }

    /**
     * Switches back to the options menu from the action mode menu.
     */
    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            adapter.setItemChecked(i, false);
        }
        actionModeEnabled = false;
    }

    /**
     * Handler for the resend button.
     */
    private boolean onResendButtonClick(ActionMode mode) {
        // Resends all checked items
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                database.sendMessage(this, adapter.getItem(i).getDatabaseId());
                break;
            }
        }

        mode.finish();
        return true;
    }

    /**
     * Handler for the info button.
     */
    private boolean onInfoButtonClick(ActionMode mode) {
        // Get first checked item
        Message message = null;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                message = adapter.getItem(i);
                break;
            }
        }

        // Display info dialog for that item
        if (message != null) {
            DateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String dialogText = "";
            if (message.isIncoming()) {
                if (message.getVoipId() != null) {
                    dialogText += getString(R.string.conversation_info_id) + " "
                                  + message.getVoipId() + "\n";
                }
                dialogText += getString(R.string.conversation_info_to) + " "
                              + Utils.getFormattedPhoneNumber(
                    message.getDid()) + "\n";
                dialogText += getString(R.string.conversation_info_from)
                              + " " + Utils.getFormattedPhoneNumber(
                    message.getContact()) + "\n";
                dialogText += getString(R.string.conversation_info_date)
                              + " " + dateFormat.format(message.getDate());
            } else {
                if (message.getVoipId() != null) {
                    dialogText += getString(R.string.conversation_info_id) + " "
                                  + message.getVoipId() + "\n";
                }
                dialogText += getString(R.string.conversation_info_to)
                              + " " + Utils.getFormattedPhoneNumber(
                    message.getContact()) + "\n";
                dialogText += getString(R.string.conversation_info_from)
                              + " " + Utils.getFormattedPhoneNumber(
                    message.getDid()) + "\n";
                dialogText += getString(R.string.conversation_info_date)
                              + " " + dateFormat.format(message.getDate());
            }
            Utils.showAlertDialog(
                this,
                getString(R.string.conversation_info_title),
                dialogText,
                getString(R.string.ok),
                null, null, null);
        }

        mode.finish();
        return true;
    }

    /**
     * Handler for the copy button.
     */
    private boolean onCopyButtonClick(ActionMode mode) {
        // Get first checked item
        Message message = null;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                message = adapter.getItem(i);
                break;
            }
        }

        // Copy text of message to clipboard
        if (message != null) {
            ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip =
                ClipData.newPlainText("Text message", message.getText());
            clipboard.setPrimaryClip(clip);
        }

        mode.finish();
        return true;
    }

    /**
     * Handler for the share button.
     */
    private boolean onShareButtonClick(ActionMode mode) {
        // Get first checked item
        Message message = null;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                message = adapter.getItem(i);
                break;
            }
        }

        // Send a share intent with the text of the message
        if (message != null) {
            Intent intent = new Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_TEXT,
                            message.getText());
            startActivity(Intent.createChooser(intent, null));
        }

        mode.finish();
        return true;
    }

    ///
    /// Behavioural handlers
    ///

    /**
     * Handler for the delete button.
     */
    private boolean onDeleteButtonClick(ActionMode mode) {
        // Get the database IDs of the messages that are checked
        final List<Long> databaseIds = new ArrayList<>();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.isItemChecked(i)) {
                databaseIds.add(adapter.getItem(i).getDatabaseId());
            }
        }

        // Show a confirmation dialog
        Utils.showAlertDialog(
            this,
            getString(R.string.conversation_delete_confirm_title),
            getString(R.string.conversation_delete_confirm_message),
            getString(R.string.delete),
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Delete each message
                    for (Long databaseId : databaseIds) {
                        database.deleteMessage(databaseId);
                    }

                    // Go back to the previous activity if no messages remain
                    if (!database.conversationHasMessages(preferences.getDid(),
                                                          contact))
                    {
                        NavUtils
                            .navigateUpFromSameTask(ConversationActivity.this);
                    } else {
                        adapter.refresh();
                    }
                }
            },
            getString(R.string.cancel),
            null);

        mode.finish();
        return true;
    }

    /**
     * Facilitates special back button behaviour, such as when the search
     * box is visible or when the action mode is enabled.
     */
    @Override
    public void onBackPressed() {
        if (actionModeEnabled) {
            // Close the action mode when enabled
            actionMode.finish();
        } else if (menu != null) {
            // Close the search box if visible
            MenuItem searchItem = menu.findItem(R.id.search_button);
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (!searchView.isIconified()) {
                searchItem.collapseActionView();
            } else {
                // Otherwise, perform normal back button behaviour
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ActivityMonitor.getInstance().deleteReferenceToActivity(this);
    }

    ///
    /// Miscellaneous handlers
    ///

    @Override
    protected void onResume() {
        super.onResume();
        ActivityMonitor.getInstance().setCurrentActivity(this);

        final EditText messageText =
            (EditText) findViewById(R.id.message_edit_text);
        Message draftMessage = database.getDraftMessageForConversation(
            preferences.getDid(), contact);
        if (draftMessage != null) {
            messageText.setText(draftMessage.getText());
            messageText.requestFocus();
            messageText.setSelection(messageText.getText().length());
        }

        // Remove any open notifications related to this conversation
        Integer id = Notifications.getInstance(getApplicationContext())
                                  .getNotificationIds().get(contact);
        if (id != null) {
            NotificationManager notificationManager = (NotificationManager)
                getApplicationContext().getSystemService(
                    Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(id);
        }

        postUpdate();
    }

    /**
     * Called after this activity loads or after a database update if this
     * activity is currently open.
     */
    public void postUpdate() {
        database.markConversationAsRead(preferences.getDid(), contact);
        adapter.refresh();
    }

    /**
     * Handles requests for the CALL_PHONE permission, which is used by the
     * call button.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions,
                                         grantResults);
        if (requestCode == 0) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.CALL_PHONE)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        // If the permission request was granted, try calling
                        // again
                        onCallButtonClick();
                    } else {
                        // Otherwise, show a warning
                        Utils.showPermissionSnackbar(
                            this,
                            R.id.coordinator_layout,
                            getString(R.string.conversation_perm_denied_call));
                    }
                }
            }
        }
    }

    /**
     * Facilitates special double-click behaviour, such as toggling an item.
     */
    @Override
    public boolean onLongClick(View view) {
        toggleItem(view);
        return true;
    }

    /**
     * Toggles the specified view.
     *
     * @param view The view to toggle.
     */
    private void toggleItem(View view) {
        // Inform the adapter that the item should be checked
        adapter.toggleItemChecked(recyclerView.getChildAdapterPosition(view));

        // Turn on or off the action mode depending on how many items are
        // checked
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

        // If the action mode is enabled, update the visible buttons to match
        // the number of checked items
        updateButtons();
    }

    /**
     * Facilitates special click behaviour, such as when the action mode is
     * enabled or if a message is clicked that has previously failed to send.
     */
    @Override
    public void onClick(View view) {
        if (actionModeEnabled) {
            // Check or uncheck item when action mode is enabled
            toggleItem(view);
        } else {
            // Resend message if has not yet been sent, but only if
            Message message =
                adapter.getItem(recyclerView.getChildAdapterPosition(view));
            if (!message.isDelivered() && !message.isDeliveryInProgress()) {
                preSendMessage(message.getDatabaseId());
            }
        }
    }

    /**
     * Update the visible buttons to match the number of checked items.
     */
    private void updateButtons() {
        MenuItem resendAction = actionMode.getMenu().findItem(
            R.id.resend_button);
        MenuItem copyAction = actionMode.getMenu().findItem(R.id.copy_button);
        MenuItem shareAction = actionMode.getMenu().findItem(R.id.share_button);
        MenuItem infoAction = actionMode.getMenu().findItem(R.id.info_button);

        int count = adapter.getCheckedItemCount();

        // The resend button should only be visible if there is a single item
        // checked and that item is in the failed to deliver state
        boolean resendVisible = false;
        if (count == 1) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isItemChecked(i)) {
                    if (!adapter.getItem(i).isDelivered()
                        && !adapter.getItem(i).isDeliveryInProgress())
                    {
                        resendVisible = true;
                        break;
                    }
                }
            }
        }
        resendAction.setVisible(resendVisible);

        // Certain buttons should not be visible if there is more than one
        // item visible
        if (count >= 2) {
            infoAction.setVisible(false);
            copyAction.setVisible(false);
            shareAction.setVisible(false);
        } else {
            infoAction.setVisible(true);
            copyAction.setVisible(true);
            shareAction.setVisible(true);
        }
    }

    /**
     * Called after this activity sends a message.
     */
    public void postSendMessage(boolean success, long databaseId) {
        database.markConversationAsRead(preferences.getDid(), contact);
        if (success) {
            // Since the message in our database does not have all of the
            // information we need (the VoIP.ms ID, the precise date of sending)
            // we delete it and retrieve the sent message from VoIP.ms; the
            // adapter refresh will occur as part of the DB sync
            database.removeMessage(databaseId);
            database.synchronize(this, true, true, null);
        } else {
            // Otherwise, mark the message as failed to deliver and refresh
            // the adapter
            database.markMessageAsFailedToSend(databaseId);
            adapter.refresh();
        }

        // Scroll to the bottom of the adapter so that the message is in view
        if (adapter.getItemCount() > 0) {
            layoutManager.scrollToPosition(adapter.getItemCount() - 1);
        }
    }



    ///
    /// Miscellaneous helper methods
    ///


}
