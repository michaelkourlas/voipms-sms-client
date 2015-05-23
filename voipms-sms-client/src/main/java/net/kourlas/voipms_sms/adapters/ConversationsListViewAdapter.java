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

package net.kourlas.voipms_sms.adapters;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.*;
import net.kourlas.voipms_sms.Database;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;

import java.util.ArrayList;
import java.util.List;

public class ConversationsListViewAdapter extends FilterableListViewAdapter<Conversation> {

    private final ConversationsActivity activity;

    public ConversationsListViewAdapter(ConversationsActivity activity) {
        super((ListView) activity.findViewById(R.id.list));
        this.activity = activity;
        refresh();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.conversations_listview_item, parent, false);
        }

        Sms sms = ((Conversation) getItem(position)).getMostRecentSms();

        ImageView checkedButton = (ImageView) convertView.findViewById(R.id.conversations_photo_checked);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkedButton.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            checkedButton.setClipToOutline(true);
        }

        QuickContactBadge contactBadge = (QuickContactBadge) convertView.findViewById(R.id.photo);
        contactBadge.assignContactFromPhone(sms.getContact(), true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            contactBadge.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            contactBadge.setClipToOutline(true);
        }

        String photoUri = Utils.getContactPhotoUri(activity, sms.getContact());
        if (photoUri != null) {
            contactBadge.setImageURI(Uri.parse(photoUri));
        } else {
            contactBadge.setImageToDefault();
        }

        TextView contactTextView = (TextView) convertView.findViewById(R.id.contact);
        String contactName = Utils.getContactName(activity, sms.getContact());
        if (contactName != null) {
            contactTextView.setText(contactName);
        } else {
            contactTextView.setText(Utils.getFormattedPhoneNumber(sms.getContact()));
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
        dateTextView.setText(Utils.getFormattedDate(sms.getDate()));

        return convertView;
    }

    @Override
    public Filter getFilter() {
        return new FilterableAdapterFilter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<Conversation> conversationResults = new ArrayList<Conversation>();

                String searchText = constraint.toString();

                Conversation[] conversations = Database.getInstance(
                        activity.getApplicationContext()).getAllConversations();
                for (Conversation conversation : conversations) {
                    String contactName = "";
                    Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(conversation.getContact()));
                    Cursor cursor = activity.getContentResolver().query(uri, new String[]{
                                    ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME}, null,
                            null, null);
                    if (cursor.moveToFirst()) {
                        contactName = cursor.getString(cursor.getColumnIndex(
                                ContactsContract.PhoneLookup.DISPLAY_NAME));
                    }
                    cursor.close();

                    String conversationText = "";
                    Sms[] allSms = conversation.getAllSms();
                    for (Sms sms : allSms) {
                        conversationText += sms.getMessage() + " ";
                    }

                    if (contactName.toLowerCase().contains(searchText.toLowerCase()) ||
                            conversationText.toLowerCase().contains(searchText.toLowerCase()) ||
                            conversation.getContact().toLowerCase().contains(searchText.toLowerCase()) ||
                            (!searchText.replaceAll("[^0-9]", "").equals("") &&
                                    conversation.getContact().replaceAll("[^0-9]", "").contains(
                                            searchText.replaceAll("[^0-9]", "")))) {
                        conversationResults.add(conversation);
                    }
                }

                results.count = conversationResults.size();
                results.values = conversationResults;

                return results;
            }
        };
    }
}
