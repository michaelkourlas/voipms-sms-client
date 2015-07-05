/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas and other contributors
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.*;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import net.kourlas.voipms_sms.*;
import net.kourlas.voipms_sms.gcm.Gcm;
import net.kourlas.voipms_sms.preferences.DidPreference;
import net.kourlas.voipms_sms.preferences.StartDatePreference;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class PreferencesActivity extends AppCompatActivity {
    PreferenceFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preferences);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources().getDimension(R.dimen.toolbar_elevation));
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        fragment = new PreferencesFragment();
        getFragmentManager().beginTransaction().replace(R.id.preference_fragment_content, fragment).commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityMonitor.getInstance().setCurrentActivity(this);
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

    public void showSelectDidDialog(boolean success, final String[] dids, String message) {
        DidPreference preference = (DidPreference) fragment.getPreferenceManager().findPreference("did");
        preference.showSelectDidDialog(success, dids, message);
    }

    /**
     * A fragment is used only because PreferenceActivity is deprecated.
     */
    public static class PreferencesFragment
            extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        Context applicationContext;
        Database database;
        Preferences preferences;
        Gcm gcm;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            applicationContext = getActivity().getApplicationContext();
            database = Database.getInstance(applicationContext);
            preferences = Preferences.getInstance(applicationContext);
            gcm = Gcm.getInstance(applicationContext);
        }

        @Override
        public void onResume() {
            super.onResume();

            // Update summary text for all preferences
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
                Preference preference = getPreferenceScreen().getPreference(i);
                if (preference instanceof PreferenceGroup) {
                    PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                    for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                        updateSummaryTextForPreference(preferenceGroup.getPreference(j));
                    }
                }
                else {
                    updateSummaryTextForPreference(preference);
                }
            }
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, @NonNull Preference preference) {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // This check shouldn't be necessary, but it apparently is...
            if (isAdded()) {
                // Update summary text for changed preference
                updateSummaryTextForPreference(findPreference(key));

                // Clear the SMS cache when messages are invalidated by account or synchronization changes
                if (key.equals(applicationContext.getString(R.string.preferences_account_email_key)) ||
                        key.equals(applicationContext.getString(R.string.preferences_account_password_key))
                    // key.equals(applicationContext.getString(R.string.preferences_sms_days_to_sync_key)) ||
                    // TODO: FIX
                        ) {
                    database.deleteAllMessages();
                }
                // Show informational message and attempt to register for GCM if notificatons are enabled
                else if (key.equals(applicationContext.getString(R.string.preferences_notifications_enable_key)) && preferences.getNotificationsEnabled()) {
                    // Notifications are not yet enabled, so the check above is the inverse of what one might expect
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(getResources().getString(R.string.preferences_notifications_enable_dialog_text));
                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            gcm.registerForGcm(getActivity(), true, true);
                        }
                    });
                    builder.show();
                }
            }
        }

        private void updateSummaryTextForPreference(Preference preference) {
            if (preference instanceof DidPreference) {
                DidPreference didPreference = (DidPreference) preference;
                didPreference.setSummary(Utils.getFormattedPhoneNumber(preferences.getDid()));
            }
            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                listPreference.setSummary(listPreference.getEntry());
            }
            // Display email address as summary text for email address setting
            else if (preference instanceof EditTextPreference) {
                EditTextPreference editTextPreference = (EditTextPreference) preference;
                if (editTextPreference.getKey().equals(getString(R.string.preferences_account_password_key))) {
                    if (!editTextPreference.getText().equals("")) {
                        editTextPreference.setSummary("********");
                    }
                    else {
                        editTextPreference.setSummary("");
                    }
                }
                else {
                    editTextPreference.setSummary(editTextPreference.getText());
                }
            }
            // Display selected notification sound as summary text for notification setting
            else if (preference instanceof RingtonePreference) {
                RingtonePreference ringtonePreference = (RingtonePreference) preference;
                Ringtone ringtone = RingtoneManager.getRingtone(getActivity(), Uri.parse(Preferences.getInstance(
                        getActivity().getApplicationContext()).getNotificationSound()));
                ringtonePreference.setSummary(ringtone.getTitle(getActivity()));
            }
            else if (preference instanceof StartDatePreference) {
                StartDatePreference datePreference = (StartDatePreference) preference;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                datePreference.setSummary(sdf.format(preferences.getStartDate()));
            }
        }
    }
}