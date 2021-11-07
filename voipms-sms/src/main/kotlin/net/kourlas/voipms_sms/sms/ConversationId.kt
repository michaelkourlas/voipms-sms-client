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

package net.kourlas.voipms_sms.sms

import androidx.room.ColumnInfo
import net.kourlas.voipms_sms.database.entities.Sms

/**
 * A conversation is uniquely identified by its participants, which are
 * represented by a DID and a contact phone number.
 *
 * @param did The DID associated with the conversation.
 * @param contact The contact associated with the conversation.
 */
data class ConversationId(
    @ColumnInfo(name = Sms.COLUMN_DID)
    val did: String,
    @ColumnInfo(name = Sms.COLUMN_CONTACT)
    val contact: String
) {
    /**
     * Gets a unique identifier for this conversation.
     */
    fun getId(): String {
        return "${did}_${contact}"
    }
}
