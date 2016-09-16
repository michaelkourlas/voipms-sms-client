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

import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.utils.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EditDatabaseActivity extends AppCompatActivity {
    private Database database;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_database);

        database = Database.getInstance(getApplicationContext());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources()
            .getDimension(R.dimen.toolbar_elevation));
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        List<Message> messages = database.getAllMessages();
        JSONArray messagesJsonArray = new JSONArray();
        for (Message message : messages) {
            messagesJsonArray.put(message.toJSON());
        }

        try {
            String databaseString = messagesJsonArray.toString(2);
            EditText editText = (EditText) findViewById(R.id.database_text);
            editText.setText(databaseString);
        } catch (Exception ex) {
            Utils.showInfoDialog(this, getString(
                R.string.preferences_database_edit_failure_load));
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_database, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            switch (item.getItemId()) {
                case R.id.save_button:
                    List<Message> messages = new ArrayList<>();
                    try {
                        EditText editText =
                            (EditText) findViewById(R.id.database_text);
                        JSONArray databaseJsonArray =
                            new JSONArray(editText.getText().toString());
                        for (int i = 0; i < databaseJsonArray.length(); i++) {
                            JSONObject messageJsonObject =
                                databaseJsonArray.getJSONObject(i);
                            Message message = new Message(messageJsonObject
                                                              .optLong(
                                                                  Database
                                                                      .COLUMN_DATABASE_ID),
                                                          messageJsonObject
                                                              .optLong(
                                                                  Database
                                                                      .COLUMN_VOIP_ID),
                                                          messageJsonObject
                                                              .getLong(
                                                                  Database
                                                                      .COLUMN_DATE),
                                                          messageJsonObject
                                                              .getLong(
                                                                  Database
                                                                      .COLUMN_TYPE),
                                                          messageJsonObject
                                                              .getString(
                                                                  Database
                                                                      .COLUMN_DID),
                                                          messageJsonObject
                                                              .getString(
                                                                  Database
                                                                      .COLUMN_CONTACT),
                                                          messageJsonObject
                                                              .getString(
                                                                  Database
                                                                      .COLUMN_MESSAGE),
                                                          messageJsonObject
                                                              .getLong(
                                                                  Database
                                                                      .COLUMN_UNREAD),
                                                          messageJsonObject
                                                              .getLong(
                                                                  Database
                                                                      .COLUMN_DELETED),
                                                          messageJsonObject
                                                              .getLong(
                                                                  Database
                                                                      .COLUMN_DELIVERED),
                                                          messageJsonObject
                                                              .getLong(
                                                                  Database
                                                                      .COLUMN_DELIVERY_IN_PROGRESS),
                                                          messageJsonObject
                                                              .getLong(
                                                                  Database
                                                                      .COLUMN_DRAFT));
                            messages.add(message);
                        }
                    } catch (Exception ex) {
                        Utils.showInfoDialog(this, getString(
                            R.string.preferences_database_edit_failure_save));
                        return false;
                    }

                    database.removeAllMessages();
                    for (Message message : messages) {
                        database.insertMessage(message);
                    }
                    finish();
                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }
}
