/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2016 Michael Kourlas
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

package net.kourlas.voipms_sms.model;

import android.support.annotation.NonNull;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.utils.Utils;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Represents a single SMS message.
 */
public class Message implements Comparable<Message> {
    /**
     * The database ID of the message.
     */
    private final Long databaseId;

    /**
     * The ID assigned to the message by VoIP.ms.
     */
    private final Long voipId;
    /**
     * The type of the message (incoming or outgoing).
     */
    private final Type type;
    /**
     * The DID associated with the message.
     */
    private final String did;
    /**
     * The contact associated with the message.
     */
    private final String contact;
    /**
     * The date of the message.
     */
    private Date date;
    /**
     * The text of the message.
     */
    private String text;

    /**
     * Whether or not the message is unread.
     */
    private boolean isUnread;

    /**
     * Whether or not the message is deleted.
     */
    private boolean isDeleted;

    /**
     * Whether or not the message has been delivered.
     */
    private boolean isDelivered;

    /**
     * Whether or not the message is currently in the process of being
     * delivered.
     */
    private boolean isDeliveryInProgress;

    /**
     * Whether or not the message is a draft.
     */
    private boolean isDraft;

    /**
     * Initializes a new instance of the Message class. This constructor is
     * intended for use when creating a Message object using information from
     * the application database.
     *
     * @param databaseId           The database ID of the message.
     * @param voipId               The ID assigned to the message by VoIP.ms.
     * @param date                 The UNIX timestamp of the message.
     * @param type                 The type of the message (1 for incoming,
     *                             0 for outgoing).
     * @param did                  The DID associated with the message.
     * @param contact              The contact associated with the message.
     * @param text                 The text of the message.
     * @param isUnread             Whether or not the message is unread (1 for
     *                             true, 0 for false).
     * @param isDeleted            Whether or not the message has been deleted
     *                             locally (1 for true, 0 for false).
     * @param isDelivered          Whether or not the message has been
     *                             delivered (1 for true, 0 for false).
     * @param isDeliveryInProgress Whether or not the message is currently in
     *                             the process of being delivered (1 for
     *                             true, 0 for false).
     * @param isDraft              Whether or not the message is a draft
     *                             (1 for true, 0 for false).
     */
    public Message(long databaseId, Long voipId, long date, long type,
                   String did, String contact, String text, long isUnread,
                   long isDeleted, long isDelivered, long isDeliveryInProgress,
                   long isDraft)
    {
        this.databaseId = databaseId;

        this.voipId = voipId;

        this.date = new Date(date * 1000);

        if (type != 0 && type != 1) {
            throw new IllegalArgumentException("type must be 0 or 1.");
        }
        this.type = type == 1 ? Type.INCOMING : Type.OUTGOING;

        if (!Utils.getDigitsOfString(did).equals(did)) {
            throw new IllegalArgumentException("did must consist only of"
                                               + " numbers.");
        }
        this.did = did;

        if (!Utils.getDigitsOfString(contact).equals(contact)) {
            throw new IllegalArgumentException("contact must consist only of"
                                               + " numbers.");
        }
        this.contact = contact;

        this.text = text;

        if (isUnread != 0 && isUnread != 1) {
            throw new IllegalArgumentException("isUnread must be 0 or 1.");
        }
        this.isUnread = isUnread == 1;

        if (isDeleted != 0 && isDeleted != 1) {
            throw new IllegalArgumentException("isDeleted must be 0 or 1.");
        }
        this.isDeleted = isDeleted == 1;

        if (isDelivered != 0 && isDelivered != 1) {
            throw new IllegalArgumentException("isDelivered must be 0 or 1.");
        }
        this.isDelivered = isDelivered == 1;

        if (isDeliveryInProgress != 0 && isDeliveryInProgress != 1) {
            throw new IllegalArgumentException("isDeliveryInProgress must be"
                                               + " 0 or 1.");
        }
        this.isDeliveryInProgress = isDeliveryInProgress == 1;

        if (isDraft != 0 && isDraft != 1) {
            throw new IllegalArgumentException("isDraft must be 0 or 1.");
        }
        this.isDraft = isDraft == 1;

        // Remove trailing newline found in messages retrieved from VoIP.ms
        if (this.type == Type.INCOMING && text.endsWith("\n")) {
            this.text = text.substring(0, text.length() - 1);
        }
    }

