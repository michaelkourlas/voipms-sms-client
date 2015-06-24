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

package net.kourlas.voipms_sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class Preferences {
    private static Preferences instance;
    private final Context applicationContext;
    private final SharedPreferences sharedPreferences;

    private Preferences(Context applicationContext) {
        this.applicationContext = applicationContext;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
    }

    public static Preferences getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Preferences(applicationContext);
        }
        return instance;
    }

    public String getEmail() {
        return sharedPreferences.getString("api_email", "");
    }

    public String getPassword() {
        return sharedPreferences.getString("api_password", "");
    }

    public int getDaysToSync() {
        return Integer.parseInt(sharedPreferences.getString("sms_days_to_sync",
                applicationContext.getResources().getString(R.string.preferences_sms_days_to_sync_default_value)));
    }

    public long getLastSync() {

        Calendar calendarThen = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendarThen.add(Calendar.DAY_OF_YEAR, -getDaysToSync());
        long then = calendarThen.getTimeInMillis();
        return sharedPreferences.getLong("last_sync",then); //milliseconds
    }

    // Last time the app performed an automatic sync (usually via GCM)
    public void setLastSync(long date) {

        // It's saved in milliseconds
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("last_sync", date);
        editor.apply();
    }

    public String getDid() {
        return sharedPreferences.getString("did", "");
    }

    public void setDid(String did) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("did", did);
        editor.apply();
    }

    public boolean getNotificationsEnabled() {
        return sharedPreferences.getBoolean("sms_notification", false);
    }

    public String getRegistrationId() {
        return sharedPreferences.getString("registration_id", "");
    }

    public void setRegistrationId(String registrationId) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("registration_id", registrationId);
        editor.apply();
    }

    public int getRegistrationIdVersion() {
        return sharedPreferences.getInt("registration_id_ver", Integer.MIN_VALUE);
    }

    public void setRegistrationIdVersion(int registrationId) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("registration_id_ver", registrationId);
        editor.apply();
    }

    public boolean getFirstRun() {
        return sharedPreferences.getBoolean("first_run", true);
    }

    public void setFirstRun(boolean firstRun) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("first_run", firstRun);
        editor.apply();
    }

    public String getNotificationSound() {
        return sharedPreferences.getString("sms_notification_ringtone",
                applicationContext.getResources().getString(
                        R.string.preferences_sms_notification_ringtone_default_value));
    }

    public boolean getNotificationVibrateEnabled() {
        return sharedPreferences.getBoolean("sms_notification_vibrate", true);
    }
}