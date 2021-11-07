/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
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

package net.kourlas.voipms_sms.database

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.database.entities.Archived
import net.kourlas.voipms_sms.database.entities.Deleted
import net.kourlas.voipms_sms.database.entities.Draft
import net.kourlas.voipms_sms.database.entities.Sms
import net.kourlas.voipms_sms.demo.getDemoNotification
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.sms.workers.SyncWorker
import net.kourlas.voipms_sms.utils.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*


/**
 * Provides access to the application's database.
 */
class Database private constructor(private val context: Context) {
    private val importExportLock = CoroutineReadWriteLock(readers = 100)
    private var database = createDatabase(context)

    /**
     * Deletes the message with the specified DID, database ID, and optionally
     * VoIP.ms ID from the database.
     */
    suspend fun deleteMessage(did: String, databaseId: Long, voipId: Long?) =
        coroutineScope {
            importExportLock.read {
                database.withTransaction {
                    if (voipId != null) {
                        insertVoipIdDeleted(did, voipId)
                    }
                    database.smsDao().deleteById(databaseId)
                }
            }

            launch {
                removeFromIndex(context, Message.getMessageUrl(databaseId))
                updateShortcuts()
            }
        }

    /**
     * Deletes all messages that are not associated with the specified DIDs.
     */
    suspend fun deleteMessagesWithoutDids(dids: Set<String>) =

        importExportLock.read {
            database.withTransaction {
                database.smsDao().deleteWithoutDids(dids)
                database.deletedDao().deleteWithoutDids(dids)
                database.draftDao().deleteWithoutDids(dids)
                database.archivedDao().deleteWithoutDids(dids)
            }
        }

    /**
     * Deletes the messages in the specified conversation from the database.
     * Also deletes any draft message if one exists.
     */
    suspend fun deleteConversation(conversationId: ConversationId) =
        coroutineScope {
            val messages = importExportLock.read {
                database.withTransaction {
                    val messages = getConversationMessagesUnsortedWithoutLock(
                        conversationId
                    )
                    for (message in messages) {
                        if (message.voipId != null) {
                            insertVoipIdDeleted(message.did, message.voipId)
                        }
                    }

                    database.smsDao().deleteConversation(
                        conversationId.did, conversationId.contact
                    )
                    database.draftDao().deleteConversation(
                        conversationId.did, conversationId.contact
                    )
                    database.archivedDao().deleteConversation(
                        conversationId.did, conversationId.contact
                    )

                    updateConversationDraftWithoutLock(conversationId, "")

                    messages
                }
            }

            launch {
                for (message in messages) {
                    removeFromIndex(context, message.messageUrl)
                }
                updateShortcuts()
            }

            messages
        }

    /**
     * Deletes all rows in the deleted messages table from the database.
     */
    suspend fun deleteTableDeletedContents() =
        importExportLock.read {
            database.deletedDao().deleteAll()
        }

    /**
     * Deletes all rows in all tables in the database.
     */
    suspend fun deleteTablesContents() = coroutineScope {
        importExportLock.read {
            database.withTransaction {
                database.smsDao().deleteAll()
                database.deletedDao().deleteAll()
                database.draftDao().deleteAll()
                database.archivedDao().deleteAll()
            }
        }

        launch {
            removeAllFromIndex(context)
            updateShortcuts()
        }
    }

    /**
     * Exports the database to the specified file descriptor.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun export(exportFd: ParcelFileDescriptor) =
        importExportLock.write {
            val dbFile = context.getDatabasePath(DATABASE_NAME)

            try {
                // Close database to persist it to disk before export
                database.close()

                val exportStream = FileOutputStream(exportFd.fileDescriptor)
                val dbStream = FileInputStream(dbFile)
                exportStream.channel.truncate(0)
                val buffer = ByteArray(1024)
                do {
                    val length = dbStream.read(buffer)
                    if (length > 0) {
                        exportStream.write(buffer, 0, length)
                    } else {
                        break
                    }
                } while (true)
                exportStream.close()
                dbStream.close()
            } finally {
                // Refresh database after export
                database.close()
                database = createDatabase(context)
            }
        }

    /**
     * Gets the draft message for the specified conversation.
     *
     * @return Null if the message does not exist.
     */
    suspend fun getConversationDraft(
        conversationId: ConversationId
    ): Message? =
        importExportLock.read {
            database.draftDao().getConversation(
                conversationId.did, conversationId.contact
            )?.toMessage()
        }

