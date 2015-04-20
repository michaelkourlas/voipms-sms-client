/*
 * VoIP.ms SMS
 * Copyright © 2015 Michael Kourlas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.kourlas.voipms_sms.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;
import net.kourlas.voipms_sms.Api;
import net.kourlas.voipms_sms.Preferences;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.adapters.ConversationListViewAdapter;
import net.kourlas.voipms_sms.adapters.SmsDatabaseAdapter;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConversationActivity extends Activity {

    private Api api;
    private SmsDatabaseAdapter smsDatabaseAdapter;
    private String contact;
    private Conversation conversation;
    private ConversationListViewAdapter conversationListViewAdapter;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation);

        final ConversationActivity conversationActivity = this;

        api = new Api(this);

        smsDatabaseAdapter = new SmsDatabaseAdapter(this);
        smsDatabaseAdapter.open();

        final String contact = getIntent().getExtras().getString("contact");
        this.contact = contact;
        conversation = smsDatabaseAdapter.getConversation(contact);
        List<Sms> conversationSmses = new ArrayList<Sms>();
        conversationSmses.addAll(Arrays.asList(conversation.getAllSms()));
        Collections.reverse(conversationSmses);

        // Mark conversation as read
        for (Sms sms : conversationSmses) {
            sms.setUnread(false);
            smsDatabaseAdapter.replaceSms(sms);
        }

        final ConversationListViewAdapter conversationListViewAdapter = new ConversationListViewAdapter(this,
                conversationSmses);
        this.conversationListViewAdapter = conversationListViewAdapter;

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contact));
            Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
                            ContactsContract.PhoneLookup.DISPLAY_NAME}, null,
                    null, null);
            if (cursor.moveToFirst()) {
                actionBar.setTitle(cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)));
            } else {
                String formattedContact = contact;
                if (formattedContact.length() == 10) {
                    MessageFormat phoneNumberFormat = new MessageFormat("({0}) {1}-{2}");
                    String[] phoneNumberArray = new String[]{formattedContact.substring(0, 3), formattedContact.substring(3, 6),
                            formattedContact.substring(6)};
                    formattedContact = phoneNumberFormat.format(phoneNumberArray);
                }
                actionBar.setTitle(formattedContact);
            }
            cursor.close();
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final ListView listView = (ListView) findViewById(R.id.listview);
        listView.setSelector(android.R.color.transparent);
        listView.setAdapter(conversationListViewAdapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
                int count = 0;
                SparseBooleanArray sparseBooleanArray = listView.getCheckedItemPositions();
                for (int i = 0; i < listView.getCount(); i++) {
                    if (sparseBooleanArray.get(i)) {
                        count++;
                    }
                }

                MenuItem copyAction = mode.getMenu().findItem(R.id.conversation_action_copy);
                MenuItem shareAction = mode.getMenu().findItem(R.id.conversation_action_share);
                if (count < 2) {
                    copyAction.setVisible(true);
                    shareAction.setVisible(true);
                } else {
                    copyAction.setVisible(false);
                    shareAction.setVisible(false);
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.conversation_secondary, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                List<Integer> selectedItems = new ArrayList<Integer>();
                SparseBooleanArray sparseBooleanArray = listView.getCheckedItemPositions();
                for (int i = 0; i < listView.getCount(); i++) {
                    if (sparseBooleanArray.get(i)) {
                        selectedItems.add(i);
                    }
                }

                switch (item.getItemId()) {
                    case R.id.conversation_action_copy:
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Text message", ((Sms) conversationListViewAdapter.getItem(
                                selectedItems.get(0))).getMessage());
                        clipboard.setPrimaryClip(clip);
                        mode.finish();
                        return true;
                    case R.id.conversation_action_share:
                        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(android.content.Intent.EXTRA_TEXT, ((Sms) conversationListViewAdapter.getItem(
                                selectedItems.get(0))).getMessage());
                        startActivity(Intent.createChooser(intent, null));
                        mode.finish();
                        return true;
                    case R.id.delete_button:
                        SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
                        for (int i = 0; i < checkedItemPositions.size(); i++) {
                            if (checkedItemPositions.get(i)) {
                                api.deleteSms(((Sms) conversationListViewAdapter.getItem(i)).getId());
                            }
                        }
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Do nothing.
            }
        });
        listView.post(new Runnable() {
            @Override
            public void run() {
                listView.smoothScrollToPosition(listView.getCount() - 1);
            }
        });

        final EditText messageText = (EditText) findViewById(R.id.message_text);
        messageText.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 15);
            }
        });
        messageText.setClipToOutline(true);
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
                } else if (viewSwitcher.getDisplayedChild() == 0) {
                    viewSwitcher.setDisplayedChild(1);
                }
            }
        });

        Preferences preferences = new Preferences(this);

        QuickContactBadge photo = (QuickContactBadge) findViewById(R.id.photo);
        photo.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        photo.setClipToOutline(true);
        photo.assignContactFromPhone(preferences.getDid(), true);
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(
                preferences.getDid()));
        Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.DISPLAY_NAME}, null,
                null, null);
        if (cursor.moveToFirst()) {
            String photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
            if (photoUri != null) {
                photo.setImageURI(Uri.parse(photoUri));
            } else {
                photo.setImageToDefault();
            }
        } else {
            photo.setImageToDefault();
        }
        cursor.close();

        ImageButton sendButton = (ImageButton) findViewById(R.id.send_button);
        sendButton.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        sendButton.setClipToOutline(true);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ProgressBar progressBar = (ProgressBar) conversationActivity.findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.VISIBLE);
                api.sendSms(contact, messageText.getText().toString());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.conversation_action_search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                conversationListViewAdapter.getFilter().filter(newText);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            switch (item.getItemId()) {
                case R.id.call_button:
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setData(Uri.parse("tel:" + contact));
                    startActivity(intent);
                    return true;
                case R.id.delete_button:
                    if (conversation.getAllSms().length == 0) {
                        NavUtils.navigateUpFromSameTask(this);
                    } else {
                        for (Sms sms : conversation.getAllSms()) {
                            api.deleteSms(sms.getId());
                        }
                    }
                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public void refreshListView() {
        conversationListViewAdapter.clear();
        conversation = smsDatabaseAdapter.getConversation(contact);
        List<Sms> conversationSmses = new ArrayList<Sms>();
        conversationSmses.addAll(Arrays.asList(conversation.getAllSms()));
        Collections.reverse(conversationSmses);
        conversationListViewAdapter.addAll(conversationSmses);
        conversationListViewAdapter.notifyDataSetChanged();
    }
}
