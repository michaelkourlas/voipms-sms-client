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

package net.kourlas.voipms_sms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Utils {
    public static String getContactName(Context applicationContext, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = applicationContext.getContentResolver().query(uri, new String[]{
                ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
            cursor.close();
            return name;
        } else {
            return null;
        }
    }

    public static String getContactPhotoUri(Context applicationContext, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = applicationContext.getContentResolver().query(uri, new String[]{
                ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI}, null, null, null);
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
        } else if (phoneNumber.length() == 11 && phoneNumber.charAt(0) == '1') {
            MessageFormat phoneNumberFormat = new MessageFormat("({0}) {1}-{2}");
            String[] phoneNumberArray = new String[]{phoneNumber.substring(1, 4), phoneNumber.substring(4, 7),
                    phoneNumber.substring(7)};
            phoneNumber = phoneNumberFormat.format(phoneNumberArray);
        }

        return phoneNumber;
    }

    public static JSONObject getJson(String urlString) throws IOException, org.json.simple.parser.ParseException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(10000);
        connection.setConnectTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        StringBuilder data = new StringBuilder();
        String newLine = System.getProperty("line.separator");
        String line;
        while ((line = reader.readLine()) != null) {
            data.append(line);
            data.append(newLine);
        }
        reader.close();

        return (JSONObject) JSONValue.parseWithException(data.toString());
    }
}