    /**
     * Gets all conversation IDs in the database associated with the specified
     * DIDs.
     */
    suspend fun getConversationIds(
        dids: Set<String>
    ): Set<ConversationId> =
        importExportLock.read {
            getConversationIdsWithoutLock(dids)
        }

    /**
     * Gets all messages in a specified conversation that match a specified
     * filter constraint. The resulting list is sorted by date, from most
     * recent to least recent.
     */
    suspend fun getConversationMessagesFiltered(
        conversationId: ConversationId,
        filterConstraint: String,
        itemLimit: Long?
    ): List<Message> =
        importExportLock.read {
            if (itemLimit != null) {
                database.smsDao().getConversationMessagesFilteredWithLimit(
                    conversationId.did, conversationId.contact,
                    filterConstraint, itemLimit
                )
            } else {
                database.smsDao()
                    .getConversationMessagesFiltered(
                        conversationId.did,
                        conversationId.contact,
                        filterConstraint
                    )
            }.map { it.toMessage() }
        }

    /**
     * Gets the number of messages in a specified conversation that match a
     * specified filter constraint.
     */
    suspend fun getConversationMessagesFilteredCount(
        conversationId: ConversationId,
        filterConstraint: String
    ): Long =
        importExportLock.read {
            database.smsDao()
                .getConversationMessagesFilteredCount(
                    conversationId.did,
                    conversationId.contact,
                    filterConstraint
                )
        }

    /**
     * Gets all unread messages that chronologically follow the most recent
     * outgoing message for the specified conversation.
     *
     * The resulting list is sorted by date, from least recent to most recent.
     */
    suspend fun getConversationMessagesUnread(
        conversationId: ConversationId
    ): List<Message> =
        importExportLock.read {
            // Retrieve the date of the most recent outgoing message.
            val date = database.smsDao()
                .getConversationMessageDateMostRecentOutgoing(
                    conversationId.did,
                    conversationId.contact
                ) ?: 0

            // Retrieve all unread messages with a date equal to or after
            // this date.
            database.smsDao()
                .getConversationMessagesUnreadAfterDate(
                    conversationId.did, conversationId.contact, date
                )
                .map { it.toMessage() }
        }

    /**
     * Gets all messages in a specified conversation. The resulting list is not
     * sorted.
     */
    suspend fun getConversationMessagesUnsorted(
        conversationId: ConversationId
    ): List<Message> =
        importExportLock.read {
            getConversationMessagesUnsortedWithoutLock(conversationId)
        }

    /**
     * Gets the most recent message in each conversation associated with the
     * specified DIDs that matches a specified filter constraint. The resulting
     * list is sorted by date, from most recent to least recent.
     */
    suspend fun getConversationsMessageMostRecentFiltered(
        dids: Set<String>,
        filterConstraint: String = "",
        contactNameCache: MutableMap<String, String>? = null
    ) =
        importExportLock.read {
            getConversationsMessageMostRecentFilteredWithoutLock(
                dids,
                filterConstraint,
                contactNameCache
            )
        }

    /**
     * Gets all DIDs used in the database.
     */
    suspend fun getDids(): Set<String> =
        importExportLock.read {
            database.smsDao().getDids().toSet()
        }

    /**
     * Retrieves the message with the specified database ID from the database.
     *
     * @return Null if the message does not exist.
     */
    suspend fun getMessageDatabaseId(databaseId: Long): Message? =
        importExportLock.read {
            database.smsDao().getById(databaseId)?.toMessage()
        }

