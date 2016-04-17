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

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import net.kourlas.voipms_sms.Database;
import net.kourlas.voipms_sms.Preferences;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ConversationsRecyclerViewAdapter
        extends RecyclerView.Adapter<ConversationsRecyclerViewAdapter.ConversationViewHolder>
        implements Filterable {
    private final Context applicationContext;
    private final Preferences preferences;
    private final LinearLayoutManager layoutManager;

    private final ConversationsActivity activity;

    private final List<Conversation> conversations;
    private final List<Boolean> checkedItems;
    private String filterConstraint;
    private String oldFilterConstraint;

    public ConversationsRecyclerViewAdapter(ConversationsActivity activity, LinearLayoutManager layoutManager) {
        this.applicationContext = activity.getApplicationContext();
        this.preferences = Preferences.getInstance(applicationContext);
        this.layoutManager = layoutManager;

        this.activity = activity;

        this.conversations = new ArrayList<>();
        this.filterConstraint = "";
        this.oldFilterConstraint = "";
        this.checkedItems = new ArrayList<>();
    }

    @Override
    public ConversationViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.conversations_item,
                viewGroup, false);
        return new ConversationViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ConversationViewHolder conversationViewHolder, int position) {
        Message newestMessage = conversations.get(position).getMostRecentSms();

        ViewSwitcher viewSwitcher = conversationViewHolder.getViewSwitcher();
        viewSwitcher.setDisplayedChild(isItemChecked(position) ? 1 : 0);

        QuickContactBadge contactBadge = conversationViewHolder.getContactBadge();
        contactBadge.assignContactFromPhone(newestMessage.getContact(), true);

        String photoUri = Utils.getContactPhotoUri(applicationContext, newestMessage.getContact());
        if (photoUri != null) {
            contactBadge.setImageURI(Uri.parse(photoUri));
        }
        else {
            contactBadge.setImageToDefault();
        }

        TextView contactTextView = conversationViewHolder.getContactTextView();
        String contactName = Utils.getContactName(applicationContext, newestMessage.getContact());
        SpannableStringBuilder contactTextBuilder = new SpannableStringBuilder();
        if (contactName != null) {
            contactTextBuilder.append(contactName);
        }
        else {
            contactTextBuilder.append(Utils.getFormattedPhoneNumber(newestMessage.getContact()));
        }
        if (!filterConstraint.equals("")) {
            int index = contactTextBuilder.toString().toLowerCase().indexOf(filterConstraint.toLowerCase());
            if (index != -1) {
                contactTextBuilder.setSpan(new BackgroundColorSpan(applicationContext.getResources().getColor(
                                R.color.highlight)), index, index + filterConstraint.length(),
                        SpannableString.SPAN_INCLUSIVE_EXCLUSIVE);
            }
        }
        contactTextView.setText(contactTextBuilder);

        final TextView messageTextView = conversationViewHolder.getMessageTextView();
        SpannableStringBuilder messageTextBuilder = new SpannableStringBuilder();
        if (!filterConstraint.equals("")) {
            boolean found = false;
            for (Message message : conversations.get(position).getMessages()) {
                int index = message.getText().toLowerCase().indexOf(filterConstraint.toLowerCase());
                if (index != -1) {
                    int nonMessageOffset = index;
                    if (newestMessage.getType() == Message.Type.OUTGOING) {
                        messageTextBuilder.insert(0, applicationContext.getString(R.string.conversations_message_you) + " ");
                        nonMessageOffset += 5;
                    }

                    int substringOffset = index - 20;
                    if (substringOffset > 0) {
                        messageTextBuilder.append("...");
                        nonMessageOffset += 3;

                        while (message.getText().charAt(substringOffset) != ' ' && substringOffset < index - 1) {
                            substringOffset += 1;
                        }
                        substringOffset += 1;
                    }
                    else {
                        substringOffset = 0;
                    }

                    messageTextBuilder.append(message.getText().substring(substringOffset));
                    messageTextBuilder.setSpan(new BackgroundColorSpan(applicationContext.getResources().getColor(
                                    R.color.highlight)), nonMessageOffset - substringOffset,
                            nonMessageOffset - substringOffset + filterConstraint.length(),
                            SpannableString.SPAN_INCLUSIVE_EXCLUSIVE);
                    found = true;
                    break;
                }
            }

            if (!found) {
                if (newestMessage.getType() == Message.Type.OUTGOING) {
                    messageTextBuilder.insert(0, applicationContext.getString(R.string.conversations_message_you) + " ");
                }
                messageTextBuilder.append(newestMessage.getText());
            }
        }
        else {
            if (newestMessage.getType() == Message.Type.OUTGOING) {
                messageTextBuilder.insert(0, applicationContext.getString(R.string.conversations_message_you) + " ");
            }
            messageTextBuilder.append(newestMessage.getText());
        }
        messageTextView.setText(messageTextBuilder);

        if (newestMessage.isUnread()) {
            contactTextView.setTypeface(null, Typeface.BOLD);
            messageTextView.setTypeface(null, Typeface.BOLD);
        }
        else {
            contactTextView.setTypeface(null, Typeface.NORMAL);
            messageTextView.setTypeface(null, Typeface.NORMAL);
        }

        TextView dateTextView = conversationViewHolder.getDateTextView();
        dateTextView.setText(Utils.getFormattedDate(applicationContext, newestMessage.getDate(), true));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    @Override
    public Filter getFilter() {
        return new ConversationsFilter();
    }

    public Conversation getItem(int position) {
        return conversations.get(position);
    }

    public boolean isItemChecked(int position) {
        return checkedItems.get(position);
    }

    public void setItemChecked(int position, boolean checked) {
        boolean previous = checkedItems.get(position);
        checkedItems.set(position, checked);

        if (previous && !checked) {
            notifyItemChanged(position);
        }
        else if (!previous && checked) {
            notifyItemChanged(position);
        }
    }

    public void toggleItemChecked(int position) {
        setItemChecked(position, !isItemChecked(position));
    }

    public int getCheckedItemCount() {
        int checkedItemCount = 0;
        for (Boolean checkedItem : checkedItems) {
            if (checkedItem) {
                checkedItemCount += 1;
            }
        }
        return checkedItemCount;
    }

    public void refresh() {
        getFilter().filter(filterConstraint);
    }

    public void refresh(String newFilterConstraint) {
        getFilter().filter(newFilterConstraint);
    }

    public class ConversationViewHolder extends RecyclerView.ViewHolder {
        private ViewSwitcher viewSwitcher;
        private QuickContactBadge contactBadge;
        private TextView contactTextView;
        private TextView messageTextView;
        private TextView dateTextView;

        public ConversationViewHolder(View itemView) {
            super(itemView);

            itemView.setClickable(true);
            itemView.setOnClickListener(activity);
            itemView.setLongClickable(true);
            itemView.setOnLongClickListener(activity);

            viewSwitcher = (ViewSwitcher) itemView.findViewById(R.id.view_switcher);
            contactBadge = (QuickContactBadge) itemView.findViewById(R.id.photo);
            Utils.applyCircularMask(contactBadge);
            ImageView contactBadgeChecked = (ImageView) itemView.findViewById(R.id.conversations_photo_checked);
            Utils.applyCircularMask(contactBadgeChecked);
            contactTextView = (TextView) itemView.findViewById(R.id.contact);
            messageTextView = (TextView) itemView.findViewById(R.id.message);
            dateTextView = (TextView) itemView.findViewById(R.id.date);
        }

        public ViewSwitcher getViewSwitcher() {
            return viewSwitcher;
        }

        public QuickContactBadge getContactBadge() {
            return contactBadge;
        }

        public TextView getContactTextView() {
            return contactTextView;
        }

        public TextView getMessageTextView() {
            return messageTextView;
        }

        public TextView getDateTextView() {
            return dateTextView;
        }
    }

    class ConversationsFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<Conversation> conversationResults = new ArrayList<>();

            oldFilterConstraint = filterConstraint;
            filterConstraint = constraint.toString().trim();

            Conversation[] conversations = Database.getInstance(applicationContext).getLimitedFilteredConversations(
                    preferences.getDid(),
                    filterConstraint
            );
            for (Conversation conversation : conversations) {
                String contactName = Utils.getContactName(applicationContext, conversation.getContact());

                String text = "";
                Message[] allSms = conversation.getMessages();
                for (Message message : allSms) {
                    text += message.getText() + " ";
                }

                if ((contactName != null && contactName.toLowerCase().contains(filterConstraint.toLowerCase())) ||
                        text.toLowerCase().contains(filterConstraint.toLowerCase()) ||
                        (!filterConstraint.replaceAll("[^0-9]", "").equals("") &&
                                conversation.getContact().replaceAll("[^0-9]", "").contains(
                                        filterConstraint.replaceAll("[^0-9]", "")))) {
                    conversationResults.add(conversation);
                }
            }

            results.count = conversationResults.size();
            results.values = conversationResults;

            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            int position = layoutManager.findFirstVisibleItemPosition();

            List<Conversation> newConversations = (List<Conversation>) results.values;

            List<Conversation> oldConversations = new LinkedList<>();
            oldConversations.addAll(conversations);
            for (Conversation oldConversation : oldConversations) {
                boolean removed = true;
                for (Conversation newConversation : newConversations) {
                    if (oldConversation.getContact().equals(newConversation.getContact()) &&
                            oldConversation.getDid().equals(newConversation.getDid())) {
                        removed = false;
                        break;
                    }
                }

                if (removed) {
                    // Conversation was removed
                    int index = conversations.indexOf(oldConversation);
                    checkedItems.remove(index);
                    conversations.remove(index);
                    notifyItemRemoved(index);
                }
            }

            for (int i = 0; i < conversations.size(); i++) {
                for (Conversation newConversation : newConversations) {
                    if (conversations.get(i).getContact().equals(newConversation.getContact()) &&
                            conversations.get(i).getDid().equals(newConversation.getDid())) {
                        if (!conversations.get(i).equals(newConversation) ||
                                !oldFilterConstraint.equals(filterConstraint)) {
                            // Conversation was changed
                            conversations.set(i, newConversation);
                            notifyItemChanged(i);
                        }
                    }
                }
            }

            List<Conversation> sortedConversations = new LinkedList<>();
            sortedConversations.addAll(conversations);
            Collections.sort(sortedConversations);
            for (int i = 0; i < sortedConversations.size(); i++) {
                if (sortedConversations.get(i) == conversations.get(i)) {
                    continue;
                }

                int index = -1;
                for (int j = 0; j < conversations.size(); j++) {
                    if (conversations.get(j) == sortedConversations.get(i)) {
                        index = j;
                        break;
                    }
                }

                // Conversation was moved
                checkedItems.add(i, checkedItems.get(index));
                checkedItems.remove(index + 1);
                conversations.add(i, conversations.get(index));
                conversations.remove(index + 1);
                notifyItemMoved(index, i);
            }

            for (int i = 0; i < newConversations.size(); i++) {
                if (conversations.size() <= i || !newConversations.get(i).equals(conversations.get(i))) {
                    // Conversation is new
                    checkedItems.add(i, false);
                    conversations.add(i, newConversations.get(i));
                    notifyItemInserted(i);
                }
            }

            TextView emptyTextView = (TextView) activity.findViewById(R.id.empty_text);
            if (conversations.size() == 0) {
                if (filterConstraint.equals("")) {
                    emptyTextView.setText(applicationContext.getString(R.string.conversations_no_messages));
                }
                else {
                    emptyTextView.setText(applicationContext.getString(R.string.conversations_no_results) +
                            " '" + filterConstraint + "'");
                }
            }
            else {
                emptyTextView.setText("");
            }

            layoutManager.scrollToPosition(position);
        }
    }
}
