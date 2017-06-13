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

package net.kourlas.voipms_sms.sms

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.Indexable
import com.google.firebase.appindexing.builders.Indexables
import com.google.firebase.appindexing.builders.MessageBuilder
import com.google.firebase.appindexing.builders.PersonBuilder
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.utils.getContactName
import net.kourlas.voipms_sms.utils.getContactPhotoUri

/**
 * Service used to perform Firebase app indexing.
 */
class AppIndexingService : IntentService(AppIndexingService::class.java.name) {
    override fun onHandleIntent(intent: Intent?) {
        replaceIndex(applicationContext)
    }

    companion object {
        /**
         * Replace the app index with the conversations in the database.
         *
         * @param context The context to use.
         */
        fun replaceIndex(context: Context) {
            val indexables = mutableListOf<Indexable>()

            // Create message indexables
            val contactNameCache = mutableMapOf<String, String>()
            val contactPhotoUriCache = mutableMapOf<String, String>()
            val messages = Database.getInstance(context).getMessagesAll(
                getDids(context))
            messages.mapTo(indexables) {
                getMessageBuilder(context, it,
                                  contactNameCache,
                                  contactPhotoUriCache).build()
            }

            // Delete app index and update index with indexables
            FirebaseAppIndex.getInstance().removeAll().addOnCompleteListener {
                updateIndex(indexables)
            }
        }

        /**
         * Update the app index with the specified indexables.
         *
         * @param indexables The specified indexables.
         */
        private fun updateIndex(indexables: List<Indexable>) {
            val max = Indexable.MAX_INDEXABLES_TO_BE_UPDATED_IN_ONE_CALL
            (0..indexables.size - 1 step max)
                .map {
                    indexables.subList(
                        it,
                        if (indexables.size > it + max) it + max - 1
                        else indexables.size - 1)
                }
                .forEach {
                    FirebaseAppIndex.getInstance().update(
                        *it.toTypedArray())
                }
        }

        /**
         * Gets a message builder based on the specified message.
         *
         * @param context The context to use.
         * @param message The specified message.
         * @param contactNameCache The contact and DID names cache.
         * @param contactPhotoUriCache The contact and DID photo URIs cache.
         */
        fun getMessageBuilder(context: Context, message: Message,
                              contactNameCache: MutableMap<String, String> = mutableMapOf(),
                              contactPhotoUriCache: MutableMap<String, String> = mutableMapOf()): MessageBuilder {
            val messageBuilder = Indexables.messageBuilder()
                .setUrl(message.messageUrl)
                .setName(message.text)
                .setIsPartOf(Indexables.conversationBuilder()
                                 .setUrl(message.conversationUrl)
                                 .setId("${message.did},${message.contact}"))

            val contactBuilder = getContactBuilder(context,
                                                   message, contactNameCache,
                                                   contactPhotoUriCache)
            val didBuilder = getDidBuilder(context,
                                           message, contactNameCache,
                                           contactPhotoUriCache)

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
         * Gets a person builder based on the contact in the specified message.
         *
         * @param context The context to use.
         * @param message The specified message.
         * @param contactNameCache The contact and DID names cache.
         * @param contactPhotoUriCache The contact and DID photo URIs cache.
         */
        private fun getContactBuilder(context: Context, message: Message,
                                      contactNameCache: MutableMap<String, String>,
                                      contactPhotoUriCache: MutableMap<String, String>): PersonBuilder {
            val contactBuilder = Indexables.personBuilder()
                .setTelephone(message.contact)
                .setUrl(message.conversationUrl)

            // Set contact photo URI
            val contactPhotoUri = getContactPhotoUri(
                context, message.contact, contactPhotoUriCache)
            if (contactPhotoUri != null) {
                contactBuilder.setImage(contactPhotoUri)
            }

            // Set contact name
            val contactName = getContactName(
                context, message.contact, contactNameCache)
            if (contactName != null) {
                contactBuilder.setName(contactName)
            }

            return contactBuilder
        }

        /**
         * Gets a person builder based on the DID in the specified message.
         *
         * @param context The context to use.
         * @param message The specified message.
         * @param contactNameCache The contact and DID names cache.
         * @param contactPhotoUriCache The contact and DID photo URIs cache.
         */
        private fun getDidBuilder(context: Context, message: Message,
                                  contactNameCache: MutableMap<String, String>,
                                  contactPhotoUriCache: MutableMap<String, String>): PersonBuilder {
            val didBuilder = Indexables.personBuilder()
                .setTelephone(message.did)
                .setUrl(message.conversationUrl)
                .setIsSelf(true)

            // Set DID photo URI
            val didPhotoUri = getContactPhotoUri(
                context, message.did, contactPhotoUriCache)
            if (didPhotoUri != null) {
                didBuilder.setImage(didPhotoUri)
            }

            // Set DID name
            val didName = getContactName(
                context, message.did, contactNameCache)
            if (didName != null) {
                didBuilder.setName(didName)
            }

            return didBuilder
        }
    }
}