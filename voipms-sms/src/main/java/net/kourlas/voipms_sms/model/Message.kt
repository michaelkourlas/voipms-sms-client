/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2017 Michael Kourlas
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

package net.kourlas.voipms_sms.model

import net.kourlas.voipms_sms.db.Database
import net.kourlas.voipms_sms.utils.Utils
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents a single SMS message.
 */
class Message : Comparable<Message> {
    /**
     * The database ID of the message. This value may be null if no ID has
     * been yet been assigned to the message because the message has not yet
     * been inserted into the database.
     */
    val databaseId: Long?

    /**
     * The ID assigned to the message by VoIP.ms. This value may be null if no
     * ID has yet been assigned to the message because the message has not yet
     * been sent.
     */
    val voipId: Long?

    /**
     * The date of the message.
     */
    var date: Date

    /**
     * The date of the message in database format.
     */
    val dateInDatabaseFormat: Long
        get() = date.time / 1000L

    /**
     * Whether or not the message is incoming (a message addressed to the DID).
     */
    val isIncoming: Boolean

    /**
     * Whether or not the message is outgoing (a message coming from the DID).
     */
    val isOutgoing: Boolean
        get() = !isIncoming

    /**
     * Whether or not the message is incoming in database format.
     */
    val isIncomingInDatabaseFormat: Long
        get() = if (isIncoming) 1L else 0L

    /**
     * The DID associated with the message.
     */
    val did: String

    /**
     * The contact associated with the message.
     */
    val contact: String

    /**
     * The text of the message.
     */
    var text: String

    /**
     * Whether or not the message is unread.
     */
    var isUnread: Boolean = false
        get() {
            // Outgoing messages must be read
            return field && isIncoming
        }

    /**
     * Gets whether or not the message is unread in database format.

     * @return Whether or not the message is unread in database format.
     */
    val isUnreadInDatabaseFormat: Long
        get() {
            // Outgoing messages must be read
            return if (isUnread && isIncoming) 1L else 0L
        }

    /**
     * Whether or not the message is deleted.
     */
    var isDeleted: Boolean = false

    /**
     * Whether or not the message is deleted in database format.
     */
    val isDeletedInDatabaseFormat: Long
        get() = if (isDeleted) 1L else 0L

    /**
     * Whether or not the message has been delivered.
     */
    var isDelivered: Boolean = false

    /**
     * Whether or not the message has been delivered in database format.
     */
    val isDeliveredInDatabaseFormat: Long
        get() = if (isDelivered) 1L else 0L

    /**
     * Whether or not the message is currently in the process of being
     * delivered.
     */
    var isDeliveryInProgress: Boolean = false

    /**
     * Whether or not the message is being delivered in database format.
     */
    val isDeliveryInProgressInDatabaseFormat: Long
        get() = if (isDeliveryInProgress) 1L else 0L

    /**
     * Whether or not the message is a draft.
     */
    var isDraft: Boolean = false

    /**
     * Whether or not the message is a draft in database format.
     */
    val isDraftInDatabaseFormat: Long
        get() = if (isDraft) 1L else 0L

    /**
     * Initializes a new instance of the Message class. This constructor is
     * intended for use when creating a Message object using information from
     * the application database.

     * @param databaseId The database ID of the message.
     * @param voipId The ID assigned to the message by VoIP.ms.
     * @param date The UNIX timestamp of the message.
     * @param isIncoming Whether or not the message is incoming (1 for
     * incoming, 0 for outgoing).
     * @param did The DID associated with the message.
     * @param contact The contact associated with the message.
     * @param text The text of the message.
     * @param isUnread Whether or not the message is unread (1 for true, 0 for
     * false).
     * @param isDeleted Whether or not the message has been deleted locally
     * (1 for true, 0 for false).
     * @param isDelivered Whether or not the message has been delivered (1 for
     * true, 0 for false).
     * @param isDeliveryInProgress Whether or not the message is currently in
     * the process of being delivered (1 for true, 0 for false).
     * @param isDraft Whether or not the message is a draft (1 for true, 0 for
     * false).
     */
    constructor(databaseId: Long, voipId: Long?, date: Long, isIncoming: Long,
                did: String, contact: String, text: String, isUnread: Long,
                isDeleted: Long, isDelivered: Long, isDeliveryInProgress: Long,
                isDraft: Long) {
        this.databaseId = databaseId

        this.voipId = voipId

        this.date = Date(date * 1000)

        this.isIncoming = toBoolean(isIncoming)

        validatePhoneNumber(did)
        this.did = did

        validatePhoneNumber(contact)
        this.contact = contact

        this.text = text

        this.isUnread = toBoolean(isUnread)

        this.isDeleted = toBoolean(isDeleted)

        this.isDelivered = toBoolean(isDelivered)

        this.isDeliveryInProgress = toBoolean(isDeliveryInProgress)

        this.isDraft = toBoolean(isDraft)

        // Remove trailing newline found in messages retrieved from VoIP.ms
        if (this.isIncoming && this.text.endsWith("\n")) {
            this.text = text.substring(0, text.length - 1)
        }
    }

