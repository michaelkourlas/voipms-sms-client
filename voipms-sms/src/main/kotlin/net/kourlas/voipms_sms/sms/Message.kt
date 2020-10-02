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

package net.kourlas.voipms_sms.sms

import com.squareup.moshi.JsonClass
import net.kourlas.voipms_sms.utils.toBoolean
import net.kourlas.voipms_sms.utils.validatePhoneNumber
import java.util.*

/**
 * Represents a single SMS message.
 *
 * @param databaseId The database ID of the message.
 * @param voipId The ID assigned to the message by VoIP.ms. This value may be null if no
 * ID has yet been assigned to the message because the message has not yet
 * been sent.
 * @param date The UNIX timestamp of the message.
 * @param isIncoming Whether or not the message is incoming (1 for
 * incoming, 0 for outgoing).
 * @param did The DID associated with the message.
 * @param contact The contact associated with the message.
 * @param text The text of the message.
 * @param isUnread Whether or not the message is unread (1 for true, 0 for
 * false).
 * @param isDelivered Whether or not the message has been delivered (1 for
 * true, 0 for false).
 * @param isDeliveryInProgress Whether or not the message is currently in
 * the process of being delivered (1 for true, 0 for false).
 * @param isDraft Whether or not the message is a draft.
 */
@JsonClass(generateAdapter = true)
class Message(val databaseId: Long, val voipId: Long?, date: Long,
              isIncoming: Long, val did: String, val contact: String,
              var text: String, isUnread: Long, isDelivered: Long,
              isDeliveryInProgress: Long, val isDraft: Boolean = false) :
    Comparable<Message> {
    // Used to construct a draft message
    constructor(did: String, contact: String, text: String) :
        this(0, 0, Date().time / 1000, 0, did, contact, text, 0, 0, 0, true)

    init {
        validatePhoneNumber(did)
        validatePhoneNumber(contact)

        // Remove training newline if one exists
        if (toBoolean(isIncoming) && this.text.endsWith("\n")) {
            this.text = text.substring(0, text.length - 1)
        }
    }

    /**
     * The conversation ID corresponding to the DID and contact in the message.
     */
    val conversationId: ConversationId
        get() = ConversationId(did, contact)

    /**
     * The date of the message.
     */
    var date: Date = Date(date * 1000)

    /**
     * Whether or not the message is incoming (a message addressed to the DID).
     */
    val isIncoming: Boolean = toBoolean(isIncoming)

    /**
     * Whether or not the message is outgoing (a message coming from the DID).
     */
    val isOutgoing: Boolean
        get() = !isIncoming

    /**
     * Whether or not the message is unread.
     */
    var isUnread: Boolean = toBoolean(isUnread)

    /**
     * Whether or not the message has been delivered.
     */
    var isDelivered: Boolean = toBoolean(isDelivered)

    /**
     * Whether or not the message is currently in the process of being
     * delivered.
     */
    var isDeliveryInProgress: Boolean = toBoolean(isDeliveryInProgress)

    /**
     * Gets the URL that can be used to access this message.
     */
    val messageUrl: String
        get() = getMessageUrl(databaseId)

    /**
     * Gets the URL that can be used to access the conversation.
     */
    val conversationUrl: String
        get() = getConversationUrl(conversationId)

    fun conversationsViewCompareTo(other: Message): Int {
        if (this.contact == other.contact && this.did == other.did) {
            return 0
        }

        if (this.date.time < other.date.time) {
            return -1
        } else if (this.date.time > other.date.time) {
            return 1
        }

        if (this.databaseId < other.databaseId) {
            return -1
        } else if (this.databaseId > other.databaseId) {
            return 1
        }

        return 0
    }

    fun conversationViewCompareTo(other: Message): Int {
        if (this.date.time < other.date.time) {
            return -1
        } else if (this.date.time > other.date.time) {
            return 1
        }

        if (this.databaseId < other.databaseId) {
            return -1
        } else if (this.databaseId > other.databaseId) {
            return 1
        }

        return 0
    }

    override fun compareTo(other: Message): Int {
        if (this.date.time > other.date.time) {
            return -1
        } else if (this.date.time < other.date.time) {
            return 1
        }

        if (this.databaseId > other.databaseId) {
            return -1
        } else if (this.databaseId < other.databaseId) {
            return 1
        }

        // Pedantic comparisons present to ensure that compareTo and equals
        // are consistent with one another

        if (this.voipId != null && other.voipId != null) {
            if (this.voipId > other.voipId) {
                return -1
            } else if (this.voipId < other.voipId) {
                return 1
            }
        } else if (this.voipId != null) {
            return -1
        } else if (other.voipId != null) {
            return 1
        }

        if (this.did > other.did) {
            return -1
        } else if (this.did < other.did) {
            return 1
        }

        if (this.contact > other.contact) {
            return -1
        } else if (this.contact < other.contact) {
            return 1
        }

        if (this.text > other.text) {
            return -1
        } else if (this.text < other.text) {
            return 1
        }

        if (this.isDraft > other.isDraft) {
            return -1
        } else if (this.isDraft < other.isDraft) {
            return 1
        }

        if (this.isIncoming > other.isIncoming) {
            return -1
        } else if (this.isIncoming < other.isIncoming) {
            return 1
        }

        if (this.isUnread > other.isUnread) {
            return -1
        } else if (this.isUnread < other.isUnread) {
            return 1
        }

        if (this.isDelivered > other.isDelivered) {
            return -1
        } else if (this.isDelivered < other.isDelivered) {
            return 1
        }

        if (this.isDeliveryInProgress > other.isDeliveryInProgress) {
            return -1
        } else if (this.isDeliveryInProgress < other.isDeliveryInProgress) {
            return 1
        }

        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Message

        if (databaseId != other.databaseId) return false
        if (voipId != other.voipId) return false
        if (did != other.did) return false
        if (contact != other.contact) return false
        if (text != other.text) return false
        if (isDraft != other.isDraft) return false
        if (date != other.date) return false
        if (isIncoming != other.isIncoming) return false
        if (isUnread != other.isUnread) return false
        if (isDelivered != other.isDelivered) return false
        if (isDeliveryInProgress != other.isDeliveryInProgress) return false

        return true
    }

    override fun hashCode(): Int {
        var result = databaseId.hashCode()
        result = 31 * result + (voipId?.hashCode() ?: 0)
        result = 31 * result + did.hashCode()
        result = 31 * result + contact.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + isDraft.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + isIncoming.hashCode()
        result = 31 * result + isUnread.hashCode()
        result = 31 * result + isDelivered.hashCode()
        result = 31 * result + isDeliveryInProgress.hashCode()
        return result
    }

    companion object {
        /**
         * Gets a URL used for Firebase indexing representing a single message.
         *
         * @param databaseId The database ID that uniquely identifies the
         * specified message.
         */
        fun getMessageUrl(
            databaseId: Long): String = "voipmssms://message?id=$databaseId"

        /**
         * Gets a URL used for Firebase indexing representing a single
         * conversation.
         *
         * @param conversationId The ID of the specified conversation.
         */
        fun getConversationUrl(
            conversationId: ConversationId): String = "voipmssms://conversation?did=${conversationId.did}" +
                                                      "&contact=${conversationId.contact}"
    }
}
