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
import net.kourlas.voipms_sms.notifications.Gcm;

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

                        Gcm.getInstance(getActivity().getApplicationContext()).registerForGcm(getActivity());
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