/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2020 Michael Kourlas
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
import androidx.preference.PreferenceManager
import de.adorsys.android.securestoragelibrary.SecurePreferences
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.subscribeToDidTopics
import java.text.SimpleDateFormat
import java.util.*

private val securePreferencesLock = Object()

fun firstRun(context: Context): Boolean = getBooleanPreference(
    context,
    context.getString(
        R.string.preferences_first_run
    ),
    true
)

fun accountConfigured(context: Context): Boolean =
    getEmail(context) != ""
        && getPassword(context) != ""

fun didsConfigured(context: Context): Boolean = getDids(context).isNotEmpty()

fun getActiveDid(context: Context): String =
    getStringPreference(
        context,
        context.getString(
            R.string.preferences_dids_active_did_key
        ),
        ""
    )

fun getAppTheme(context: Context): String =
    getStringPreference(
        context,
        context.getString(R.string.preferences_theme_key),
        context.getString(R.string.preferences_theme_default_value)
    )

fun getConnectTimeout(context: Context): Int =
    getStringPreference(
        context,
        context.getString(R.string.preferences_network_connect_timeout_key),
        context.getString(
            R.string.preferences_network_connect_timeout_default_value
        )
    )
        .toIntOrNull() ?: 15

fun getDids(
    context: Context,
    onlyShowInConversationsView: Boolean = false,
    onlyRetrieveMessages: Boolean = false,
    onlyShowNotifications: Boolean = false
): Set<String> {
    val did = getStringPreference(
        context, context.getString(
            R.string.preferences_dids_did_key
        ), ""
    )
    val default: Set<String> = if (did != "") {
        setOf(did)
    } else {
        emptySet()
    }
    var set = getStringSetPreference(
        context, context.getString(
            R.string.preferences_dids_key
        ), default
    )
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

fun getEmail(context: Context): String {
    val email = getSecureStringPreference(
        context,
        context.getString(R.string.preferences_account_email_key),
        null
    ) ?: ""
    if (email == "") {
        val emailAtOldStorageLocation = getStringPreference(
            context,
            context.getString(R.string.preferences_account_email_key),
            ""
        )
        if (emailAtOldStorageLocation != "") {
            // If the email is present at the old storage location, move it to
            // the secure preferences (which use the Android keystore)
            setEmail(context, emailAtOldStorageLocation)
            removePreference(
                context, context.getString(
                    R.string.preferences_account_email_key
                )
            )
        }
        return emailAtOldStorageLocation
    }
    return email
}

fun getFirstSyncAfterSignIn(context: Context): Boolean =
    getBooleanPreference(
        context,
        context.getString(R.string.preferences_first_sync_after_sign_in_key),
        false
    )

fun getMessageTextBoxMaximumSize(context: Context): Int =
    getStringPreference(
        context,
        context.getString(
            R.string.preferences_message_text_box_maximum_size_key
        ),
        context.getString(
            R.string.preferences_message_text_box_maximum_size_default_value
        )
    )
        .toIntOrNull() ?: 0

@Deprecated(
    "Remove when Android versions earlier than Oreo are no longer supported."
)
fun getNotificationsEnabled(context: Context): Boolean {
    val value: String = context.getString(
        R.string.preferences_notifications_enable_default_value
    )
    return getBooleanPreference(
        context,
        context.getString(R.string.preferences_notifications_enable_key),
        value.toBoolean()
    )
}

@Deprecated(
    "Remove when Android versions earlier than Oreo are no longer supported."
)
fun getNotificationVibrateEnabled(context: Context): Boolean {
    val value: String = context.getString(
        R.string.preferences_notifications_vibrate_default_value
    )
    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_notifications_vibrate_key
        ),
        value.toBoolean()
    )
}

@Deprecated(
    "Remove when Android versions earlier than Oreo are no longer supported."
)
fun getNotificationSound(context: Context): String =
    getStringPreference(
        context,
        context.getString(
            R.string.preferences_notifications_sound_key
        ),
        context.getString(
            R.string.preferences_notifications_sound_default_value
        )
    )