    /**
     * Gets the most recent message in the set of messages associated with the
     * specified DIDs.
     *
     * @return Null if the message does not exist.
     */
    suspend fun getMessageMostRecent(dids: Set<String>): Message? =
        importExportLock.read {
            database.smsDao().getMostRecent(dids)?.toMessage()
        }

    /**
     * Gets all of the messages in the message table with the specified DIDs.
     * The resulting list is sorted by VoIP.ms ID and by database ID in
     * descending order.
     */
    suspend fun getMessagesAll(dids: Set<String>): List<Message> =
        importExportLock.read {
            database.smsDao().getAll(dids).map { it.toMessage() }
        }

    /**
     * Imports the database from the specified file descriptor.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun import(importFd: ParcelFileDescriptor) = coroutineScope {
        importExportLock.write {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val backupFile = File("${dbFile.absolutePath}.backup")

            try {
                // Close database to persist it to disk before import
                database.close()

                // Try importing database, but restore from backup on failure
                dbFile.copyTo(backupFile, overwrite = true)
                try {
                    val importStream =
                        FileInputStream(importFd.fileDescriptor)
                    val dbStream = FileOutputStream(dbFile)

                    dbStream.channel.truncate(0)

                    val buffer = ByteArray(1024)
                    do {
                        val length = importStream.read(buffer)
                        if (length > 0) {
                            dbStream.write(buffer, 0, length)
                        } else {
                            break
                        }
                    } while (true)
                    importStream.close()
                    dbStream.close()

                    // Try refreshing database
                    database = createDatabase(context)
                } catch (e: Exception) {
                    backupFile.copyTo(dbFile, overwrite = true)
                    throw e
                }
            } finally {
                // Refresh database
                database.close()
                database = createDatabase(context)

                backupFile.delete()
            }
        }

        launch {
            replaceIndex(context)
        }
    }

    /**
     * Inserts new outgoing messages into the database with the specified
     * conversation ID and text. This message is marked as in the process of
     * being delivered.
     *
     * @return The database IDs of the inserted message.
     */
    suspend fun insertConversationMessagesDeliveryInProgress(
        conversationId: ConversationId,
        texts: List<String>
    ): List<Long> = coroutineScope {
        val messages = importExportLock.read {
            val smses = texts.map {
                Sms(
                    did = conversationId.did,
                    contact = conversationId.contact,
                    text = it,
                    deliveryInProgress = 1
                )
            }
            database.withTransaction {
                smses.map {
                    // No need to take the insertion lock, since there is no
                    // chance these messages already exist in the database.
                    val databaseId = database.smsDao().insert(it)
                    it.toMessage(databaseId)
                }
            }
        }

        launch {
            for (message in messages) {
                addMessageToIndex(context, message)
            }
            updateShortcuts()
        }

        messages.map { it.databaseId }
    }

    /**
     * Inserts messages originally taken from the database.
     */
    suspend fun insertMessagesDatabase(messages: List<Message>) =
        coroutineScope {
            importExportLock.read {
                database.withTransaction {
                    for (message in messages) {
                        val sms = Sms(
                            voipId = message.voipId,
                            date = message.date.time / 1000L,
                            incoming = if (message.isIncoming) 1L else 0L,
                            did = message.did,
                            contact = message.contact,
                            text = message.text,
                            unread = if (message.isUnread) 1L else 0L,
                            delivered = if (message.isDelivered) 1L else 0L,
                            deliveryInProgress = if (message.isDeliveryInProgress)
                                1L else 0L
                        )

                        // Don't add the message if it already exists in our
                        // database.
                        if (message.voipId == null
                            || database.smsDao().getIdByVoipId(
                                message.did, message.voipId
                            ) == null
                        ) {
                            database.smsDao().insert(sms)
                        }
                    }
                }
            }

            launch {
                updateShortcuts()
            }
        }

