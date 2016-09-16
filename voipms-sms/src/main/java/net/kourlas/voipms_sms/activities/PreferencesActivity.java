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

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.*;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.notifications.Notifications;
import net.kourlas.voipms_sms.notifications.PushNotifications;
import net.kourlas.voipms_sms.preferences.DidPreference;
import net.kourlas.voipms_sms.preferences.Preferences;
import net.kourlas.voipms_sms.preferences.StartDatePreference;
import net.kourlas.voipms_sms.receivers.SynchronizationIntervalReceiver;
import net.kourlas.voipms_sms.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class PreferencesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preferences);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        ViewCompat.setElevation(toolbar, getResources()
            .getDimension(R.dimen.toolbar_elevation));
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        PreferenceFragment fragment = new PreferencesFragment();
        getFragmentManager().beginTransaction().replace(
            R.id.preference_fragment_content, fragment).commit();
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

    /**
     * A fragment is used only because PreferenceActivity is deprecated.
     */
    public static class PreferencesFragment
        extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        Context applicationContext;
        Database database;
        Preferences preferences;
        Notifications notifications;
        PushNotifications pushNotifications;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            getPreferenceScreen().getSharedPreferences()
                                 .registerOnSharedPreferenceChangeListener(
                                     this);

            applicationContext = getActivity().getApplicationContext();
            database = Database.getInstance(applicationContext);
            preferences = Preferences.getInstance(applicationContext);
            notifications = Notifications.getInstance(applicationContext);
            pushNotifications = PushNotifications
                .getInstance(applicationContext);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                             @NonNull Preference preference)
        {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        @Override
        public void onResume() {
            super.onResume();

            // Update summary text for all preferences
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i)
            {
                Preference preference = getPreferenceScreen().getPreference(i);
                if (preference instanceof PreferenceGroup) {
                    PreferenceGroup preferenceGroup =
                        (PreferenceGroup) preference;
                    for (int j = 0; j < preferenceGroup.getPreferenceCount();
                         ++j) {
                        updateSummaryTextForPreference(
                            preferenceGroup.getPreference(j));
                    }
                } else {
                    updateSummaryTextForPreference(preference);
                }
            }
        }

        private void updateSummaryTextForPreference(Preference preference) {
            if (preference instanceof DidPreference) {
                DidPreference didPreference = (DidPreference) preference;
                didPreference.setSummary(
                    Utils.getFormattedPhoneNumber(preferences.getDid()));
            }
            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                listPreference.setSummary(listPreference.getEntry());
            }
            // Display email address as summary text for email address setting
            else if (preference instanceof EditTextPreference) {
                EditTextPreference editTextPreference =
                    (EditTextPreference) preference;
                if (editTextPreference.getKey().equals(
                    getString(R.string.preferences_account_password_key)))
                {
                    if (!editTextPreference.getText().equals("")) {
                        editTextPreference
                            .setSummary(applicationContext.getString(
                                R.string
                                    .preferences_account_password_placeholder));
                    } else {
                        editTextPreference.setSummary("");
                    }
                } else {
                    editTextPreference.setSummary(editTextPreference.getText());
                }
            }
            // Display selected notification sound as summary text for
            // notification setting
            else if (preference instanceof RingtonePreference) {
                RingtonePreference ringtonePreference =
                    (RingtonePreference) preference;
                String notificationSound =
                    Preferences.getInstance(
                        getActivity().getApplicationContext())
                               .getNotificationSound();
                if (notificationSound.equals("")) {
                    ringtonePreference.setSummary("None");
                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                        getActivity(), Uri.parse(notificationSound));
                    if (ringtone != null) {
                        ringtonePreference.setSummary(ringtone.getTitle(
                            getActivity()));
                    } else {
                        ringtonePreference.setSummary("Unknown ringtone");
                    }
                }
            } else if (preference instanceof StartDatePreference) {
                StartDatePreference datePreference =
                    (StartDatePreference) preference;
                SimpleDateFormat sdf =
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                datePreference
                    .setSummary(sdf.format(preferences.getStartDate()));
            }
        }

        @Override
        public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key)
        {
            // This check shouldn't be necessary, but it apparently is...
            if (isAdded()) {
                // Update summary text for changed preference
                updateSummaryTextForPreference(findPreference(key));

                if (key
                    .equals(getString(R.string.preferences_sync_interval_key)))
                {
                    SynchronizationIntervalReceiver
                        .setupSynchronizationInterval(applicationContext);
                }
            }
        }
    }
}