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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.firebase.appindexing.FirebaseAppIndex
import net.kourlas.voipms_sms.utils.getContactName
import net.kourlas.voipms_sms.utils.getDigitsOfString
import java.io.File

import java.text.SimpleDateFormat
import java.util.*

/**
 * Provides access to the application's database.
 */
class Database private constructor(private val context: Context) {
    private val databaseHelper = DatabaseHelper(context)
    private var database = databaseHelper.writableDatabase

    /**
     * Deletes the specified message from the database.
     *
     * @param did The DID associated with the message.
     * @param databaseId The database ID of the message.
     * @param voipId The VoIP.ms ID of the message, if applicable.
     */
    fun deleteMessage(did: String, databaseId: Long, voipId: Long?) {
        synchronized(this) {
            // Track deleted VoIP.ms ID
            if (voipId != null) {
                insertVoipIdDeleted(did, voipId)
            }

            // Remove message from database
            database.delete(TABLE_MESSAGE,
                            "$COLUMN_DATABASE_ID = $databaseId",
                            null)
            FirebaseAppIndex.getInstance().remove(Message.getMessageUrl(
                databaseId))
        }
    }

    /**
     * Deletes all messages associated with the specified DIDs.
     *
     * @param dids The specified DIDs.
     */
    fun deleteMessages(dids: Set<String>) {
        var query = ""
        for (did in dids) {
            query += "$COLUMN_DID != $did AND "
        }
        if (dids.isNotEmpty()) {
            query = query.substring(0, query.length - 5)
        }

        synchronized(this) {
            // Remove messages from database
            database.delete(TABLE_MESSAGE, query, null)
            database.delete(TABLE_DELETED, query, null)
            database.delete(TABLE_DRAFT, query, null)
            database.delete(TABLE_ARCHIVED, query, null)
        }
    }

    /**
     * Deletes the messages in the specified conversation from the database.
     * Also deletes any draft message if one exists.
     *
     * @param conversationId The ID of the specified conversation
     */
    fun deleteMessages(conversationId: ConversationId) {
        val did = conversationId.did
        val contact = conversationId.contact

        val messages = getMessagesConversation(conversationId)

        synchronized(this) {
            for (message in messages) {
                // Remove messages from index
                FirebaseAppIndex.getInstance().remove(message.messageUrl)

                // Add VoIP.ms IDs from messages to database
                val values = ContentValues()
                values.put(COLUMN_VOIP_ID, message.voipId)
                values.put(COLUMN_DID, did)
                database.replaceOrThrow(TABLE_DELETED, null, values)
            }

            // Remove messages from database
            database.delete(TABLE_MESSAGE,
                            "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact",
                            null)
            database.delete(TABLE_DRAFT,
                            "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact",
                            null)
            database.delete(TABLE_ARCHIVED,
                            "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact",
                            null)
        }
    }

    /**
     * Deletes the deleted messages table from the database.
     */
    fun deleteTableDeleted() {
        synchronized(this) {
            database.delete(TABLE_DELETED, null, null)
        }
    }

    /**
     * Deletes the entire database.
     */
    fun deleteTablesAll() {
        synchronized(this) {
            database.delete(TABLE_MESSAGE, null, null)
            database.delete(TABLE_DELETED, null, null)
            database.delete(TABLE_DRAFT, null, null)
            database.delete(TABLE_ARCHIVED, null, null)

            FirebaseAppIndex.getInstance().removeAll()
        }
    }

    /**
     * Exports the database to the database import and export file path.
     */
    fun export() {
        synchronized(this) {
            try {
                // Close database to persist it to disk
                database.close()

                // Export database
                val dbPath = context.getDatabasePath(Database.DATABASE_NAME)
                val exportPath = getExportPath()
                dbPath.copyTo(exportPath, overwrite = true)
            } finally {
                // Refresh database
                database.close()
                database = databaseHelper.writableDatabase
            }
        }
    }

    /**
     * Gets the database import and export file path.
     */
    fun getImportPath(): File {
        return File(context.getExternalFilesDir(null).absolutePath +
                    "/backup_import.db")
    }

    /**
     * Gets the database import and export file path.
     */
    fun getExportPath(): File {
        return File(context.getExternalFilesDir(null).absolutePath +
                    "/backup_export.db")
    }

    /**
     * Retrieves the message with the specified database ID from the database.
     *
     * @param databaseId The specified database ID.
     * @return The message with the specified database ID from the database, or
     * null if it does not exist.
     */
    fun getMessageDatabaseId(databaseId: Long): Message? {
        synchronized(this) {
            val cursor = database.query(TABLE_MESSAGE, messageColumns,
                                        "$COLUMN_DATABASE_ID = $databaseId",
                                        null, null, null, null)
            val messages = getMessagesCursor(cursor)
            if (messages.size > 0) {
                return messages[0]
            } else {
                return null
            }
        }
    }

    /**
     * Gets the draft message for the specified conversation.
     *
     * @param conversationId The ID of the specified conversation.
     * @return The draft message for the specified conversation.
     */
    fun getMessageDraft(conversationId: ConversationId): Message? {
        synchronized(this) {
            return getMessageDraftWithoutLock(conversationId)
        }
    }

    /**
     * Gets the most recent message in the set of messages associated with the
     * specified DIDs.
     *
     * @param dids The specified DIDs.
     * @return The most recent message in the set of messages associated with
     * the specified DIDs.
     */
    fun getMessageMostRecent(dids: Set<String>): Message? {
        var query = ""
        for (did in dids) {
            query += "$COLUMN_DID=$did OR "
        }
        if (dids.isNotEmpty()) {
            query = query.substring(0, query.length - 4)
        }

        synchronized(this) {
            val cursor = database.query(TABLE_MESSAGE, messageColumns, query,
                                        null, null, null,
                                        COLUMN_DATE + " DESC", "1")
            val messages = getMessagesCursor(cursor)
            if (messages.size > 0) {
                return messages[0]
            } else {
                return null
            }
        }
    }

