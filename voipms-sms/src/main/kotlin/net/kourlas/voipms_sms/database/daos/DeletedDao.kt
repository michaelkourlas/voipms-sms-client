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
import net.kourlas.voipms_sms.database.entities.Deleted

@Dao
interface DeletedDao {
    @Query(
        "DELETE FROM ${Deleted.TABLE_NAME} WHERE ${Deleted.COLUMN_DID} NOT IN(:dids) AND ${Deleted.COLUMN_VOIP_ID} = :voipId"
    )
    suspend fun delete(dids: Set<String>, voipId: Long)

    @Query("DELETE FROM ${Deleted.TABLE_NAME}")
    suspend fun deleteAll()

    @Query(
        "DELETE FROM ${Deleted.TABLE_NAME} WHERE ${Deleted.COLUMN_DID} NOT IN(:dids)"
    )
    suspend fun deleteWithoutDids(dids: Set<String>)

    @Query(
        "SELECT * FROM ${Deleted.TABLE_NAME} WHERE ${Deleted.COLUMN_DID} = :did AND ${Deleted.COLUMN_VOIP_ID} = :voipId LIMIT 1"
    )
    suspend fun get(did: String, voipId: Long): Deleted?

    @Insert
    suspend fun insert(deleted: Deleted): Long
}