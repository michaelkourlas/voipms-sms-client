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
import android.graphics.Outline;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.*;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.activities.NewConversationActivity;

import java.util.ArrayList;
import java.util.List;

public class NewConversationListViewAdapter extends BaseAdapter implements SectionIndexer, Filterable {

    public static final int PRIMARY_ITEM = 0;
    public static final int SECONDARY_ITEM = 1;

    NewConversationActivity activity;
    List<PhoneNumberEntry> originalEntries;
    List<PhoneNumberEntry> filteredEntries;
    String currentFilterQuery;

    public NewConversationListViewAdapter(NewConversationActivity activity, List<PhoneNumberEntry> phoneNumberEntries) {
        this.activity = activity;

        this.originalEntries = new ArrayList<PhoneNumberEntry>();
        this.originalEntries.addAll(phoneNumberEntries);

        this.filteredEntries = new ArrayList<PhoneNumberEntry>();
        this.filteredEntries.addAll(phoneNumberEntries);

        this.currentFilterQuery = "";
    }

    public void add(int index, PhoneNumberEntry item) {
        originalEntries.add(index, item);

        getFilter().filter(currentFilterQuery);
    }

    public void remove(int index) {
        originalEntries.remove(index);

        getFilter().filter(currentFilterQuery);
    }

    @Override
    public int getCount() {
        return filteredEntries.size();
    }

    @Override
    public Object getItem(int position) {
        return filteredEntries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        PhoneNumberEntry phoneNumberEntry = (PhoneNumberEntry) getItem(position);
        if (phoneNumberEntry.isPrimary()) {
            return PRIMARY_ITEM;
        } else {
            return SECONDARY_ITEM;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        PhoneNumberEntry phoneNumberEntry = (PhoneNumberEntry) getItem(position);

        // Set item layout
        if (phoneNumberEntry.isPrimary()) {
            convertView = inflater.inflate(R.layout.new_conversation_listview_item_primary, parent, false);
        } else {
            convertView = inflater.inflate(R.layout.new_conversation_listview_item_secondary, parent, false);
        }

        // Add indexes to entries where appropriate
        TextView textView = (TextView) convertView.findViewById(R.id.letter);
        if (phoneNumberEntry.isPrimary() && !phoneNumberEntry.isTypedIn()) {
            if (position == 0 || phoneNumberEntry.getName().charAt(0) !=
                    ((PhoneNumberEntry) getItem(position - 1)).getName().charAt(0)) {
                textView.setText(phoneNumberEntry.getName().toUpperCase().charAt(0) + "");
            }
        }

        if (phoneNumberEntry.isPrimary()) {
            QuickContactBadge photo = (QuickContactBadge) convertView.findViewById(R.id.photo);
            photo.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            photo.setClipToOutline(true);

            photo.assignContactFromPhone(phoneNumberEntry.getPhoneNumber(), true);

            if (phoneNumberEntry.isTypedIn()) {
                photo.setScaleType(ImageView.ScaleType.CENTER);
                photo.setBackgroundResource(R.color.primary);
                photo.setImageResource(R.drawable.ic_dialpad_white_24dp);
            } else if (phoneNumberEntry.getPhotoUri() == null) {
                photo.setImageToDefault();
            } else {
                photo.setImageURI(Uri.parse(phoneNumberEntry.getPhotoUri()));
            }

            TextView contactTextView = (TextView) convertView.findViewById(R.id.contact);
            if (phoneNumberEntry.isTypedIn()) {
                contactTextView.setText("Manual entry");
            } else {
                contactTextView.setText(phoneNumberEntry.getName());
            }
        }

        TextView phoneNumberTextView = (TextView) convertView.findViewById(R.id.phone_number);
        phoneNumberTextView.setText(phoneNumberEntry.getPhoneNumber());

        return convertView;
    }

    @Override
    public Object[] getSections() {
        List<String> sections = new ArrayList<String>();

        if (getCount() > 0 && ((PhoneNumberEntry) getItem(0)).isTypedIn()) {
            sections.add("");
        }

        int itemCount = getCount();
        for (int i = getCount() > 0 && ((PhoneNumberEntry) getItem(0)).isTypedIn() ? 1 : 0; i < itemCount; i++) {
            PhoneNumberEntry phoneNumberEntry = (PhoneNumberEntry) getItem(i);
            if (!sections.contains(phoneNumberEntry.getName().toUpperCase().charAt(0) + "")) {
                sections.add(phoneNumberEntry.getName().toUpperCase().charAt(0) + "");
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
        for (int i = getCount() > 0 && ((PhoneNumberEntry) getItem(0)).isTypedIn() ? 1 : 0; i < itemCount; i++) {
            if (((PhoneNumberEntry) getItem(i)).getName().toUpperCase().charAt(0) ==
                    getSections()[sectionIndex].toString().charAt(0)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public int getSectionForPosition(int position) {
        if (((PhoneNumberEntry) getItem(position)).isTypedIn()) {
            return 0;
        }

        String[] sections = (String[]) getSections();
        for (int i = sections.length > 0 && sections[0].equals("") ? 1 : 0; i < sections.length; i++) {
            if (sections[i].charAt(0) == ((PhoneNumberEntry) getItem(position)).getName().toUpperCase().charAt(0)) {
                return i;
            }
        }
        return 0;
    }

    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<PhoneNumberEntry> phoneNumberEntryResults = new ArrayList<PhoneNumberEntry>();

                String searchText = constraint.toString();
                for (PhoneNumberEntry phoneNumberEntry : originalEntries) {
                    if (phoneNumberEntry.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                            phoneNumberEntry.getPhoneNumber().toLowerCase().contains(searchText.toLowerCase()) ||
                            (!searchText.replaceAll("[^0-9]", "").equals("") && phoneNumberEntry.getPhoneNumber().replaceAll("[^0-9]", "").contains(searchText.replaceAll("[^0-9]", ""))) ||
                            phoneNumberEntry.isTypedIn()) {
                        phoneNumberEntryResults.add(phoneNumberEntry);
                    }
                }

                for (int i = 0; i < phoneNumberEntryResults.size(); i++) {
                    boolean found = false;
                    for (int j = 0; j < i; j++) {
                        if (phoneNumberEntryResults.get(i).getName().equals(phoneNumberEntryResults.get(j).getName())) {
                            phoneNumberEntryResults.get(i).setPrimary(false);
                            phoneNumberEntryResults.get(j).setPrimary(true);
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        phoneNumberEntryResults.get(i).setPrimary(true);
                    }
                }

                results.count = phoneNumberEntryResults.size();
                results.values = phoneNumberEntryResults;

                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                currentFilterQuery = constraint.toString();

                filteredEntries.clear();
                if (results.values != null) {
                    filteredEntries.addAll((List<PhoneNumberEntry>) results.values);
                }

                notifyDataSetChanged();
            }
        };
    }

    public static class PhoneNumberEntry {
        private String name;
        private String phoneNumber;
        private String photoUri;
        private boolean primary;
        private boolean typedIn;

        public PhoneNumberEntry(String name, String phoneNumber, String photoUri, boolean primary, boolean typedIn) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.photoUri = photoUri;
            this.primary = primary;
            this.typedIn = typedIn;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
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
    }
}
