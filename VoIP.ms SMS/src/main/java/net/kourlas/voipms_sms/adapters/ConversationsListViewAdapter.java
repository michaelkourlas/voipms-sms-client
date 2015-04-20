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

package net.kourlas.voipms_sms.adapters;

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
import android.widget.*;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;

import java.text.MessageFormat;
import java.util.*;

public class ConversationsListViewAdapter extends BaseAdapter implements Filterable {

    ConversationsActivity activity;
    List<Conversation> originalConversations;
    List<Conversation> filteredConversations;
    String currentFilterQuery;

    public ConversationsListViewAdapter(ConversationsActivity activity, List<Conversation> conversations) {
        this.activity = activity;

        this.originalConversations = new ArrayList<Conversation>();
        this.originalConversations.addAll(conversations);

        this.filteredConversations = new ArrayList<Conversation>();
        this.filteredConversations.addAll(conversations);

        this.currentFilterQuery = "";
    }

    public void addAll(Collection<Conversation> collection) {
        originalConversations.addAll(collection);

        getFilter().filter(currentFilterQuery);
    }

    public void clear() {
        originalConversations.clear();

        getFilter().filter(currentFilterQuery);
    }

    @Override
    public int getCount() {
        return filteredConversations.size();
    }

    @Override
    public Object getItem(int position) {
        return filteredConversations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.conversations_listview_item, parent, false);
        }

        Sms sms = ((Conversation) getItem(position)).getMostRecentSms();

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
        Cursor cursor = activity.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
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
        cursor.close();

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
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<Conversation> conversationResults = new ArrayList<Conversation>();

                String searchText = constraint.toString();
                for (Conversation conversation : originalConversations) {
                    String name = "";
                    Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(conversation.getContact()));
                    Cursor cursor = activity.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID,
                                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.DISPLAY_NAME}, null,
                            null, null);
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                    }

                    Sms[] allSms = conversation.getAllSms();
                    String message = "";
                    for (Sms sms : allSms) {
                        message += sms.getMessage() + " ";
                    }

                    if (name.toLowerCase().contains(searchText.toLowerCase()) ||
                            message.toLowerCase().contains(searchText.toLowerCase()) ||
                            conversation.getContact().toLowerCase().contains(searchText.toLowerCase()) ||
                            (!searchText.replaceAll("[^0-9]", "").equals("") && conversation.getContact().replaceAll("[^0-9]", "").contains(searchText.replaceAll("[^0-9]", "")))) {
                        conversationResults.add(conversation);
                    }
                }

                results.count = conversationResults.size();
                results.values = conversationResults;

                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                currentFilterQuery = constraint.toString();

                filteredConversations.clear();
                filteredConversations.addAll((List<Conversation>) results.values);

                notifyDataSetChanged();
            }
        };
    }
}
