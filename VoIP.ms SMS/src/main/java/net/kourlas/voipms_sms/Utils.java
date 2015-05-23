/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
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

package net.kourlas.voipms_sms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Utils {

    public static String getContactName(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
            cursor.close();
            return name;
        } else {
            return null;
        }
    }

    public static String getContactPhotoUri(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI}, null, null, null);
        if (cursor.moveToFirst()) {
            String photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
            cursor.close();
            return photoUri;
        } else {
            cursor.close();
            return null;
        }
    }

    public static String getFormattedDate(Date smsDate) {
        Calendar smsCalendar = Calendar.getInstance();
        smsCalendar.setTimeInMillis(smsDate.getTime());

        Calendar oneMinuteAgo = Calendar.getInstance();
        oneMinuteAgo.add(Calendar.MINUTE, -1);
        if (oneMinuteAgo.getTime().before(smsDate)) {
            // Last minute: X seconds ago
            long seconds = (Calendar.getInstance().getTime().getTime() - smsCalendar.getTime().getTime()) / 1000;
            if (seconds < 10) {
                return "Just now";
            } else {
                return seconds + " seconds ago";
            }
        }

        Calendar oneHourAgo = Calendar.getInstance();
        oneHourAgo.add(Calendar.HOUR_OF_DAY, -1);
        if (oneHourAgo.getTime().before(smsDate)) {
            // Last hour: X minutes ago
            long minutes = (Calendar.getInstance().getTime().getTime() - smsCalendar.getTime().getTime()) / (1000 * 60);
            if (minutes == 1) {
                return "1 minute ago";
            } else {
                return minutes + " minutes ago";
            }
        }

        if (compareDateWithoutTime(Calendar.getInstance(), smsCalendar) == 0) {
            // Today: h:mm a
            DateFormat format = new SimpleDateFormat("h:mm a", Locale.getDefault());
            return format.format(smsDate);
        }

        if (Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) == smsCalendar.get(
                Calendar.WEEK_OF_YEAR) && Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(
                Calendar.YEAR) == smsCalendar.get(Calendar.YEAR)) {
            // This week: EEE
            DateFormat format = new SimpleDateFormat("EEE", Locale.getDefault());
            return format.format(smsDate);
        }

        if (Calendar.getInstance().get(Calendar.YEAR) == smsCalendar.get(Calendar.YEAR)) {
            // This year: EEE, MMM d
            DateFormat format = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
            return format.format(smsDate);
        }

        // Any: EEE, MMM d, yyyy
        DateFormat format = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        return format.format(smsDate);
    }

    private static int compareDateWithoutTime(Calendar c1, Calendar c2) {
        if (c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR))
            return c1.get(Calendar.YEAR) - c2.get(Calendar.YEAR);
        if (c1.get(Calendar.MONTH) != c2.get(Calendar.MONTH))
            return c1.get(Calendar.MONTH) - c2.get(Calendar.MONTH);
        return c1.get(Calendar.DAY_OF_MONTH) - c2.get(Calendar.DAY_OF_MONTH);
    }

    public static String getFormattedPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() == 10) {
            MessageFormat phoneNumberFormat = new MessageFormat("({0}) {1}-{2}");
            String[] phoneNumberArray = new String[]{phoneNumber.substring(0, 3), phoneNumber.substring(3, 6),
                    phoneNumber.substring(6)};
            phoneNumber = phoneNumberFormat.format(phoneNumberArray);
        }
        return phoneNumber;
    }
}
