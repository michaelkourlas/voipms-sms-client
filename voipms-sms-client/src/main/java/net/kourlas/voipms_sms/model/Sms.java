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

    public Sms(long id, long date, long type, String did, String contact, String message, long isUnread) {
        this.id = id;
        this.date = new Date(date * 1000);
        this.type = type == 1 ? Type.INCOMING : Type.OUTGOING;
        this.did = did;
        this.contact = contact;
        this.message = message;
        this.isUnread = isUnread == 1;
    }

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
