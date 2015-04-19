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

package net.kourlas.voipms_sms.data;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import net.kourlas.voipms_sms.R;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ConversationsListViewAdapter extends ArrayAdapter<Conversation> {

    Context context;

    public ConversationsListViewAdapter(Activity context, List<Conversation> conversations) {
        super(context, R.layout.conversations_listview_item, conversations);
        this.context = context;
    }

    private static int compareDateWithoutTime(Calendar c1, Calendar c2) {
        if (c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR))
            return c1.get(Calendar.YEAR) - c2.get(Calendar.YEAR);
        if (c1.get(Calendar.MONTH) != c2.get(Calendar.MONTH))
            return c1.get(Calendar.MONTH) - c2.get(Calendar.MONTH);
        return c1.get(Calendar.DAY_OF_MONTH) - c2.get(Calendar.DAY_OF_MONTH);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.conversations_listview_item, parent, false);
        }

        Sms sms = this.getItem(position).getMostRecentSms();

        QuickContactBadge contactBadge = (QuickContactBadge) convertView.findViewById(R.id.photo);
        contactBadge.assignContactFromPhone(sms.getContact(), true);
        contactBadge.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        contactBadge.setClipToOutline(true);

        ImageView checkedButton = (ImageView) convertView.findViewById(R.id.conversations_photo_checked);
        checkedButton.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        checkedButton.setClipToOutline(true);

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sms.getContact()));
        Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.DISPLAY_NAME}, null,
                null, null);
        TextView contactTextView = (TextView) convertView.findViewById(R.id.contact);
        if (cursor.moveToFirst()) {
            String photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
            if (photoUri != null) {
                contactBadge.setImageURI(Uri.parse(photoUri));
            } else {
                contactBadge.setImageToDefault();
            }

            contactTextView.setText(cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)));
        } else {
            contactBadge.setImageToDefault();

            String contactText = sms.getContact();
            if (contactText.length() == 10) {
                MessageFormat phoneNumberFormat = new MessageFormat("({0}) {1}-{2}");
                String[] phoneNumberArray = new String[]{contactText.substring(0, 3), contactText.substring(3, 6),
                        contactText.substring(6)};
                contactText = phoneNumberFormat.format(phoneNumberArray);
            }
            contactTextView.setText(contactText);
        }

        TextView messageTextView = (TextView) convertView.findViewById(R.id.message);
        if (sms.getType() == Sms.Type.INCOMING) {
            messageTextView.setText(sms.getMessage());
        } else {
            messageTextView.setText("You: " + sms.getMessage());
        }

        if (sms.isUnread()) {
            contactTextView.setTypeface(null, Typeface.BOLD);
            messageTextView.setTypeface(null, Typeface.BOLD);
        } else {
            contactTextView.setTypeface(null, Typeface.NORMAL);
            messageTextView.setTypeface(null, Typeface.NORMAL);
        }

        TextView dateTextView = (TextView) convertView.findViewById(R.id.date);

        Date smsDate = sms.getDate();
        Calendar smsCalendar = Calendar.getInstance();
        smsCalendar.setTimeInMillis(smsDate.getTime());
        boolean dateComplete = false;

        Calendar oneMinuteAgo = Calendar.getInstance();
        oneMinuteAgo.add(Calendar.MINUTE, -1);
        if (oneMinuteAgo.getTime().before(smsDate)) {
            // Last minute: X seconds ago
            long seconds = (Calendar.getInstance().getTime().getTime() - smsCalendar.getTime().getTime()) / 1000;
            if (seconds < 10) {
                dateTextView.setText("Just now");
            } else {
                dateTextView.setText(seconds + " seconds ago");
            }

            dateComplete = true;
        }

        Calendar oneHourAgo = Calendar.getInstance();
        oneHourAgo.add(Calendar.HOUR_OF_DAY, -1);
        if (!dateComplete && oneHourAgo.getTime().before(smsDate)) {
            // Last hour: X minutes ago
            long minutes = (Calendar.getInstance().getTime().getTime() - smsCalendar.getTime().getTime()) / (1000 * 60);
            if (minutes == 1) {
                dateTextView.setText("1 minute ago");
            } else {
                dateTextView.setText(minutes + " minutes ago");
            }

            dateComplete = true;
        }

        if (!dateComplete && ConversationsListViewAdapter.compareDateWithoutTime(Calendar.getInstance(),
                smsCalendar) == 0) {
            // Today: h:mm a
            DateFormat format = new SimpleDateFormat("h:mm a");
            dateTextView.setText(format.format(smsDate));

            dateComplete = true;
        }

        if (!dateComplete && Calendar.getInstance().get(Calendar.WEEK_OF_YEAR) == smsCalendar.get(
                Calendar.WEEK_OF_YEAR) && Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(
                Calendar.YEAR) == smsCalendar.get(Calendar.YEAR)) {
            // This week: EEE
            DateFormat format = new SimpleDateFormat("EEE");
            dateTextView.setText(format.format(smsDate));

            dateComplete = true;
        }

        if (!dateComplete && Calendar.getInstance().get(Calendar.YEAR) == smsCalendar.get(Calendar.YEAR)) {
            // This year: EEE, MMM d
            DateFormat format = new SimpleDateFormat("EEE, MMM d");
            dateTextView.setText(format.format(smsDate));

            dateComplete = true;
        }

        if (!dateComplete) {
            // Any: EEE, MMM d, yyyy
            DateFormat format = new SimpleDateFormat("EEE, MMM d, yyyy");
            dateTextView.setText(format.format(smsDate));
        }

        return convertView;
    }
}
