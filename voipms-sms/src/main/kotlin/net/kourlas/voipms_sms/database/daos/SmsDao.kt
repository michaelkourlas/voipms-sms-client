/*
 * VoIP.ms SMS
 * Copyright (C) 2021 Michael Kourlas
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

package net.kourlas.voipms_sms.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import net.kourlas.voipms_sms.database.entities.Sms
import net.kourlas.voipms_sms.sms.ConversationId

@Dao
interface SmsDao {
    @Query("DELETE FROM ${Sms.TABLE_NAME}")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DATABASE_ID} = :id"
    )
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact"
    )
    suspend fun deleteConversation(did: String, contact: String)

    @Query(
        "DELETE FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} NOT IN(:dids)"
    )
    suspend fun deleteWithoutDids(dids: Set<String>)

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} IN(:dids) ORDER BY ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC"
    )
    suspend fun getAll(dids: Set<String>): List<Sms>

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DATABASE_ID} = :id"
    )
    suspend fun getById(id: Long): Sms?

    @Query(
        "SELECT DISTINCT ${Sms.COLUMN_CONTACT}, ${Sms.COLUMN_DID} FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} IN(:dids)"
    )
    suspend fun getConversationIds(dids: Set<String>): List<ConversationId>

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact AND ${Sms.COLUMN_TEXT} LIKE '%' || :filterConstraint || '%' ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} DESC, ${Sms.COLUMN_DATE} DESC, ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC"
    )
    suspend fun getConversationMessagesFiltered(
        did: String, contact: String,
        filterConstraint: String
    ): List<Sms>

    @Query(
        "SELECT COUNT(*) FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact AND ${Sms.COLUMN_TEXT} LIKE '%' || :filterConstraint || '%' ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} DESC, ${Sms.COLUMN_DATE} DESC, ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC"
    )
    suspend fun getConversationMessagesFilteredCount(
        did: String,
        contact: String,
        filterConstraint: String
    ): Long

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact AND ${Sms.COLUMN_TEXT} LIKE '%' || :filterConstraint || '%' ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} DESC, ${Sms.COLUMN_DATE} DESC, ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC LIMIT :itemLimit"
    )
    suspend fun getConversationMessagesFilteredWithLimit(
        did: String,
        contact: String,
        filterConstraint: String,
        itemLimit: Long
    ): List<Sms>

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact AND ${Sms.COLUMN_INCOMING} = 1 AND ${Sms.COLUMN_DATE} >= :date AND ${Sms.COLUMN_UNREAD} = 1 ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} ASC, ${Sms.COLUMN_DATE} ASC, ${Sms.COLUMN_VOIP_ID} ASC, ${Sms.COLUMN_DATABASE_ID} ASC"
    )
    suspend fun getConversationMessagesUnreadAfterDate(
        did: String,
        contact: String,
        date: Long
    ): List<Sms>

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact"
    )
    suspend fun getConversationMessagesUnsorted(
        did: String,
        contact: String
    ): List<Sms>

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} DESC, ${Sms.COLUMN_DATE} DESC, ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC LIMIT 1"
    )
    suspend fun getConversationMessageMostRecent(
        did: String,
        contact: String
    ): Sms?

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact AND (${Sms.COLUMN_TEXT} LIKE '%' || :filterConstraint || '%' COLLATE NOCASE) ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} DESC, ${Sms.COLUMN_DATE} DESC, ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC LIMIT 1"
    )
    suspend fun getConversationMessageMostRecentFiltered(
        did: String,
        contact: String,
        filterConstraint: String
    ): Sms?

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact AND (${Sms.COLUMN_TEXT} LIKE '%' || :filterConstraint || '%' COLLATE NOCASE OR ${Sms.COLUMN_CONTACT} LIKE '%' || :numericFilterConstraint || '%' OR ${Sms.COLUMN_DID} LIKE '%' || :numericFilterConstraint || '%')  ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} DESC, ${Sms.COLUMN_DATE} DESC, ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC LIMIT 1"
    )
    suspend fun getConversationMessageMostRecentFiltered(
        did: String,
        contact: String,
        filterConstraint: String,
        numericFilterConstraint: String
    ): Sms?

    @Query(
        "SELECT COALESCE(MAX(${Sms.COLUMN_DATE}), 0) AS ${Sms.COLUMN_DATE} FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact AND ${Sms.COLUMN_INCOMING} = 0"
    )
    suspend fun getConversationMessageDateMostRecentOutgoing(
        did: String,
        contact: String
    ): Long?

    @Query(
        "SELECT ${Sms.COLUMN_DATABASE_ID} FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_VOIP_ID} = :voipId"
    )
    suspend fun getIdByVoipId(did: String, voipId: Long): Long?

    @Query("SELECT DISTINCT ${Sms.COLUMN_DID} FROM ${Sms.TABLE_NAME}")
    suspend fun getDids(): List<String>

    @Query(
        "SELECT * FROM ${Sms.TABLE_NAME} WHERE ${Sms.COLUMN_DID} IN(:dids) ORDER BY ${Sms.COLUMN_DELIVERY_IN_PROGRESS} DESC, ${Sms.COLUMN_DATE} DESC, ${Sms.COLUMN_VOIP_ID} DESC, ${Sms.COLUMN_DATABASE_ID} DESC LIMIT 1"
    )
    suspend fun getMostRecent(dids: Set<String>): Sms?

    @Insert
    suspend fun insert(sms: Sms): Long

    @Query(
        "UPDATE ${Sms.TABLE_NAME} SET ${Sms.COLUMN_UNREAD} = 0 WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact"
    )
    suspend fun markConversationRead(did: String, contact: String)

    @Query(
        "UPDATE ${Sms.TABLE_NAME} SET ${Sms.COLUMN_UNREAD} = 1 WHERE ${Sms.COLUMN_DID} = :did AND ${Sms.COLUMN_CONTACT} = :contact"
    )
    suspend fun markConversationUnread(did: String, contact: String)

    @Query(
        "UPDATE ${Sms.TABLE_NAME} SET ${Sms.COLUMN_DELIVERED} = 0, ${Sms.COLUMN_DELIVERY_IN_PROGRESS} = 1 WHERE ${Sms.COLUMN_DATABASE_ID} = :id"
    )
    suspend fun markMessageDeliveryInProgress(id: Long)

    @Query(
        "UPDATE ${Sms.TABLE_NAME} SET ${Sms.COLUMN_DELIVERED} = 0, ${Sms.COLUMN_DELIVERY_IN_PROGRESS} = 0 WHERE ${Sms.COLUMN_DATABASE_ID} = :id"
    )
    suspend fun markMessageNotSent(id: Long)

    @Query(
        "UPDATE ${Sms.TABLE_NAME} SET ${Sms.COLUMN_VOIP_ID} = :voipId, ${Sms.COLUMN_DELIVERED} = 1, ${Sms.COLUMN_DELIVERY_IN_PROGRESS} = 0, ${Sms.COLUMN_DATE} = :date WHERE ${Sms.COLUMN_DATABASE_ID} = :id"
    )
    suspend fun markMessageSent(id: Long, voipId: Long, date: Long)
}