    /**
     * Inserts new messages from the VoIP.ms API.
     *
     * @param retrieveDeletedMessages If true, then any existing messages that
     * have the same VoIP.ms ID as a message in the list of new messages are
     * marked as not deleted.
     * @return The conversation IDs associated with the newly added messages.
     */
    suspend fun insertMessagesVoipMsApi(
        incomingMessages: List<SyncWorker.IncomingMessage>,
        retrieveDeletedMessages: Boolean
    ): Set<ConversationId> =
        coroutineScope {
            val (addedMessages, addedConversationIds) = importExportLock.read {
                val addedConversationIds = mutableSetOf<ConversationId>()
                val addedMessages = mutableListOf<Message>()

                database.withTransaction {
                    for (incomingMessage in incomingMessages) {
                        if (retrieveDeletedMessages) {
                            // Retrieve deleted messages is true, so we should
                            // remove this message from our list of deleted
                            // messages.
                            database.deletedDao()
                                .delete(
                                    setOf(incomingMessage.did),
                                    incomingMessage.voipId
                                )
                        } else if (database.deletedDao().get(
                                incomingMessage.did,
                                incomingMessage.voipId
                            ) != null
                        ) {
                            // Retrieve deleted messages is not true and this
                            // message has been previously deleted, so we
                            // shouldn't add it back
                            continue
                        }

                        val databaseId = database.smsDao().getIdByVoipId(
                            incomingMessage.did, incomingMessage.voipId
                        )
                        if (databaseId != null) {
                            continue
                        }

                        val sms = Sms(
                            voipId = incomingMessage.voipId,
                            date = incomingMessage.date.time / 1000L,
                            incoming = if (incomingMessage.isIncoming)
                                1L else 0L,
                            did = incomingMessage.did,
                            contact = incomingMessage.contact,
                            text = incomingMessage.text,
                            unread = if (incomingMessage.isIncoming)
                                1L else 0L,
                            delivered = 1L,
                            deliveryInProgress = 0L
                        )
                        val newDatabaseId = database.smsDao().insert(sms)

                        addedConversationIds.add(
                            ConversationId(
                                incomingMessage.did,
                                incomingMessage.contact
                            )
                        )
                        addedMessages.add(sms.toMessage(newDatabaseId))

                        // Mark the conversation as unarchived.
                        database.archivedDao().deleteConversation(
                            incomingMessage.did, incomingMessage.contact
                        )
                    }
                }

                Pair(addedMessages, addedConversationIds)
            }

            launch {
                for (message in addedMessages) {
                    addMessageToIndex(context, message)
                }
                if (addedMessages.isNotEmpty()) {
                    updateShortcuts()
                }
            }

            addedConversationIds
        }

    /**
     * Returns whether the specified conversation is archived.
     */
    suspend fun isConversationArchived(
        conversationId: ConversationId
    ): Boolean =
        importExportLock.read {
            database.archivedDao().getConversation(
                conversationId.did, conversationId.contact
            )?.archived == 1
        }

    /**
     * Marks the specified conversation as archived.
     */
    suspend fun markConversationArchived(
        conversationId: ConversationId
    ) =
        importExportLock.read {
            database.withTransaction {
                val existingArchived = database.archivedDao()
                    .getConversation(conversationId.did, conversationId.contact)
                val newArchived =
                    Archived(
                        databaseId = existingArchived?.databaseId ?: 0,
                        did = conversationId.did,
                        contact = conversationId.contact, archived = 1
                    )
                database.archivedDao().update(newArchived)
            }
        }

    /**
     * Marks the specified conversation as unarchived.
     */
    suspend fun markConversationUnarchived(
        conversationId: ConversationId
    ) =
        importExportLock.read {
            database.withTransaction {
                val existingArchived = database.archivedDao()
                    .getConversation(conversationId.did, conversationId.contact)
                val newArchived =
                    Archived(
                        databaseId = existingArchived?.databaseId ?: 0,
                        did = conversationId.did,
                        contact = conversationId.contact, archived = 0
                    )
                database.archivedDao().update(newArchived)
            }
        }

    /**
     * Marks the specified conversation as read.
     **/
    suspend fun markConversationRead(
        conversationId: ConversationId
    ) =
        importExportLock.read {
            database.smsDao().markConversationRead(
                conversationId.did,
                conversationId.contact
            )
        }

