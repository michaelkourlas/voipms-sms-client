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

import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.*;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import net.kourlas.voipms_sms.App;
import net.kourlas.voipms_sms.Database;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.gcm.Gcm;

public class PreferencesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.getInstance().setCurrentActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.getInstance().deleteReferenceToActivity(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.getInstance().deleteReferenceToActivity(this);
    }

    public static class PreferencesFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
                Preference preference = getPreferenceScreen().getPreference(i);
                if (preference instanceof PreferenceGroup) {
                    PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                    for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                        updatePreference(preferenceGroup.getPreference(j));
                    }
                } else {
                    updatePreference(preference);
                }
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (isAdded()) {
                updatePreference(findPreference(key));

                if (key.equals("api_email") || key.equals("api_password") ||
                        key.equals("reset")) {
                    Database.getInstance(getActivity().getApplicationContext()).deleteAllSMS();

                    if (key.equals("reset") && sharedPreferences.getBoolean("reset", false)) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean("reset", false);
                        editor.apply();
                    }
                } else if (key.equals("sms_notification")) {
                    if (sharedPreferences.getBoolean("sms_notification", false)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setMessage(getResources().getString(R.string.preferences_enable_notifications_text));
                        builder.setPositiveButton(R.string.ok, null);
                        builder.show();

                        Gcm.getInstance(getActivity().getApplicationContext()).registerForGcm(getActivity(), true);
                    }
                }
            }
        }

        private void updatePreference(Preference preference) {
            if (preference instanceof ListPreference) {
                // Display selected days to sync setting as summary text for days to sync setting
                ListPreference listPreference = (ListPreference) preference;
                listPreference.setSummary(listPreference.getEntry());
            } else if (preference instanceof EditTextPreference) {
                // Display email address as summary text for email address setting
                EditTextPreference editTextPreference = (EditTextPreference) preference;
                if ((editTextPreference.getEditText().getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) !=
                        InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                    editTextPreference.setSummary(editTextPreference.getText());
                }
            } else if (preference instanceof RingtonePreference) {
                // Display selected notification sound as summary text for notification setting
                RingtonePreference ringtonePreference = (RingtonePreference) preference;
                String ringtonePath = getPreferenceManager().getSharedPreferences().getString(
                        "sms_notification_ringtone", getResources().getString(
                                R.string.preferences_sms_notification_ringtone_default_value));
                Ringtone ringtone = RingtoneManager.getRingtone(getActivity(), Uri.parse(ringtonePath));
                ringtonePreference.setSummary(ringtone.getTitle(getActivity()));
            }
        }
    }
}