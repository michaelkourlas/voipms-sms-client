/*
 * VoIP.ms SMS
 * Copyright © 2015 Michael Kourlas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.kourlas.voipms_sms.model;

import android.support.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Sms implements Comparable<Sms> {
    private final long id;
    private Date date;
    private Type type;
    private String did;
    private String contact;
    private String message;
    private boolean isUnread;

    /**
     * Initializes a new instance of the Sms class. This constructor is used to re-create SMS objects from the database.
     *
     * @param id      The ID associated with the SMS.
     * @param date    The date the SMS was received.
     * @param type    The type of SMS (incoming or outgoing).
     * @param did     The local phone number associated with the SMS.
     * @param contact The remote phone number associated with the SMS.
     * @param message The text of the SMS.
     */
    public Sms(long id, long date, long type, String did, String contact, String message, long isUnread) {
        this.id = id;
        this.date = new Date(date * 1000);
        this.type = type == 1 ? Type.INCOMING : Type.OUTGOING;
        this.did = did;
        this.contact = contact;
        this.message = message;
        this.isUnread = isUnread == 1;
    }

    /**
     * Initializes a new instance of the Sms class. This constructor is used to create SMS objects from data collected
     * from the VoIP.ms API. SMSes are unread by default.
     *
     * @param id      The ID associated with the SMS.
     * @param date    The date the SMS was received.
     * @param type    The type of SMS (incoming or outgoing).
     * @param did     The local phone number associated with the SMS.
     * @param contact The remote phone number associated with the SMS.
     * @param message The text of the SMS.
     * @throws ParseException when the date parameter does not contain a valid date string from the VoIP.ms API.
     */
    public Sms(String id, String date, String type, String did, String contact, String message) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        this.id = Long.parseLong(id);
        this.date = sdf.parse(date);
        this.type = type.equals("1") ? Type.INCOMING : Type.OUTGOING;
        this.did = did;
        this.contact = contact;
        this.message = message;
        this.isUnread = type.equals("1");
    }

    /**
     * Gets the ID associated with the SMS.
     *
     * @return the ID associated with the SMS.
     */
    public long getId() {
        return id;
    }

    public Date getDate() {
        return date;
    }

    public long getRawDate() {
        return date.getTime() / 1000;
    }

    public Type getType() {
        return type;
    }

    public long getRawType() {
        return type == Type.INCOMING ? 1 : 0;
    }

    public String getDid() {
        return did;
    }

    public String getContact() {
        return contact;
    }

    public String getMessage() {
        return message;
    }

    public boolean isUnread() {
        return isUnread;
    }

    public void setUnread(boolean unread) {
        isUnread = unread;
    }

    public int getRawUnread() {
        return isUnread ? 1 : 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Sms) {
            Sms otherSms = (Sms) other;
            return id == otherSms.id && date.equals(otherSms.date) && type == otherSms.type &&
                    did.equals(otherSms.did) && contact.equals(otherSms.contact) && message.equals(otherSms.message);
        } else {
            return super.equals(other);
        }
    }

    @Override
    public int compareTo(@NonNull Sms another) {
        if (!this.date.before(another.date) && !this.date.after(another.date)) {
            return 0;
        } else if (this.date.before(another.date)) {
            return 1;
        } else {
            return -1;
        }
    }

    public enum Type {
        INCOMING,
        OUTGOING
    }
}
