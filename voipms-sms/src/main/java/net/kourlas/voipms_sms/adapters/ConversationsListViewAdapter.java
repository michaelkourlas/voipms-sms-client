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
        dateTextView.setText(Utils.getFormattedDate(sms.getDate(), true));

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
