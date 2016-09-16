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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.adapters.NewConversationListViewAdapter;

import static net.kourlas.voipms_sms.adapters.NewConversationListViewAdapter
    .ContactItem;

public class NewConversationActivity extends AppCompatActivity {
    private String messageText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_conversation);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null && type
            .equals("text/plain"))
        {
            this.messageText = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources()
            .getDimension(R.dimen.toolbar_elevation));
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);

            actionBar.setCustomView(R.layout.new_conversation_toolbar);
            actionBar.setDisplayShowCustomEnabled(true);
        }

        final Activity newConversationActivity = this;

        final NewConversationListViewAdapter newConversationListViewAdapter =
            new NewConversationListViewAdapter(
                this);

        if (actionBar != null) {
            SearchView searchView = (SearchView) actionBar.getCustomView()
                                                          .findViewById(
                                                              R.id.search_view);
            searchView.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
            searchView
                .setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        String phoneNumber = newText.replaceAll("[^0-9]", "");
                        if (phoneNumber.equals("")) {
                            newConversationListViewAdapter.hideTypedInItem();
                        } else {
                            newConversationListViewAdapter
                                .showTypedInItem(phoneNumber);
                        }
                        newConversationListViewAdapter.refresh(newText);
                        return true;
                    }
                });
            searchView.requestFocus();

            // Hide search icon
            ImageView searchMagIcon =
                (ImageView) searchView.findViewById(R.id.search_mag_icon);
            searchMagIcon.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
        }

        final ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(newConversationListViewAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ContactItem contactItem =
                (ContactItem) newConversationListViewAdapter
                    .getItem(position);

            String phoneNumber =
                contactItem.getPhoneNumber().replaceAll("[^0-9]", "");

            Intent intent1 = new Intent(newConversationActivity,
                                        ConversationActivity.class);
            intent1.putExtra(getString(R.string.conversation_extra_contact),
                             phoneNumber);
            if (messageText != null) {
                intent1.putExtra(
                    getString(R.string.conversation_extra_message_text),
                    messageText);
            }
            intent1.putExtra(getString(R.string.conversation_extra_focus),
                             true);
            startActivity(intent1);
        });
        listView.setFastScrollEnabled(true);

        newConversationListViewAdapter.refresh("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ActivityMonitor.getInstance().deleteReferenceToActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        ActivityMonitor.getInstance().deleteReferenceToActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        ActivityMonitor.getInstance().setCurrentActivity(this);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.new_conversation, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            switch (item.getItemId()) {
                case R.id.keyboard_button:
                    SearchView searchView =
                        (SearchView) actionBar.getCustomView()
                                              .findViewById(R.id.search_view);
                    if (searchView.getInputType()
                        == (InputType.TYPE_TEXT_VARIATION_PERSON_NAME))
                    {
                        searchView.setInputType(InputType.TYPE_CLASS_PHONE);
                        item.setIcon(R.drawable.ic_keyboard_white_24dp);
                        item.setTitle(
                            R.string.new_conversation_action_keyboard);
                    } else {
                        searchView.setInputType(
                            InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                        item.setIcon(R.drawable.ic_dialpad_white_24dp);
                        item.setTitle(R.string.new_conversation_action_dialpad);
                    }
                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }
}
