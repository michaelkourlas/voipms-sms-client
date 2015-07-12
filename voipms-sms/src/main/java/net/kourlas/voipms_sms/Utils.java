/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas and other contributors
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewOutlineProvider;
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
        }
        else {
            cursor.close();
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
        }
        else {
            cursor.close();
            return null;
        }
    }

    public static String getFormattedDate(Context applicationContext, Date smsDate, boolean conversationsView) {
        Calendar smsCalendar = Calendar.getInstance();
        smsCalendar.setTimeInMillis(smsDate.getTime());

        Calendar oneMinuteAgo = Calendar.getInstance();
        oneMinuteAgo.add(Calendar.MINUTE, -1);
        if (oneMinuteAgo.getTime().before(smsDate)) {
            // Last minute: X seconds ago
            long seconds = (Calendar.getInstance().getTime().getTime() - smsCalendar.getTime().getTime()) / 1000;
            if (seconds < 10) {
                return applicationContext.getString(R.string.utils_date_just_now);
            }
            else {
                return seconds + " " + applicationContext.getString(R.string.utils_date_seconds_ago);
            }
        }

        Calendar oneHourAgo = Calendar.getInstance();
        oneHourAgo.add(Calendar.HOUR_OF_DAY, -1);
        if (oneHourAgo.getTime().before(smsDate)) {
            // Last hour: X minutes ago
            long minutes = (Calendar.getInstance().getTime().getTime() - smsCalendar.getTime().getTime()) / (1000 * 60);
            if (minutes == 1) {
                return applicationContext.getString(R.string.utils_date_one_minute_ago);
            }
            else {
                return minutes + " " + applicationContext.getString(R.string.utils_date_minutes_ago);
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
            if (conversationsView) {
                // This week: EEE
                DateFormat format = new SimpleDateFormat("EEE", Locale.getDefault());
                return format.format(smsDate);
            }
            else {
                // This week: EEE h:mm a
                DateFormat format = new SimpleDateFormat("EEE h:mm a", Locale.getDefault());
                return format.format(smsDate);
            }
        }

        if (Calendar.getInstance().get(Calendar.YEAR) == smsCalendar.get(Calendar.YEAR)) {
            if (conversationsView) {
                // This year: MMM d
                DateFormat format = new SimpleDateFormat("MMM d", Locale.getDefault());
                return format.format(smsDate);
            }
            else {
                // This year: MMM d h:mm a
                DateFormat format = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
                return format.format(smsDate);
            }
        }

        if (conversationsView) {
            // Any: MMM d, yyyy
            DateFormat format = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return format.format(smsDate);
        }
        else {
            // Any: MMM d, yyyy h:mm a
            DateFormat format = new SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault());
            return format.format(smsDate);
        }
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
        else if (phoneNumber.length() == 11 && phoneNumber.charAt(0) == '1') {
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

    public static void applyCircularMask(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            view.setClipToOutline(true);
        }
    }

    public static Bitmap applyCircularMask(Bitmap bitmap) {
        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, paint);
        paint.setAntiAlias(true);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public static void applyRoundedCornersMask(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 15);
                }
            });
            view.setClipToOutline(true);
        }
    }

    public static boolean isNetworkConnectionAvailable(Context applicationContext) {
        ConnectivityManager connMgr = (ConnectivityManager) applicationContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public static void showInfoDialog(Activity activity, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(text);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }
}
