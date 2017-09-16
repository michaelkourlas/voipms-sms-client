/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2017 Michael Kourlas
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

package net.kourlas.voipms_sms.conversation

import android.graphics.Bitmap
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.QuickContactBadge
import android.widget.TextView
import com.futuremind.recyclerviewfastscroll.SectionTitleProvider
import com.google.firebase.crash.FirebaseCrash
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.demo.demo
import net.kourlas.voipms_sms.demo.getConversationDemoMessages
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.utils.*

/**
 * Recycler view adapter used by [ConversationActivity].
 *
 * @param activity The source [ConversationActivity].
 * @param recyclerView The recycler view used by the activity.
 * @param layoutManager The layout manager used by the recycler view.
 * @param conversationId The conversation ID of the conversation displayed by
 * the activity, consisting of the DID and contact.
 * @param contactName The name associated with the contact.
 * @param contactBitmap The photo associated with the contact.
 */
class ConversationRecyclerViewAdapter(
    private val activity: ConversationActivity,
    private val recyclerView: RecyclerView,
    private val layoutManager: LinearLayoutManager,
    private val conversationId: ConversationId,
    private val contactName: String?,
    private val contactBitmap: Bitmap?) :
    RecyclerView.Adapter<ConversationRecyclerViewAdapter.MessageViewHolder>(),
    Filterable, SectionTitleProvider,
    Iterable<ConversationRecyclerViewAdapter.MessageItem> {

    // List of items shown by the adapter; the index of each item
    // corresponds to the location of each item in the adapter
    private val _messageItems = mutableListOf<MessageItem>()
    val messageItems: List<MessageItem>
        get() = _messageItems

    // Current and previous filter constraint
    private var currConstraint: String = ""
    private var prevConstraint: String = ""

    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): MessageViewHolder {
        // Inflate the appropriate view, given the view type
        val itemView = when (viewType) {
            R.layout.conversation_item_incoming -> {
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.conversation_item_incoming,
                             parent, false)
            }
            R.layout.conversation_item_outgoing -> {
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.conversation_item_outgoing,
                             parent, false)
            }
            else -> throw Exception("Unknown view type $viewType")
        }
        return MessageViewHolder(itemView, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder,
                                  position: Int) {
        // Set up view to match message at position
        updateViewHolderViewHeight(holder, position)
        updateViewHolderContactBadge(holder, position)
        updateViewHolderMessageText(holder, position)
        updateViewHolderDateText(holder, position)
        updateViewHolderColours(holder, position)
    }

    /**
     * Sets the height of the view represented by the view holder to the
     * appropriate height, depending on whether the message is the first
     * message in a group.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderViewHeight(holder: MessageViewHolder,
                                           position: Int) {
        val marginParams = holder.itemView.layoutParams
            as ViewGroup.MarginLayoutParams
        marginParams.topMargin = if (isFirstMessageInGroup(position)) {
            activity.resources.getDimension(
                R.dimen.conversation_item_margin_top_primary).toInt()
        } else {
            activity.resources.getDimension(
                R.dimen.conversation_item_margin_top_secondary).toInt()
        }
    }

    /**
     * Displays or hides the contact badge on the view holder. If the view
     * holder is displayed, sets the content of the view holder to a photo
     * or a material design letter.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderContactBadge(holder: MessageViewHolder,
                                             position: Int) {
        val messageItem = messageItems[position]
        val message = messageItem.message

        val contactBadge = holder.contactBadge
        val contactBadgeLetterText = holder.contactBadgeLetterText
        if (contactBadge != null && contactBadgeLetterText != null) {
            if (isFirstMessageInGroup(position)) {
                holder.contactBadge.visibility = View.VISIBLE
                holder.contactBadgeLetterText.visibility = View.VISIBLE

                contactBadge.assignContactFromPhone(message.contact, true)
                if (contactBitmap != null) {
                    // Show bitmap for contact with bitmap
                    holder.contactBadge.setBackgroundResource(0)
                    contactBadge.setImageBitmap(contactBitmap)
                    holder.contactBadgeLetterText.text = ""
                } else {
                    // Show material design color and first letter for contact
                    // without bitmap
                    holder.contactBadge.setBackgroundColor(
                        getMaterialDesignColour(
                            message.contact))
                    holder.contactBadge.setImageResource(
                        android.R.color.transparent)
                    holder.contactBadgeLetterText.text = getContactInitial(
                        contactName,
                        message.contact)
                }
            } else {
                holder.contactBadge.visibility = View.INVISIBLE
                holder.contactBadgeLetterText.visibility = View.INVISIBLE
            }
        }
    }

    /**
     * Displays the text of the message on the view holder. Selects and
     * highlights part of the text if a filter is configured.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderMessageText(holder: MessageViewHolder,
                                            position: Int) {
        val messageItem = messageItems[position]
        val message = messageItem.message

        val messageText = holder.messageText
        val messageTextBuilder = SpannableStringBuilder()
        messageTextBuilder.append(message.text)

        // Highlight text that matches filter
        if (currConstraint != "") {
            val index = message.text.toLowerCase().indexOf(
                currConstraint.toLowerCase())
            if (index != -1) {
                messageTextBuilder.setSpan(
                    BackgroundColorSpan(
                        ContextCompat.getColor(
                            activity, R.color.highlight)),
                    index,
                    index + currConstraint.length,
                    SpannableString.SPAN_INCLUSIVE_EXCLUSIVE)
                messageTextBuilder.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            activity, R.color.dark_gray)),
                    index,
                    index + currConstraint.length,
                    SpannableString.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }
        messageText.text = messageTextBuilder
    }

    /**
     * Displays the date of the message on the view holder. Displays a message
     * about the status of the message in certain cases instead, such as when
     * the message is being sent or has failed to send. Hides the text depending
     * on the message's placement in a group.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderDateText(holder: MessageViewHolder,
                                         position: Int) {
        val messageItem = messageItems[position]
        val message = messageItem.message

        val dateText = holder.dateText
        if (!message.isDelivered) {
            if (!message.isDeliveryInProgress) {
                // Show tried but failed to send text
                val dateTextBuilder = SpannableStringBuilder()
                if (messageItems[position].checked) {
                    dateTextBuilder.append(activity.getString(
                        R.string.conversation_message_not_sent_selected))
                } else {
                    dateTextBuilder.append(activity.getString(
                        R.string.conversation_message_not_sent))
                }
                dateTextBuilder.setSpan(
                    ForegroundColorSpan(
                        if (messageItems[position].checked)
                            ContextCompat.getColor(
                                activity,
                                android.R.color.white)
                        else
                            ContextCompat.getColor(
                                activity,
                                android.R.color.holo_red_dark)),
                    0,
                    dateTextBuilder.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                dateText.text = dateTextBuilder
            } else {
                // Show sending text
                dateText.text = activity.getString(
                    R.string.conversation_message_sending)
            }
            dateText.visibility = View.VISIBLE
        } else if (isLastMessageInGroup(position)) {
            // Show date
            dateText.text = getFormattedDate(activity,
                                             message.date, false)
            dateText.visibility = View.VISIBLE
        } else {
            // Hide date altogether
            dateText.visibility = View.GONE
        }
    }

    /**
     * Sets the colours of text in the view holder, depending on the message
     * type.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    fun updateViewHolderColours(holder: MessageViewHolder, position: Int) {
        val messageItem = messageItems[position]
        val message = messageItem.message

        val smsContainer = holder.smsContainer
        val messageText = holder.messageText
        val dateText = holder.dateText
        if (message.isIncoming) {
            if (messageItem.checked) {
                smsContainer.setBackgroundResource(
                    android.R.color.holo_blue_dark)
            } else {
                smsContainer.setBackgroundResource(R.color.primary)
            }
        } else {
            if (messageItem.checked) {
                smsContainer.setBackgroundResource(
                    android.R.color.holo_blue_dark)
                messageText.setTextColor(ContextCompat.getColor(
                    activity, android.R.color.white))
                messageText.setLinkTextColor(ContextCompat.getColor(
                    activity, android.R.color.white))
                dateText.setTextColor(ContextCompat.getColor(
                    activity, R.color.message_translucent_white))
            } else {
                smsContainer.setBackgroundResource(android.R.color.white)
                messageText.setTextColor(ContextCompat.getColor(
                    activity, R.color.dark_gray))
                messageText.setLinkTextColor(ContextCompat.getColor(
                    activity, R.color.dark_gray))
                dateText.setTextColor(R.color.message_translucent_dark_grey)
            }
        }
    }

    override fun getItemViewType(i: Int): Int =
        // There are two different view types: one for incoming messages and
        // one for outgoing messages
        if (messageItems[i].message.isIncoming) {
            R.layout.conversation_item_incoming
        } else {
            R.layout.conversation_item_outgoing
        }

    override fun getItemCount(): Int = messageItems.size

    /**
     * Gets the number of items in the adapter that are checked.
     *
     * @return The number of items in the adapter that are checked.
     */
    fun getCheckedItemCount(): Int = messageItems.filter { it.checked }.size

    operator fun get(i: Int): MessageItem = messageItems[i]

    override fun iterator(): Iterator<MessageItem> = messageItems.iterator()

    override fun getSectionTitle(position: Int): String =
        getScrollBarDate(messageItems[position].message.date)

    override fun getFilter(): Filter = object : Filter() {
        /**
         * Perform filtering using the specified filter constraint.
         *
         * @param constraint The specified constraint.
         * @return The filtered objects.
         */
        fun doFiltering(constraint: CharSequence): List<Message> {
            // Get filtered messages
            @Suppress("ConstantConditionIf")
            return if (!demo) {
                Database.getInstance(activity)
                    .getMessagesConversationFiltered(
                        conversationId,
                        constraint.toString()
                            .trim { it <= ' ' }
                            .toLowerCase())
            } else {
                getConversationDemoMessages()
            }
        }

        override fun performFiltering(
            constraint: CharSequence): Filter.FilterResults = try {
            val messages = doFiltering(constraint)

            // Return filtered messages
            val results = Filter.FilterResults()
            results.count = messages.size
            results.values = messages
            results
        } catch (e: Exception) {
            FirebaseCrash.report(e)
            Filter.FilterResults()
        }

        override fun publishResults(constraint: CharSequence,
                                    results: Filter.FilterResults?) {
            if (results?.values == null) {
                showSnackbar(activity, R.id.coordinator_layout,
                             activity.getString(
                                 R.string.new_conversation_error_refresh))
                return
            }

            // Process new filter string
            prevConstraint = currConstraint
            currConstraint = constraint.toString().trim { it <= ' ' }

            // The Android results interface uses type Any, so we have
            // no choice but to use an unchecked cast
            @Suppress("UNCHECKED_CAST")
            val newMessages = results.values as List<Message>

            // Create copy of current messages
            val oldMessages = mutableListOf<Message>()
            messageItems.mapTo(oldMessages) { it.message }

            // Iterate through messages, determining which messages have
            // been added, changed, or removed to show appropriate
            // animations and update views
            var newIdx = 0
            var oldIdx = 0
            val messageIndexes = mutableListOf<Int>()
            while (oldIdx < oldMessages.size || newIdx < newMessages.size) {
                // Positive value indicates addition, negative value
                // indicates deletion, zero indicates changed, moved, or
                // nothing
                val comparison: Int = when {
                    newIdx >= newMessages.size -> -1
                    oldIdx >= oldMessages.size -> 1
                    else -> oldMessages[oldIdx]
                        .conversationViewCompareTo(newMessages[newIdx])
                }

                when {
                    comparison < 0 -> {
                        // Remove old message
                        _messageItems.removeAt(newIdx)
                        notifyItemRemoved(newIdx)
                        oldIdx += 1
                    }
                    comparison > 0 -> {
                        // Add new message
                        _messageItems.add(newIdx,
                                          MessageItem(newMessages[newIdx]))
                        notifyItemInserted(newIdx)
                        newIdx += 1
                    }
                    else -> {
                        // Even though the view might not need to be changed,
                        // update the underlying message anyways just to be
                        // safe
                        messageItems[newIdx].message = newMessages[newIdx]
                        messageIndexes.add(newIdx)

                        oldIdx += 1
                        newIdx += 1
                    }
                }
            }

            for (idx in messageIndexes) {
                // Get the view holder for the view
                val viewHolder = recyclerView
                    .findViewHolderForAdapterPosition(idx)
                    as MessageViewHolder?

                if (viewHolder != null) {
                    // Try to update the view holder directly so that we
                    // don't see the "change" animation
                    onBindViewHolder(viewHolder, idx)
                } else {
                    // We can't find the view holder (probably because
                    // it's not actually visible), so we'll just tell
                    // the adapter to redraw the whole view to be safe
                    notifyItemChanged(idx)
                }
            }

            // Show message if filter returned no messages
            val emptyTextView = activity.findViewById<TextView>(
                R.id.empty_text)
            if (messageItems.isEmpty()) {
                if (currConstraint == "") {
                    emptyTextView.text = activity.getString(
                        R.string.conversation_no_messages)
                } else {
                    emptyTextView.text = activity.getString(
                        R.string.conversation_no_results, currConstraint)
                }
            } else {
                emptyTextView.text = ""
            }

            // Hack to force last message to not be below the send message
            // text box
            if (messageItems.size > 1) {
                if (layoutManager.findLastVisibleItemPosition()
                    >= messageItems.size - 2) {
                    layoutManager.scrollToPosition(messageItems.size - 1)
                }
            }
        }
    }

    /**
     * Refreshes the adapter using the currently defined filter constraint.
     */
    fun refresh() = filter.filter(currConstraint)

    /**
     * Refreshes the adapter using the specified filter constraint.
     *
     * @param constraint The specified filter constraint.
     */
    fun refresh(constraint: String) = filter.filter(constraint)

    /**
     * Returns true if the message at the specified position is the first
     * message in a group, which is a collection of messages that are
     * spaced together.
     *
     * @param i The position of the specified message.
     * @return True if the message at the specified position is the first
     * message in a group.
     */
    private fun isFirstMessageInGroup(i: Int): Boolean {
        val message = _messageItems[i].message
        val previousMessage: Message? = if (i > 0) {
            _messageItems[i - 1].message
        } else {
            null
        }
        return previousMessage == null
               || message.isIncoming != previousMessage.isIncoming
               || message.date.time - previousMessage.date.time > 60000
    }

    /**
     * Returns true if the message at the specified position is the last
     * message in a group, which is a collection of messages that are
     * spaced together.
     *
     * @param i The position of the specified message.
     * @return True if the message at the specified position is the last
     * message in a group.
     */
    private fun isLastMessageInGroup(i: Int): Boolean {
        if (i == _messageItems.size - 1) {
            return true
        }
        return isFirstMessageInGroup(i + 1)
    }

    /**
     * A container for a message item in the adapter that also tracks whether
     * the item is checked in addition to the message itself.
     *
     * @param message The message represented by the message item.
     */
    inner class MessageItem(var message: Message) {
        private var _checked = false
        val checked: Boolean
            get() = _checked

        /**
         * Sets whether or not the message item is checked.
         *
         * @param value True if checked, false if not.
         * @param position The position of the message item in the adapter.
         */
        fun setChecked(value: Boolean, position: Int) {
            val previous = _checked
            _checked = value

            val holder = recyclerView.findViewHolderForAdapterPosition(position)
                as MessageViewHolder?

            if ((previous && !_checked) || (!previous && _checked)) {
                if (holder != null) {
                    updateViewHolderColours(holder, position)
                } else {
                    notifyItemChanged(position)
                }
            }
        }

        /**
         * Toggles the checked state of this message item.
         *
         * @param position The position of the message item in the adapter.
         */
        fun toggle(position: Int) = setChecked(!_checked, position)
    }

    /**
     * A container for the views associated with a message item.
     *
     * @param itemView The primary view of the message item.
     */
    inner class MessageViewHolder internal constructor(
        itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        // All configurable views on a message item
        internal val contactBadge: QuickContactBadge? =
            if (viewType == R.layout.conversation_item_incoming) {
                itemView.findViewById(R.id.photo)
            } else {
                null
            }
        internal val contactBadgeLetterText: TextView? =
            if (viewType == R.layout.conversation_item_incoming) {
                itemView.findViewById(R.id.photo_letter)
            } else {
                null
            }
        internal val smsContainer: View =
            itemView.findViewById(R.id.sms_container)
        internal val messageText: TextView =
            itemView.findViewById(R.id.message)
        internal val dateText: TextView =
            itemView.findViewById(R.id.date)

        init {
            // Allow the message view itself to be selectable and add rounded
            // corners to it
            smsContainer.isClickable = true
            smsContainer.setOnClickListener(activity)
            smsContainer.isLongClickable = true
            smsContainer.setOnLongClickListener(activity)
            applyRoundedCornersMask(smsContainer)

            // Apply circular mask to and remove overlay from contact badge
            // to match Android Messages aesthetic
            if (contactBadge != null) {
                applyCircularMask(contactBadge)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    contactBadge.setOverlay(null)
                }
            }
        }
    }
}
