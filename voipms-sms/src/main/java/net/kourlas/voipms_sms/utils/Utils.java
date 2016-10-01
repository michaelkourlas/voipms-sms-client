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

package net.kourlas.voipms_sms.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewOutlineProvider;
import net.kourlas.voipms_sms.R;
import org.json.JSONException;
import org.json.JSONObject;

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

/**
 * Contains various common utility methods.
 */
@SuppressWarnings("WeakerAccess")
public class Utils {
    /**
     * Gets the name of a contact from the Android contacts provider, given a
     * phone number.
     *
     * @param applicationContext The application context.
     * @param phoneNumber        The phone number of the contact.
     * @return The name of the contact.
     */
    public static String getContactName(Context applicationContext,
                                        String phoneNumber)
    {
        try {
            Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber));
            Cursor cursor = applicationContext.getContentResolver().query(
                uri,
                new String[] {
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                },
                null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String name = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.PhoneLookup.DISPLAY_NAME));
                    cursor.close();
                    return name;
                } else {
                    cursor.close();
                }
            }
            return null;
        } catch (SecurityException ex) {
            return null;
        }
    }

    /**
     * Gets a URI pointing to a contact's photo, given a phone number.
     *
     * @param applicationContext The application context.
     * @param phoneNumber        The phone number of the contact.
     * @return A URI pointing to the contact's photo.
     */
    public static String getContactPhotoUri(Context applicationContext,
                                            String phoneNumber)
    {
        try {
            Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber));
            return getContactPhotoUri(applicationContext, uri);
        } catch (SecurityException ex) {
            return null;
        }
    }

    /**
     * Gets a URI pointing to a contact's photo, given the URI for that contact.
     *
     * @param applicationContext The application context.
     * @param uri                The URI of the contact.
     * @return A URI pointing to the contact's photo.
     */
    public static String getContactPhotoUri(Context applicationContext,
                                            Uri uri)
    {
        try {
            Cursor cursor = applicationContext.getContentResolver().query(
                uri, new String[] {
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
                },
                null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String photoUri = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
                    cursor.close();
                    return photoUri;
                } else {
                    cursor.close();
                }
            }

            return null;
        } catch (SecurityException ex) {
            return null;
        }
    }

    /**
     * Formats a date for display in the application.
     *
     * @param applicationContext The application context.
     * @param date               The date to format.
     * @param hideTime           Omits the time in the formatted date if true.
     * @return The formatted date.
     */
    public static String getFormattedDate(Context applicationContext, Date date,
                                          boolean hideTime)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        Calendar oneMinuteAgo = Calendar.getInstance();
        oneMinuteAgo.add(Calendar.MINUTE, -1);
        if (oneMinuteAgo.getTime().before(date)) {
            // Last minute: X seconds ago
            long seconds = (Calendar.getInstance().getTime().getTime()
                            - calendar.getTime().getTime()) / 1000;
            if (seconds < 10) {
                return applicationContext
                    .getString(R.string.utils_date_just_now);
            } else {
                return seconds + " " + applicationContext
                    .getString(R.string.utils_date_seconds_ago);
            }
        }

        Calendar oneHourAgo = Calendar.getInstance();
        oneHourAgo.add(Calendar.HOUR_OF_DAY, -1);
        if (oneHourAgo.getTime().before(date)) {
            // Last hour: X minutes ago
            long minutes = (Calendar.getInstance().getTime().getTime()
                            - calendar.getTime().getTime()) / (1000 * 60);
            if (minutes == 1) {
                return applicationContext
                    .getString(R.string.utils_date_one_minute_ago);
            } else {
                return minutes + " " + applicationContext
                    .getString(R.string.utils_date_minutes_ago);
            }
        }

        if (compareDateWithoutTime(Calendar.getInstance(), calendar) == 0) {
            // Today: h:mm a
            DateFormat format = new SimpleDateFormat("h:mm a",
                                                     Locale.getDefault());
            return format.format(date);
        }

        if (Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
            == calendar.get(Calendar.WEEK_OF_YEAR)
            && Calendar.getInstance().get(Calendar.YEAR)
               == calendar.get(Calendar.YEAR))
        {
            if (hideTime) {
                // This week: EEE
                DateFormat format = new SimpleDateFormat("EEE",
                                                         Locale.getDefault());
                return format.format(date);
            } else {
                // This week: EEE h:mm a
                DateFormat format = new SimpleDateFormat("EEE h:mm a",
                                                         Locale.getDefault());
                return format.format(date);
            }
        }

        if (Calendar.getInstance().get(Calendar.YEAR)
            == calendar.get(Calendar.YEAR))
        {
            if (hideTime) {
                // This year: MMM d
                DateFormat format = new SimpleDateFormat("MMM d",
                                                         Locale.getDefault());
                return format.format(date);
            } else {
                // This year: MMM d h:mm a
                DateFormat format = new SimpleDateFormat("MMM d, h:mm a",
                                                         Locale.getDefault());
                return format.format(date);
            }
        }

        if (hideTime) {
            // Any: MMM d, yyyy
            DateFormat format = new SimpleDateFormat("MMM d, yyyy",
                                                     Locale.getDefault());
            return format.format(date);
        } else {
            // Any: MMM d, yyyy h:mm a
            DateFormat format = new SimpleDateFormat("MMM d, yyyy, h:mm a",
                                                     Locale.getDefault());
            return format.format(date);
        }
    }

    /**
     * Returns true if two dates are equivalent (excluding times).
     *
     * @param c1 The first date to compare.
     * @param c2 The second date to compare.
     * @return True if the two dates are equivalent (excluding times).
     */
    private static int compareDateWithoutTime(Calendar c1, Calendar c2) {
        if (c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR)) {
            return c1.get(Calendar.YEAR) - c2.get(Calendar.YEAR);
        }
        if (c1.get(Calendar.MONTH) != c2.get(Calendar.MONTH)) {
            return c1.get(Calendar.MONTH) - c2.get(Calendar.MONTH);
        }
        return c1.get(Calendar.DAY_OF_MONTH) - c2.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Returns a phone number for display in the application.
     *
     * @param phoneNumber The phone number to format.
     * @return The formatted phone number.
     */
    public static String getFormattedPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() == 10) {
            MessageFormat phoneNumberFormat =
                new MessageFormat("({0}) {1}-{2}");
            String[] phoneNumberArray = new String[] {
                phoneNumber.substring(0, 3),
                phoneNumber.substring(3, 6),
                phoneNumber.substring(6)
            };
            phoneNumber = phoneNumberFormat.format(phoneNumberArray);
        } else if (phoneNumber.length() == 11 && phoneNumber.charAt(0) == '1') {
            MessageFormat phoneNumberFormat =
                new MessageFormat("({0}) {1}-{2}");
            String[] phoneNumberArray = new String[] {
                phoneNumber.substring(1, 4),
                phoneNumber.substring(4, 7),
                phoneNumber.substring(7)
            };
            phoneNumber = phoneNumberFormat.format(phoneNumberArray);
        }

        return phoneNumber;
    }

    /**
     * Retrieves a JSON object from the specified URL.
     * <p/>
     * Note that this is a blocking method; it should not be called from the
     * URI thread.
     *
     * @param urlString The URL to retrieve the JSON from.
     * @return The JSON object at the specified URL.
     * @throws IOException   If a connection to the server could not be
     *                       established.
     * @throws JSONException If the server did not return valid JSON.
     */
    public static JSONObject getJson(String urlString)
        throws IOException, JSONException
    {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(10000);
        connection.setConnectTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.connect();

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), "UTF-8"));
        StringBuilder data = new StringBuilder();
        String newLine = System.getProperty("line.separator");
        String line;
        while ((line = reader.readLine()) != null) {
            data.append(line);
            data.append(newLine);
        }
        reader.close();

        return new JSONObject(data.toString());
    }

    /**
     * Checks if the Internet connection is available.
     *
     * @param applicationContext The application context.
     * @return True if the Internet connection is available, false otherwise.
     */
    public static boolean isNetworkConnectionAvailable(
        Context applicationContext)
    {
        ConnectivityManager connMgr =
            (ConnectivityManager) applicationContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * Applies a circular mask to a view.
     * <p/>
     * Note that this method only works on Lollipop and above; it will
     * silently fail on older versions.
     *
     * @param view The view to apply the mask to.
     */
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

    /**
     * Applies a circular mask to a bitmap.
     *
     * @param bitmap The bitmap to apply the mask to.
     */
    public static Bitmap applyCircularMask(Bitmap bitmap) {
        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                                                  bitmap.getHeight(),
                                                  Bitmap.Config.ARGB_8888);
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

    /**
     * Applies a rectangular rounded corners mask to a view.
     * <p/>
     * Note that this method only works on Lollipop and above; it will
     * silently fail on older versions.
     *
     * @param view The view to apply the mask to.
     */
    public static void applyRoundedCornersMask(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline
                        .setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                                      15);
                }
            });
            view.setClipToOutline(true);
        }
    }

    /**
     * Shows a standard information dialog to the user.
     *
     * @param context The source context.
     * @param text    The text of the dialog.
     */
    public static void showInfoDialog(Context context, String text) {
        showAlertDialog(context, null, text, context.getString(R.string.ok),
                        null, null, null);
    }

    /**
     * Shows an alert dialog to the user.
     *
     * @param context              The source context.
     * @param title                The title of the dialog.
     * @param text                 The text of the dialog.
     * @param positiveButtonText   The text of the positive button of the
     *                             dialog.
     * @param positiveButtonAction The action to be taken when the positive
     *                             button is clicked.
     * @param negativeButtonText   The text of the negative button of the
     *                             dialog.
     * @param negativeButtonAction The action to be taken when the negative
     *                             button is clicked.
     */
    public static AlertDialog showAlertDialog(
        Context context,
        String title,
        String text,
        String positiveButtonText,
        DialogInterface.OnClickListener positiveButtonAction,
        String negativeButtonText,
        DialogInterface.OnClickListener negativeButtonAction)
    {

        AlertDialog.Builder builder =
            new AlertDialog.Builder(context, R.style.DialogTheme);
        builder.setMessage(text);
        builder.setTitle(title);
        builder.setPositiveButton(positiveButtonText, positiveButtonAction);
        builder.setNegativeButton(negativeButtonText, negativeButtonAction);
        builder.setCancelable(false);
        return builder.show();
    }

    /**
     * Shows a snackbar requesting a permission with a button linking to the
     * application settings page.
     *
     * @param activity The host activity for the snackbar.
     * @param viewId   The ID of the view to add the snackbar to.
     * @param text     The text to display.
     */
    public static void showPermissionSnackbar(Activity activity,
                                              int viewId,
                                              String text)
    {
        final View view = activity.findViewById(viewId);
        Snackbar snackbar = Snackbar.make(
            view,
            text,
            Snackbar.LENGTH_LONG);
        snackbar.setAction(
            R.string.settings,
            v -> {
                Intent intent = new Intent();
                intent.setAction(
                    Settings
                        .ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                Uri uri = Uri.fromParts(
                    "package", activity.getPackageName(), null);
                intent.setData(uri);
                activity.startActivity(intent);
            });
        snackbar.show();
    }

    /**
     * Returns a string consisting only of the digits in the specified string.
     *
     * @param str The specified string.
     * @return A string consisting only of the digits in the specified string.
     */
    public static String getDigitsOfString(String str) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isDigit(c)) {
                stringBuilder.append(c);
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Returns the string representation of the specified phone number type.
     *
     * @param type The specified phone number type.
     * @return The string representation of the specified phone number type.
     */
    public static String getPhoneNumberType(int type) {
        switch (type) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                return "Home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                return "Mobile";
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                return "Work";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME:
                return "Home Fax";
            case ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK:
                return "Work Fax";
            case ContactsContract.CommonDataKinds.Phone.TYPE_MAIN:
                return "Main";
            case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER:
                return "Other";
            case ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM:
                return "Custom";
            case ContactsContract.CommonDataKinds.Phone.TYPE_PAGER:
                return "Pager";
            default:
                return "";
        }
    }
}
