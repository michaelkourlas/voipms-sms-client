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

import java.util.Date;

/**
 * Provides access to the application's preferences.
 */
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
        return sharedPreferences.getString(applicationContext.getString(R.string.preferences_account_email_key), "");
    }

    public String getPassword() {
        return sharedPreferences.getString(applicationContext.getString(R.string.preferences_account_password_key), "");
    }

    public String getDid() {
        return sharedPreferences.getString(applicationContext.getString(R.string.preferences_account_did_key), "");
    }

    public void setDid(String did) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(applicationContext.getString(R.string.preferences_account_did_key), did);
        editor.apply();
    }

    public Date getStartDate() {
        long milliseconds = sharedPreferences.getLong(applicationContext.getString(
                R.string.preferences_sync_start_date_key), Long.MIN_VALUE);
        return milliseconds != Long.MIN_VALUE ? new Date(milliseconds) : new Date();
    }

    public void setStartDate(Date date) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(applicationContext.getString(R.string.preferences_sync_start_date_key), date.getTime());
        editor.apply();
    }

    public long getSyncInterval() {
        return Long.parseLong(sharedPreferences.getString(applicationContext.getString(
                R.string.preferences_sync_interval_key), "0"));
    }

    public long getLastCompleteSyncTime() {
        return sharedPreferences.getLong(applicationContext.getString(
                R.string.preferences_sync_last_complete_time_key), 0);
    }

    public void setLastCompleteSyncTime(long lastCompleteSyncTime) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(applicationContext.getString(R.string.preferences_sync_last_complete_time_key),
                lastCompleteSyncTime);
        editor.apply();
    }

    public boolean getRetrieveOnlyRecentMessages() {
        return sharedPreferences.getBoolean(applicationContext.getString(R.string.preferences_sync_retrieve_only_recent_messages_key), true);
    }

    public boolean getRetrieveDeletedMessages() {
        return sharedPreferences.getBoolean(applicationContext.getString(R.string.preferences_sync_retrieve_deleted_messages_key), false);
    }

    public boolean getPropagateLocalDeletions() {
        return sharedPreferences.getBoolean(applicationContext.getString(R.string.preferences_sync_propagate_local_deletions_key), true);
    }

    public boolean getPropagateRemoteDeletions() {
        return sharedPreferences.getBoolean(applicationContext.getString(R.string.preferences_sync_propagate_remote_deletions_key), false);
    }

    public boolean getNotificationsEnabled() {
        return sharedPreferences.getBoolean(applicationContext.getString(
                R.string.preferences_notifications_enable_key), false);
    }

    public String getGcmInstanceId() {
        return sharedPreferences.getString(applicationContext.getString(R.string.preferences_gcm_instance_id_key), "");
    }

    public void setGcmInstanceId(String instanceId) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(applicationContext.getString(R.string.preferences_gcm_instance_id_key), instanceId);
        editor.apply();
    }

    public String getGcmToken() {
        return sharedPreferences.getString(applicationContext.getString(R.string.preferences_gcm_token_key), "");
    }

    public void setGcmToken(String gcmToken) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(applicationContext.getString(R.string.preferences_gcm_token_key), gcmToken);
        editor.apply();
    }

    public String getNotificationSound() {
        return sharedPreferences.getString(applicationContext.getString(R.string.preferences_notifications_sound_key),
                applicationContext.getResources().getString(
                        R.string.preferences_notifications_sound_default_value));
    }

    public boolean getNotificationVibrateEnabled() {
        return sharedPreferences.getBoolean(applicationContext.getString(
                R.string.preferences_notifications_vibrate_key), true);
    }
}