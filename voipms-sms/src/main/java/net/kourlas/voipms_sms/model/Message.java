/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
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
import net.kourlas.voipms_sms.Database;
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
    private static long uniqueObjectIdentifierCount = 0;

    /**
     * The database ID of the message.
     */
    private final Long databaseId;

    /**
     * The ID assigned to the message by VoIP.ms.
     */
    private final Long voipId;

    /**
     * The date of the message.
     */
    private Date date;

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
     * The text of the message.
     */
    private final String text;
    private final long uniqueObjectIdentifier;
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
     * Whether or not the message is currently in the process of being delivered.
     */
    private boolean isDeliveryInProgress;

    /**
     * Initializes a new instance of the Message class. This constructor is intended for use when creating a Message
     * object using information from the application database.
     *
     * @param databaseId           The database ID of the message.
     * @param voipId               The ID assigned to the message by VoIP.ms.
     * @param date                 The UNIX timestamp of the message.
     * @param type                 The type of the message (1 for incoming, 0 for outgoing).
     * @param did                  The DID associated with the message.
     * @param contact              The contact associated with the message.
     * @param text                 The text of the message.
     * @param isUnread             Whether or not the message is unread (1 for true, 0 for false).
     * @param isDeleted            Whether or not the message has been deleted locally (1 for true, 0 for false).
     * @param isDelivered          Whether or not the message has been delivered (1 for true, 0 for false).
     * @param isDeliveryInProgress Whether or not the message is currently in the process of being delivered (1 for
     *                             true, 0 for false).
     */
    public Message(long databaseId, Long voipId, long date, long type, String did, String contact, String text,
                   long isUnread, long isDeleted, long isDelivered, long isDeliveryInProgress) {
        this.databaseId = databaseId;

        this.voipId = voipId;

        this.date = new Date(date * 1000);

        if (type != 0 && type != 1) {
            throw new IllegalArgumentException("type must be 0 or 1.");
        }
        this.type = type == 1 ? Type.INCOMING : Type.OUTGOING;

        if (!did.replaceAll("[^0-9]", "").equals(did)) {
            throw new IllegalArgumentException("did must consist only of numbers.");
        }
        this.did = did;

        if (!contact.replaceAll("[^0-9]", "").equals(contact)) {
            throw new IllegalArgumentException("contact must consist only of numbers.");
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
            throw new IllegalArgumentException("isDeliveryInProgress must be 0 or 1.");
        }
        this.isDeliveryInProgress = isDeliveryInProgress == 1;

        this.uniqueObjectIdentifier = uniqueObjectIdentifierCount++;
    }

    /**
     * Initializes a new instance of the Message class. This constructor is intended for use when creating a Message
     * object using information from the VoIP.ms API.
     *
     * @param voipId  The ID assigned to the message by VoIP.ms.
     * @param date    The UNIX timestamp of the message.
     * @param type    The type of the message (1 for incoming, 0 for outgoing).
     * @param did     The DID associated with the message.
     * @param contact The contact associated with the message.
     * @param text    The text of the message.
     */
    public Message(String voipId, String date, String type, String did, String contact, String text)
            throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));

        this.databaseId = null;

        this.voipId = Long.parseLong(voipId);

        this.date = sdf.parse(date);

        if (!type.equals("0") && !type.equals("1")) {
            throw new IllegalArgumentException("type must be 0 or 1.");
        }
        this.type = type.equals("1") ? Type.INCOMING : Type.OUTGOING;

        if (!did.replaceAll("[^0-9]", "").equals(did)) {
            throw new IllegalArgumentException("did must consist only of numbers.");
        }
        this.did = did;

        if (!contact.replaceAll("[^0-9]", "").equals(contact)) {
            throw new IllegalArgumentException("contact must consist only of numbers.");
        }
        this.contact = contact;

        this.text = text;

        this.isUnread = type.equals("1");

        this.isDeleted = false;

        this.isDelivered = true;

        this.isDeliveryInProgress = false;

        this.uniqueObjectIdentifier = uniqueObjectIdentifierCount++;
    }

    /**
     * Initializes a new instance of the Message class. This constructor is intended for use when creating a new Message
     * object that will be sent to another contact using the VoIP.ms API.
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

        if (!did.replaceAll("[^0-9]", "").equals(did)) {
            throw new IllegalArgumentException("did must consist only of numbers.");
        }
        this.did = did;

        if (!contact.replaceAll("[^0-9]", "").equals(contact)) {
            throw new IllegalArgumentException("contact must consist only of numbers.");
        }
        this.contact = contact;

        this.text = text;

        this.isUnread = false;

        this.isDeleted = false;

        this.isDelivered = true;

        this.isDeliveryInProgress = true;

        this.uniqueObjectIdentifier = uniqueObjectIdentifierCount++;
    }

    /**
     * Gets the database ID of the message. This value may be null if no ID has been yet been assigned to the message
     * (i.e. if the message has not yet been inserted into the database).
     *
     * @return The database ID of the message.
     */
    public Long getDatabaseId() {
        return databaseId;
    }

    /**
     * Gets the ID assigned to the message by VoIP.ms. This value may be null if no ID has yet been assigned to the
     * message (i.e. if the message has not yet been sent).
     *
     * @return The ID assigned to the message by VoIP.ms.
     */
    public Long getVoipId() {
        return voipId;

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
     * Gets the date of the message in database format.
     *
     * @return The date of the message in database format.
     */
    public long getDateInDatabaseFormat() {
        return date.getTime() / 1000;
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
     * Gets whether or not the message is unread.
     *
     * @return Whether or not the message is unread.
     */
    public boolean isUnread() {
        return isUnread;
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
     * Gets whether or not the message is unread in database format.
     *
     * @return Whether or not the message is unread in database format.
     */
    public int isUnreadInDatabaseFormat() {
        return isUnread ? 1 : 0;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public int isDeletedInDatabaseFormat() {
        return isDeleted ? 1 : 0;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    public void setDelivered(boolean isDelivered) {
        this.isDelivered = isDelivered;
    }

    public int isDeliveredInDatabaseFormat() {
        return isDelivered ? 1 : 0;
    }

    public boolean isDeliveryInProgress() {
        return isDeliveryInProgress;
    }

    public void setDeliveryInProgress(boolean isDeliveryInProgress) {
        this.isDeliveryInProgress = isDeliveryInProgress;
    }

    public int isDeliveryInProgressInDatabaseFormat() {
        return isDeliveryInProgress ? 1 : 0;
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
            boolean databaseIdEquals = (databaseId == null && other.databaseId == null) ||
                    (databaseId != null && other.databaseId != null && databaseId.equals(other.databaseId));
            boolean voipIdEquals = (voipId == null && other.voipId == null) ||
                    (voipId != null && other.voipId != null && voipId.equals(other.voipId));
            return databaseIdEquals && voipIdEquals && date.equals(other.date) && type == other.type &&
                    did.equals(other.did) && contact.equals(other.contact) && text.equals(other.text) &&
                    isUnread == other.isUnread && isDeleted == other.isDeleted && isDelivered == other.isDelivered &&
                    isDeliveryInProgress == other.isDeliveryInProgress;
        }
        else {
            return super.equals(o);
        }
    }

    /**
     * Compares this message to another message.
     *
     * @param another The other message.
     * @return 1 will be returned if this message's date is before the other message's date, and -1 will be returned if
     * this message's date is after the other message's date.
     * <p/>
     * If the dates of the messages are identical, 1 will be returned if this message's text is alphabetically after
     * the other message's date, and -1 will be returned if this message's text is alphabetically before the other
     * message's date.
     * <p/>
     * If the texts of the messages are identical, 1 or -1 will be returned depending on the values of the objects'
     * unique internal identifiers. These identifiers are generated at runtime and are constant for the duration of the
     * application's execution, though they will not necessarily be constant between executions. They ensure that for
     * the duration of the application's execution, messages will always have a deterministic sorting order.
     */
    @Override
    public int compareTo(@NonNull Message another) {
        if (this.date.before(another.date)) {
            return 1;
        }
        else if (this.date.after(another.date)) {
            return -1;
        }

        int textCompare = this.text.compareTo(another.text);
        if (textCompare == 1 || textCompare == -1) {
            return textCompare;
        }

        if (this.uniqueObjectIdentifier > another.uniqueObjectIdentifier) {
            return 1;
        }
        else {
            return -1;
        }
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
            jsonObject.put(Database.COLUMN_DELETED, isDeletedInDatabaseFormat());
            jsonObject.put(Database.COLUMN_DELIVERED, isDeliveredInDatabaseFormat());
            jsonObject.put(Database.COLUMN_DELIVERY_IN_PROGRESS, isDeliveryInProgressInDatabaseFormat());
            return jsonObject;
        } catch (JSONException ex) {
            // This should never happen
            throw new Error();
        }
    }

    /**
     * Represents the type of the message.
     */
    public enum Type {
        /**
         * Represents an incoming message (a message addressed to the DID).
         */
        INCOMING,
        /**
         * Represents an outgoing message (a message coming from the DID).
         */
        OUTGOING
    }
}
