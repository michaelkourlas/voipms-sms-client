/*
 * VoIP.ms SMS
 * Copyright (C) 2017 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences

import android.content.Context
import android.preference.PreferenceManager
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.subscribeToDidTopics
import java.util.*

fun getConnectTimeout(context: Context): Int {
    return getStringPreference(
        context,
        context.getString(R.string.preferences_network_connect_timeout_key),
        context.getString(
            R.string.preferences_network_connect_timeout_default_value))
               .toIntOrNull() ?: 0
}

fun getDids(context: Context): Set<String> {
    val did = getStringPreference(context, context.getString(
        R.string.preferences_account_did_key), "")
    val default: Set<String>
    if (did != "") {
        default = setOf(did)
    } else {
        default = emptySet()
    }
    return getStringSetPreference(context, context.getString(
        R.string.preferences_account_dids_key), default)
}

fun getEmail(context: Context): String {
    return getStringPreference(context, context.getString(
        R.string.preferences_account_email_key), "")
}

fun getLastCompleteSyncTime(context: Context): Long {
    return getLongPreference(context, context.getString(
        R.string.preferences_sync_last_complete_time_key), 0)
}

fun getNotificationsEnabled(context: Context): Boolean {
    return getBooleanPreference(
        context,
        context.getString(R.string.preferences_notifications_enable_key),
        context.getString(
            R.string.preferences_notifications_enable_default_value)
            .toBoolean())
}

fun getNotificationVibrateEnabled(context: Context): Boolean {
    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_notifications_vibrate_key),
        context.getString(
            R.string.preferences_notifications_vibrate_default_value)
            .toBoolean())
}

fun getNotificationSound(context: Context): String {
    return getStringPreference(
        context,
        context.getString(
            R.string.preferences_notifications_sound_key),
        context.getString(
            R.string.preferences_notifications_sound_default_value))
}

fun getPassword(context: Context): String {
    return getStringPreference(context, context.getString(
        R.string.preferences_account_password_key), "")
}

fun getRetrieveDeletedMessages(context: Context): Boolean {
    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_sync_retrieve_deleted_messages_key),
        context.getString(
            R.string.preferences_sync_retrieve_deleted_messages_default_value)
            .toBoolean())
}

fun getRetrieveOnlyRecentMessages(context: Context): Boolean {
    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_sync_retrieve_only_recent_messages_key),
        context.getString(
            R.string.preferences_sync_retrieve_only_recent_messages_default_value)
            .toBoolean())
}

fun getSetupCompletedForVersion(context: Context): Long {
    return getLongPreference(
        context,
        context.getString(R.string.preferences_setup_completed_for_version_key),
        context.getString(
            R.string.preferences_setup_completed_for_version_default_value)
            .toLong())
}

fun getStartDate(context: Context): Date {
    val milliseconds = getLongPreference(context, context.getString(
        R.string.preferences_sync_start_date_key), Long.MIN_VALUE)
    return if (milliseconds != java.lang.Long.MIN_VALUE) {
        Date(milliseconds)
    } else {
        Date()
    }
}

fun getSyncInterval(context: Context): Double {
    return getStringPreference(context, context.getString(
        R.string.preferences_sync_interval_key), "0").toDouble()
}


fun getReadTimeout(context: Context): Int {
    return getStringPreference(
        context,
        context.getString(R.string.preferences_network_read_timeout_key),
        context.getString(
            R.string.preferences_network_read_timeout_default_value))
               .toIntOrNull() ?: 0
}

fun isAccountActive(context: Context): Boolean {
    return getEmail(context) != "" && getPassword(context) != ""
           && getDids(context).isNotEmpty()
}

fun setDids(context: Context, dids: Set<String>) {
    setStringSetPreference(context, context.getString(
        R.string.preferences_account_dids_key), dids)
    subscribeToDidTopics(context)
}

fun setLastCompleteSyncTime(context: Context, lastCompleteSyncTime: Long) {
    setLongPreference(context, context.getString(
        R.string.preferences_sync_last_complete_time_key), lastCompleteSyncTime)
}

fun setSetupCompletedForVersion(context: Context, version: Long) {
    if (getLongPreference(context, context.getString(
        R.string.preferences_setup_completed_for_version_key), 0) < version) {
        setLongPreference(
            context,
            context.getString(
                R.string.preferences_setup_completed_for_version_key),
            version)
    }
}

fun setStartDate(context: Context, date: Date) {
    setLongPreference(context, context.getString(
        R.string.preferences_sync_start_date_key), date.time)
}

private fun getBooleanPreference(context: Context, key: String,
                                 default: Boolean): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext)
    return sharedPreferences.getBoolean(key, default)
}

private fun getLongPreference(context: Context, key: String,
                              default: Long): Long {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext)
    return sharedPreferences.getLong(key, default)
}

private fun getStringPreference(context: Context, key: String,
                                default: String): String {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext)
    return sharedPreferences.getString(key, default)
}

private fun getStringSetPreference(context: Context, key: String,
                                   default: Set<String>): Set<String> {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext)
    return sharedPreferences.getStringSet(key, default)
}

private fun setLongPreference(context: Context, key: String, value: Long) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext)
    val editor = sharedPreferences.edit()
    with(editor) {
        putLong(key, value)
        apply()
    }
}

private fun setStringSetPreference(context: Context, key: String,
                                   value: Set<String>) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext)
    val editor = sharedPreferences.edit()
    with(editor) {
        putStringSet(key, value)
        apply()
    }
}