    /**
     * Initializes a new instance of the Message class. This constructor is
     * intended for use when creating a Message object using information from
     * the VoIP.ms API.
     *
     * @param voipId  The ID assigned to the message by VoIP.ms.
     * @param date    The UNIX timestamp of the message.
     * @param type    The type of the message (1 for incoming, 0 for outgoing).
     * @param did     The DID associated with the message.
     * @param contact The contact associated with the message.
     * @param text    The text of the message.
     */
    public Message(String voipId, String date, String type, String did,
                   String contact, String text)
        throws ParseException
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                                                    Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));

        this.databaseId = null;

        this.voipId = Long.parseLong(voipId);

        this.date = sdf.parse(date);

        if (!type.equals("0") && !type.equals("1")) {
            throw new IllegalArgumentException("type must be 0 or 1.");
        }
        this.type = type.equals("1") ? Type.INCOMING : Type.OUTGOING;

        if (!Utils.getDigitsOfString(did).equals(did)) {
            throw new IllegalArgumentException("did must consist only of"
                                               + " numbers.");
        }
        this.did = did;

        if (!Utils.getDigitsOfString(contact).equals(contact)) {
            throw new IllegalArgumentException("contact must consist only of"
                                               + " numbers.");
        }
        this.contact = contact;

        this.text = text;

        this.isUnread = type.equals("1");

        this.isDeleted = false;

        this.isDelivered = true;

        this.isDeliveryInProgress = false;

        this.isDraft = false;
    }

    /**
     * Initializes a new instance of the Message class. This constructor is
     * intended for use when creating a new Message object that will be sent to
     * another contact using the VoIP.ms API.
     *
     * @param did     The DID associated with the message.
     * @param contact The contact associated with the message.
     * @param text    The text of the message.
     */
    public Message(String did, String contact, String text) {
        this.databaseId = null;

        this.voipId = null;

        this.date = new Date();

        this.type = Type.OUTGOING;

        if (!Utils.getDigitsOfString(did).equals(did)) {
            throw new IllegalArgumentException("did must consist only of"
                                               + " numbers.");
        }
        this.did = did;

        if (!Utils.getDigitsOfString(contact).equals(contact)) {
            throw new IllegalArgumentException("contact must consist only of"
                                               + " numbers.");
        }
        this.contact = contact;

        this.text = text;

        this.isUnread = false;

        this.isDeleted = false;

        this.isDelivered = true;

        this.isDeliveryInProgress = true;

        this.isDraft = false;
    }

    /**
     * Gets the date of the message.
     *
     * @return The date of the message.
     */
    public Date getDate() {
        return date;
    }

    /**
     * Sets the date of the message.
     *
     * @param date The date of the message.
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * Gets the type of the message (incoming or outgoing).
     *
     * @return The type of the message (incoming or outgoing).
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets whether or not the message is deleted.
     *
     * @return Whether or not the message is deleted.
     */
    public boolean isDeleted() {
        return isDeleted;
    }

    /**
     * Gets whether or not the message has been delivered.
     *
     * @return Whether or not the message has been delivered.
     */
    public boolean isDelivered() {
        return isDelivered;
    }

    /**
     * Gets whether or not the message is being delivered.
     *
     * @return Whether or not the message is being delivered.
     */
    public boolean isDeliveryInProgress() {
        return isDeliveryInProgress;
    }

    /**
     * Gets whether or not the message is a draft.
     *
     * @return Whether or not the message is a draft.
     */
    public boolean isDraft() {
        return isDraft;
    }

    /**
     * Sets whether or not the message is a draft.
     *
     * @param isDraft Whether or not the message is a draft.
     */
    public void setDraft(boolean isDraft) {
        this.isDraft = isDraft;
    }

    /**
     * Returns whether or not the message is identical to another object.
     *
     * @param o The other object.
     * @return Whether or not the message is identical to another object.
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Message) {
            if (this == o) {
                return true;
            }

            Message other = (Message) o;
            boolean databaseIdEquals =
                (databaseId == null && other.databaseId == null) ||
                (databaseId != null && other.databaseId != null && databaseId
                    .equals(other.databaseId));
            boolean voipIdEquals = (voipId == null && other.voipId == null) ||
                                   (voipId != null && other.voipId != null
                                    && voipId.equals(other.voipId));
            return databaseIdEquals && voipIdEquals
                   && date.equals(other.date)
                   && type == other.type
                   && did.equals(other.did)
                   && contact.equals(other.contact)
                   && text.equals(other.text)
                   && isUnread() == other.isUnread()
                   && isDeleted == other.isDeleted
                   && isDelivered == other.isDelivered
                   && isDeliveryInProgress == other.isDeliveryInProgress
                   && isDraft == other.isDraft;
        } else {
            return super.equals(o);
        }
    }

    /**
     * Gets whether or not the message is unread.
     *
     * @return Whether or not the message is unread.
     */
    public boolean isUnread() {
        // Outgoing messages must be read
        return isUnread && type == Type.INCOMING;
    }

    /**
     * Sets whether or not the message is unread.
     *
     * @param unread Whether or not the message is unread.
     */
    public void setUnread(boolean unread) {
        isUnread = unread;
    }

    /**
     * Returns true if this message and the specified message are part of the
     * same conversation.
     *
     * @param another The other message.
     * @return True if this message and the specified message are part of the
     * same conversation.
     */
    public boolean equalsConversation(Message another) {
        return this.contact.equals(another.contact)
               && this.did.equals(another.did);
    }

    /**
     * Returns true if this message and the specified message have the same
     * database ID.
     *
     * @param another The other message.
     * @return True if this message and the specified message have the same
     * database ID.
     */
    public boolean equalsDatabaseId(Message another) {
        return this.getDatabaseId() != null
               && another.getDatabaseId() != null &&
               this.getDatabaseId()
                   .equals(another.getDatabaseId());
    }

    /**
     * Gets the database ID of the message. This value may be null if no ID has
     * been yet been assigned to the message (i.e. if the message has not yet
     * been inserted into the database).
     *
     * @return The database ID of the message.
     */
    public Long getDatabaseId() {
        return databaseId;
    }

    /**
     * Compares this message to another message. Compares according to ideal
     * sorting order for the conversations view.
     *
     * @param another The other message.
     * @return -1, 1, or 0 if this message is less than, greater than, or
     * equal to the other message.
     */
    @Override
    public int compareTo(@NonNull Message another) {
        if (this.isDraft && !another.isDraft) {
            return -1;
        } else if (!this.isDraft && another.isDraft) {
            return 1;
        }

        if (this.date.getTime() > another.date.getTime()) {
            return -1;
        } else if (this.date.getTime() < another.date.getTime()) {
            return 1;
        }

        if (this.databaseId != null && another.databaseId != null) {
            if (this.databaseId > another.databaseId) {
                return -1;
            } else if (this.databaseId < another.databaseId) {
                return 1;
            }
        }

        return 0;
    }

    /**
     * Returns a JSON version of this message.
     *
     * @return A JSON version of this message.
     */
    public JSONObject toJSON() {
        try {
            JSONObject jsonObject = new JSONObject();
            if (databaseId != null) {
                jsonObject.put(Database.COLUMN_DATABASE_ID, getDatabaseId());
            }
            if (voipId != null) {
                jsonObject.put(Database.COLUMN_VOIP_ID, getVoipId());
            }
            jsonObject.put(Database.COLUMN_DATE, getDateInDatabaseFormat());
            jsonObject.put(Database.COLUMN_TYPE, getTypeInDatabaseFormat());
            jsonObject.put(Database.COLUMN_DID, getDid());
            jsonObject.put(Database.COLUMN_CONTACT, getContact());
            jsonObject.put(Database.COLUMN_MESSAGE, getText());
            jsonObject.put(Database.COLUMN_UNREAD, isUnreadInDatabaseFormat());
            jsonObject
                .put(Database.COLUMN_DELETED, isDeletedInDatabaseFormat());
            jsonObject
                .put(Database.COLUMN_DELIVERED, isDeliveredInDatabaseFormat());
            jsonObject.put(Database.COLUMN_DELIVERY_IN_PROGRESS,
                           isDeliveryInProgressInDatabaseFormat());
            jsonObject.put(Database.COLUMN_DRAFT, isDraftInDatabaseFormat());
            return jsonObject;
        } catch (JSONException ex) {
            // This should never happen
            throw new Error();
        }
    }

    /**
     * Gets the ID assigned to the message by VoIP.ms. This value may be null
     * if no ID has yet been assigned to the  message (i.e. if the message has
     * not yet been sent).
     *
     * @return The ID assigned to the message by VoIP.ms.
     */
    public Long getVoipId() {
        return voipId;

    }

    /**
     * Gets the date of the message in database format.
     *
     * @return The date of the message in database format.
     */
    public long getDateInDatabaseFormat() {
        return date.getTime() / 1000;
    }

    /**
     * Gets the type of the message in database format.
     *
     * @return The type of the message in database format.
     */
    public long getTypeInDatabaseFormat() {
        return type == Type.INCOMING ? 1 : 0;
    }

    /**
     * Gets the DID associated with the message.
     *
     * @return The DID associated with the message.
     */
    public String getDid() {
        return did;
    }

    /**
     * Gets the contact associated with the message.
     *
     * @return The contact associated with the message.
     */
    public String getContact() {
        return contact;
    }

    /**
     * Gets the text of the message.
     *
     * @return The text of the message.
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the text of the message.
     *
     * @param text The text of the message.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets whether or not the message is unread in database format.
     *
     * @return Whether or not the message is unread in database format.
     */
    public int isUnreadInDatabaseFormat() {
        // Outgoing messages must be read
        return (isUnread && type == Type.INCOMING) ? 1 : 0;
    }

    /**
     * Gets whether or not the message is deleted in database format.
     *
     * @return Whether or not the message is deleted in database format.
     */
    public int isDeletedInDatabaseFormat() {
        return isDeleted ? 1 : 0;
    }

    /**
     * Gets whether or not the message has been delivered in database format.
     *
     * @return Whether or not the message has been delivered in databse format.
     */
    public int isDeliveredInDatabaseFormat() {
        return isDelivered ? 1 : 0;
    }

    /**
     * Gets whether or not the message is being delivered in database format.
     *
     * @return Whether or not the message is being delivered in database format.
     */
    public int isDeliveryInProgressInDatabaseFormat() {
        return isDeliveryInProgress ? 1 : 0;
    }

    /**
     * Gets whether or not the message is a draft in database format.
     *
     * @return Whether or not the message is a draft in database format.
     */
    public int isDraftInDatabaseFormat() {
        return isDraft ? 1 : 0;
    }

    /**
     * Represents the type of the message.
     */
    public enum Type {
        /**
         * Represents an outgoing message (a message coming from the DID).
         */
        OUTGOING,
        /**
         * Represents an incoming message (a message addressed to the DID).
         */
        INCOMING,
    }
}
