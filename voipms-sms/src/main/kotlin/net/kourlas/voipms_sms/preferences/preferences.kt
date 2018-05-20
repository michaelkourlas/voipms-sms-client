/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2018 Michael Kourlas
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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

fun getConnectTimeout(context: Context): Int =
    getStringPreference(
        context,
        context.getString(R.string.preferences_network_connect_timeout_key),
        context.getString(
            R.string.preferences_network_connect_timeout_default_value))
        .toIntOrNull() ?: 0

fun getDids(context: Context,
            onlyShowInConversationsView: Boolean = false,
            onlyRetrieveMessages: Boolean = false,
            onlyShowNotifications: Boolean = false): Set<String> {
    val did = getStringPreference(context, context.getString(
        R.string.preferences_dids_did_key), "")
    val default: Set<String> = if (did != "") {
        setOf(did)
    } else {
        emptySet()
    }
    var set = getStringSetPreference(context, context.getString(
        R.string.preferences_dids_key), default)
    if (onlyShowInConversationsView) {
        set = set.filter { getDidShowInConversationsView(context, it) }.toSet()
    }
    if (onlyRetrieveMessages) {
        set = set.filter { getDidRetrieveMessages(context, it) }.toSet()
    }
    if (onlyShowNotifications) {
        set = set.filter { getDidShowNotifications(context, it) }.toSet()
    }
    return set
}

fun getEmail(context: Context): String =
    getStringPreference(context,
                        context.getString(
                            R.string.preferences_account_email_key),
                        "")

fun getLastCompleteSyncTime(context: Context): Long =
    getLongPreference(context,
                      context.getString(
                          R.string.preferences_sync_last_complete_time_key),
                      0)

@Deprecated(
    "Remove when Android versions earlier than Oreo are no longer supported.")
fun getNotificationsEnabled(context: Context): Boolean =
    getBooleanPreference(
        context,
        context.getString(R.string.preferences_notifications_enable_key),
        context.getString(
            R.string.preferences_notifications_enable_default_value)!!
            .toBoolean())

@Deprecated(
    "Remove when Android versions earlier than Oreo are no longer supported.")
fun getNotificationVibrateEnabled(context: Context): Boolean =
    getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_notifications_vibrate_key),
        context.getString(
            R.string.preferences_notifications_vibrate_default_value)!!
            .toBoolean())

@Deprecated(
    "Remove when Android versions earlier than Oreo are no longer supported.")
fun getNotificationSound(context: Context): String =
    getStringPreference(
        context,
        context.getString(
            R.string.preferences_notifications_sound_key),
        context.getString(
            R.string.preferences_notifications_sound_default_value))

fun getPassword(context: Context): String =
    getStringPreference(context,
                        context.getString(
                            R.string.preferences_account_password_key),
                        "")

fun getRetrieveDeletedMessages(context: Context): Boolean =
    getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_sync_retrieve_deleted_messages_key),
        context.getString(
            R.string.preferences_sync_retrieve_deleted_messages_default_value)!!
            .toBoolean())

fun getRetrieveOnlyRecentMessages(context: Context): Boolean =
    getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_sync_retrieve_only_recent_messages_key),
        context.getString(
            R.string.preferences_sync_retrieve_only_recent_messages_default_value)!!
            .toBoolean())

fun getStartDate(context: Context): Date {
    return try {
        try {
            val dateString = getStringPreference(context, context.getString(
                R.string.preferences_sync_start_date_key), "")
            if (dateString != "") {
                val sdf = SimpleDateFormat("MM/dd/YYYY", Locale.US)
                sdf.parse(dateString)
            } else {
                Date()
            }
        } catch (_: ParseException) {
            Date()
        }
    } catch (_: ClassCastException) {
        val milliseconds = getLongPreference(context, context.getString(
            R.string.preferences_sync_start_date_key), Long.MIN_VALUE)
        if (milliseconds != java.lang.Long.MIN_VALUE) {
            Date(milliseconds)
        } else {
            Date()
        }
    }
}

fun getSyncInterval(context: Context): Double =
    getStringPreference(context,
                        context.getString(
                            R.string.preferences_sync_interval_key),
                        "0").toDouble()


fun getReadTimeout(context: Context): Int =
    getStringPreference(
        context,
        context.getString(R.string.preferences_network_read_timeout_key),
        context.getString(
            R.string.preferences_network_read_timeout_default_value))
        .toIntOrNull() ?: 0

fun isAccountActive(context: Context): Boolean =
    getEmail(context) != ""
    && getPassword(context) != ""
    && getDids(context).isNotEmpty()

fun setDids(context: Context, dids: Set<String>) {
    val currentDids = getDids(context)
    val newDids = dids.filter { it -> it !in currentDids }

    setStringSetPreference(context, context.getString(
        R.string.preferences_dids_key), dids)
    for (did in newDids) {
        setDidShowInConversationsView(context, did, true)
        setDidRetrieveMessages(context, did, true)
        setDidShowNotifications(context, did, true)
    }
}

fun setDidShowInConversationsView(context: Context, did: String,
                                  value: Boolean) {
    setBooleanPreference(context,
                         context.getString(
                             R.string.preferences_did_show_in_conversations_view,
                             did),
                         value)
}

fun getDidShowInConversationsView(context: Context, did: String): Boolean {
    if (did !in getDids(context)) {
        return false
    }

    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_show_in_conversations_view,
            did),
        true)
}

fun setDidRetrieveMessages(context: Context, did: String, value: Boolean) {
    setBooleanPreference(context,
                         context.getString(
                             R.string.preferences_did_retrieve_messages, did),
                         value)
}

fun getDidRetrieveMessages(context: Context, did: String): Boolean {
    if (did !in getDids(context)) {
        return false
    }

    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_retrieve_messages, did),
        true)
}

fun setDidShowNotifications(context: Context, did: String, value: Boolean) {
    setBooleanPreference(context, context.getString(
        R.string.preferences_did_show_notifications, did), value)
}

fun getDidShowNotifications(context: Context, did: String): Boolean {
    if (did !in getDids(context)) {
        return false
    }

    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_show_notifications, did),
        true)
}

fun setLastCompleteSyncTime(context: Context,
                            lastCompleteSyncTime: Long) =
    setLongPreference(
        context, context.getString(
        R.string.preferences_sync_last_complete_time_key), lastCompleteSyncTime)

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

private fun setBooleanPreference(context: Context, key: String,
                                 value: Boolean) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext)
    val editor = sharedPreferences.edit()
    with(editor) {
        putBoolean(key, value)
        apply()
    }
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