    /**
     * Initializes a new instance of the Message class. This constructor is
     * intended for use when creating a Message object using information from
     * the VoIP.ms API.
     *
     * @param voipId The ID assigned to the message by VoIP.ms.
     * @param date The UNIX timestamp of the message.
     * @param isIncoming Whether or not the message is incoming (1 for
     * incoming, 0 for outgoing).
     * @param did The DID associated with the message.
     * @param contact The contact associated with the message.
     * @param text The text of the message.
     */
    @Throws(ParseException::class)
    constructor(voipId: String, date: String, isIncoming: String, did: String,
                contact: String, text: String) {
        this.databaseId = null

        this.voipId = voipId.toLong()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                                   Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("America/New_York")
        this.date = sdf.parse(date)

        this.isIncoming = toBoolean(isIncoming)

        validatePhoneNumber(did)
        this.did = did

        validatePhoneNumber(contact)
        this.contact = contact

        this.text = text

        this.isUnread = this.isIncoming

        this.isDeleted = false

        this.isDelivered = true

        this.isDeliveryInProgress = false

        this.isDraft = false
    }

    /**
     * Initializes a new instance of the Message class. This constructor is
     * intended for use when creating a new Message object that will be sent to
     * another contact using the VoIP.ms API.

     * @param did The DID associated with the message.
     * @param contact The contact associated with the message.
     * @param text The text of the message.
     */
    constructor(did: String, contact: String, text: String) {
        this.databaseId = null

        this.voipId = null

        this.date = Date()

        this.isIncoming = false

        validatePhoneNumber(did)
        this.did = did

        validatePhoneNumber(contact)
        this.contact = contact

        this.text = text

        this.isUnread = false

        this.isDeleted = false

        this.isDelivered = true

        this.isDeliveryInProgress = true

        this.isDraft = false
    }

    /**
     * Returns true if this message and the specified message are part of the
     * same conversation.

     * @param another The other message.
     * *
     * @return True if this message and the specified message are part of the
     * * same conversation.
     */
    fun equalsConversation(another: Message): Boolean {
        return this.contact == another.contact && this.did == another.did
    }

    /**
     * Returns true if this message and the specified message have the same
     * database ID.

     * @param another The other message.
     * *
     * @return True if this message and the specified message have the same
     * * database ID.
     */
    fun equalsDatabaseId(another: Message): Boolean {
        return this.databaseId != null
               && another.databaseId != null &&
               this.databaseId == another.databaseId
    }

    /**
     * Returns a JSON version of this message.
     */
    fun toJSON(): JSONObject {
        val jsonObject = JSONObject()
        if (databaseId != null) {
            jsonObject.put(Database.COLUMN_DATABASE_ID, databaseId)
        }
        if (voipId != null) {
            jsonObject.put(Database.COLUMN_VOIP_ID, voipId)
        }
        jsonObject.put(Database.COLUMN_DATE, dateInDatabaseFormat)
        jsonObject.put(Database.COLUMN_INCOMING, isIncomingInDatabaseFormat)
        jsonObject.put(Database.COLUMN_DID, did)
        jsonObject.put(Database.COLUMN_CONTACT, contact)
        jsonObject.put(Database.COLUMN_MESSAGE, text)
        jsonObject.put(Database.COLUMN_UNREAD, isUnreadInDatabaseFormat)
        jsonObject.put(Database.COLUMN_DELETED, isDeletedInDatabaseFormat)
        jsonObject.put(Database.COLUMN_DELIVERED, isDeliveredInDatabaseFormat)
        jsonObject.put(Database.COLUMN_DELIVERY_IN_PROGRESS,
                       isDeliveryInProgressInDatabaseFormat)
        jsonObject.put(Database.COLUMN_DRAFT, isDraftInDatabaseFormat)
        return jsonObject
    }

    override fun compareTo(other: Message): Int {
        if (this.isDraft && !other.isDraft) {
            return -1
        } else if (!this.isDraft && other.isDraft) {
            return 1
        }

        if (this.date.time > other.date.time) {
            return -1
        } else if (this.date.time < other.date.time) {
            return 1
        }

        if (this.databaseId != null && other.databaseId != null) {
            if (this.databaseId > other.databaseId) {
                return -1
            } else if (this.databaseId < other.databaseId) {
                return 1
            }
        }

        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Message

        if (databaseId != other.databaseId) return false
        if (voipId != other.voipId) return false
        if (date != other.date) return false
        if (isIncoming != other.isIncoming) return false
        if (did != other.did) return false
        if (contact != other.contact) return false
        if (text != other.text) return false
        if (isDeleted != other.isDeleted) return false
        if (isDelivered != other.isDelivered) return false
        if (isDeliveryInProgress != other.isDeliveryInProgress) return false
        if (isDraft != other.isDraft) return false

        return true
    }

    override fun hashCode(): Int {
        var result = databaseId?.hashCode() ?: 0
        result = 31 * result + (voipId?.hashCode() ?: 0)
        result = 31 * result + date.hashCode()
        result = 31 * result + isIncoming.hashCode()
        result = 31 * result + did.hashCode()
        result = 31 * result + contact.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + isDelivered.hashCode()
        result = 31 * result + isDeliveryInProgress.hashCode()
        result = 31 * result + isDraft.hashCode()
        return result
    }
}

private fun validatePhoneNumber(value: String) {
    if (Utils.getDigitsOfString(value) != value) {
        throw IllegalArgumentException("value must consist only of numbers")
    }
}

private fun toBoolean(value: Long): Boolean {
    if (value != 0L && value != 1L) {
        throw IllegalArgumentException("value must be 0 or 1")
    }
    return value == 1L
}

private fun toBoolean(value: String): Boolean {
    if (value != "0" && value != "1") {
        throw IllegalArgumentException("value must be 0 or 1")
    }
    return value == "0"
}