fun getPassword(context: Context): String {
    val password = getSecureStringPreference(
        context,
        context.getString(R.string.preferences_account_password_key),
        null
    ) ?: ""
    if (password == "") {
        val passwordAtOldStorageLocation = getStringPreference(
            context,
            context.getString(R.string.preferences_account_password_key),
            ""
        )
        if (passwordAtOldStorageLocation != "") {
            // If the password is present at the old storage location, move it
            // to the secure preferences (which use the Android keystore)
            setPassword(context, passwordAtOldStorageLocation)
            removePreference(
                context, context.getString(
                    R.string.preferences_account_password_key
                )
            )
        }
        return passwordAtOldStorageLocation
    }
    return password
}

fun getReadTimeout(context: Context): Int =
    getStringPreference(
        context,
        context.getString(R.string.preferences_network_read_timeout_key),
        context.getString(
            R.string.preferences_network_read_timeout_default_value
        )
    )
        .toIntOrNull() ?: 15

fun getRetrieveDeletedMessages(context: Context): Boolean {
    val value: String = context.getString(
        R.string.preferences_sync_retrieve_deleted_messages_default_value
    )
    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_sync_retrieve_deleted_messages_key
        ),
        value.toBoolean()
    )
}

fun getRetrieveOnlyRecentMessages(context: Context): Boolean {
    val value: String = context.getString(
        R.string.preferences_sync_retrieve_only_recent_messages_default_value
    )
    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_sync_retrieve_only_recent_messages_key
        ),
        value.toBoolean()
    )
}

fun getSetupCompletedForVersion(context: Context): Long =
    getLongPreference(
        context,
        context.getString(R.string.preferences_setup_completed_for_version_key),
        context.getString(
            R.string.preferences_setup_completed_for_version_default_value
        )
            .toLong()
    )

fun getStartDate(context: Context): Date {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -30)

    try {
        return try {
            val dateString = getStringPreference(
                context, context.getString(
                    R.string.preferences_sync_start_date_key
                ), ""
            )
            if (dateString != "") {
                val sdf = SimpleDateFormat("MM/dd/yyyy", Locale.US)
                sdf.parse(dateString) ?: throw Exception(
                    "Failed to parse date $dateString"
                )
            } else {
                setStartDate(context, calendar.time)
                calendar.time
            }
        } catch (_: Exception) {
            setStartDate(context, calendar.time)
            calendar.time
        }
    } catch (_: ClassCastException) {
        val milliseconds = getLongPreference(
            context, context.getString(
                R.string.preferences_sync_start_date_key
            ), Long.MIN_VALUE
        )
        return if (milliseconds != java.lang.Long.MIN_VALUE) {
            val date = Date(milliseconds)
            setStartDate(context, date)
            date
        } else {
            setStartDate(context, calendar.time)
            calendar.time
        }
    }
}

fun getSyncInterval(context: Context): Double =
    getStringPreference(
        context,
        context.getString(
            R.string.preferences_sync_interval_key
        ),
        "0"
    ).toDouble()

fun setFirstRun(context: Context, firstRun: Boolean) {
    setBooleanPreference(
        context,
        context.getString(R.string.preferences_first_run),
        firstRun
    )
}

fun setActiveDid(context: Context, did: String) {
    setStringPreference(
        context, context.getString(
            R.string.preferences_dids_active_did_key
        ), did
    )
}

fun setDids(context: Context, dids: Set<String>) {
    val currentDids = getDids(context)
    val newDids = dids.filter { it !in currentDids }

    setStringSetPreference(
        context, context.getString(
            R.string.preferences_dids_key
        ), dids
    )
    for (did in newDids) {
        setDidShowInConversationsView(context, did, true)
        setDidRetrieveMessages(context, did, true)
        setDidShowNotifications(context, did, true)
    }

    subscribeToDidTopics(context)
}

fun setDidRetrieveMessages(context: Context, did: String, value: Boolean) {
    setBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_retrieve_messages_key,
            did
        ),
        value
    )
}

fun getDidRetrieveMessages(context: Context, did: String): Boolean {
    if (did !in getDids(context)) {
        return false
    }

    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_retrieve_messages_key, did
        ),
        true
    )
}

