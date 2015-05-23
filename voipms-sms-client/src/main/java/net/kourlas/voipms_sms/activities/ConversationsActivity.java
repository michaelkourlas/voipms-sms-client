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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.text.InputType;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;
import net.kourlas.voipms_sms.*;
import net.kourlas.voipms_sms.adapters.ConversationsListViewAdapter;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;
import net.kourlas.voipms_sms.notifications.Gcm;

public class ConversationsActivity extends AppCompatActivity {
    private final ConversationsActivity conversationsActivity = this;
    private ConversationsListViewAdapter conversationsListViewAdapter;
    private ListView listView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversations);

        conversationsListViewAdapter = new ConversationsListViewAdapter(this);

        SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) findViewById(
                R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Api.getInstance(getApplicationContext()).updateSmsDatabase(conversationsActivity, true, false);
            }
        });

        final ListView listView = (ListView) findViewById(R.id.list);
        this.listView = listView;
        listView.setAdapter(conversationsListViewAdapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation conversation = (Conversation) listView.getAdapter().getItem(position);
                String contact = conversation.getContact();

                Intent intent = new Intent(conversationsActivity, ConversationActivity.class);
                intent.putExtra("contact", contact);
                startActivity(intent);
            }
        });
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            private void updateReadUnreadButton(ActionMode mode) {
                int read = 0;
                int unread = 0;

                SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
                for (int i = 0; i < conversationsListViewAdapter.getCount(); i++) {
                    if (checkedItemPositions.get(i)) {
                        Conversation conversation = (Conversation) conversationsListViewAdapter.getItem(i);
                        if (conversation.isUnread()) {
                            unread++;
                        } else {
                            read++;
                        }
                    }
                }

                MenuItem item = mode.getMenu().findItem(R.id.mark_read_unread_button);
                if (read > unread) {
                    item.setIcon(R.drawable.ic_markunread_white_24dp);
                    item.setTitle(R.string.conversations_action_mark_unread);
                } else {
                    item.setIcon(R.drawable.ic_drafts_white_24dp);
                    item.setTitle(R.string.conversations_action_mark_read);
                }
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                  long id, boolean checked) {
                updateReadUnreadButton(mode);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.mark_read_unread_button:
                        SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
                        for (int i = 0; i < conversationsListViewAdapter.getCount(); i++) {
                            if (checkedItemPositions.get(i)) {
                                Conversation conversation = (Conversation) conversationsListViewAdapter.getItem(i);
                                Sms[] smses = conversation.getAllSms();
                                for (Sms sms : smses) {
                                    sms.setUnread(item.getTitle().equals(getResources().getString(
                                            R.string.conversations_action_mark_unread)));
                                    Database.getInstance(getApplicationContext()).replaceSms(sms);
                                }
                            }
                        }
                        conversationsListViewAdapter.refresh();
                        updateReadUnreadButton(mode);
                        mode.finish();
                        return true;
                    case R.id.delete_button:
                        checkedItemPositions = listView.getCheckedItemPositions();
                        for (int i = 0; i < checkedItemPositions.size(); i++) {
                            if (checkedItemPositions.get(i)) {
                                Conversation conversation = (Conversation) conversationsListViewAdapter.getItem(i);
                                for (Sms sms : conversation.getAllSms()) {
                                    Api.getInstance(getApplicationContext()).deleteSms(conversationsActivity, sms.getId());
                                }
                            }
                        }
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.conversations_secondary, menu);
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Do nothing.
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
        });

        ImageButton button = (ImageButton) findViewById(R.id.new_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Preferences.getInstance(getApplicationContext()).getEmail().equals("")) {
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.api_new_conversation_email), Toast.LENGTH_LONG).show();
                } else if (Preferences.getInstance(getApplicationContext()).getPassword().equals("")) {
                    Toast.makeText(getApplicationContext(), getResources().getString(
                            R.string.api_new_conversation_password), Toast.LENGTH_LONG).show();
                } else if (Preferences.getInstance(getApplicationContext()).getDid().equals("")) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.api_new_conversation_did),
                            Toast.LENGTH_LONG).show();
                } else {
                    Intent intent = new Intent(conversationsActivity, NewConversationActivity.class);
                    startActivity(intent);
                }
            }
        });

        if (Preferences.getInstance(getApplicationContext()).getFirstRun()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.conversations_first_run_dialog_text));
            builder.setPositiveButton(getString(R.string.preferences_name), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent preferencesIntent = new Intent(conversationsActivity, PreferencesActivity.class);
                    startActivity(preferencesIntent);
                    Preferences.getInstance(getApplicationContext()).setFirstRun(false);
                }
            });
            builder.setCancelable(false);
            builder.show();
        }
    }

    @Override
    public void onResume() {
        App.getInstance().setCurrentActivity(this);

        conversationsListViewAdapter.refresh();

        if (!Preferences.getInstance(getApplicationContext()).getFirstRun()) {
            Api.getInstance(getApplicationContext()).updateSmsDatabase(conversationsActivity, true, false);
        }

        Gcm.getInstance(getApplicationContext()).registerForGcm(this);

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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversations, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.search_button).getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                conversationsListViewAdapter.refresh(newText);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.select_did_button:
                Api.getInstance(getApplicationContext()).showSelectDidDialog(conversationsActivity);
                return true;
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public ConversationsListViewAdapter getConversationsListViewAdapter() {
        return conversationsListViewAdapter;
    }

    public ListView getListView() {
        return listView;
    }
}
