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

package net.kourlas.voipms_sms.adapters;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.SubscriptSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import net.kourlas.voipms_sms.Database;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.model.Sms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationListViewAdapter extends FilterableListViewAdapter<Sms> {

    public static final int ITEM_LEFT_PRIMARY = 0;
    public static final int ITEM_LEFT_SECONDARY = 1;
    private static final int ITEM_COUNT = 4;
    private static final int ITEM_RIGHT_PRIMARY = 2;
    private static final int ITEM_RIGHT_SECONDARY = 3;

    private final ConversationActivity activity;
    private final String contact;

    public ConversationListViewAdapter(ConversationActivity activity, String contact) {
        super((ListView) activity.findViewById(R.id.list));
        this.activity = activity;
        this.contact = contact;
        refresh();
    }

    @Override
    public int getViewTypeCount() {
        return ITEM_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        Sms sms = (Sms) getItem(position);
        Sms previousSms = null;
        if (position > 0) {
            previousSms = (Sms) getItem(position - 1);
        }

        if (sms.getType() == Sms.Type.INCOMING) {
            if (previousSms == null || previousSms.getType() == Sms.Type.OUTGOING ||
                    sms.getRawDate() - previousSms.getRawDate() > 10) {
                return ITEM_LEFT_PRIMARY;
            } else {
                return ITEM_LEFT_SECONDARY;
            }
        } else {
            if (previousSms == null || previousSms.getType() == Sms.Type.INCOMING ||
                    sms.getRawDate() - previousSms.getRawDate() > 10) {
                return ITEM_RIGHT_PRIMARY;
            } else {
                return ITEM_RIGHT_SECONDARY;
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        Sms sms = (Sms) getItem(position);

        int viewType = getItemViewType(position);
        switch (viewType) {
            case ITEM_LEFT_PRIMARY:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_left_primary, parent, false);
                }
                break;
            case ITEM_LEFT_SECONDARY:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_left_secondary, parent, false);
                }
                break;
            case ITEM_RIGHT_PRIMARY:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_right_primary, parent, false);
                }
                break;
            case ITEM_RIGHT_SECONDARY:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.conversation_listview_item_right_secondary, parent, false);
                }
                break;
        }

        convertView.setTag(viewType);

        if (viewType == ITEM_LEFT_PRIMARY || viewType == ITEM_RIGHT_PRIMARY) {
            QuickContactBadge photo = (QuickContactBadge) convertView.findViewById(R.id.photo);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                photo.setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                photo.setClipToOutline(true);
            }
            if (viewType == ITEM_LEFT_PRIMARY) {
                photo.assignContactFromPhone(sms.getContact(), true);
            } else {
                photo.assignContactFromPhone(sms.getDid(), true);
            }

            Uri uri;
            if (viewType == ITEM_LEFT_PRIMARY) {
                uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(
                        sms.getContact()));
            } else {
                uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sms.getDid()));
            }
            Cursor cursor = activity.getContentResolver().query(uri, new String[]{
                    ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor.moveToFirst()) {
                String photoUri = cursor.getString(cursor.getColumnIndex(
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI));
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

        TextView text = (TextView) convertView.findViewById(R.id.message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            text.setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 15);
                }
            });
            text.setClipToOutline(true);
        }

        final SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(sms.getMessage() + "\n" +
                Utils.getFormattedDate(sms.getDate()));
        final AbsoluteSizeSpan sizeSpan = new AbsoluteSizeSpan(12, true);
        spannableStringBuilder.setSpan(sizeSpan, sms.getMessage().length(), spannableStringBuilder.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        spannableStringBuilder.setSpan(new SubscriptSpan(), sms.getMessage().length(), spannableStringBuilder.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        text.setText(spannableStringBuilder);

        return convertView;
    }

    @Override
    public Filter getFilter() {
        return new FilterableAdapterFilter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<Sms> smsResults = new ArrayList<Sms>();

                String searchText = constraint.toString();

                Sms[] smses = Database.getInstance(activity.getApplicationContext()).getConversation(
                        contact).getAllSms();
                for (Sms sms : smses) {
                    if (sms.getMessage().toLowerCase().contains(searchText.toLowerCase())) {
                        smsResults.add(sms);
                    }
                }
                Collections.reverse(smsResults);

                results.count = smsResults.size();
                results.values = smsResults;

                return results;
            }
        };
    }
}