fun setDidShowInConversationsView(
    context: Context, did: String,
    value: Boolean
) {
    setBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_show_in_conversations_view_key,
            did
        ),
        value
    )
}

fun getDidShowInConversationsView(context: Context, did: String): Boolean {
    if (did !in getDids(context)) {
        return false
    }

    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_show_in_conversations_view_key,
            did
        ),
        true
    )
}

fun setDidShowNotifications(context: Context, did: String, value: Boolean) {
    setBooleanPreference(
        context, context.getString(
            R.string.preferences_did_show_notifications_key, did
        ), value
    )
}

fun getDidShowNotifications(context: Context, did: String): Boolean {
    if (did !in getDids(context)) {
        return false
    }

    return getBooleanPreference(
        context,
        context.getString(
            R.string.preferences_did_show_notifications_key, did
        ),
        true
    )
}

fun setEmail(context: Context, email: String) {
    setSecureStringPreference(
        context, context.getString(
            R.string.preferences_account_email_key
        ), email
    )
}

fun setFirstSyncAfterSignIn(context: Context, firstSyncAfterSignIn: Boolean) {
    setBooleanPreference(
        context,
        context.getString(R.string.preferences_first_sync_after_sign_in_key),
        firstSyncAfterSignIn
    )
}

fun setSetupCompletedForVersion(context: Context, version: Long) {
    if (getLongPreference(
            context,
            context.getString(
                R.string.preferences_setup_completed_for_version_key
            ),
            0
        ) < version
    ) {
        setLongPreference(
            context,
            context.getString(
                R.string.preferences_setup_completed_for_version_key
            ),
            version
        )
    }
}

fun setPassword(context: Context, password: String) {
    setSecureStringPreference(
        context, context.getString(
            R.string.preferences_account_password_key
        ), password
    )
}

fun setStartDate(context: Context, date: Date) {
    setStringPreference(
        context,
        context.getString(R.string.preferences_sync_start_date_key),
        SimpleDateFormat("MM/dd/yyyy", Locale.US).format(date)
    )
}

fun setRawSyncInterval(context: Context, string: String) {
    setStringPreference(
        context, context.getString(
            R.string.preferences_sync_interval_key
        ), string
    )
}

@Suppress("SameParameterValue")
private fun getSecureStringPreference(
    context: Context, key: String,
    default: String?
): String? {
    synchronized(securePreferencesLock) {
        return SecurePreferences.getStringValue(context, key, default)
    }
}

private fun getBooleanPreference(
    context: Context, key: String,
    default: Boolean
): Boolean {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    return sharedPreferences.getBoolean(key, default)
}

private fun getLongPreference(
    context: Context, key: String,
    default: Long
): Long {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    return sharedPreferences.getLong(key, default)
}

private fun getStringPreference(
    context: Context, key: String,
    default: String
): String {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    return sharedPreferences.getString(key, null) ?: default
}

private fun getStringSetPreference(
    context: Context, key: String,
    default: Set<String>
): Set<String> {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    return sharedPreferences.getStringSet(key, null) ?: default
}

private fun setBooleanPreference(
    context: Context, key: String,
    value: Boolean
) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    val editor = sharedPreferences.edit()
    with(editor) {
        putBoolean(key, value)
        apply()
    }
}

private fun setSecureStringPreference(
    context: Context, key: String,
    value: String
) {
    synchronized(securePreferencesLock) {
        SecurePreferences.setValue(context, key, value)
    }
}

private fun setStringPreference(
    context: Context, key: String,
    value: String
) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    val editor = sharedPreferences.edit()
    with(editor) {
        putString(key, value)
        apply()
    }
}

private fun setLongPreference(context: Context, key: String, value: Long) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    val editor = sharedPreferences.edit()
    with(editor) {
        putLong(key, value)
        apply()
    }
}

private fun setStringSetPreference(
    context: Context, key: String,
    value: Set<String>
) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    val editor = sharedPreferences.edit()
    with(editor) {
        putStringSet(key, value)
        apply()
    }
}

fun removePreference(context: Context, key: String) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    )
    val editor = sharedPreferences.edit()
    with(editor) {
        editor.remove(key)
        apply()
    }
}
