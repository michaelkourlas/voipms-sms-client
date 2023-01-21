/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2023 Michael Kourlas
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
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.QuickContactBadge
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.demo.getConversationDemoMessages
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.ui.FastScroller
import net.kourlas.voipms_sms.utils.*
import java.util.*
import kotlin.math.max

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
    private val contactBitmap: Bitmap
) :
    RecyclerView.Adapter<ConversationRecyclerViewAdapter.MessageViewHolder>(),
    Filterable,
    Iterable<ConversationRecyclerViewAdapter.MessageItem>,
    FastScroller.SectionTitleProvider {

    // List of items shown by the adapter; the index of each item
    // corresponds to the location of each item in the adapter.
    private val _messageItems = mutableListOf<MessageItem>()
    val messageItems: List<MessageItem>
        get() = _messageItems

    // Current and previous filter constraint.
    private var currConstraint: String = ""
    private var prevConstraint: String = ""

    // The total number of items that can be retrieved and which have been
    // retrieved.
    private var maxLimit = 0L
    private var currLimit = ADDITIONAL_ITEMS_INCREMENT

    // Whether the adapter is currently loading additional items.
    var loadingMoreItems = false

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MessageViewHolder {
        // Inflate the appropriate view, given the view type
        val itemView = when (viewType) {
            R.layout.conversation_item_incoming -> {
                LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.conversation_item_incoming,
                        parent, false
                    )
            }
            R.layout.conversation_item_outgoing -> {
                LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.conversation_item_outgoing,
                        parent, false
                    )
            }
            else -> throw Exception("Unknown view type $viewType")
        }
        return MessageViewHolder(itemView, viewType)
    }

    override fun onBindViewHolder(
        holder: MessageViewHolder,
        position: Int
    ) {
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
    private fun updateViewHolderViewHeight(
        holder: MessageViewHolder,
        position: Int
    ) {
        val marginParams = holder.itemView.layoutParams
            as ViewGroup.MarginLayoutParams
        marginParams.topMargin =
            if (isFirstMessageInGroup(
                    position,
                    combineIncomingOutgoing = false
                )
            ) {
                activity.resources.getDimension(
                    R.dimen.conversation_item_margin_top_primary
                ).toInt()
            } else {
                activity.resources.getDimension(
                    R.dimen.conversation_item_margin_top_secondary
                ).toInt()
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
    private fun updateViewHolderContactBadge(
        holder: MessageViewHolder,
        position: Int
    ) {
        val messageItem = messageItems[position]
        val message = messageItem.message

        val contactBadge = holder.contactBadge
        if (contactBadge != null) {
            // Show contact badge if first message in group
            if (isFirstMessageInGroup(
                    position,
                    combineIncomingOutgoing = false
                )
            ) {
                holder.contactBadge.visibility = View.VISIBLE
                contactBadge.assignContactFromPhone(message.contact, true)
                contactBadge.setImageBitmap(contactBitmap)
            } else {
                holder.contactBadge.visibility = View.INVISIBLE
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
    private fun updateViewHolderMessageText(
        holder: MessageViewHolder,
        position: Int
    ) {
        val messageItem = messageItems[position]
        val message = messageItem.message

        val messageText = holder.messageText
        val messageTextBuilder = SpannableStringBuilder()
        messageTextBuilder.append(message.text)

        // Highlight text that matches filter
        if (currConstraint != "") {
            val index = message.text.lowercase(Locale.getDefault()).indexOf(
                currConstraint.lowercase(Locale.getDefault())
            )
            if (index != -1) {
                messageTextBuilder.setSpan(
                    BackgroundColorSpan(
                        ContextCompat.getColor(
                            activity, R.color.highlight
                        )
                    ),
                    index,
                    index + currConstraint.length,
                    SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
                )
                messageTextBuilder.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            activity, android.R.color.black
                        )
                    ),
                    index,
                    index + currConstraint.length,
                    SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
                )
            }
        }
        messageText.text = messageTextBuilder

        Linkify.addLinks(
            messageText,
            Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS or Linkify.WEB_URLS
        )
        messageText.movementMethod = null
        (messageText as MessageTextView).messageLongClickListener = {
            val pos = messageItems.indexOf(messageItem)
            if (pos != -1) {
                messageItem.toggle(pos)
            }
        }
    }

    /**
     * Displays the date and time for the message. Optionally displays a
     * the status of the message in certain cases instead, such as when the
     * message is being sent or has failed to send. Optionally hides it
     * altogether, depending on the message's placement in a group.
     *
     * Optionally displays the date and time of the conversation group,
     * depending on the message's placement in the group.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderDateText(
        holder: MessageViewHolder,
        position: Int
    ) {
        val messageItem = messageItems[position]
        val message = messageItem.message

        // Set per-message date and time
        val dateText = holder.dateText
        if (!message.isDelivered) {
            if (!message.isDeliveryInProgress) {
                // Show tried but failed to send text
                val dateTextBuilder = SpannableStringBuilder()
                dateTextBuilder.append(
                    activity.getString(
                        R.string.conversation_message_not_sent
                    )
                )
                dateTextBuilder.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            activity,
                            android.R.color.holo_red_dark
                        )
                    ),
                    0,
                    dateTextBuilder.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
                dateTextBuilder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    dateTextBuilder.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
                dateText.text = dateTextBuilder
            } else {
                // Show sending text
                dateText.text = activity.getString(
                    R.string.conversation_message_sending
                )
            }
            dateText.visibility = View.VISIBLE
        } else {
            dateText.text = getConversationViewDate(activity, message.date)
        }

        // Set conversation group date and time
        val topDateText = holder.topDateText
        if (isFirstMessageInGroup(position, combineIncomingOutgoing = true)) {
            topDateText.text = getConversationViewTopDate(message.date)
            topDateText.visibility = View.VISIBLE
        } else {
            topDateText.visibility = View.GONE
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

        // Incoming messages have the secondary color, while outgoing messages
        // have the primary color; dark variants are used for selection
        val smsContainer = holder.smsContainer
        if (message.isIncoming) {
            if (messageItem.checked) {
                smsContainer.setBackgroundResource(
                    R.color.message_incoming_checked
                )
            } else {
                smsContainer.setBackgroundResource(R.color.message_incoming)
            }
        } else {
            if (messageItem.checked) {
                smsContainer.setBackgroundResource(
                    R.color.message_outgoing_checked
                )
            } else {
                smsContainer.setBackgroundResource(R.color.message_outgoing)
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
     */
    fun getCheckedItemCount(): Int = messageItems.filter { it.checked }.size

    operator fun get(i: Int): MessageItem = messageItems[i]

    override fun iterator(): Iterator<MessageItem> = messageItems.iterator()

    override fun getSectionTitle(position: Int): String =
        getScrollBarDate(messageItems[position].message.date)

    override fun getFilter(): Filter = object : Filter() {
        /**
         * Perform filtering using the specified filter constraint.
         */
        fun doFiltering(constraint: CharSequence): ConversationFilter {
            // Get filtered messages
            val resultsObject = ConversationFilter()
            @Suppress("ConstantConditionIf")
            if (!BuildConfig.IS_DEMO) {
                runBlocking {
                    val filterString = constraint.toString()
                        .trim { it <= ' ' }
                        .lowercase(Locale.getDefault())
                    maxLimit = Database.getInstance(activity)
                        .getConversationMessagesFilteredCount(
                            conversationId, filterString
                        )
                    if (currLimit > maxLimit) {
                        currLimit = max(maxLimit, ADDITIONAL_ITEMS_INCREMENT)
                    }
                    resultsObject.messages.addAll(
                        Database.getInstance(activity)
                            .getConversationMessagesFiltered(
                                conversationId,
                                filterString,
                                currLimit
                            ).asReversed()
                    )
                }
            } else {
                resultsObject.messages.addAll(
                    getConversationDemoMessages(
                        activity.bubble
                    )
                )
            }
            return resultsObject
        }

        override fun performFiltering(
            constraint: CharSequence
        ): FilterResults = try {
            val resultsObject = doFiltering(constraint)

            // Return filtered messages
            val results = FilterResults()
            results.count = resultsObject.messages.size
            results.values = resultsObject
            results
        } catch (e: Exception) {
            logException(e)
            FilterResults()
        }

        override fun publishResults(
            constraint: CharSequence,
            results: FilterResults?
        ) {
            if (results?.values == null) {
                showSnackbar(
                    activity, R.id.coordinator_layout,
                    activity.getString(
                        R.string.new_conversation_error_refresh
                    )
                )
                return
            }

            // Process new filter string
            prevConstraint = currConstraint
            currConstraint = constraint.toString().trim { it <= ' ' }

            // The Android results interface uses type Any, so we have
            // no choice but to use an unchecked cast
            val resultsObject = results.values as ConversationFilter
            val newMessages = resultsObject.messages

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
                        _messageItems.add(
                            newIdx,
                            MessageItem(newMessages[newIdx])
                        )
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
                R.id.empty_text
            )
            if (messageItems.isEmpty()) {
                if (currConstraint == "") {
                    emptyTextView.text = activity.getString(
                        R.string.conversation_no_messages
                    )
                } else {
                    emptyTextView.text = activity.getString(
                        R.string.conversation_no_results, currConstraint
                    )
                }
            } else {
                emptyTextView.text = ""
            }

            // Hack to force last message to not be below the send message
            // text box
            if (messageItems.size > 1) {
                if (layoutManager.findLastVisibleItemPosition()
                    >= messageItems.size - 2
                ) {
                    layoutManager.scrollToPosition(messageItems.size - 1)
                }
            }

            loadingMoreItems = false
        }
    }

    /**
     * Refreshes the adapter using the currently defined filter constraint.
     */
    fun refresh() = filter.filter(currConstraint)

    /**
     * Refreshes the adapter using the specified filter constraint.
     */
    fun refresh(constraint: String) = filter.filter(constraint)

    /**
     * Loads additional items from the database.
     */
    fun loadMoreItems() {
        if (currLimit + ADDITIONAL_ITEMS_INCREMENT <= maxLimit) {
            loadingMoreItems = true
            currLimit += ADDITIONAL_ITEMS_INCREMENT
            refresh()
        }
    }

    /**
     * Helper class used to store messages.
     */
    class ConversationFilter {
        internal val messages = mutableListOf<Message>()
    }

    /**
     * Returns true if the message at the specified position is the first
     * message in a group, which is a collection of messages that are
     * spaced together based on whether the message is incoming or outgoing,
     * as well as the time between the messages.
     *
     * @param position The position of the specified message.
     * @param combineIncomingOutgoing If true, both incoming and outgoing
     * messages are considered to be part of the same group.
     */
    private fun isFirstMessageInGroup(
        position: Int, combineIncomingOutgoing: Boolean
    ): Boolean {
        val message = _messageItems[position].message
        val previousMessage: Message? = if (position > 0) {
            _messageItems[position - 1].message
        } else {
            null
        }
        return previousMessage == null
            || (!combineIncomingOutgoing
            && message.isIncoming != previousMessage.isIncoming)
            || message.date.time - previousMessage.date.time > 60000
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
         * @param checked Whether the message item is checked.
         * @param position The position of the message item in the adapter.
         */
        fun setChecked(checked: Boolean, position: Int) {
            val previous = _checked
            _checked = checked

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
        itemView: View,
        viewType: Int
    ) : RecyclerView.ViewHolder(
        itemView
    ) {
        // All configurable views on a message item
        internal val contactBadge: QuickContactBadge? =
            if (viewType == R.layout.conversation_item_incoming) {
                itemView.findViewById(R.id.photo)
            } else {
                null
            }
        internal val smsContainer: View =
            itemView.findViewById(R.id.sms_container)
        internal val messageText: TextView =
            itemView.findViewById(R.id.message)
        internal val dateText: TextView =
            itemView.findViewById(R.id.date)
        internal val topDateText: TextView =
            itemView.findViewById(R.id.top_date)

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
                contactBadge.setOverlay(null)
            }
        }
    }

    companion object {
        // The number of additional items to retrieve when loadMoreItems is
        // called.
        private const val ADDITIONAL_ITEMS_INCREMENT = 100L

        // When the message with this index is shown, we should start to load
        // more items.
        const val START_LOAD_INDEX = ADDITIONAL_ITEMS_INCREMENT / 4
    }
}
