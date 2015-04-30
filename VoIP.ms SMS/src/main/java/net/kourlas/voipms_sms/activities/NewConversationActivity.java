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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.adapters.NewConversationListViewAdapter;

import static net.kourlas.voipms_sms.adapters.NewConversationListViewAdapter.ContactItem;

public class NewConversationActivity extends ActionBarActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_conversation);

        final Activity newConversationActivity = this;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);

            actionBar.setCustomView(R.layout.new_conversation_text);
            actionBar.setDisplayShowCustomEnabled(true);

            final NewConversationListViewAdapter newConversationListViewAdapter = new NewConversationListViewAdapter(
                    this);

            SearchView searchView = (SearchView) actionBar.getCustomView().findViewById(R.id.search_view);
            searchView.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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
                        newConversationListViewAdapter.showTypedInItem(phoneNumber);
                    }
                    newConversationListViewAdapter.refresh(newText);
                    return true;
                }
            });
            searchView.requestFocus();



            // Hide search icon
            ImageView searchMagIcon = (ImageView) searchView.findViewById(R.id.search_mag_icon);
            searchMagIcon.setLayoutParams(new LinearLayout.LayoutParams(0, 0));

            final ListView listView = (ListView) findViewById(R.id.list);
            listView.setAdapter(newConversationListViewAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ContactItem contactItem = (ContactItem) newConversationListViewAdapter.getItem(position);

                    String phoneNumber = contactItem.getPhoneNumber().replaceAll("[^0-9]", "");

                    Intent intent = new Intent(newConversationActivity, ConversationActivity.class);
                    intent.putExtra("contact", phoneNumber);
                    startActivity(intent);
                }
            });
            listView.setFastScrollEnabled(true);
        }
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
                    SearchView searchView = (SearchView) actionBar.getCustomView().findViewById(R.id.search_view);
                    if (searchView.getInputType() == (InputType.TYPE_TEXT_VARIATION_PERSON_NAME)) {
                        searchView.setInputType(InputType.TYPE_CLASS_PHONE);
                        item.setIcon(R.drawable.ic_keyboard_white_24dp);
                        item.setTitle(R.string.new_conversation_action_keyboard);
                    } else {
                        searchView.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                        item.setIcon(R.drawable.ic_dialpad_white_24dp);
                        item.setTitle(R.string.new_conversation_action_dialpad);
                    }
                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }
}