    /**
     * Marks the specified conversation as unread.
     **/
    suspend fun markConversationUnread(conversationId: ConversationId) =
        importExportLock.read {
            database.smsDao().markConversationUnread(
                conversationId.did,
                conversationId.contact
            )
        }

    /**
     * Marks the message with the specified database ID as in the process of
     * being delivered.
     */
    suspend fun markMessageDeliveryInProgress(databaseId: Long) =
        importExportLock.read {
            database.smsDao().markMessageDeliveryInProgress(databaseId)
        }

    /**
     * Marks the message with the specified database ID as having failed to
     * be sent.
     */
    suspend fun markMessageNotSent(databaseId: Long) =
        importExportLock.read {
            database.smsDao().markMessageNotSent(databaseId)
        }

    /**
     * Marks the message with the specified database ID as having been sent.
     * In addition, adds the specified VoIP.ms ID to the message.
     */
    suspend fun markMessageSent(databaseId: Long, voipId: Long) =
        importExportLock.read {
            database.smsDao()
                .markMessageSent(databaseId, voipId, Date().time / 1000L)
        }

    /**
     * Updates the draft message associated with the specified conversation
     * and containing the specified text.
     *
     * Any existing draft message with the specified conversation is
     * automatically removed. If an empty message is inserted, any existing
     * message is removed from the database.
     */
    suspend fun updateConversationDraft(
        conversationId: ConversationId,
        text: String
    ) =
        importExportLock.read {
            database.withTransaction {
                updateConversationDraftWithoutLock(conversationId, text)
            }
        }

