/*
 * VoIP.ms SMS
 * Copyright (C) 2020-2021 Michael Kourlas
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

package net.kourlas.voipms_sms.utils

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.Indexable
import com.google.firebase.appindexing.builders.Indexables
import com.google.firebase.appindexing.builders.MessageBuilder
import com.google.firebase.appindexing.builders.PersonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.sms.Message

/**
 * Adds the specified message to the app index.
 */
@Suppress("BlockingMethodInNonBlockingContext", "HasPlatformType")
suspend fun addMessageToIndex(context: Context, message: Message) =
    withContext(Dispatchers.IO) {
        try {
            Tasks.await(
                FirebaseAppIndex.getInstance(context).update(
                    getMessageBuilder(
                        context, message
                    ).build()
                )
            )
        } catch (e: Exception) {
            logException(e)
        }
    }

/**
 * Remove the entry with the specified URI from the app index.
 */
@Suppress("BlockingMethodInNonBlockingContext", "HasPlatformType")
suspend fun removeFromIndex(context: Context, string: String) =
    withContext(Dispatchers.IO) {
        try {
            Tasks.await(FirebaseAppIndex.getInstance(context).remove(string))
        } catch (e: Exception) {
            logException(e)
        }
    }

/**
 * Remove the all entries from the app index.
 */
@Suppress("BlockingMethodInNonBlockingContext", "HasPlatformType")
suspend fun removeAllFromIndex(context: Context) =
    withContext(Dispatchers.IO) {
        try {
            Tasks.await(FirebaseAppIndex.getInstance(context).removeAll())
        } catch (e: Exception) {
            logException(e)
        }
    }

/**
 * Replace the app index with the conversations in the database.
 */
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun replaceIndex(context: Context) = withContext(Dispatchers.IO) {
    val applicationContext = context.applicationContext
    val indexables = mutableListOf<Indexable>()

    // Create message indexables
    val contactNameCache = mutableMapOf<String, String>()
    val contactPhotoUriCache = mutableMapOf<String, String>()
    val messages = Database.getInstance(
        context
    ).getMessagesAll(
        getDids(context, onlyShowInConversationsView = true)
    )
    messages.mapTo(indexables) {
        getMessageBuilder(
            context, it,
            contactNameCache,
            contactPhotoUriCache
        ).build()
    }

    try {
        // Delete app index and update index with indexables
        Tasks.await(
            FirebaseAppIndex.getInstance(applicationContext).removeAll()
        )
        val max = Indexable.MAX_INDEXABLES_TO_BE_UPDATED_IN_ONE_CALL
        (indexables.indices step max)
            .map {
                indexables.subList(
                    it,
                    if (indexables.size > it + max) it + max - 1
                    else indexables.size - 1
                )
            }
            .forEach {
                Tasks.await(
                    FirebaseAppIndex.getInstance(context).update(
                        *it.toTypedArray()
                    )
                )
            }
    } catch (e: Exception) {
        logException(e)
    }
}

/**
 * Gets a message builder based on the specified message. Certain data is
 * retrieved from the specified caches to avoid repeated requests.
 */
private fun getMessageBuilder(
    context: Context, message: Message,
    contactNameCache: MutableMap<String, String> =
        mutableMapOf(),
    contactPhotoUriCache: MutableMap<String, String> =
        mutableMapOf()
): MessageBuilder {
    val messageBuilder = Indexables.messageBuilder()
        .setUrl(message.messageUrl)
        .setName(message.text)
        .setIsPartOf(
            Indexables.conversationBuilder()
                .setUrl(message.conversationUrl)
                .setId("${message.did},${message.contact}")
        )

    val contactBuilder = getContactBuilder(
        context,
        message, contactNameCache,
        contactPhotoUriCache
    )
    val didBuilder = getDidBuilder(
        context,
        message, contactNameCache,
        contactPhotoUriCache
    )

    if (message.isIncoming) {
        messageBuilder.setDateReceived(message.date)
        messageBuilder.setSender(contactBuilder)
        messageBuilder.setRecipient(didBuilder)
    } else {
        messageBuilder.setDateSent(message.date)
        messageBuilder.setSender(didBuilder)
        messageBuilder.setRecipient(contactBuilder)
    }

    return messageBuilder
}

/**
 * Gets a person builder based on the contact in the specified message. Certain
 * data is retrieved from the specified caches to avoid repeated requests.
 */
private fun getContactBuilder(
    context: Context, message: Message,
    contactNameCache: MutableMap<String, String>,
    contactPhotoUriCache: MutableMap<String, String>
): PersonBuilder {
    val contactBuilder = Indexables.personBuilder()
        .setTelephone(message.contact)
        .setUrl(message.conversationUrl)

    // Set contact photo URI
    val contactPhotoUri = getContactPhotoUri(
        context, message.contact, contactPhotoUriCache
    )
    if (contactPhotoUri != null) {
        contactBuilder.setImage(contactPhotoUri)
    }

    // Set contact name
    val contactName = getContactName(
        context, message.contact, contactNameCache
    )
    if (contactName != null) {
        contactBuilder.setName(contactName)
    }

    return contactBuilder
}

/**
 * Gets a person builder based on the DID in the specified message. Certain
 * data is retrieved from the specified caches to avoid repeated requests.
 */
private fun getDidBuilder(
    context: Context, message: Message,
    contactNameCache: MutableMap<String, String>,
    contactPhotoUriCache: MutableMap<String, String>
): PersonBuilder {
    val didBuilder = Indexables.personBuilder()
        .setTelephone(message.did)
        .setUrl(message.conversationUrl)
        .setIsSelf(true)

    // Set DID photo URI
    val didPhotoUri = getContactPhotoUri(
        context, message.did, contactPhotoUriCache
    )
    if (didPhotoUri != null) {
        didBuilder.setImage(didPhotoUri)
    }

    // Set DID name
    val didName = getContactName(
        context, message.did, contactNameCache
    )
    if (didName != null) {
        didBuilder.setName(didName)
    }

    return didBuilder
}