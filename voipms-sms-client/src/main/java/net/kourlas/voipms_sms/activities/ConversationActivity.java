/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
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

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;
import net.kourlas.voipms_sms.*;
import net.kourlas.voipms_sms.adapters.ConversationListViewAdapter;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;

import java.util.ArrayList;
import java.util.List;

public class ConversationActivity extends AppCompatActivity {
    private final ConversationActivity conversationsActivity = this;
    private ConversationListViewAdapter conversationListViewAdapter;
    private String contact;
    private ListView listView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation);

        final ConversationActivity conversationActivity = this;

        contact = getIntent().getExtras().getString("contact");
        conversationListViewAdapter = new ConversationListViewAdapter(this, contact);

        final String contact = this.contact;
        Conversation conversation = Database.getInstance(getApplicationContext()).getConversation(contact);

        // Mark conversation as read
        for (Sms sms : conversation.getAllSms()) {
            sms.setUnread(false);
            Database.getInstance(getApplicationContext()).replaceSms(sms);
        }

        // Set up action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            String contactName = Utils.getContactName(this, contact);
            if (contactName != null) {
                actionBar.setTitle(contactName);
            } else {
                actionBar.setTitle(Utils.getFormattedPhoneNumber(contact));
            }
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final ListView listView = (ListView) findViewById(R.id.list);
        this.listView = listView;
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

                MenuItem copyAction = mode.getMenu().findItem(R.id.copy_button);
                MenuItem shareAction = mode.getMenu().findItem(R.id.share_button);
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
                    case R.id.copy_button:
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Text message",
                                ((Sms) conversationListViewAdapter.getItem(selectedItems.get(0))).getMessage());
                        clipboard.setPrimaryClip(clip);
                        mode.finish();
                        return true;
                    case R.id.share_button:
                        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(android.content.Intent.EXTRA_TEXT, ((Sms) conversationListViewAdapter.getItem(
                                selectedItems.get(0))).getMessage());
                        startActivity(Intent.createChooser(intent, null));
                        mode.finish();
                        return true;
                    case R.id.delete_button:
                        SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
                        for (int i = 0; i < listView.getCount(); i++) {
                            if (checkedItemPositions.get(i)) {
                                Api.getInstance(getApplicationContext()).deleteSms(conversationsActivity,
                                        ((Sms) conversationListViewAdapter.getItem(i)).getId());
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
                } else if (viewSwitcher.getDisplayedChild() == 0) {
                    viewSwitcher.setDisplayedChild(1);
                }
            }
        });

        QuickContactBadge photo = (QuickContactBadge) findViewById(R.id.photo);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            photo.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            photo.setClipToOutline(true);
        }
        photo.assignContactFromPhone(Preferences.getInstance(getApplicationContext()).getDid(), true);
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(
                Preferences.getInstance(getApplicationContext()).getDid()));
        Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sendButton.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            sendButton.setClipToOutline(true);
        }
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ProgressBar progressBar = (ProgressBar) conversationActivity.findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.VISIBLE);
                Api.getInstance(getApplicationContext()).sendSms(conversationsActivity, contact, messageText.getText().toString());
            }
        });

        conversationListViewAdapter.requestScrollToBottom();
        conversationListViewAdapter.refresh();
    }

    @Override
    protected void onResume() {
        App.getInstance().setCurrentActivity(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        App.getInstance().deleteReferenceToActivity(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        App.getInstance().deleteReferenceToActivity(this);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation, menu);

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
                conversationListViewAdapter.getFilter().filter(newText);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            switch (item.getItemId()) {
                case R.id.call_button:
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setData(Uri.parse("tel:" + contact));
                    startActivity(intent);
                    return true;
                case R.id.delete_button:
                    Conversation conversation = Database.getInstance(getApplicationContext()).getConversation(contact);
                    if (conversation.getAllSms().length == 0) {
                        NavUtils.navigateUpFromSameTask(this);
                    } else {
                        for (Sms sms : conversation.getAllSms()) {
                            Api.getInstance(getApplicationContext()).deleteSms(conversationsActivity, sms.getId());
                        }
                    }
                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public ConversationListViewAdapter getConversationListViewAdapter() {
        return conversationListViewAdapter;
    }

    public String getContact() {
        return contact;
    }

    public ListView getListView() {
        return listView;
    }
}
