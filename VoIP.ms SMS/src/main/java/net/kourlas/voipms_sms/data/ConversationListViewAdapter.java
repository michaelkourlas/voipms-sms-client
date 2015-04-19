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

import android.content.Context;
import android.database.Cursor;
import android.graphics.Outline;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.SubscriptSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ArrayAdapter;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.data.Sms;

import java.util.List;

public class ConversationListViewAdapter extends ArrayAdapter<Sms> {

    public static final int LEFT_START = 0;
    public static final int LEFT_CONTINUE = 1;
    public static final int RIGHT_START = 2;
    public static final int RIGHT_CONTINUE = 3;

    ConversationActivity conversationActivity;

    public ConversationListViewAdapter(ConversationActivity conversationActivity, List<Sms> smses) {
        super(conversationActivity, R.layout.conversation_listview_item_left_start, smses);

        this.conversationActivity = conversationActivity;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public int getItemViewType(int position) {
        Sms sms = getItem(position);
        Sms previousSms = null;
        if (position > 0) {
            previousSms = getItem(position - 1);
        }

        if (sms.getType() == Sms.Type.INCOMING) {
            if (previousSms == null || previousSms.getType() == Sms.Type.OUTGOING || sms.getRawDate() - previousSms.getRawDate() > 10) {
                return LEFT_START;
            } else {
                return LEFT_CONTINUE;
            }
        } else {
            if (previousSms == null || previousSms.getType() == Sms.Type.INCOMING || sms.getRawDate() - previousSms.getRawDate() > 10) {
                return RIGHT_START;
            } else {
                return RIGHT_CONTINUE;
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) conversationActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        Sms sms = getItem(position);

        int viewType = getItemViewType(position);
        switch (viewType) {
            case LEFT_START:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_left_start, parent, false);
                }
                break;
            case LEFT_CONTINUE:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_left_continue, parent, false);
                }
                break;
            case RIGHT_START:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_right_start, parent, false);
                }
                break;
            case RIGHT_CONTINUE:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_right_continue, parent, false);
                }
                break;
        }

        convertView.setTag(viewType);

        if (viewType == LEFT_START || viewType == RIGHT_START) {
            QuickContactBadge photo = (QuickContactBadge) convertView.findViewById(R.id.photo);
            photo.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            photo.setClipToOutline(true);
            if (viewType == LEFT_START) {
                photo.assignContactFromPhone(sms.getContact(), true);
            } else {
                photo.assignContactFromPhone(sms.getDid(), true);
            }

            Uri uri;
            if (viewType == LEFT_START) {
                uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(
                        sms.getContact()));
            } else {
                uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sms.getDid()));
            }
            Cursor cursor = conversationActivity.getContentResolver().query(uri, new String[]{
                    ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor.moveToFirst()) {
                String photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
                if (photoUri != null) {
                    photo.setImageURI(Uri.parse(photoUri));
                } else {
                    photo.setImageToDefault();
                }
            } else {
                photo.setImageToDefault();
            }
            cursor.close();
        }

        TextView text = (TextView) convertView.findViewById(R.id.text);
        text.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 15);
            }
        });
        text.setClipToOutline(true);

        final SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(sms.getMessage() + "\n" + Utils.getFormattedDate(sms.getDate()));
        final AbsoluteSizeSpan sizeSpan = new AbsoluteSizeSpan(12, true);
        spannableStringBuilder.setSpan(sizeSpan, sms.getMessage().length(), spannableStringBuilder.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        spannableStringBuilder.setSpan(new SubscriptSpan(), sms.getMessage().length(), spannableStringBuilder.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        text.setText(spannableStringBuilder);

        return convertView;
    }
}
