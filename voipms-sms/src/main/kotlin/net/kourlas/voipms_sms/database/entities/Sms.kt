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
import java.util.*

@Entity(tableName = Sms.TABLE_NAME)
data class Sms(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_DATABASE_ID) val databaseId: Long = 0,
    @ColumnInfo(name = COLUMN_VOIP_ID) val voipId: Long? = null,
    @ColumnInfo(name = COLUMN_DATE) val date: Long = Date().time / 1000L,
    @ColumnInfo(name = COLUMN_INCOMING) val incoming: Long = 0,
    @ColumnInfo(name = COLUMN_DID) val did: String = "",
    @ColumnInfo(name = COLUMN_CONTACT) val contact: String = "",
    @ColumnInfo(name = COLUMN_TEXT) val text: String = "",
    @ColumnInfo(name = COLUMN_UNREAD) val unread: Long = 0,
    @ColumnInfo(name = COLUMN_DELIVERED) val delivered: Long = 0,
    @ColumnInfo(name = COLUMN_DELIVERY_IN_PROGRESS)
    val deliveryInProgress: Long = 0
) {
    fun toMessage(): Message = Message(this)

    fun toMessage(databaseId: Long): Message = Message(this, databaseId)

    companion object {
        const val TABLE_NAME = "sms"

        const val COLUMN_DATABASE_ID = "DatabaseId"
        const val COLUMN_VOIP_ID = "VoipId"
        const val COLUMN_DATE = "Date"
        const val COLUMN_INCOMING = "Type"
        const val COLUMN_DID = "Did"
        const val COLUMN_CONTACT = "Contact"
        const val COLUMN_TEXT = "Text"
        const val COLUMN_UNREAD = "Unread"
        const val COLUMN_DELIVERED = "Delivered"
        const val COLUMN_DELIVERY_IN_PROGRESS = "DeliveryInProgress"
    }
}