    /**
     * Update the app shortcuts.
     */
    suspend fun updateShortcuts() = withContext(Dispatchers.IO) {
        importExportLock.read {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                // There is one static shortcut, which reduces the number of
                // dynamic shortcut slots available.
                val maxCount =
                    ShortcutManagerCompat.getMaxShortcutCountPerActivity(
                        context
                    ) - 1

                // Update the dynamic shortcuts.
                val messages = if (BuildConfig.IS_DEMO) {
                    listOf(getDemoNotification())
                } else {
                    getConversationsMessageMostRecentFilteredWithoutLock(
                        net.kourlas.voipms_sms.preferences.getDids(
                            context, onlyShowInConversationsView = true
                        )
                    )
                }
                val conversationIdStrings =
                    messages.map { it.conversationId.getId() }
                val shortcutInfoList = messages.map {
                    val intent =
                        Intent(context, ConversationActivity::class.java)
                    intent.action = "android.intent.action.VIEW"
                    intent.putExtra(
                        context.getString(R.string.conversation_did),
                        it.did
                    )
                    intent.putExtra(
                        context.getString(R.string.conversation_contact),
                        it.contact
                    )

                    val contactName = getContactName(context, it.contact)
                    val label =
                        contactName ?: getFormattedPhoneNumber(it.contact)
                    val icon = IconCompat.createWithAdaptiveBitmap(
                        getContactPhotoAdaptiveBitmap(
                            context, contactName,
                            it.contact
                        )
                    )
                    ShortcutInfoCompat.Builder(
                        context, it.conversationId.getId()
                    )
                        .setIcon(icon)
                        .setIntent(intent)
                        .setPerson(
                            Person.Builder()
                                .setName(label)
                                .setKey(it.contact)
                                .setIcon(icon)
                                .setUri("tel:${it.contact}")
                                .build()
                        )
                        .setLongLabel(label)
                        .setShortLabel(
                            if (contactName != null) label.split(
                                " "
                            )[0] else label
                        )
                        .setLongLived(true)
                        .setLocusId(LocusIdCompat(it.conversationId.getId()))
                        .setCategories(setOf("existing_conversation_target"))
                        .setRank(0)
                        .build()
                }
                val dynamicShortcutInfoList =
                    shortcutInfoList.zip(0 until maxCount).map { it.first }
                try {
                    ShortcutManagerCompat.updateShortcuts(
                        context,
                        shortcutInfoList
                    )
                    ShortcutManagerCompat.setDynamicShortcuts(
                        context, dynamicShortcutInfoList
                    )
                } catch (e: Exception) {
                    // Occasionally this will fail because the maximum number of
                    // dynamic shortcuts was exceeded? There's nothing we can do
                    // about this, since we are following
                    // getMaxShortcutCountPerActivity().
                    logException(e)
                }

                // Look at our existing list of cached and pinned shortcuts to make
                // sure they still exist. If not, delete or remove them.
                val existingPinnedShortcutInfoList =
                    ShortcutManagerCompat.getShortcuts(
                        context,
                        ShortcutManagerCompat.FLAG_MATCH_PINNED
                    )
                for (shortcutInfo in existingPinnedShortcutInfoList) {
                    if (shortcutInfo.id !in conversationIdStrings) {
                        ShortcutManagerCompat.disableShortcuts(
                            context, listOf(
                                shortcutInfo.id
                            ), context.getString(
                                R.string.pinned_shortcut_disable_error
                            )
                        )
                    }
                }
                val existingCachedShortcutInfoList =
                    ShortcutManagerCompat.getShortcuts(
                        context,
                        ShortcutManagerCompat.FLAG_MATCH_CACHED
                    )
                for (shortcutInfo in existingCachedShortcutInfoList) {
                    if (shortcutInfo.id !in conversationIdStrings) {
                        ShortcutManagerCompat.removeLongLivedShortcuts(
                            context,
                            listOf(shortcutInfo.id)
                        )
                    }
                }
            }
        }
    }

    /**
     * Gets all conversation IDs in the database associated with the specified
     * DIDs.
     *
     * This method intentionally does not use lock semantics; this is a
     * responsibility of the caller.
     */
    private suspend fun getConversationIdsWithoutLock(
        dids: Set<String>
    ): Set<ConversationId> {
        return database.smsDao().getConversationIds(dids).toSet()
    }

    /**
     * Gets all messages in a specified conversation. The resulting list is not
     * sorted.s
     *
     * This method intentionally does not use lock semantics; this is a
     * responsibility of the caller.
     */
    private suspend fun getConversationMessagesUnsortedWithoutLock(
        conversationId: ConversationId
    ): List<Message> {
        return database.smsDao()
            .getConversationMessagesUnsorted(
                conversationId.did,
                conversationId.contact
            )
            .map { it.toMessage() }
    }

    /**
     * Gets the draft messages for all conversations associated with the
     * specified DIDs.
     *
     * This method intentionally does not use lock semantics; this is a
     * responsibility of the caller.
     */
    private suspend fun getConversationsMessageDraft(
        dids: Set<String>
    ): List<Message> {
        return database.draftDao()
            .getConversations(dids)
            .map { it.toMessage() }
    }

    /**
     * Gets the most recent draft message in each conversation associated
     * with the specified DIDs that matches a specified filter constraint.
     *
     * This method intentionally does not use lock semantics; this is a
     * responsibility of the caller.
     */
    private suspend fun getConversationsMessageDraftFiltered(
        dids: Set<String>, filterConstraint: String
    ): List<Message> {
        return getConversationsMessageDraft(dids)
            .filter {
                it.text
                    .lowercase(Locale.getDefault())
                    .contains(filterConstraint)
            }
            .toMutableList()
    }

    /**
     * Gets the most recent message in each conversation associated with the
     * specified DIDs that matches a specified filter constraint. The resulting
     * list is sorted by date, from most recent to least recent.
     *
     * This method intentionally does not use lock semantics; this is a
     * responsibility of the caller.
     */
    private suspend fun getConversationsMessageMostRecentFilteredWithoutLock(
        dids: Set<String>,
        filterConstraint: String = "",
        contactNameCache: MutableMap<String, String>? = null
    ): List<Message> {
        val numericFilterConstraint = getDigitsOfString(filterConstraint)

        var messages = mutableListOf<Message>()
        for (conversationId in getConversationIdsWithoutLock(dids)) {
            // If we are using filters, first check to see if our filter
            // matches the the DID phone number, contact phone number, and
            // message text, since we can check those using SQL.
            if (filterConstraint.isNotEmpty()) {
                val sms = if (numericFilterConstraint != "") {
                    database.smsDao()
                        .getConversationMessageMostRecentFiltered(
                            conversationId.did,
                            conversationId.contact,
                            filterConstraint,
                            numericFilterConstraint
                        )
                } else {
                    database.smsDao()
                        .getConversationMessageMostRecentFiltered(
                            conversationId.did,
                            conversationId.contact,
                            filterConstraint
                        )
                }

                if (sms != null) {
                    messages.add(sms.toMessage())
                    continue
                }
            }

            // Otherwise, simply get the most recent message for the
            // conversation without using any filters.
            val sms = database.smsDao()
                .getConversationMessageMostRecent(
                    conversationId.did,
                    conversationId.contact
                ) ?: continue

            // If no filter constraint was provided, just add the message
            // to the list.
            if (filterConstraint.isEmpty()) {
                messages.add(sms.toMessage())
                continue
            }

            // Otherwise, check if the message matches our contact name
            // filter. We could not check this as part of the first SQL
            // query, since it requires an external lookup.
            val contactName = getContactName(
                context,
                sms.contact,
                contactNameCache
            )
            val lowercaseContactName = contactName?.lowercase(
                Locale.getDefault()
            )
            if (lowercaseContactName?.contains(filterConstraint) == true) {
                messages.add(sms.toMessage())
            }
        }

        // Replace messages with any applicable draft messages
        val draftMessages = getConversationsMessageDraftFiltered(
            dids, filterConstraint
        )
        for (draftMessage in draftMessages) {
            var added = false
            messages = messages.map {
                if (it.conversationId == draftMessage.conversationId) {
                    added = true
                    draftMessage
                } else {
                    it
                }
            }.toMutableList()
            if (!added && draftMessage.did in dids) {
                messages.add(0, draftMessage)
            }
        }

        messages.sort()
        return messages
    }

    /**
     * Inserts the deleted VoIP.ms message ID associated with the specified
     * DID into the database.
     *
     * This method intentionally does not use lock semantics; this is a
     * responsibility of the caller.
     */
    private suspend fun insertVoipIdDeleted(did: String, voipId: Long) {
        if (database.deletedDao().get(did, voipId) != null) {
            // VoIP.ms ID and DID already in table
            return
        }
        database.deletedDao().insert(Deleted(did = did, voipId = voipId))
    }

    /**
     * Updates the draft message associated with the specified conversation
     * and containing the specified text.
     *
     * Any existing draft message with the specified conversation is
     * automatically removed. If an empty message is inserted, any existing
     * message is removed from the database.
     *
     * This method intentionally does not use lock semantics; this is a
     * responsibility of the caller.
     */
    private suspend fun updateConversationDraftWithoutLock(
        conversationId: ConversationId,
        text: String
    ) {
        // If text is empty, then just delete any existing draft message.
        if (text == "") {
            database.draftDao().deleteConversation(
                conversationId.did,
                conversationId.contact
            )
            return
        }

        // Otherwise, update the draft message, if any.
        val existingDraft = database.draftDao().getConversation(
            conversationId.did, conversationId.contact
        )
        val newDraft = Draft(
            databaseId = existingDraft?.databaseId ?: 0,
            did = conversationId.did,
            contact = conversationId.contact,
            text = text
        )
        database.draftDao().update(newDraft)
    }

    companion object {
        private const val DATABASE_NAME = "sms.db"
        const val DATABASE_VERSION = 10

        private val migration9To10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Do nothing.
            }
        }

        // It is not a leak to store an instance to the Application object,
        // since it has the same lifetime as the application itself
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: Database? = null

        /**
         * Gets the sole instance of the Database class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(context: Context): Database =
            instance ?: synchronized(this) {
                instance ?: Database(
                    context.applicationContext
                ).also { instance = it }
            }

        /**
         * Creates a RoomDatabase instance.
         */
        private fun createDatabase(context: Context): AbstractDatabase {
            return Room.databaseBuilder(
                context, AbstractDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(migration9To10)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
