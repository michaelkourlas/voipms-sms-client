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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.adapters.NewConversationListViewAdapter;

import java.util.ArrayList;
import java.util.List;

import static net.kourlas.voipms_sms.adapters.NewConversationListViewAdapter.PhoneNumberEntry;

public class NewConversationActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_conversation);

        final Activity newConversationActivity = this;

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);

            actionBar.setCustomView(R.layout.new_conversation_text);
            actionBar.setDisplayShowCustomEnabled(true);

            final List<PhoneNumberEntry> phoneNumberEntries = new ArrayList<PhoneNumberEntry>();
            Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null,
                    null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)).equals(
                            "1")) {
                        String contact = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.Contacts.DISPLAY_NAME));
                        String phoneNumber = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        String photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI));

                        boolean showPhoto = true;
                        for (PhoneNumberEntry phoneNumberEntry : phoneNumberEntries) {
                            if (contact.equals(phoneNumberEntry.getName())) {
                                showPhoto = false;
                            }
                        }

                        PhoneNumberEntry phoneNumberEntry = new PhoneNumberEntry(contact, phoneNumber, photoUri, showPhoto, false);
                        phoneNumberEntries.add(phoneNumberEntry);
                    }
                }
            }
            cursor.close();

            final NewConversationListViewAdapter newConversationListViewAdapter = new NewConversationListViewAdapter(
                    this, phoneNumberEntries);

            EditText editText = (EditText) actionBar.getCustomView().findViewById(R.id.new_conversation_text);
            editText.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    // Do nothing.
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String phoneNumber = s.toString().replaceAll("[^0-9]", "");
                    if (phoneNumber.equals("")) {
                        if (newConversationListViewAdapter.getCount() > 0 && ((PhoneNumberEntry) newConversationListViewAdapter.getItem(0)).isTypedIn()) {
                            newConversationListViewAdapter.remove(0);
                        }
                    } else {
                        if (newConversationListViewAdapter.getCount() > 0 && ((PhoneNumberEntry) newConversationListViewAdapter.getItem(0)).isTypedIn()) {
                            ((PhoneNumberEntry) newConversationListViewAdapter.getItem(0)).setName(phoneNumber);
                            ((PhoneNumberEntry) newConversationListViewAdapter.getItem(0)).setPhoneNumber(phoneNumber);
                        } else {
                            newConversationListViewAdapter.add(0, new PhoneNumberEntry(phoneNumber, phoneNumber, null, true, true));
                        }
                    }
                    newConversationListViewAdapter.notifyDataSetChanged();

                    newConversationListViewAdapter.getFilter().filter(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // Do nothing.
                }
            });
            editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    return true;
                }
            });
            editText.requestFocus();

            final ListView listView = (ListView) findViewById(R.id.list);
            listView.setAdapter(newConversationListViewAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    PhoneNumberEntry phoneNumberEntry = (PhoneNumberEntry) newConversationListViewAdapter.getItem(position);

                    String phoneNumber = phoneNumberEntry.getPhoneNumber().replaceAll("[^0-9]", "");

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
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            switch (item.getItemId()) {
                case R.id.new_conversation_action_keyboard:
                    EditText editText = (EditText) actionBar.getCustomView().findViewById(R.id.new_conversation_text);
                    if (editText.getInputType() == (InputType.TYPE_TEXT_VARIATION_PERSON_NAME)) {
                        editText.setInputType(InputType.TYPE_CLASS_PHONE);
                        item.setIcon(R.drawable.ic_keyboard_white_24dp);
                        item.setTitle(R.string.new_conversation_action_keyboard);
                    } else {
                        editText.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                        item.setIcon(R.drawable.ic_dialpad_white_24dp);
                        item.setTitle(R.string.new_conversation_action_dialpad);
                    }

                    InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    keyboard.showSoftInput(editText, 0);

                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }
}
