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

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.preferences.Preferences;
import net.kourlas.voipms_sms.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationsRecyclerViewAdapter
    extends
    RecyclerView.Adapter<ConversationsRecyclerViewAdapter
        .ConversationViewHolder>
    implements Filterable
{
    private final Context applicationContext;
    private final Database database;
    private final Preferences preferences;
    private final LinearLayoutManager layoutManager;

    private final ConversationsActivity activity;

    private final List<Message> messages;
    private final List<Boolean> checkedItems;
    private String filterConstraint;
    private String oldFilterConstraint;

    public ConversationsRecyclerViewAdapter(ConversationsActivity activity,
                                            LinearLayoutManager layoutManager)
    {
        this.applicationContext = activity.getApplicationContext();
        this.database = Database.getInstance(applicationContext);
        this.preferences = Preferences.getInstance(applicationContext);
        this.layoutManager = layoutManager;

        this.activity = activity;

        this.messages = new ArrayList<>();
        this.filterConstraint = "";
        this.oldFilterConstraint = "";
        this.checkedItems = new ArrayList<>();
    }

    @Override
    public ConversationViewHolder onCreateViewHolder(ViewGroup viewGroup,
                                                     int i)
    {
        View itemView = LayoutInflater.from(viewGroup.getContext())
                                      .inflate(R.layout.conversations_item,
                                               viewGroup, false);
        return new ConversationViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ConversationViewHolder conversationViewHolder,
                                 int position)
    {
        Message message = messages.get(position);

        ViewSwitcher viewSwitcher = conversationViewHolder.getViewSwitcher();
        viewSwitcher.setDisplayedChild(isItemChecked(position) ? 1 : 0);

        QuickContactBadge contactBadge =
            conversationViewHolder.getContactBadge();
        contactBadge.assignContactFromPhone(message.getContact(), true);

        String photoUri = Utils.getContactPhotoUri(applicationContext,
                                                   message.getContact());
        if (photoUri != null) {
            contactBadge.setImageURI(Uri.parse(photoUri));
        } else {
            contactBadge.setImageToDefault();
        }

        TextView contactTextView = conversationViewHolder.getContactTextView();
        String contactName = Utils.getContactName(applicationContext,
                                                  message.getContact());
        SpannableStringBuilder contactTextBuilder =
            new SpannableStringBuilder();
        if (contactName != null) {
            contactTextBuilder.append(contactName);
        } else {
            contactTextBuilder.append(
                Utils.getFormattedPhoneNumber(message.getContact()));
        }
        if (!filterConstraint.equals("")) {
            int index = contactTextBuilder.toString().toLowerCase().indexOf(
                filterConstraint.toLowerCase());
            if (index != -1) {
                contactTextBuilder.setSpan(
                    new BackgroundColorSpan(ContextCompat.getColor(
                        applicationContext, R.color.highlight)),
                    index,
                    index + filterConstraint.length(),
                    SpannableString.SPAN_INCLUSIVE_EXCLUSIVE);
            }
        }
        contactTextView.setText(contactTextBuilder);

        final TextView messageTextView =
            conversationViewHolder.getMessageTextView();
        SpannableStringBuilder messageTextBuilder =
            new SpannableStringBuilder();

        int index = message.getText()
                           .toLowerCase()
                           .indexOf(filterConstraint.toLowerCase());
        if (!filterConstraint.equals("") && index != -1) {
            int nonMessageOffset = index;
            if (message.getType() == Message.Type.OUTGOING) {
                messageTextBuilder.insert(
                    0, applicationContext.getString(
                        R.string.conversations_message_you) + " ");
                nonMessageOffset += 5;
            }

            int substringOffset = index - 20;
            if (substringOffset > 0) {
                messageTextBuilder.append("...");
                nonMessageOffset += 3;

                while (message.getText().charAt(substringOffset) != ' '
                       && substringOffset < index - 1) {
                    substringOffset += 1;
                }
                substringOffset += 1;
            } else {
                substringOffset = 0;
            }

            messageTextBuilder.append(message.getText()
                                             .substring(
                                                 substringOffset));
            messageTextBuilder.setSpan(
                new BackgroundColorSpan(
                    ContextCompat.getColor(applicationContext,
                                           R.color.highlight)),
                nonMessageOffset - substringOffset,
                nonMessageOffset - substringOffset
                + filterConstraint.length(),
                SpannableString.SPAN_INCLUSIVE_EXCLUSIVE);
        } else {
            if (message.getType() == Message.Type.OUTGOING) {
                messageTextBuilder.append(applicationContext.getString(
                    R.string.conversations_message_you));
                messageTextBuilder.append(" ");
            }
            messageTextBuilder.append(message.getText());
        }
        messageTextView.setText(messageTextBuilder);

        if (message.isUnread()) {
            contactTextView.setTypeface(null, Typeface.BOLD);
            messageTextView.setTypeface(null, Typeface.BOLD);
        } else {
            contactTextView.setTypeface(null, Typeface.NORMAL);
            messageTextView.setTypeface(null, Typeface.NORMAL);
        }

        // Set date line
        TextView dateTextView = conversationViewHolder.getDateTextView();
        if (message.isDraft()) {
            SpannableStringBuilder dateTextBuilder =
                new SpannableStringBuilder();
            dateTextBuilder.append(applicationContext.getString(
                R.string.conversations_message_draft));
            dateTextBuilder.setSpan(
                new StyleSpan(Typeface.ITALIC),
                0,
                dateTextBuilder.length(),
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            dateTextView.setText(dateTextBuilder);
        } else if (!message.isDelivered()) {
            if (!message.isDeliveryInProgress()) {
                SpannableStringBuilder dateTextBuilder =
                    new SpannableStringBuilder();
                dateTextBuilder.append(applicationContext.getString(
                    R.string.conversations_message_not_sent));
                dateTextBuilder.setSpan(
                    new ForegroundColorSpan(
                        ContextCompat.getColor(
                            applicationContext,
                            android.R.color.holo_red_dark)),
                    0,
                    dateTextBuilder.length(),
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                dateTextView.setText(dateTextBuilder);
            } else {
                dateTextView.setText(applicationContext.getString(
                    R.string.conversations_message_sending));
            }
        } else {
            dateTextView.setText(Utils.getFormattedDate(applicationContext,
                                                        message.getDate(),
                                                        true));
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public boolean isItemChecked(int position) {
        return checkedItems.get(position);
    }

    public Message getItem(int position) {
        return messages.get(position);
    }

    public void toggleItemChecked(int position) {
        setItemChecked(position, !isItemChecked(position));
    }

    public void setItemChecked(int position, boolean checked) {
        boolean previous = checkedItems.get(position);
        checkedItems.set(position, checked);

        if (previous && !checked) {
            notifyItemChanged(position);
        } else if (!previous && checked) {
            notifyItemChanged(position);
        }
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

    @Override
    public Filter getFilter() {
        return new ConversationsFilter();
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

        ConversationViewHolder(View itemView) {
            super(itemView);

            itemView.setClickable(true);
            itemView.setOnClickListener(activity);
            itemView.setLongClickable(true);
            itemView.setOnLongClickListener(activity);

            viewSwitcher =
                (ViewSwitcher) itemView.findViewById(R.id.view_switcher);
            contactBadge =
                (QuickContactBadge) itemView.findViewById(R.id.photo);
            Utils.applyCircularMask(contactBadge);
            ImageView contactBadgeChecked = (ImageView) itemView
                .findViewById(R.id.conversations_photo_checked);
            Utils.applyCircularMask(contactBadgeChecked);
            contactTextView = (TextView) itemView.findViewById(R.id.contact);
            messageTextView = (TextView) itemView.findViewById(R.id.message);
            dateTextView = (TextView) itemView.findViewById(R.id.date);
        }

        ViewSwitcher getViewSwitcher() {
            return viewSwitcher;
        }

        QuickContactBadge getContactBadge() {
            return contactBadge;
        }

        TextView getContactTextView() {
            return contactTextView;
        }

        TextView getMessageTextView() {
            return messageTextView;
        }

        TextView getDateTextView() {
            return dateTextView;
        }
    }

    class ConversationsFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();

            oldFilterConstraint = filterConstraint;
            filterConstraint = constraint.toString().trim();

            List<Message> messages =
                database.getMostRecentFilteredMessageForAllConversations(
                    preferences.getDid(), filterConstraint.toLowerCase());

            results.count = messages.size();
            results.values = messages;

            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint,
                                      FilterResults results)
        {
            int position = layoutManager.findFirstVisibleItemPosition();

            List<Message> newMessages = Collections.emptyList();
            if (results.values != null) {
                newMessages = (List<Message>) results.values;
            }

            // Remove old messages from the adapter
            List<Message> oldMessages = new ArrayList<>();
            oldMessages.addAll(messages);
            for (Message oldMessage : oldMessages) {
                boolean removed = true;
                for (Message newMessage : newMessages) {
                    if (oldMessage.equalsConversation(newMessage)) {
                        removed = false;
                        break;
                    }
                }

                if (removed) {
                    // Message was removed
                    int index = messages.indexOf(oldMessage);
                    checkedItems.remove(index);
                    messages.remove(index);
                    notifyItemRemoved(index);
                }
            }

            // Update changed messages in the adapter
            List<Message> newConversationMessages = new ArrayList<>();
            newConversationMessages.addAll(newMessages);
            for (int i = 0; i < messages.size(); i++) {
                for (Message newMessage : newMessages) {
                    if (messages.get(i).equalsConversation(newMessage)) {
                        // Message was changed (or the filter constraint has
                        // changed and we want a change animation for all
                        // messages)
                        if (!messages.get(i).equals(newMessage)
                            || !oldFilterConstraint.equals(filterConstraint))
                        {
                            messages.set(i, newMessage);
                            notifyItemChanged(i);
                        }
                        newConversationMessages.remove(newMessage);
                    }
                }
            }
            newMessages = newConversationMessages;

            // Update moved messages in the adapter
            List<Message> sortedMessages = new ArrayList<>();
            sortedMessages.addAll(messages);
            Collections.sort(sortedMessages);
            for (int i = 0; i < sortedMessages.size(); i++) {
                if (sortedMessages.get(i) == messages.get(i)) {
                    continue;
                }

                int index = -1;
                for (int j = 0; j < messages.size(); j++) {
                    if (messages.get(j) == sortedMessages.get(i)) {
                        index = j;
                        break;
                    }
                }

                // Message was moved
                checkedItems.add(i, checkedItems.get(index));
                checkedItems.remove(index + 1);
                messages.add(i, messages.get(index));
                messages.remove(index + 1);
                notifyItemMoved(index, i);
            }

            // Add new messages to the adapter
            for (Message newMessage : newMessages) {
                if (messages.size() >= 1) {
                    int i = 0;
                    while (i < messages.size()
                           && newMessage.compareTo(messages.get(i)) > 0) {
                        i++;
                    }
                    checkedItems.add(i, false);
                    messages.add(i, newMessage);
                    notifyItemInserted(i);
                } else {
                    checkedItems.add(0, false);
                    messages.add(0, newMessage);
                    notifyItemInserted(0);
                }
            }

            TextView emptyTextView =
                (TextView) activity.findViewById(R.id.empty_text);
            if (messages.size() == 0) {
                if (filterConstraint.equals("")) {
                    emptyTextView.setText(applicationContext.getString(
                        R.string.conversations_no_messages));
                } else {
                    emptyTextView.setText(applicationContext.getString(
                        R.string.conversations_no_results, filterConstraint));
                }
            } else {
                emptyTextView.setText("");
            }

            layoutManager.scrollToPosition(position);
        }
    }
}
