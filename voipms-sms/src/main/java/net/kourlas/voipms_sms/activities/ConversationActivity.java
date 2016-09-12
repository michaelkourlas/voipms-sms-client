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
import android.app.Activity;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
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
import android.util.Log;
import android.view.*;
import android.widget.*;
import net.kourlas.voipms_sms.*;
import net.kourlas.voipms_sms.adapters.ConversationRecyclerViewAdapter;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Message;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
    public static final String TAG = "ConversationActivity";
    private static final int PERM_REQ_CALL = 0;

    private final ConversationActivity activity = this;

    private String contact;

    private Menu menu;

    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private ConversationRecyclerViewAdapter adapter;

    private ActionMode actionMode;
    private boolean actionModeEnabled;

    private Database database;
    private Preferences preferences;

    public static void sendMessage(Activity sourceActivity, long databaseId) {
        Context applicationContext = sourceActivity.getApplicationContext();
        Database database = Database.getInstance(applicationContext);
        Preferences preferences = Preferences.getInstance(applicationContext);

        Message message = database.getMessageWithDatabaseId(preferences.getDid(), databaseId);
        SendMessageTask task = new SendMessageTask(sourceActivity.getApplicationContext(), message, sourceActivity);

        if (preferences.getEmail().equals("") || preferences.getPassword().equals("") ||
                preferences.getDid().equals("")) {
            // Do not show an error; this method should never be called unless the email, password and DID are set
            task.cleanup(false);
            return;
        }

        if (!Utils.isNetworkConnectionAvailable(applicationContext)) {
            Toast.makeText(applicationContext, applicationContext.getString(R.string.conversation_send_error_network),
                    Toast.LENGTH_SHORT).show();
            task.cleanup(false);
            return;
        }

        try {
            String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                    "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                    "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                    "method=sendSMS" + "&" +
                    "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                    "dst=" + URLEncoder.encode(message.getContact(), "UTF-8") + "&" +
                    "message=" + URLEncoder.encode(message.getText(), "UTF-8");
            task.start(voipUrl);
        } catch (UnsupportedEncodingException ex) {
            // This should never happen since the encoding (UTF-8) is hardcoded
            throw new Error(ex);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation);

        database = Database.getInstance(getApplicationContext());
        preferences = Preferences.getInstance(getApplicationContext());

        contact = getIntent().getStringExtra(getString(R.string.conversation_extra_contact));
        // Remove the leading one from a North American phone number
        // (e.g. +1 (123) 555-4567)
        if ((contact.length() == 11) && (contact.charAt(0) == '1')) {
            contact = contact.substring(1);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources().getDimension(R.dimen.toolbar_elevation));
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            String contactName = Utils.getContactName(this, contact);
            if (contactName != null) {
                actionBar.setTitle(contactName);
            }
            else {
                actionBar.setTitle(Utils.getFormattedPhoneNumber(contact));
            }
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.setStackFromEnd(true);
        adapter = new ConversationRecyclerViewAdapter(this, layoutManager, contact);
        recyclerView = (RecyclerView) findViewById(R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        actionMode = null;
        actionModeEnabled = false;

        final EditText messageText = (EditText) findViewById(R.id.message_edit_text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            messageText.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 15);
                }
            });
            messageText.setClipToOutline(true);
        }
        messageText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing.
            }

            @Override
            public void afterTextChanged(Editable s) {
                ViewSwitcher viewSwitcher = (ViewSwitcher) findViewById(R.id.view_switcher);
                if (s.toString().equals("") && viewSwitcher.getDisplayedChild() == 1) {
                    viewSwitcher.setDisplayedChild(0);
                }
                else if (viewSwitcher.getDisplayedChild() == 0) {
                    viewSwitcher.setDisplayedChild(1);
                }
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
        String intentMessageText = getIntent().getStringExtra(getString(R.string.conversation_extra_message_text));
        if (intentMessageText != null) {
            messageText.setText(intentMessageText);
        }
        boolean intentFocus = getIntent().getBooleanExtra(getString(R.string.conversation_extra_focus), false);
        if (intentFocus) {
            messageText.requestFocus();
        }

        RelativeLayout messageSection = (RelativeLayout) findViewById(R.id.message_section);
        ViewCompat.setElevation(messageSection, 8);

        QuickContactBadge photo = (QuickContactBadge) findViewById(R.id.photo);
        Utils.applyCircularMask(photo);
        photo.assignContactFromPhone(preferences.getDid(), true);
        String photoUri = Utils.getContactPhotoUri(getApplicationContext(), preferences.getDid());
        if (photoUri != null) {
            photo.setImageURI(Uri.parse(photoUri));
        }
        else {
            photo.setImageToDefault();
        }

        final ImageButton sendButton = (ImageButton) findViewById(R.id.send_button);
        Utils.applyCircularMask(sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preSendMessage();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityMonitor.getInstance().setCurrentActivity(this);

        Integer id = Notifications.getInstance(getApplicationContext()).getNotificationIds().get(contact);
        if (id != null) {
            NotificationManager notificationManager = (NotificationManager)
                    getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(id);
        }

        markConversationAsRead();

        adapter.refresh();
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
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation, menu);
        this.menu = menu;

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            MenuItem phoneMenuItem = menu.findItem(R.id.call_button);
            phoneMenuItem.setVisible(false);
        }

        SearchView searchView = (SearchView) menu.findItem(R.id.search_button).getActionView();
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

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions,
                                         grantResults);
        if (requestCode == PERM_REQ_CALL) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.CALL_PHONE)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        callButtonHandler();
                    } else {
                        Snackbar snackbar = Snackbar.make(
                            findViewById(R.id.coordinator_layout),
                            getString(R.string.conversation_perm_denied_call),
                            Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(
                            R.string.settings,
                            v -> {
                                Intent intent = new Intent();
                                intent.setAction(
                                    Settings
                                        .ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NO_HISTORY);
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                                Uri uri = Uri.fromParts(
                                    "package", activity.getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            });
                        snackbar.show();
                    }
                }
            }
        }
    }

    private boolean callButtonHandler() {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + contact));

        if (ContextCompat.checkSelfPermission(this,
                                              Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.CALL_PHONE},
                PERM_REQ_CALL);
        } else {
            try {
                startActivity(intent);
            } catch (SecurityException ignored) {
                // Do nothing.
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            switch (item.getItemId()) {
                case R.id.call_button:
                    return callButtonHandler();
                case R.id.delete_button:
                    Conversation conversation = database.getConversation(preferences.getDid(), contact);
                    if (conversation.getMessages().length == 0) {
                        NavUtils.navigateUpFromSameTask(this);
                    }
                    else {
                        List<Long> databaseIds = new ArrayList<>();
                        for (int i = 0; i < adapter.getItemCount(); i++) {
                            if (adapter.getItem(i).getDatabaseId() != null) {
                                databaseIds.add(adapter.getItem(i).getDatabaseId());
                            }
                        }

                        Long[] databaseIdsArray = new Long[databaseIds.size()];
                        databaseIds.toArray(databaseIdsArray);
                        deleteMessages(databaseIdsArray);
                    }
                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.conversation_secondary, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.resend_button) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isItemChecked(i)) {
                    sendMessage(adapter.getItem(i).getDatabaseId());
                    break;
                }
            }

            actionMode.finish();
            return true;
        }
        else if (menuItem.getItemId() == R.id.info_button) {
            Message message = null;
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isItemChecked(i)) {
                    message = adapter.getItem(i);
                    break;
                }
            }

            if (message != null) {
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String dialogText;
                if (message.getType() == Message.Type.INCOMING) {
                    dialogText = (message.getVoipId() == null ? "" : (getString(R.string.conversation_info_id) +
                            " " + message.getVoipId() + "\n")) + getString(R.string.conversation_info_to) + " " +
                            Utils.getFormattedPhoneNumber(message.getDid()) + "\n" +
                            getString(R.string.conversation_info_from) + " " +
                            Utils.getFormattedPhoneNumber(message.getContact()) + "\n" +
                            getString(R.string.conversation_info_date) + " " + dateFormat.format(message.getDate());
                }
                else {
                    dialogText = (message.getVoipId() == null ? "" : (getString(R.string.conversation_info_id) +
                            " " + message.getVoipId() + "\n")) + getString(R.string.conversation_info_to) + " " +
                            Utils.getFormattedPhoneNumber(message.getContact()) + "\n" +
                            getString(R.string.conversation_info_from) + " " +
                            Utils.getFormattedPhoneNumber(message.getDid()) + "\n" +
                            getString(R.string.conversation_info_date) + " " + dateFormat.format(message.getDate());
                }
                Utils.showAlertDialog(this, getString(R.string.conversation_info_title), dialogText,
                        getString(R.string.ok), null, null, null);
            }

            actionMode.finish();
            return true;
        }
        else if (menuItem.getItemId() == R.id.copy_button) {
            Message message = null;
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isItemChecked(i)) {
                    message = adapter.getItem(i);
                    break;
                }
            }

            if (message != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Text message", message.getText());
                clipboard.setPrimaryClip(clip);
            }

            actionMode.finish();
            return true;
        }
        else if (menuItem.getItemId() == R.id.share_button) {
            Message message = null;
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isItemChecked(i)) {
                    message = adapter.getItem(i);
                    break;
                }
            }

            if (message != null) {
                Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(android.content.Intent.EXTRA_TEXT, message.getText());
                startActivity(Intent.createChooser(intent, null));
            }

            actionMode.finish();
            return true;
        }
        else if (menuItem.getItemId() == R.id.delete_button) {
            List<Long> databaseIds = new ArrayList<>();
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isItemChecked(i)) {
                    databaseIds.add(adapter.getItem(i).getDatabaseId());
                }
            }

            Long[] databaseIdsArray = new Long[databaseIds.size()];
            databaseIds.toArray(databaseIdsArray);
            deleteMessages(databaseIdsArray);

            actionMode.finish();
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            adapter.setItemChecked(i, false);
        }
        actionModeEnabled = false;
    }

    private void updateButtons() {
        MenuItem resendAction = actionMode.getMenu().findItem(R.id.resend_button);
        MenuItem copyAction = actionMode.getMenu().findItem(R.id.copy_button);
        MenuItem shareAction = actionMode.getMenu().findItem(R.id.share_button);
        MenuItem infoAction = actionMode.getMenu().findItem(R.id.info_button);

        int count = adapter.getCheckedItemCount();

        boolean resendVisible = false;
        if (count == 1) {
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isItemChecked(i)) {
                    if (!adapter.getItem(i).isDelivered() && !adapter.getItem(i).isDeliveryInProgress()) {
                        resendVisible = true;
                        break;
                    }
                }
            }
        }
        resendAction.setVisible(resendVisible);

        if (count >= 2) {
            infoAction.setVisible(false);
            copyAction.setVisible(false);
            shareAction.setVisible(false);
        }
        else {
            infoAction.setVisible(true);
            copyAction.setVisible(true);
            shareAction.setVisible(true);
        }
    }

    @Override
    public void onClick(View view) {
        if (actionModeEnabled) {
            toggleItem(view);
        }
        else {
            Message message = adapter.getItem(recyclerView.getChildAdapterPosition(view));
            if (!message.isDelivered() && !message.isDeliveryInProgress()) {
                preSendMessage(message.getDatabaseId());
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        toggleItem(view);
        return true;
    }

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

    public void deleteMessages(final Long[] databaseIds) {
        Utils.showAlertDialog(this, getString(R.string.conversation_delete_confirm_title),
                getString(R.string.conversation_delete_confirm_message),
                getString(R.string.delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (long databaseId : databaseIds) {
                            Message message = database.getMessageWithDatabaseId(preferences.getDid(), databaseId);
                            if (message.getVoipId() == null) {
                                database.removeMessage(databaseId);
                            }
                            else {
                                message.setDeleted(true);
                                database.insertMessage(message);
                            }
                            adapter.refresh();
                        }
                    }
                },
                getString(R.string.cancel), null);
    }

    public void preSendMessage() {
        EditText messageEditText = (EditText) findViewById(R.id.message_edit_text);
        String messageText = messageEditText.getText().toString();
        while (true) {
            if (messageText.length() > 160) {
                long databaseId = database.insertMessage(new Message(preferences.getDid(), contact,
                        messageText.substring(0, 160)));
                messageText = messageText.substring(160);
                adapter.refresh();
                preSendMessage(databaseId);
            }
            else {
                long databaseId = database.insertMessage(new Message(preferences.getDid(), contact,
                        messageText.substring(0, messageText.length())));
                adapter.refresh();
                preSendMessage(databaseId);
                break;
            }
        }
        messageEditText.setText("");
    }

    public void preSendMessage(long databaseId) {
        Message message = database.getMessageWithDatabaseId(preferences.getDid(), databaseId);
        message.setDelivered(false);
        message.setDeliveryInProgress(true);
        database.insertMessage(message);
        adapter.refresh();

        sendMessage(databaseId);
    }

    public void postSendMessage(boolean success, long databaseId) {
        if (success) {
            database.removeMessage(databaseId);
            database.synchronize(true, true, this);
        }
        else {
            Message message = database.getMessageWithDatabaseId(preferences.getDid(), databaseId);
            message.setDelivered(false);
            message.setDeliveryInProgress(false);
            database.insertMessage(message);
            adapter.refresh();
        }

        if (adapter.getItemCount() > 0) {
            layoutManager.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    public void postUpdate() {
        markConversationAsRead();
        adapter.refresh();
    }

    public String getContact() {
        return contact;
    }

    public void markConversationAsRead() {
        for (Message message : database.getConversation(preferences.getDid(), contact).getMessages()) {
            message.setUnread(false);
            database.insertMessage(message);
        }
    }

    public void sendMessage(long databaseId) {
        sendMessage(this, databaseId);
    }

    public static class SendMessageTask {
        private final Context applicationContext;

        private final Message message;
        private final Activity sourceActivity;

        public SendMessageTask(Context applicationContext, Message message, Activity sourceActivity) {
            this.applicationContext = applicationContext;

            this.message = message;
            this.sourceActivity = sourceActivity;
        }

        public void start(String voipUrl) {
            new SendMessageAsyncTask().execute(voipUrl);
        }

        public void cleanup(boolean success) {
            if (sourceActivity instanceof ConversationActivity) {
                ((ConversationActivity) sourceActivity).postSendMessage(success, message.getDatabaseId());
            }
            else if (sourceActivity instanceof ConversationQuickReplyActivity) {
                ((ConversationQuickReplyActivity) sourceActivity).postSendMessage(success, message.getDatabaseId());
            }
        }

        private class SendMessageAsyncTask extends AsyncTask<String, String, Boolean> {
            @Override
            protected Boolean doInBackground(String... params) {
                JSONObject resultJson;
                try {
                    resultJson = Utils.getJson(params[0]);
                } catch (JSONException ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    publishProgress(applicationContext.getString(R.string.conversation_send_error_api_parse));
                    return false;
                } catch (Exception ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    publishProgress(applicationContext.getString(R.string.conversation_send_error_api_request));
                    return false;
                }

                String status = resultJson.optString("status");
                if (status == null) {
                    publishProgress(applicationContext.getString(R.string.conversation_send_error_api_parse));
                    return false;
                }
                if (!status.equals("success")) {
                    publishProgress(applicationContext.getString(
                            R.string.conversation_send_error_api_error).replace("{error}", status));
                    return false;
                }

                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                cleanup(success);
            }

            /**
             * Shows a toast to the user.
             *
             * @param message The message to show. This must be a String array with a single element containing the
             *                message.
             */
            @Override
            protected void onProgressUpdate(String... message) {
                Toast.makeText(applicationContext, message[0], Toast.LENGTH_SHORT).show();
            }
        }
    }
}
