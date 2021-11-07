/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2021 Michael Kourlas
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

package net.kourlas.voipms_sms.conversations

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.demo.getConversationsDemoMessages
import net.kourlas.voipms_sms.preferences.getActiveDid
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.utils.*
import java.util.*

/**
 * Recycler view adapter used by [ConversationsActivity] and
 * [ConversationsArchivedActivity].
 *
 * @param activity The source [ConversationsActivity] or
 * [ConversationsArchivedActivity].
 * @param recyclerView The recycler view used by the activity.
 * @param layoutManager The layout manager used by the recycler view.
 */
class ConversationsRecyclerViewAdapter<T>(
    private val activity: T,
    private val recyclerView: RecyclerView,
    private val layoutManager: LinearLayoutManager
) :
    RecyclerView.Adapter<ConversationsRecyclerViewAdapter<T>.ConversationViewHolder>(),
    Filterable,
    Iterable<ConversationsRecyclerViewAdapter<T>.ConversationItem>
    where T : AppCompatActivity, T : View.OnClickListener, T : View.OnLongClickListener {
    // List of items shown by the adapter; the index of each item
    // corresponds to the location of each item in the adapter
    private val _conversationItems = mutableListOf<ConversationItem>()
    val conversationItems: List<ConversationItem>
        get() = _conversationItems

    // Current and previous filter constraint
    private var currConstraint: String = ""
    private var prevConstraint: String = ""

    // Caches
    private val contactNameCache = mutableMapOf<String, String>()
    private val contactBitmapCache = mutableMapOf<String, Bitmap>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ConversationViewHolder {
        // There is only one item view type
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.conversations_item, parent, false)
        return ConversationViewHolder(itemView)
    }

    override fun onBindViewHolder(
        holder: ConversationViewHolder,
        position: Int
    ) {
        // Set up view to match message at position
        updateViewHolderContactBadge(holder, position)
        updateViewHolderContactText(holder, position)
        updateViewHolderMessageText(holder, position)
        updateViewHolderDateText(holder, position)
    }

    /**
     * Changes the contact badge to a check mark, a photo, or a material design
     * letter.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    fun updateViewHolderContactBadge(
        holder: ConversationViewHolder,
        position: Int
    ) {
        val conversationItem = conversationItems[position]
        val message = conversationItem.message

        holder.viewSwitcher.displayedChild =
            if (conversationItem.checked) 1 else 0
        if (!conversationItem.checked) {
            holder.contactBadge.assignContactFromPhone(message.contact, true)
            holder.contactBadge.setImageBitmap(
                conversationItem.contactBitmap
            )
        }
    }

    /**
     * Displays the contact name or phone number associated with the
     * conversation on the view holder. Selects and highlights part of the
     * text if a filter is configured. Marks text as bold if unread.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderContactText(
        holder: ConversationViewHolder,
        position: Int
    ) {
        val conversationItem = conversationItems[position]
        val message = conversationItem.message

        val contactTextBuilder = SpannableStringBuilder()
        contactTextBuilder.append(
            conversationItem.contactName ?: getFormattedPhoneNumber(
                conversationItem.message.contact
            )
        )

        // Highlight text that matches filter
        if (currConstraint != "") {
            val index = contactTextBuilder.toString()
                .lowercase(Locale.getDefault())
                .indexOf(currConstraint.lowercase(Locale.getDefault()))
            if (index != -1) {
                contactTextBuilder.setSpan(
                    BackgroundColorSpan(
                        ContextCompat.getColor(activity, R.color.highlight)
                    ),
                    index,
                    index + currConstraint.length,
                    SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
                )
                contactTextBuilder.setSpan(
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

        holder.contactTextView.text = contactTextBuilder

        // Mark text as bold if unread
        if (message.isUnread) {
            holder.contactTextView.setTypeface(null, Typeface.BOLD)
        } else {
            holder.contactTextView.setTypeface(null, Typeface.NORMAL)
        }
    }

    /**
     * Displays the text of the displayed message of the conversation on the
     * view holder. Selects and highlights part of the text if a filter is
     * configured. Marks text as bold if unread.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderMessageText(
        holder: ConversationViewHolder,
        position: Int
    ) {
        val conversationItem = conversationItems[position]
        val message = conversationItem.message

        val messageTextBuilder = SpannableStringBuilder()

        // Highlight text that matches filter
        val index = message.text.lowercase(Locale.getDefault()).indexOf(
            currConstraint.lowercase(Locale.getDefault())
        )
        if (currConstraint != "" && index != -1) {
            var nonMessageOffset = index
            if (message.isOutgoing) {
                // Preface with "You: " if outgoing
                val youStr = activity.getString(
                    R.string.conversations_message_you
                ) + " "
                messageTextBuilder.insert(0, youStr)
                nonMessageOffset += youStr.length
            }

            // If match is in the middle of the string, show partial string
            var substringOffset = index - 20
            if (substringOffset > 0) {
                messageTextBuilder.append("…")
                nonMessageOffset += 1

                while (message.text[substringOffset] != ' '
                    && substringOffset < index - 1
                ) {
                    substringOffset += 1
                }
                substringOffset += 1
            } else {
                substringOffset = 0
            }

            messageTextBuilder.append(message.text.substring(substringOffset))
            messageTextBuilder.setSpan(
                BackgroundColorSpan(
                    ContextCompat.getColor(
                        activity,
                        R.color.highlight
                    )
                ),
                nonMessageOffset - substringOffset,
                nonMessageOffset - substringOffset + currConstraint.length,
                SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
            )
            messageTextBuilder.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(
                        activity, android.R.color.black
                    )
                ),
                nonMessageOffset - substringOffset,
                nonMessageOffset - substringOffset + currConstraint.length,
                SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
            )
        } else {
            if (message.isOutgoing) {
                // Preface with "You: " if outgoing
                messageTextBuilder.append(
                    activity.getString(
                        R.string.conversations_message_you
                    )
                )
                messageTextBuilder.append(" ")
            }
            messageTextBuilder.append(message.text)
        }
        holder.messageTextView.text = messageTextBuilder

        // Mark text as bold and supporting additional lines if unread
        if (message.isUnread) {
            holder.messageTextView.setTypeface(null, Typeface.BOLD)
            holder.messageTextView.maxLines = 3
        } else {
            holder.messageTextView.setTypeface(null, Typeface.NORMAL)
            holder.messageTextView.maxLines = 1
        }
    }

    /**
     * Displays the date of the displayed message of the conversation
     * on the view holder. Shows special text if the message is a draft,
     * is sending or is not sent.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderDateText(
        holder: ConversationViewHolder,
        position: Int
    ) {
        val conversationItem = conversationItems[position]
        val message = conversationItem.message

        if (message.isDraft) {
            // Show indication that the first message is a draft
            val dateTextBuilder = SpannableStringBuilder()
            dateTextBuilder.append(
                activity.getString(
                    R.string.conversations_message_draft
                )
            )
            dateTextBuilder.setSpan(
                StyleSpan(Typeface.ITALIC), 0,
                dateTextBuilder.length,
                Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            holder.dateTextView.text = dateTextBuilder
        } else if (!message.isDelivered) {
            if (!message.isDeliveryInProgress) {
                // Show indication that the first message has not yet been sent
                val dateTextBuilder = SpannableStringBuilder()
                dateTextBuilder.append(
                    activity.getString(
                        R.string.conversations_message_not_sent
                    )
                )
                dateTextBuilder.setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            activity,
                            android.R.color.holo_red_dark
                        )
                    ),
                    0, dateTextBuilder.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
                dateTextBuilder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    dateTextBuilder.length,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                )
                holder.dateTextView.text = dateTextBuilder
            } else {
                // Show indication that the first message is being sent
                holder.dateTextView.text = activity.getString(
                    R.string.conversations_message_sending
                )
            }
        } else {
            // Show date of message
            holder.dateTextView.text = getConversationsViewDate(
                activity, message.date
            )
        }
    }

    override fun getItemCount(): Int = conversationItems.size

    /**
     * Gets the number of items in the adapter that are checked.
     */
    fun getCheckedItemCount(): Int =
        conversationItems.filter { it.checked }.size

    operator fun get(i: Int): ConversationItem = conversationItems[i]

    override fun iterator(): Iterator<ConversationItem> =
        conversationItems.iterator()

    override fun getFilter(): Filter = object : Filter() {
        /**
         * Perform filtering using the specified filter constraint.
         */
        fun doFiltering(constraint: CharSequence): ConversationsFilter {
            val resultsObject = ConversationsFilter()

            @Suppress("ConstantConditionIf")
            if (!BuildConfig.IS_DEMO) {
                val activeDid = getActiveDid(activity)
                runBlocking {
                    resultsObject.messages.addAll(
                        Database.getInstance(activity)
                            .getConversationsMessageMostRecentFiltered(
                                if (activeDid == "")
                                    getDids(
                                        activity,
                                        onlyShowInConversationsView = true
                                    )
                                else setOf(activeDid),
                                constraint.toString()
                                    .trim { it <= ' ' }
                                    .lowercase(Locale.getDefault())).filter {
                                val archived = Database.getInstance(activity)
                                    .isConversationArchived(it.conversationId)
                                if (activity is ConversationsArchivedActivity) {
                                    archived
                                } else {
                                    !archived
                                }
                            })
                }
            } else {
                resultsObject.messages.addAll(
                    getConversationsDemoMessages()
                )
            }

            for (message in resultsObject.messages) {
                @Suppress("ConstantConditionIf")
                val contactName = if (!BuildConfig.IS_DEMO) {
                    getContactName(
                        activity,
                        message.contact,
                        contactNameCache
                    )
                } else {
                    net.kourlas.voipms_sms.demo.getContactName(
                        message.contact
                    )
                }
                if (contactName != null) {
                    resultsObject.contactNames[message.contact] =
                        contactName
                }

                val bitmap = getContactPhotoBitmap(
                    activity,
                    contactName,
                    message.contact,
                    activity.resources.getDimensionPixelSize(
                        R.dimen.contact_badge
                    ),
                    contactBitmapCache
                )
                resultsObject.contactBitmaps[message.contact] = bitmap
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
                        R.string.conversations_error_refresh
                    )
                )
                return
            }

            // Process new filter string
            prevConstraint = currConstraint
            currConstraint = constraint.toString().trim { it <= ' ' }

            val position = layoutManager.findFirstVisibleItemPosition()

            // The Android results interface uses type Any, so we
            // have no choice but to use an unchecked cast
            val resultsObject = results.values as ConversationsFilter

            // Get new messages from results list
            val newMessages: List<Message>
            if (results.values != null) {
                // The Android results interface uses type Any, so we have
                // no choice but to use an unchecked cast
                @Suppress("UNCHECKED_CAST")
                newMessages = resultsObject.messages
            } else {
                newMessages = emptyList()
            }

            // Create copy of current messages
            val oldMessages = mutableListOf<Message>()
            conversationItems.mapTo(oldMessages) { it.message }

            // Iterate through messages, determining which messages have
            // been added, changed, or removed to show appropriate
            // animations and update views
            var newIdx = 0
            var oldIdx = 0
            val messageIndexes = mutableListOf<Int>()
            while (oldIdx < oldMessages.size || newIdx < newMessages.size) {
                // Positive value indicates deletion, negative value
                // indicates addition, zero indicates changed, moved, or
                // nothing
                val comparison: Int = when {
                    newIdx >= newMessages.size -> 1
                    oldIdx >= oldMessages.size -> -1
                    else -> oldMessages[oldIdx]
                        .conversationsViewCompareTo(newMessages[newIdx])
                }

                when {
                    comparison < 0 -> {
                        // Add new message
                        val contact = newMessages[newIdx].contact
                        _conversationItems.add(
                            newIdx,
                            ConversationItem(
                                newMessages[newIdx],
                                resultsObject.contactNames[contact],
                                resultsObject.contactBitmaps[contact]!!
                            )
                        )
                        notifyItemInserted(newIdx)
                        newIdx += 1
                    }
                    comparison > 0 -> {
                        // Remove old message
                        _conversationItems.removeAt(newIdx)
                        notifyItemRemoved(newIdx)
                        oldIdx += 1
                    }
                    else -> {
                        // Update the underlying message
                        conversationItems[newIdx].message = newMessages[newIdx]
                        messageIndexes.add(newIdx)

                        oldIdx += 1
                        newIdx += 1
                    }
                }
            }

            for (idx in messageIndexes) {
                recyclerView.findViewHolderForAdapterPosition(idx)?.let {
                    // Try to update the view holder directly so that we
                    // don't see the "change" animation
                    @Suppress("RemoveRedundantQualifierName", "UNCHECKED_CAST")
                    onBindViewHolder(
                        it as ConversationsRecyclerViewAdapter<T>
                        .ConversationViewHolder, idx
                    )
                } ?: run {
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
            if (conversationItems.isEmpty()) {
                if (currConstraint == "") {
                    when {
                        getDids(
                            activity,
                            onlyShowInConversationsView = true
                        ).isEmpty() ->
                            emptyTextView.text = activity.getString(
                                R.string.conversations_no_dids
                            )
                        activity is ConversationsArchivedActivity ->
                            emptyTextView.text = activity.getString(
                                R.string.conversations_archived_no_messages
                            )
                        else -> emptyTextView.text = activity.getString(
                            R.string.conversations_no_messages
                        )
                    }
                } else {
                    emptyTextView.text = activity.getString(
                        R.string.conversations_no_results, currConstraint
                    )
                }
            } else {
                emptyTextView.text = ""
            }

            layoutManager.scrollToPosition(position)
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
     * Helper class used to store retrieved contact names and bitmaps in
     * addition to messages.
     *
     * This exists because contact name and photo retrieval needs to
     * happen during filtering so that it occurs on a thread other than the
     * UI thread.
     */
    class ConversationsFilter {
        internal val messages = mutableListOf<Message>()
        internal val contactNames = mutableMapOf<String, String>()
        internal val contactBitmaps = mutableMapOf<String, Bitmap>()
    }

    /**
     * A container for a conversation item in the adapter that also tracks
     * whether the item is checked in addition to the conversation itself.
     *
     * @param message The currently displayed message in the conversation.
     * @param contactName The name of the displayed contact.
     * @param contactBitmap The photo of the displayed contact.
     */
    inner class ConversationItem(
        var message: Message, val contactName: String?,
        val contactBitmap: Bitmap
    ) {
        private var _checked = false
        val checked: Boolean
            get() = _checked

        /**
         * Sets whether or not the conversation item is checked.
         *
         * @param position The position of the message item in the adapter.
         */
        fun setChecked(checked: Boolean, position: Int) {
            val previous = _checked
            _checked = checked

            if ((previous && !_checked) || (!previous && _checked)) {

                recyclerView.findViewHolderForAdapterPosition(position)?.let {
                    @Suppress("RemoveRedundantQualifierName", "UNCHECKED_CAST")
                    updateViewHolderContactBadge(
                        it as ConversationsRecyclerViewAdapter<T>
                        .ConversationViewHolder, position
                    )
                } ?: run {
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
     * A container for the views associated with a conversation item.
     *
     * @param itemView The primary view of the conversation item.
     */
    inner class ConversationViewHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        // All configurable views on a message item
        internal val viewSwitcher: ViewSwitcher =
            itemView.findViewById(R.id.view_switcher)
        internal val contactBadge: QuickContactBadge =
            itemView.findViewById(R.id.photo)
        internal val contactTextView: TextView =
            itemView.findViewById(R.id.contact)
        internal val messageTextView: TextView =
            itemView.findViewById(R.id.message)
        internal val dateTextView: TextView =
            itemView.findViewById(R.id.date)

        init {
            // Allow the conversation view itself to be clickable and
            // selectable
            itemView.isClickable = true
            itemView.setOnClickListener(activity)
            itemView.isLongClickable = true
            itemView.setOnLongClickListener(activity)

            // Apply circular mask to and remove overlay from contact badge
            // to match Android Messages aesthetic; in addition, set up
            // check box image
            applyCircularMask(contactBadge)
            contactBadge.setOverlay(null)
            val contactBadgeChecked = itemView
                .findViewById<ImageView>(R.id.conversations_photo_checked)
            applyCircularMask(contactBadgeChecked)
        }
    }
}
