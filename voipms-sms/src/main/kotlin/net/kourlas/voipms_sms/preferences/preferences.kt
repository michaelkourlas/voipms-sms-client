/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2022 Michael Kourlas
 *
 * Portions copyright (C) 2017 adorsys GmbH & Co. KG (taken from
 * SecurePreferences.java and KeystoreTool.java)
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
import android.content.SharedPreferences
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.Base64
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.subscribeToDidTopics
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream

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

fun getEmail(context: Context): String = getSecureStringPreference(
    context,
    context.getString(R.string.preferences_account_email_key),
    ""
)

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

fun getPassword(context: Context): String = getSecureStringPreference(
    context,
    context.getString(R.string.preferences_account_password_key),
    ""
)

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

@Deprecated(
    "Remove when Android versions earlier than Oreo are no longer supported."
)
fun setNotificationSound(context: Context, uri: String) {
    setStringPreference(
        context,
        context.getString(R.string.preferences_notifications_sound_key),
        uri
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
    default: String
) = synchronized(securePreferencesLock) {
    getEncryptedSharedPreferences(context.applicationContext).getString(
        key,
        null
    ) ?: default
}

private fun getBooleanPreference(
    context: Context, key: String,
    default: Boolean
): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    ).getBoolean(key, default)
}

private fun getLongPreference(
    context: Context, key: String,
    default: Long
): Long {
    return PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    ).getLong(key, default)
}

private fun getStringPreference(
    context: Context, key: String,
    default: String
): String {
    return PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    ).getString(key, null) ?: default
}

private fun getStringSetPreference(
    context: Context, key: String,
    default: Set<String>
): Set<String> {
    return PreferenceManager.getDefaultSharedPreferences(
        context.applicationContext
    ).getStringSet(key, null) ?: default
}

private fun setBooleanPreference(
    context: Context, key: String,
    value: Boolean
) {
    PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        .edit().putBoolean(key, value).apply()
}

private fun setSecureStringPreference(
    context: Context, key: String,
    value: String
) = synchronized(securePreferencesLock) {
    getEncryptedSharedPreferences(context.applicationContext).edit()
        .putString(key, value).apply()
}

private fun setStringPreference(
    context: Context, key: String,
    value: String
) {
    PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        .edit().putString(key, value).apply()
}

private fun setLongPreference(context: Context, key: String, value: Long) {
    PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        .edit().putLong(key, value).apply()
}

private fun setStringSetPreference(
    context: Context, key: String,
    value: Set<String>
) {
    PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        .edit().putStringSet(key, value).apply()
}

private fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
    val masterKey =
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    return EncryptedSharedPreferences.create(
        context,
        context.getString(
            R.string.preferences_encrypted_file_name,
            context.packageName
        ),
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

fun removePreference(context: Context, key: String) {
    PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        .edit().remove(key).apply()
}

private const val LEGACY_KEYSTORE_TYPE = "AndroidKeyStore"
private const val LEGACY_SECURE_SHARED_PREFERENCES_NAME = "SecurePreferences"
private const val LEGACY_KEY_CIPHER_JELLYBEAN_PROVIDER = "AndroidOpenSSL"
private const val LEGACY_KEY_CIPHER_MARSHMALLOW_PROVIDER =
    "AndroidKeyStoreBCWorkaround"
private const val LEGACY_KEY_TRANSFORMATION_ALGORITHM = "RSA/ECB/PKCS1Padding"
private const val LEGACY_KEY_ALIAS = "adorsysKeyPair"

fun migrateLegacySecurePreferences(context: Context) = synchronized(
    securePreferencesLock
) {
    // These are the preferences to migrate.
    val preferenceKeys = listOf(
        context.getString(R.string.preferences_account_email_key),
        context.getString(R.string.preferences_account_password_key)
    )

    for (preferenceKey in preferenceKeys) {
        if (getEncryptedSharedPreferences(context).getString(
                preferenceKey,
                null
            ) != null
        ) {
            // Migration is not required.
            continue
        }

        // First, check whether the preference is stored in plain-text. If so,
        // migrate it to EncryptedSharedPreferences. We will remove the old
        // preference later on.
        val plainTextPreference =
            getStringPreference(context, preferenceKey, "")
        if (plainTextPreference != "") {
            getEncryptedSharedPreferences(context).edit()
                .putString(preferenceKey, plainTextPreference).apply()
            continue
        }

        // Next, check whether the preference is stored in the
        // secure-storage-android SharedPreferences. If so, migrate it to
        // EncryptedSharedPreferences. We will remove the old preference later
        // on.
        try {
            // Load the private key used by secure-storage-android from the
            // keystore.
            val keyStore = KeyStore.getInstance(LEGACY_KEYSTORE_TYPE)
            keyStore.load(null)
            val privateKey = keyStore.getKey(LEGACY_KEY_ALIAS, null) ?: continue

            // Extract the encrypted value.
            val encryptedValue = context
                .getSharedPreferences(
                    LEGACY_SECURE_SHARED_PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                ).getString(preferenceKey, null)
            if (encryptedValue == null || encryptedValue == "") {
                continue
            }

            // Decrypt the encrypted value.
            val cipher = if (VERSION.SDK_INT >= VERSION_CODES.M) {
                Cipher.getInstance(
                    LEGACY_KEY_TRANSFORMATION_ALGORITHM,
                    LEGACY_KEY_CIPHER_MARSHMALLOW_PROVIDER
                )
            } else {
                Cipher.getInstance(
                    LEGACY_KEY_TRANSFORMATION_ALGORITHM,
                    LEGACY_KEY_CIPHER_JELLYBEAN_PROVIDER
                )
            }
            cipher.init(Cipher.DECRYPT_MODE, privateKey)

            val decryptedValue = CipherInputStream(
                ByteArrayInputStream(
                    Base64.decode(
                        encryptedValue,
                        Base64.DEFAULT
                    )
                ), cipher
            ).bufferedReader().use { it.readText() }
            getEncryptedSharedPreferences(context).edit()
                .putString(preferenceKey, decryptedValue).apply()
            continue
        } catch (e: Exception) {
            // Do nothing.
        }
    }

    // Remove any plain-text preferences. Delete the secure-storage-android
    // SharedPreferences and the key used to encrypt and decrypt them.
    try {
        for (preferenceKey in preferenceKeys) {
            removePreference(context, preferenceKey)
        }

        context.getSharedPreferences(
            LEGACY_SECURE_SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().apply()
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            context.deleteSharedPreferences(
                LEGACY_SECURE_SHARED_PREFERENCES_NAME
            )
        }

        val keyStore = KeyStore.getInstance(LEGACY_KEYSTORE_TYPE)
        keyStore.load(null)
        keyStore.deleteEntry(LEGACY_KEY_ALIAS)
    } catch (e: Exception) {
        // Do nothing.
    }
}