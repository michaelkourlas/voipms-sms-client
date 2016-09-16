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

package net.kourlas.voipms_sms.adapters;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.*;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.activities.NewConversationActivity;

import java.util.ArrayList;
import java.util.List;

public class NewConversationListViewAdapter extends BaseAdapter
    implements Filterable, SectionIndexer
{
    private static final int ITEM_COUNT = 2;
    private static final int ITEM_PRIMARY = 0;
    private static final int ITEM_SECONDARY = 1;

    private final NewConversationActivity activity;

    private final List<NewConversationListViewAdapter.ContactItem> items;
    private String typedInPhoneNumber;

    public NewConversationListViewAdapter(NewConversationActivity activity) {
        this.activity = activity;

        this.typedInPhoneNumber = null;
        this.items = new ArrayList<>();
    }

    public void refresh(String newFilterConstraint) {
        getFilter().filter(newFilterConstraint);
    }

    public Filter getFilter() {
        return new NewConversationFilter();
    }

    public void showTypedInItem(String phoneNumber) {
        typedInPhoneNumber = phoneNumber;
    }

    public void hideTypedInItem() {
        typedInPhoneNumber = null;
    }
    @Override
    public int getItemViewType(int position) {
        ContactItem contactItem = (ContactItem) getItem(position);
        if (contactItem.isPrimary()) {
            return ITEM_PRIMARY;
        } else {
            return ITEM_SECONDARY;
        }
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public int getViewTypeCount() {
        return ITEM_COUNT;
    }

    @SuppressWarnings("WeakerAccess")
    public static class ContactItem {
        private final long id;
        private final String name;
        private final String phoneNumber;
        private final String photoUri;
        private final boolean typedIn;
        private boolean primary;

        ContactItem(long id, String name, String phoneNumber, String photoUri,
                    boolean primary, boolean typedIn)
        {
            this.id = id;
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.photoUri = photoUri;
            this.primary = primary;
            this.typedIn = typedIn;
        }

        public String getName() {
            return name;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public String getPhotoUri() {
            return photoUri;
        }

        public boolean isPrimary() {
            return primary;
        }

        public void setPrimary(boolean primary) {
            this.primary = primary;
        }

        public boolean isTypedIn() {
            return typedIn;
        }

        public long getId() {
            return id;
        }
    }

    class NewConversationFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<ContactItem> contactItemResults = new ArrayList<>();

            String searchText = constraint.toString();

            List<ContactItem> phoneNumberEntries = new ArrayList<>();

            phoneNumberEntries.addAll(getContacts());
            if (typedInPhoneNumber != null) {
                phoneNumberEntries.add(0, new ContactItem(-1,
                                                          typedInPhoneNumber,
                                                          typedInPhoneNumber,
                                                          null, true,
                                                          true));
            }

            for (ContactItem contactItem : phoneNumberEntries) {
                if (contactItem.getName().toLowerCase()
                               .contains(searchText.toLowerCase()) ||
                    contactItem.getPhoneNumber().toLowerCase()
                               .contains(searchText.toLowerCase()) ||
                    (!searchText.replaceAll("[^0-9]", "").equals("") &&
                     contactItem.getPhoneNumber().replaceAll("[^0-9]", "")
                                .contains(
                                    searchText.replaceAll("[^0-9]", ""))) ||
                    contactItem.isTypedIn())
                {
                    contactItemResults.add(contactItem);
                }
            }

            for (int i = 0; i < contactItemResults.size(); i++) {
                boolean found = false;
                for (int j = 0; j < i; j++) {
                    if (contactItemResults.get(i).getName().equals(
                        contactItemResults.get(j).getName()))
                    {
                        contactItemResults.get(i).setPrimary(false);
                        contactItemResults.get(j).setPrimary(true);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    contactItemResults.get(i).setPrimary(true);
                }
            }

            results.count = contactItemResults.size();
            results.values = contactItemResults;

            return results;
        }

        private List<ContactItem> getContacts() {
            List<ContactItem> phoneNumberEntries = new ArrayList<>();

            try {
                Cursor cursor = activity.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    + " ASC");
                if (cursor != null && cursor.getCount() > 0) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(cursor.getColumnIndex(
                            ContactsContract.Contacts.HAS_PHONE_NUMBER))
                                  .equals("1"))
                        {
                            long id = cursor.getLong(
                                cursor.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone
                                        .CONTACT_ID));
                            String contact = cursor.getString(
                                cursor.getColumnIndex(
                                    ContactsContract.Contacts.DISPLAY_NAME));
                            String phoneNumber = cursor.getString(
                                cursor.getColumnIndex(
                                    ContactsContract.CommonDataKinds
                                        .Phone.NUMBER));
                            String photoUri = cursor.getString(
                                cursor.getColumnIndex(
                                    ContactsContract.Contacts.PHOTO_URI));

                            boolean showPhoto = true;
                            for (ContactItem contactItem : phoneNumberEntries) {
                                if (id == contactItem.getId()) {
                                    showPhoto = false;
                                }
                            }

                            ContactItem contactItem = new ContactItem(
                                id, contact, phoneNumber, photoUri, showPhoto,
                                false);
                            phoneNumberEntries.add(contactItem);
                        }
                    }
                    cursor.close();
                } else if (cursor != null) {
                    cursor.close();
                }
                return phoneNumberEntries;
            } catch (SecurityException ignored) {
                return phoneNumberEntries;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint,
                                      FilterResults results)
        {
            items.clear();
            if (results.values != null) {
                items.addAll((List<ContactItem>) results.values);
            }
            notifyDataSetChanged();
        }
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }


    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);

        ContactItem contactItem = (ContactItem) getItem(position);

        // Set item layout
        if (contactItem.isPrimary()) {
            convertView = inflater.inflate(
                R.layout.new_conversation_item_primary, parent, false);
        } else {
            convertView = inflater.inflate(
                R.layout.new_conversation_item_secondary, parent, false);
        }

        // Add indexes to entries where appropriate
        TextView textView = (TextView) convertView.findViewById(R.id.letter);
        if (contactItem.isPrimary() && !contactItem.isTypedIn()) {
            if (position == 0 || contactItem.getName().charAt(0) !=
                                 ((ContactItem) getItem(position - 1)).getName()
                                                                      .charAt(
                                                                          0))
            {
                textView.setText(String.valueOf(
                    contactItem.getName().toUpperCase().charAt(0)));
            }
        }

        if (contactItem.isPrimary()) {
            QuickContactBadge photo =
                (QuickContactBadge) convertView.findViewById(R.id.photo);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                photo.setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline
                            .setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                photo.setClipToOutline(true);
            }

            photo.assignContactFromPhone(contactItem.getPhoneNumber(), true);

            if (contactItem.isTypedIn()) {
                photo.setScaleType(ImageView.ScaleType.CENTER);
                photo.setBackgroundResource(R.color.primary);
                photo.setImageResource(R.drawable.ic_dialpad_white_24dp);
            } else if (contactItem.getPhotoUri() == null) {
                photo.setImageToDefault();
            } else {
                photo.setImageURI(Uri.parse(contactItem.getPhotoUri()));
            }

            TextView contactTextView =
                (TextView) convertView.findViewById(R.id.contact);
            if (contactItem.isTypedIn()) {
                contactTextView.setText(
                    activity.getString(R.string.new_conversation_manual_entry));
            } else {
                contactTextView.setText(contactItem.getName());
            }
        }

        TextView phoneNumberTextView =
            (TextView) convertView.findViewById(R.id.phone_number);
        phoneNumberTextView.setText(contactItem.getPhoneNumber());

        return convertView;
    }

    @Override
    public Object[] getSections() {
        List<String> sections = new ArrayList<>();

        if (getCount() > 0 && ((ContactItem) getItem(0)).isTypedIn()) {
            sections.add("");
        }

        int itemCount = getCount();
        for (int i =
             getCount() > 0 && ((ContactItem) getItem(0)).isTypedIn() ? 1 : 0;
             i < itemCount; i++) {
            ContactItem contactItem = (ContactItem) getItem(i);
            if (!sections
                .contains(contactItem.getName().toUpperCase().charAt(0) + ""))
            {
                sections
                    .add(contactItem.getName().toUpperCase().charAt(0) + "");
            }
        }

        String[] sectionsArray = new String[sections.size()];
        return sections.toArray(sectionsArray);
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (getSections()[sectionIndex].toString().equals("")) {
            return 0;
        }

        int itemCount = getCount();
        for (int i =
             getCount() > 0 && ((ContactItem) getItem(0)).isTypedIn() ? 1 : 0;
             i < itemCount; i++) {
            if (((ContactItem) getItem(i)).getName().toUpperCase().charAt(0) ==
                getSections()[sectionIndex].toString().charAt(0))
            {
                return i;
            }
        }
        return 0;
    }

    @Override
    public int getSectionForPosition(int position) {
        if (((ContactItem) getItem(position)).isTypedIn()) {
            return 0;
        }

        String[] sections = (String[]) getSections();
        for (int i = sections.length > 0 && sections[0].equals("") ? 1 : 0;
             i < sections.length; i++) {
            if (sections[i].charAt(0) == ((ContactItem) getItem(position))
                .getName().toUpperCase().charAt(0))
            {
                return i;
            }
        }
        return 0;
    }


}