    /**
     * Gets all of the messages in the message table with the specified DIDs.
     * The resulting list is sorted by database ID in descending order.
     *
     * @return All of the messages in the message table.
     */
    fun getMessagesAll(dids: Set<String>): List<Message> {
        var query = ""
        for (did in dids) {
            query += "$COLUMN_DID=$did OR "
        }
        if (dids.isNotEmpty()) {
            query = query.substring(0, query.length - 4)
        }

        synchronized(this) {
            val cursor = database.query(
                TABLE_MESSAGE,
                messageColumns,
                query,
                null, null, null,
                COLUMN_DATABASE_ID + " DESC")
            return getMessagesCursor(cursor)
        }
    }

    /**
     * Get the messages associated with the specified conversation.
     *
     * @param conversationId The ID of the specified conversation.
     * @return The messages associated with the specified conversation.
     */
    fun getMessagesConversation(conversationId: ConversationId): List<Message> {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            val cursor = database.query(
                TABLE_MESSAGE,
                messageColumns,
                "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact",
                null, null, null, null)
            return getMessagesCursor(cursor)
        }
    }

    /**
     * Gets all messages in a specified conversation that match a specified
     * filter constraint. The resulting list is sorted by date, from least
     * recent to most recent.
     *
     * @param conversationId The ID of the specified conversation.
     * @param filterConstraint The filter constraint.
     * @return All messages in a specified conversation that match a specified
     * filter constraint.
     */
    fun getMessagesConversationFiltered(conversationId: ConversationId,
                                        filterConstraint: String): List<Message> {
        val did = conversationId.did
        val contact = conversationId.contact

        // Process filter constraint for use in SQL query
        val filterString = "%$filterConstraint%"
        val params = arrayOf(filterString)

        synchronized(this) {
            val cursor = database.query(
                TABLE_MESSAGE,
                messageColumns,
                "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact" +
                " AND $COLUMN_MESSAGE LIKE ?",
                params,
                null, null,
                "$COLUMN_DATE ASC, $COLUMN_DATABASE_ID DESC")
            return getMessagesCursor(cursor)
        }
    }

    /**
     * Gets the most recent message in each conversation associated with the
     * specified DIDs that matches a specified filter constraint. The resulting
     * list is sorted by date, from  most recent to least recent.
     *
     * @param dids The specified DIDs.
     * @param filterConstraint The filter constraint.
     * @return The most recent message in each conversation associated with the
     * specified DIDs that matches a specified filter constraint
     */
    fun getMessagesMostRecentFiltered(
        dids: Set<String>, filterConstraint: String,
        contactNameCache: MutableMap<String, String>? = null): List<Message> {
        // Process filter constraints for use in SQL query
        val filterString = "%$filterConstraint%"
        var params = arrayOf(filterString)
        val numberFilterConstraint = getDigitsOfString(
            filterConstraint)
        var numericFilterStringQuery = ""
        if (numberFilterConstraint != "") {
            val numericFilterString = "%$numberFilterConstraint%"
            params = arrayOf(filterString, numericFilterString,
                             numericFilterString)
            numericFilterStringQuery = "OR $COLUMN_CONTACT LIKE ?" +
                                       " OR $COLUMN_DID LIKE ?"
        }

        synchronized(this) {
            val allMessages = mutableListOf<Message>()
            for (did in dids) {
                // First, retrieve the most recent message for each
                // conversation, filtering only on the DID phone number,
                // contact phone number and message text
                var query = "SELECT * FROM $TABLE_MESSAGE a INNER JOIN" +
                            " (SELECT $COLUMN_DATABASE_ID, $COLUMN_CONTACT," +
                            " MAX($COLUMN_DATE)" +
                            " AS $COLUMN_DATE FROM $TABLE_MESSAGE" +
                            " WHERE ($COLUMN_MESSAGE LIKE ?" +
                            " COLLATE NOCASE $numericFilterStringQuery)" +
                            " AND $COLUMN_DID=$did GROUP BY $COLUMN_CONTACT)" +
                            " b on a.$COLUMN_DATABASE_ID" +
                            " = b.$COLUMN_DATABASE_ID" +
                            " AND a.$COLUMN_DATE = b.$COLUMN_DATE" +
                            " ORDER BY $COLUMN_DATE DESC, $COLUMN_DATABASE_ID" +
                            " DESC"
                var cursor = database.rawQuery(query, params)
                val messages = getMessagesCursor(cursor)

                // Then, retrieve the most recent message for each conversation
                // without filtering; if any conversation present in the second
                // list is not present in the first list, filter the message in
                // the second list on contact name and add it to the first list
                // if there is a match
                query = "SELECT * FROM $TABLE_MESSAGE a INNER JOIN" +
                        " (SELECT $COLUMN_DATABASE_ID, $COLUMN_CONTACT," +
                        " MAX($COLUMN_DATE)" +
                        " AS $COLUMN_DATE FROM $TABLE_MESSAGE" +
                        " WHERE $COLUMN_DID=$did GROUP BY $COLUMN_CONTACT)" +
                        " b on a.$COLUMN_DATABASE_ID = b.$COLUMN_DATABASE_ID" +
                        " AND a.$COLUMN_DATE = b.$COLUMN_DATE" +
                        " ORDER BY $COLUMN_DATE DESC, $COLUMN_DATABASE_ID DESC"
                cursor = database.rawQuery(query, null)
                val contactNameMessages = getMessagesCursor(cursor)
                cursor.close()
                loop@ for (contactNameMessage in contactNameMessages) {
                    @Suppress("LoopToCallChain")
                    for (message in messages) {
                        if (message.contact == contactNameMessage.contact) {
                            continue@loop
                        }
                    }

                    val contactName = getContactName(context,
                                                     contactNameMessage.contact,
                                                     contactNameCache)
                    if (contactName != null && contactName.toLowerCase()
                        .contains(filterConstraint)) {
                        messages.add(contactNameMessage)
                    }
                }
                messages.sort()

                // Replace messages with any applicable draft messages
                val draftMessages = getMessagesDraftFiltered(dids,
                                                             filterConstraint)
                for (draftMessage in draftMessages) {
                    var messageAdded = false
                    for (i in 0..messages.size - 1) {
                        if (messages[i].contact == draftMessage.contact) {
                            messages.removeAt(i)
                            messages.add(i, draftMessage)
                            messageAdded = true
                            break
                        }
                    }
                    if (!messageAdded) {
                        messages.add(0, draftMessage)
                    }
                }
                messages.sort()

                allMessages.addAll(messages)
            }

            allMessages.sort()
            return allMessages
        }
    }

    /**
     * Gets all unread messages that chronologically follow the most recent
     * outgoing message for the specified conversation.
     *
     * The resulting list is sorted by date, from least recent to most recent.
     *
     * @param conversationId The ID of the specified conversation.
     * @return All unread messages that chronologically follow the most recent
     * outgoing message for the specified conversation.
     */
    fun getMessagesUnread(conversationId: ConversationId): List<Message> {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            // Retrieve the most recent outgoing message
            var cursor = database.query(
                TABLE_MESSAGE,
                arrayOf("COALESCE(MAX($COLUMN_DATE), 0) AS $COLUMN_DATE"),
                "$COLUMN_DID=$did AND $COLUMN_CONTACT=$contact" +
                " AND $COLUMN_INCOMING=0",
                null, null, null, null)
            cursor.moveToFirst()
            var date: Long = 0
            if (!cursor.isAfterLast) {
                date = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE))
            }
            cursor.close()

            // Retrieve all unread messages with a date equal to or after the
            // most recent outgoing message
            cursor = database.query(
                TABLE_MESSAGE, messageColumns,
                "$COLUMN_DID=$did AND $COLUMN_CONTACT=$contact" +
                " AND $COLUMN_INCOMING=1 AND $COLUMN_DATE>=$date" +
                " AND $COLUMN_UNREAD=1",
                null, null, null,
                "$COLUMN_DATE ASC, $COLUMN_DATABASE_ID DESC")
            return getMessagesCursor(cursor)
        }
    }

    /**
     * Imports the database from the database import and export file path.
     */
    fun import() {
        synchronized(this) {
            val dbPath = context.getDatabasePath(Database.DATABASE_NAME)
            val backupPath = File("${dbPath.absolutePath}.backup")

            try {
                // Close database to persist it to disk
                database.close()

                // Try importing database, but restore from backup on failure
                dbPath.copyTo(backupPath, overwrite = true)
                try {
                    val importPath = getImportPath()
                    importPath.copyTo(dbPath, overwrite = true)

                    // Try refreshing database
                    database = databaseHelper.writableDatabase

                    AppIndexingService.replaceIndex(context)
                } catch (e: Exception) {
                    backupPath.copyTo(dbPath, overwrite = true)
                    throw e
                }
            } finally {
                // Refresh database
                database.close()
                database = databaseHelper.writableDatabase

                // Remove backup file
                backupPath.delete()
            }
        }
    }

    /**
     * Inserts a new outgoing message into the database with the specified
     * conversation ID and text. This message is marked as in the process of
     * being delivered.
     *
     * @param conversationId The specified conversation ID.
     * @param text The specified text.
     * @return The database ID of the inserted message.
     */
    fun insertMessageDeliveryInProgress(conversationId: ConversationId,
                                        text: String): Long {
        val did = conversationId.did
        val contact = conversationId.contact

        val values = ContentValues()
        values.putNull(COLUMN_VOIP_ID)
        values.put(COLUMN_DATE, Date().time / 1000L)
        values.put(COLUMN_INCOMING, 0L)
        values.put(COLUMN_DID, did)
        values.put(COLUMN_CONTACT, contact)
        values.put(COLUMN_MESSAGE, text)
        values.put(COLUMN_UNREAD, 0L)
        values.put(COLUMN_DELIVERED, 0L)
        values.put(COLUMN_DELIVERY_IN_PROGRESS, 1L)

        synchronized(this) {
            val databaseId = database.insertOrThrow(TABLE_MESSAGE, null,
                                                    values)
            if (databaseId == -1L) {
                throw Exception("Returned database ID was -1")
            }

            val message = getMessageDatabaseId(databaseId)
            if (message != null) {
                FirebaseAppIndex.getInstance().update(
                    AppIndexingService.getMessageBuilder(context,
                                                         message).build())
            }

            return databaseId
        }
    }

    /**
     * Inserts a new draft message into the database associated with the
     * specified conversation and containing the specified text.
     *
     * Any existing draft message with the specified conversation is
     * automatically removed. If an empty message is inserted, any existing
     * message is removed from the database.
     *
     * @param conversationId The ID of the specified conversation.
     * @param text The text of the new draft message.
     */
    fun insertMessageDraft(conversationId: ConversationId, text: String) {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            val databaseId = getDraftDatabaseIdConversation(conversationId)

            // If text is empty, then delete any existing draft message
            if (text == "") {
                if (databaseId != null) {
                    database.delete(TABLE_DRAFT,
                                    "$COLUMN_DATABASE_ID = $databaseId", null)
                }
                return
            }

            val values = ContentValues()
            if (databaseId != null) {
                values.put(COLUMN_DATABASE_ID, databaseId)
            }
            values.put(COLUMN_DID, did)
            values.put(COLUMN_CONTACT, contact)
            values.put(COLUMN_MESSAGE, text)

            database.replaceOrThrow(TABLE_DRAFT, null, values)
        }
    }

    /**
     * Inserts a new message with the specified VoIP.ms ID if one does not
     * already exist.
     *
     * @param voipId The specified VoIP.ms ID.
     * @param date The date of the message.
     * @param isIncoming Whether the message is incoming.
     * @param conversationId The conversation ID of the message.
     * @param text The text of the message.
     * @param retrieveDeletedMessages If true, then any existing message with
     * the specified VoIP.ms ID is marked as not deleted.
     * @return Whether a new message was inserted.
     */
    fun insertMessageVoipMsApi(voipId: Long, date: Date,
                               isIncoming: Boolean,
                               conversationId: ConversationId, text: String,
                               retrieveDeletedMessages: Boolean): Boolean {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            if (retrieveDeletedMessages) {
                removeDeletedVoipId(setOf(did), voipId)
            } else if (isVoipIdDeleted(did, voipId)) {
                return false
            }

            val values = ContentValues()

            val databaseId = getMessageDatabaseIdVoipId(did, voipId)
            if (databaseId != null) {
                return false
            }

            values.put(COLUMN_VOIP_ID, voipId)
            values.put(COLUMN_DATE, date.time / 1000L)
            values.put(COLUMN_INCOMING, if (isIncoming) 1L else 0L)
            values.put(COLUMN_DID, did)
            values.put(COLUMN_CONTACT, contact)
            values.put(COLUMN_MESSAGE, text)
            values.put(COLUMN_UNREAD, if (isIncoming) 1L else 0L)
            values.put(COLUMN_DELIVERED, 1L)
            values.put(COLUMN_DELIVERY_IN_PROGRESS, 0L)

            val newId = database.insertOrThrow(TABLE_MESSAGE, null, values)
            if (newId == -1L) {
                throw Exception("Returned database ID was -1")
            }

            val message = getMessageDatabaseId(newId)
            if (message != null) {
                FirebaseAppIndex.getInstance().update(
                    AppIndexingService.getMessageBuilder(context,
                                                         message).build())
            }

            return true
        }
    }

    /**
     * Returns whether the specified conversation is archived.
     *
     * @param conversationId The ID of the specified conversation.
     * @return Whether the specified conversation is archived.
     */
    fun isConversationArchived(conversationId: ConversationId): Boolean {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            val cursor = database.query(
                TABLE_ARCHIVED, archivedColumns,
                "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact",
                null, null, null, null)
            cursor.moveToFirst()
            val archived = !cursor.isAfterLast
            cursor.close()
            return archived
        }
    }

    /**
     * Returns whether the specified conversation has any messages or drafts.
     *
     * @param conversationId The ID of the specified conversation.
     * @return Whether the specified conversation has any messages or drafts.
     */
    fun isConversationEmpty(conversationId: ConversationId): Boolean {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            val cursor = database.query(
                TABLE_MESSAGE,
                messageColumns,
                "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact",
                null, null, null, null)
            cursor.moveToFirst()
            val hasMessages =
                !cursor.isAfterLast
                || getMessageDraftWithoutLock(conversationId) != null
            cursor.close()
            return hasMessages
        }
    }

    /**
     * Marks the specified conversation as archived.
     *
     * @param conversationId The ID of the specified conversation.
     */
    fun markConversationArchived(conversationId: ConversationId) {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            val databaseId = getArchivedDatabaseIdConversation(conversationId)

            val values = ContentValues()
            if (databaseId != null) {
                values.put(COLUMN_DATABASE_ID, databaseId)
            }
            values.put(COLUMN_DID, did)
            values.put(COLUMN_CONTACT, contact)
            values.put(COLUMN_ARCHIVED, "1")

            database.replaceOrThrow(TABLE_ARCHIVED, null, values)
        }
    }

    /**
     * Marks the specified conversation as read.
     *
     * @param conversationId The ID of the specified conversation.
     */
    fun markConversationRead(conversationId: ConversationId) {
        val did = conversationId.did
        val contact = conversationId.contact

        val contentValues = ContentValues()
        contentValues.put(COLUMN_UNREAD, "0")

        synchronized(this) {
            database.update(TABLE_MESSAGE, contentValues,
                            "$COLUMN_CONTACT=$contact AND $COLUMN_DID=$did",
                            null)
        }
    }

    /**
     * Marks the specified conversation as unarchived.
     *
     * @param conversationId The ID of the specified conversation.
     */
    fun markConversationUnarchived(conversationId: ConversationId) {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            database.delete(TABLE_ARCHIVED,
                            "$COLUMN_CONTACT=$contact AND $COLUMN_DID=$did",
                            null)
        }
    }

    /**
     * Marks the specified conversation as unread.
     *
     * @param conversationId The ID of the specified conversation.
     */
    fun markConversationUnread(conversationId: ConversationId) {
        val did = conversationId.did
        val contact = conversationId.contact

        val contentValues = ContentValues()
        contentValues.put(COLUMN_UNREAD, "1")

        synchronized(this) {
            database.update(TABLE_MESSAGE, contentValues,
                            "$COLUMN_CONTACT=$contact AND $COLUMN_DID=$did",
                            null)
        }
    }

    /**
     * Marks the message with the specified database ID as in the process of
     * being delivered.
     *
     * @param databaseId The specified database ID.
     */
    fun markMessageDeliveryInProgress(databaseId: Long) {
        val contentValues = ContentValues()
        contentValues.put(COLUMN_DELIVERED, "0")
        contentValues.put(COLUMN_DELIVERY_IN_PROGRESS, "1")

        synchronized(this) {
            database.update(TABLE_MESSAGE, contentValues,
                            "$COLUMN_DATABASE_ID=$databaseId", null)
        }
    }

    /**
     * Marks the message with the specified database ID as having failed to
     * be sent.
     *
     * @param databaseId The specified database ID.
     */
    fun markMessageNotSent(databaseId: Long) {
        val contentValues = ContentValues()
        contentValues.put(COLUMN_DELIVERED, "0")
        contentValues.put(COLUMN_DELIVERY_IN_PROGRESS, "0")

        synchronized(this) {
            database.update(TABLE_MESSAGE, contentValues,
                            "$COLUMN_DATABASE_ID=$databaseId", null)
        }
    }

    /**
     * Marks the message with the specified database ID as having been sent.
     * In addition, adds the specified VoIP.ms ID to the message.
     *
     * @param databaseId The specified database ID.
     * @param voipId The specified VoIP.ms ID.
     */
    fun markMessageSent(databaseId: Long, voipId: Long) {
        val contentValues = ContentValues()
        contentValues.put(COLUMN_VOIP_ID, voipId)
        contentValues.put(COLUMN_DELIVERED, "1")
        contentValues.put(COLUMN_DELIVERY_IN_PROGRESS, "0")
        contentValues.put(COLUMN_DATE, Date().time / 1000L)

        synchronized(this) {
            database.update(TABLE_MESSAGE, contentValues,
                            "$COLUMN_DATABASE_ID=$databaseId", null)
        }
    }

    /**
     * Removes the deleted VoIP.ms message ID associated with the specified
     * DIDs from the database.
     *
     * @param dids The specified DIDs.
     * @param voipId The specified VoIP.ms message ID.
     */
    fun removeDeletedVoipId(dids: Set<String>, voipId: Long) {
        var query = "$COLUMN_VOIP_ID = $voipId AND ("
        for (did in dids) {
            query += "$COLUMN_DID=$did OR "
        }
        if (dids.isNotEmpty()) {
            query = query.substring(0, query.length - 4) + ")"
        }

        synchronized(this) {
            database.delete(TABLE_DELETED, query, null)
        }
    }

    /**
     * Deletes the message with the specified database ID from the database.
     *
     * @param databaseId The specified database ID.
     */
    fun removeMessage(databaseId: Long) {
        synchronized(this) {
            database.delete(TABLE_MESSAGE, "$COLUMN_DATABASE_ID=$databaseId",
                            null)
        }
    }

    /**
     * Gets the database ID for the row in the archived table for the specified
     * conversation.
     *
     * @param conversationId The ID of the specified conversation.
     * @return The database ID.
     */
    private fun getArchivedDatabaseIdConversation(
        conversationId: ConversationId): Long? {
        val did = conversationId.did
        val contact = conversationId.contact

        val cursor = database.query(
            TABLE_ARCHIVED,
            archivedColumns,
            "$COLUMN_DID=$did AND $COLUMN_CONTACT=$contact",
            null, null, null, null)
        if (cursor.moveToFirst()) {
            val databaseId = cursor.getLong(cursor.getColumnIndexOrThrow(
                COLUMN_DATABASE_ID))
            cursor.close()
            return databaseId
        }
        cursor.close()
        return null
    }

    /**
     * Gets the database ID for the row in the draft table for the specified
     * conversation.
     *
     * @param conversationId The ID of the specified conversation.
     * @return The database ID.
     */
    private fun getDraftDatabaseIdConversation(
        conversationId: ConversationId): Long? {
        val did = conversationId.did
        val contact = conversationId.contact

        val cursor = database.query(
            TABLE_DRAFT,
            draftColumns,
            "$COLUMN_DID=$did AND $COLUMN_CONTACT=$contact",
            null, null, null, null)
        if (cursor.moveToFirst()) {
            val databaseId = cursor.getLong(cursor.getColumnIndexOrThrow(
                COLUMN_DATABASE_ID))
            cursor.close()
            return databaseId
        }
        cursor.close()
        return null
    }

    /**
     * Gets the database ID for the row in the database with the specified
     * VoIP.ms ID.
     *
     * @param did The specified DID.
     * @param voipId The specified VoIP.ms ID.
     * @return The database ID.
     */
    private fun getMessageDatabaseIdVoipId(did: String, voipId: Long): Long? {
        val cursor = database.query(
            TABLE_MESSAGE,
            messageColumns,
            "$COLUMN_DID=$did AND $COLUMN_VOIP_ID=$voipId",
            null, null, null, null)
        if (cursor.moveToFirst()) {
            val databaseId = cursor.getLong(cursor.getColumnIndexOrThrow(
                COLUMN_DATABASE_ID))
            cursor.close()
            return databaseId
        }
        cursor.close()
        return null
    }

    /**
     * Retrieves all of the messages that can be accessed by the specified
     * cursor. This function consumes the cursor.
     *
     * @param cursor The cursor from which to retrieve the messages.
     * @return The messages that could be accessed by the specified cursor.
     */
    private fun getMessagesCursor(cursor: Cursor): MutableList<Message> {
        val messages = mutableListOf<Message>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val message = Message(
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(COLUMN_DATABASE_ID)),
                if (cursor.isNull(cursor.getColumnIndexOrThrow(
                    COLUMN_VOIP_ID))) null else cursor.getLong(
                    cursor.getColumnIndex(COLUMN_VOIP_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_INCOMING)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTACT)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UNREAD)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DELIVERED)),
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(COLUMN_DELIVERY_IN_PROGRESS)))
            messages.add(message)
            cursor.moveToNext()
        }
        cursor.close()
        return messages
    }

    /**
     * Gets the draft message for the specified conversation.
     *
     * @param conversationId The ID of the specified conversation.
     * @return The draft message for the specified conversation.
     */
    private fun getMessageDraftWithoutLock(
        conversationId: ConversationId): Message? {
        val did = conversationId.did
        val contact = conversationId.contact

        synchronized(this) {
            val cursor = database.query(
                TABLE_DRAFT, draftColumns,
                "$COLUMN_DID = $did AND $COLUMN_CONTACT = $contact",
                null, null, null, null)
            cursor.moveToFirst()
            var message: Message? = null
            if (!cursor.isAfterLast) {
                message = Message(
                    cursor.getString(cursor.getColumnIndexOrThrow(
                        COLUMN_DID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(
                        COLUMN_CONTACT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(
                        COLUMN_MESSAGE)))
            }
            cursor.close()
            return message
        }
    }

    /**
     * Gets the draft messages for all conversations associated with the
     * specified DIDs.
     *
     * @param dids The specified DIDs.
     * @return The draft messages for all conversations associated with the
     * specified DIDs.
     */
    private fun getMessagesDraft(dids: Set<String>): List<Message> {
        var query = ""
        for (did in dids) {
            query += "$COLUMN_DID=$did OR "
        }
        if (dids.isNotEmpty()) {
            query = query.substring(0, query.length - 4)
        }
        val cursor = database.query(
            TABLE_DRAFT, draftColumns,
            query,
            null, null, null,
            "$COLUMN_DID DESC, $COLUMN_CONTACT DESC")
        cursor.moveToFirst()
        val messages = mutableListOf<Message>()
        while (!cursor.isAfterLast) {
            messages.add(Message(
                cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_DID)),
                cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_CONTACT)),
                cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_MESSAGE))))
            cursor.moveToNext()
        }
        cursor.close()
        return messages
    }

    /**
     * Gets the most recent draft message in each conversation associated
     * with the specified DIDs that matches a specified filter constraint.
     *
     * @param dids The specified DIDs.
     * @param filterConstraint The filter constraint.
     * @return The most recent draft message in each conversation associated
     * with the specified DIDs that matches a specified filter constraint.
     */
    private fun getMessagesDraftFiltered(dids: Set<String>,
                                         filterConstraint: String): List<Message> {
        val messages = getMessagesDraft(dids)
        val filteredMessages = messages
            .filter { it.text.toLowerCase().contains(filterConstraint) }
            .toMutableList()
        return filteredMessages
    }

    /**
     * Inserts the deleted VoIP.ms message ID associated with the specified
     * DID into the database.
     *
     * @param did The specified DID.
     * @param voipId The specified VoIP.ms message ID.
     */
    private fun insertVoipIdDeleted(did: String, voipId: Long) {
        if (isVoipIdDeleted(did, voipId)) {
            // VoIP.ms ID and DID already in table
            return
        }

        val values = ContentValues()
        values.put(COLUMN_VOIP_ID, voipId)
        values.put(COLUMN_DID, did)

        database.replaceOrThrow(TABLE_DELETED, null, values)
    }

    /**
     * Returns whether the specified VoIP.ms message ID associated with the
     * specified DID is marked as deleted.
     *
     * @param did The specified DID.
     * @param voipId The specified VoIP.ms message ID.
     * @return Whether the specified VoIP.ms message ID associated with the
     * specified DID is marked as deleted.
     */
    private fun isVoipIdDeleted(did: String, voipId: Long): Boolean {
        val cursor = database.query(TABLE_DELETED, deletedColumns,
                                    "$COLUMN_DID = $did AND" +
                                    " $COLUMN_VOIP_ID = $voipId",
                                    null, null, null, null)
        cursor.moveToFirst()
        val deleted = !cursor.isAfterLast
        cursor.close()
        return deleted
    }

    /**
     * Subclass of the SQLiteOpenHelper class for use with the Database class.
     *
     * @param context The context to be used by SQLiteOpenHelper.
     */
    private inner class DatabaseHelper internal constructor(
        context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null,
                                             DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(DATABASE_MESSAGE_TABLE_CREATE)
            db.execSQL(DATABASE_DELETED_TABLE_CREATE)
            db.execSQL(DATABASE_DRAFT_TABLE_CREATE)
            db.execSQL(DATABASE_ARCHIVED_TABLE_CREATE)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int,
                               newVersion: Int) {
            if (oldVersion <= 5) {
                // For version 5 and below, the database was nothing more
                // than a cache so it can simply be dropped
                db.execSQL("DROP TABLE IF EXISTS sms")
                onCreate(db)
                return
            }

            // After version 5, the database must be converted; it cannot
            // be simply dropped
            if (oldVersion <= 6) {
                handleTimeConversion6(db)
            }
            if (oldVersion <= 7) {
                handleDraftAddition7(db)
            }
            if (oldVersion <= 8) {
                handleDeletedConversion8(db)
                handleDraftConversion8(db)
                handleArchivedAddition8(db)
                handleColumnRemoval8(db)
            }
        }

        /**
         * Handles DST conversion from version 6 onwards.
         *
         * @param db The database to use.
         */
        fun handleTimeConversion6(db: SQLiteDatabase) {
            // In version 6, dates from VoIP.ms were parsed as if
            // they did not have daylight savings time when
            // they actually did; the code below re-parses the dates
            // properly
            val table = "sms"
            val columns = arrayOf("DatabaseId", "VoipId", "Date",
                                  "Type", "Did", "Contact", "Text",
                                  "Unread", "Deleted", "Delivered",
                                  "DeliveryInProgress")
            val cursor = db.query(table, columns, null, null, null,
                                  null,
                                  null)
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val databaseId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(columns[0]))
                val voipId = if (cursor.isNull(cursor.getColumnIndexOrThrow(
                    columns[1]))) null else cursor.getLong(
                    cursor.getColumnIndex(columns[1]))
                var date = cursor.getLong(
                    cursor.getColumnIndexOrThrow(columns[2]))
                val type = cursor.getLong(
                    cursor.getColumnIndexOrThrow(columns[3]))
                val did = cursor.getString(
                    cursor.getColumnIndexOrThrow(columns[4]))
                val contact = cursor.getString(
                    cursor.getColumnIndexOrThrow(columns[5]))
                val text = cursor.getString(
                    cursor.getColumnIndexOrThrow(columns[6]))
                val unread = cursor.getLong(
                    cursor.getColumnIndexOrThrow(columns[7]))
                val deleted = cursor.getLong(
                    cursor.getColumnIndexOrThrow(columns[8]))
                val delivered = cursor.getLong(
                    cursor.getColumnIndexOrThrow(columns[9]))
                val deliveryInProgress = cursor.getLong(
                    cursor.getColumnIndexOrThrow(columns[10]))

                // Incorrect date has an hour removed outside of
                // daylight savings time
                var dateObj = Date(date * 1000)

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                // Incorrect date converted to UTC with an hour
                // removed outside of daylight savings time
                val dateString = sdf.format(dateObj)

                // Incorrect date string is parsed as if it were
                // EST/EDT; it is now four hours ahead of EST/EDT
                // at all times
                sdf.timeZone = TimeZone.getTimeZone("America/New_York")
                dateObj = sdf.parse(dateString)

                val calendar = Calendar.getInstance(
                    TimeZone.getTimeZone("America/New_York"), Locale.US)
                calendar.time = dateObj
                calendar.add(Calendar.HOUR_OF_DAY, -4)
                // Date is now stored correctly
                date = calendar.time.time / 1000L

                val values = ContentValues()
                values.put(columns[0], databaseId)
                values.put(columns[1], voipId)
                values.put(columns[2], date)
                values.put(columns[3], type)
                values.put(columns[4], did)
                values.put(columns[5], contact)
                values.put(columns[6], text)
                values.put(columns[7], unread)
                values.put(columns[8], deleted)
                values.put(columns[9], delivered)
                values.put(columns[10], deliveryInProgress)

                db.replace(table, null, values)
                cursor.moveToNext()
            }
            cursor.close()
        }

        /**
         * Handles the addition of a draft feature from version 7 onwards.
         *
         * @param db The database to use.
         */
        fun handleDraftAddition7(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE sms" +
                       " ADD Draft INTEGER NOT NULL DEFAULT(0)")
        }

        /**
         * Handles a change in the way deleted messages are tracked from version
         * 8 onwards.
         *
         * @param db The database to use.
         */
        fun handleDeletedConversion8(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE deleted(" +
                       "DatabaseId INTEGER PRIMARY KEY AUTOINCREMENT" +
                       " NOT NULL," +
                       "VoipId INTEGER NOT NULL," +
                       "Did TEXT NOT NULL)")

            val table = "sms"
            val columns = arrayOf("DatabaseId", "VoipId", "Date",
                                  "Type", "Did", "Contact", "Text",
                                  "Unread", "Deleted", "Delivered",
                                  "DeliveryInProgress", "Draft")
            val cursor = db.query(table, columns, "Deleted=1",
                                  null, null, null, null)
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val voipId = (if (cursor.isNull(cursor.getColumnIndexOrThrow(
                    columns[1]))) null else cursor.getLong(
                    cursor.getColumnIndex(columns[1]))) ?: continue
                val did = cursor.getString(cursor.getColumnIndexOrThrow(
                    columns[4]))

                val values = ContentValues()
                values.put(COLUMN_VOIP_ID, voipId)
                values.put(COLUMN_DID, did)
                db.replaceOrThrow(TABLE_DELETED, null, values)

                cursor.moveToNext()
            }
            cursor.close()
            db.delete(table, "Deleted=1", null)
        }

        /**
         * Handles a change in the way draft messages are tracked from version
         * 8 onwards.
         *
         * @param db The database to use.
         */
        fun handleDraftConversion8(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE draft(" +
                       "DatabaseId INTEGER PRIMARY KEY AUTOINCREMENT" +
                       " NOT NULL," +
                       "Did TEXT NOT NULL," +
                       "Contact TEXT NOT NULL," +
                       "Text TEXT NOT NULL)")

            val table = "sms"
            val columns = arrayOf("DatabaseId", "VoipId", "Date",
                                  "Type", "Did", "Contact", "Text",
                                  "Unread", "Deleted", "Delivered",
                                  "DeliveryInProgress", "Draft")
            val cursor = db.query(table, columns, "Draft=1",
                                  null, null, null, null)
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val did = cursor.getString(cursor.getColumnIndexOrThrow(
                    columns[4]))
                val contact = cursor.getString(cursor.getColumnIndexOrThrow(
                    columns[5]))
                val text = cursor.getString(cursor.getColumnIndexOrThrow(
                    columns[6]))

                if (text != "") {
                    val values = ContentValues()
                    values.put(COLUMN_DID, did)
                    values.put(COLUMN_CONTACT, contact)
                    values.put(COLUMN_MESSAGE, text)
                    db.replaceOrThrow(TABLE_DRAFT, null, values)
                }

                cursor.moveToNext()
            }
            cursor.close()
            db.delete(table, "Draft=1", null)
        }

        /**
         * Handles the addition of a archived feature from version 8 onwards.
         *
         * @param db The database to use.
         */
        fun handleArchivedAddition8(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE archived(" +
                       "DatabaseId INTEGER PRIMARY KEY AUTOINCREMENT" +
                       " NOT NULL," +
                       "Did TEXT NOT NULL," +
                       "Contact TEXT NOT NULL," +
                       "Archived INTEGER NOT NULL)")
        }

        /**
         * Handles the removal of the draft and deleted columns from the main
         * table from version 8 onwards.
         *
         * @param db The database to use.
         */
        fun handleColumnRemoval8(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE sms RENAME TO sms_backup")
            db.execSQL("CREATE TABLE sms(" +
                       "DatabaseId INTEGER PRIMARY KEY AUTOINCREMENT NOT" +
                       " NULL," +
                       "VoipId INTEGER," +
                       "Date INTEGER NOT NULL," +
                       "Type INTEGER NOT NULL," +
                       "Did TEXT NOT NULL," +
                       "Contact TEXT NOT NULL," +
                       "Text TEXT NOT NULL," +
                       "Unread INTEGER NOT NULL," +
                       "Delivered INTEGER NOT NULL," +
                       "DeliveryInProgress INTEGER NOT NULL)")
            db.execSQL("INSERT INTO sms SELECT" +
                       " DatabaseId, VoipId, Date, Type, Did," +
                       " Contact, Text, Unread, Delivered," +
                       " DeliveryInProgress" +
                       " FROM sms_backup")
            db.execSQL("DROP TABLE sms_backup")
        }

        private val DATABASE_MESSAGE_TABLE_CREATE =
            "CREATE TABLE " + TABLE_MESSAGE + "(" +
            COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT" +
            " NOT NULL," +
            COLUMN_VOIP_ID + " INTEGER," +
            COLUMN_DATE + " INTEGER NOT NULL," +
            COLUMN_INCOMING + " INTEGER NOT NULL," +
            COLUMN_DID + " TEXT NOT NULL," +
            COLUMN_CONTACT + " TEXT NOT NULL," +
            COLUMN_MESSAGE + " TEXT NOT NULL," +
            COLUMN_UNREAD + " INTEGER NOT NULL," +
            COLUMN_DELIVERED + " INTEGER NOT NULL," +
            COLUMN_DELIVERY_IN_PROGRESS + " INTEGER NOT NULL)"
        private val DATABASE_DELETED_TABLE_CREATE =
            "CREATE TABLE " + TABLE_DELETED + "(" +
            COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT" +
            " NOT NULL," +
            COLUMN_VOIP_ID + " INTEGER NOT NULL," +
            COLUMN_DID + " TEXT NOT NULL)"
        private val DATABASE_DRAFT_TABLE_CREATE =
            "CREATE TABLE " + TABLE_DRAFT + "(" +
            COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT" +
            " NOT NULL," +
            COLUMN_DID + " TEXT NOT NULL," +
            COLUMN_CONTACT + " TEXT NOT NULL," +
            COLUMN_MESSAGE + " TEXT NOT NULL)"
        private val DATABASE_ARCHIVED_TABLE_CREATE =
            "CREATE TABLE " + TABLE_ARCHIVED + "(" +
            COLUMN_DATABASE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT" +
            " NOT NULL," +
            COLUMN_DID + " TEXT NOT NULL," +
            COLUMN_CONTACT + " TEXT NOT NULL," +
            COLUMN_ARCHIVED + " INTEGER NOT NULL)"
    }

    companion object {
        val DATABASE_NAME = "sms.db"
        private val DATABASE_VERSION = 9

        private val TABLE_MESSAGE = "sms"
        private val TABLE_DELETED = "deleted"
        private val TABLE_DRAFT = "draft"
        private val TABLE_ARCHIVED = "archived"

        val COLUMN_DATABASE_ID = "DatabaseId"
        val COLUMN_VOIP_ID = "VoipId"
        val COLUMN_DATE = "Date"
        val COLUMN_INCOMING = "Type"
        val COLUMN_DID = "Did"
        val COLUMN_CONTACT = "Contact"
        val COLUMN_MESSAGE = "Text"
        val COLUMN_UNREAD = "Unread"
        val COLUMN_DELIVERED = "Delivered"
        val COLUMN_DELIVERY_IN_PROGRESS = "DeliveryInProgress"
        val COLUMN_ARCHIVED = "Archived"

        private val messageColumns = arrayOf(COLUMN_DATABASE_ID,
                                             COLUMN_VOIP_ID,
                                             COLUMN_DATE,
                                             COLUMN_INCOMING,
                                             COLUMN_DID,
                                             COLUMN_CONTACT,
                                             COLUMN_MESSAGE,
                                             COLUMN_UNREAD,
                                             COLUMN_DELIVERED,
                                             COLUMN_DELIVERY_IN_PROGRESS)
        private val deletedColumns = arrayOf(COLUMN_DATABASE_ID,
                                             COLUMN_VOIP_ID,
                                             COLUMN_DID)
        private val draftColumns = arrayOf(COLUMN_DATABASE_ID,
                                           COLUMN_DID,
                                           COLUMN_CONTACT,
                                           COLUMN_MESSAGE)
        private val archivedColumns = arrayOf(COLUMN_DATABASE_ID,
                                              COLUMN_DID,
                                              COLUMN_CONTACT,
                                              COLUMN_ARCHIVED)

        private var instance: Database? = null

        /**
         * Gets the sole instance of the Database class. Initializes the
         * instance if it does not already exist.
         *
         * @param context The context used to initialize the database.
         * @return The sole instance of the Database class.
         */
        fun getInstance(context: Context): Database {
            if (instance == null) {
                instance = Database(context.applicationContext)
            }
            return instance!!
        }
    }
}
