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

package net.kourlas.voipms_sms.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.kourlas.voipms_sms.sms.Message

@Entity(tableName = Draft.TABLE_NAME)
class Draft(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_DATABASE_ID) val databaseId: Long = 0,
    @ColumnInfo(name = COLUMN_DID) val did: String,
    @ColumnInfo(name = COLUMN_CONTACT) val contact: String,
    @ColumnInfo(name = COLUMN_MESSAGE) val text: String
) {
    fun toMessage(): Message = Message(this)

    companion object {
        const val TABLE_NAME = "draft"

        const val COLUMN_DATABASE_ID = "DatabaseId"
        const val COLUMN_DID = "Did"
        const val COLUMN_CONTACT = "Contact"
        const val COLUMN_MESSAGE = "Text"
    }
}