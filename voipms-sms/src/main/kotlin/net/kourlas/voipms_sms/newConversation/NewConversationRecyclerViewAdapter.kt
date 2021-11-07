/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
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

package net.kourlas.voipms_sms.newConversation

import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.demo.getNewConversationContacts
import net.kourlas.voipms_sms.ui.FastScroller
import net.kourlas.voipms_sms.utils.*
import java.util.*

/**
 * Recycler view adapter used by [NewConversationActivity].
 *
 * @param activity The source [NewConversationActivity].
 * @param recyclerView The recycler view used by the activity.
 */
class NewConversationRecyclerViewAdapter(
    private val activity: NewConversationActivity,
    private val recyclerView: RecyclerView
) :
    RecyclerView.Adapter<
        NewConversationRecyclerViewAdapter.ContactViewHolder>(),
    Filterable,
    FastScroller.SectionTitleProvider {

    // List of items shown by the adapter; the index of each item
    // corresponds to the location of each item in the adapter
    private val _contactItems = mutableListOf<BaseContactItem>()
    val contactItems: List<BaseContactItem>
        get() = _contactItems

    // Current filter constraint
    var currConstraint = ""

    // List of all possible contact items, except for the typed in phone number
    private val allContactItems = mutableListOf<ContactItem>()

    // Reference to the typed in phone number; a contact item will be created
    // using filtering for this number if not null
    var typedInPhoneNumber: String = ""

    init {
        // When the recycler view adapter is created, load all contacts from
        // the Android contacts provider
        loadAllContactItems()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ContactViewHolder =
        // There is only one item view type
        ContactViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.new_conversation_item,
                    parent, false
                )
        )

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        updateViewHolderIndex(holder, position)
        updateViewHolderContactBadge(holder, position)
        updateViewHolderContact(holder, position)
    }

    /**
     * Adds an index label to the first contact item starting with its first
     * letter.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderIndex(
        holder: ContactViewHolder,
        position: Int
    ) {
        val contactItem = contactItems[position]

        // Add indexes to entries where appropriate
        if (contactItem is ContactItem) {
            val previousItem = if (position != 0) {
                contactItems[position - 1]
            } else {
                null
            }

            val currentInitial = getContactInitial(contactItem.getSortingName())
            if (position == 0
                || previousItem is TypedInContactItem
                || (previousItem is ContactItem
                    && currentInitial != getContactInitial(
                    previousItem.getSortingName()
                ))
            ) {
                holder.letterText.text = currentInitial
            } else {
                holder.letterText.text = ""
            }
        } else {
            holder.letterText.text = ""
        }
    }

    /**
     * Changes the contact badge to a check mark, a photo, or a material design
     * letter.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderContactBadge(
        holder: ContactViewHolder,
        position: Int
    ) {
        val contactItem = contactItems[position]

        // Set contact photo
        holder.contactBadge.assignContactFromPhone(
            contactItem.primaryPhoneNumber, true
        )
        if (contactItem is TypedInContactItem) {
            // Show dialpad for typed in phone number
            holder.contactBadge.scaleType = ImageView.ScaleType.CENTER
            holder.contactBadge.setBackgroundResource(R.color.typed_in_contact)
            holder.contactBadge.setImageResource(
                R.drawable.ic_dialpad_toolbar_24dp
            )
        } else if (contactItem is ContactItem) {
            holder.contactBadge.scaleType = ImageView.ScaleType.CENTER_CROP
            holder.contactBadge.setBackgroundResource(0)
            holder.contactBadge.setImageBitmap(contactItem.bitmap)
        }
    }

    /**
     * Displays the name, phone number, and type associated with the contact
     * on the view holder.
     *
     * @param holder The message view holder to use.
     * @param position The position of the view in the adapter.
     */
    private fun updateViewHolderContact(
        holder: ContactViewHolder,
        position: Int
    ) {
        val contactItem = contactItems[position]

        // Set contact name
        if (contactItem is TypedInContactItem) {
            holder.contactText.text = activity.getString(
                R.string.new_conversation_manual_entry
            )
        } else if (contactItem is ContactItem) {
            holder.contactText.text = contactItem.name
        }

        // Set phone number text
        if (contactItem is ContactItem
            && !contactItem.showSeparateNameAndPhoneNumber()
        ) {
            holder.phoneNumberText.visibility = View.GONE
        } else {
            var text = contactItem.primaryPhoneNumber
            if (contactItem is ContactItem
                && contactItem.phoneNumbersAndTypes.size > 1
            ) {
                // Add (+X) if there are secondary phone numbers
                text += " (+${contactItem.phoneNumbersAndTypes.size - 1})"

            }
            holder.phoneNumberText.text = text
            holder.phoneNumberText.visibility = View.VISIBLE
        }


        // Set phone number type
        if (contactItem is ContactItem) {
            if (contactItem.phoneNumbersAndTypes.size == 1) {
                holder.phoneNumberTypeText.text =
                    contactItem.phoneNumbersAndTypes[0].type
            } else {
                holder.phoneNumberTypeText.text = activity.getString(
                    R.string.new_conversation_multiple
                )
            }
            holder.phoneNumberTypeText.visibility = View.VISIBLE
        } else {
            holder.phoneNumberTypeText.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = contactItems.size

    operator fun get(i: Int): BaseContactItem = contactItems[i]

    override fun getSectionTitle(position: Int): String {
        // The typed in phone number item has no section title
        val contactItem = contactItems[position]
        return if (contactItem is ContactItem) {
            getContactInitial(contactItem.getSortingName())
        } else {
            ""
        }
    }

    override fun getFilter(): Filter = object : Filter() {
        /**
         * Perform filtering using the specified filter constraint.
         *
         * @param constraint The specified constraint.
         * @return The filtered objects.
         */
        fun doFiltering(constraint: CharSequence): List<BaseContactItem> {
            val filteredContactItems = mutableListOf<BaseContactItem>()

            // If there is a typed in phone number, always include it in
            // the filtered list
            if (typedInPhoneNumber != "") {
                filteredContactItems.add(
                    TypedInContactItem(
                        typedInPhoneNumber
                    )
                )
            }

            // Perform actual filtering
            val currConstraint = constraint.toString().trim { it <= ' ' }
            for (contactItem in allContactItems) {
                val match =
                    contactItem.name.lowercase(Locale.getDefault()).contains(
                        currConstraint.lowercase(Locale.getDefault())
                    )
                        || contactItem.primaryPhoneNumber
                        .lowercase(Locale.getDefault())
                        .contains(
                            currConstraint.lowercase(
                                Locale.getDefault()
                            )
                        )
                        || (getDigitsOfString(currConstraint) != ""
                        && getDigitsOfString(
                        contactItem.primaryPhoneNumber
                    ).contains(
                        getDigitsOfString(currConstraint)
                    ))
                if (match) {
                    filteredContactItems.add(contactItem)
                }
            }

            return filteredContactItems
        }

        override fun performFiltering(
            constraint: CharSequence
        ): FilterResults = try {
            val filteredContactItems = doFiltering(constraint)

            // Return the filtered results
            val results = FilterResults()
            results.count = filteredContactItems.size
            results.values = filteredContactItems
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
            currConstraint = constraint.toString().trim { it <= ' ' }

            // The Android results interface uses type Any, so we
            // have no choice but to use an unchecked cast)
            @Suppress("UNCHECKED_CAST")
            val newContactItems = results.values as List<BaseContactItem>

            // Create copy of current contacts
            val oldContactItems = mutableListOf<BaseContactItem>()
            oldContactItems.addAll(contactItems)

            // Iterate through contacts, determining which contacts have
            // been added or removed to show appropriate
            // animations and update views
            var newIdx = 0
            var oldIdx = 0
            val contactItemIndexes = mutableListOf<Int>()
            while (oldIdx < oldContactItems.size
                || newIdx < newContactItems.size
            ) {
                // Positive value indicates addition, negative value
                // indicates deletion, zero indicates changed, moved, or
                // nothing
                val comparison: Int
                if (newIdx >= newContactItems.size) {
                    comparison = -1
                } else if (oldIdx >= oldContactItems.size) {
                    comparison = 1
                } else if (oldContactItems[oldIdx] is TypedInContactItem
                    && newContactItems[newIdx] is TypedInContactItem
                ) {
                    comparison = 0
                } else if (oldContactItems[oldIdx] is TypedInContactItem) {
                    comparison = -1
                } else if (newContactItems[newIdx] is TypedInContactItem) {
                    comparison = 1
                } else {
                    val oldContact = oldContactItems[oldIdx] as ContactItem
                    val newContact = newContactItems[newIdx] as ContactItem
                    comparison = oldContact.name.compareTo(
                        newContact.name, ignoreCase = true
                    )
                }

                when {
                    comparison < 0 -> {
                        // Remove old contact
                        _contactItems.removeAt(newIdx)
                        notifyItemRemoved(newIdx)
                        oldIdx += 1
                    }
                    comparison > 0 -> {
                        // Add new contact
                        _contactItems.add(newIdx, newContactItems[newIdx])
                        notifyItemInserted(newIdx)
                        newIdx += 1
                    }
                    else -> {
                        // Replace existing contact, just to be safe
                        _contactItems[newIdx] = newContactItems[newIdx]
                        contactItemIndexes.add(newIdx)

                        oldIdx += 1
                        newIdx += 1
                    }
                }
            }

            for (idx in contactItemIndexes) {
                // Get the view holder for the view
                val viewHolder = recyclerView
                    .findViewHolderForAdapterPosition(idx)
                    as ContactViewHolder?

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

            // Show message if filter returned no contacts
            val emptyTextView = activity.findViewById<TextView>(
                R.id.empty_text
            )
            if (contactItems.isEmpty()) {
                if (currConstraint == "") {
                    emptyTextView.text = activity.getString(
                        R.string.new_conversation_no_contacts
                    )
                } else {
                    emptyTextView.text = activity.getString(
                        R.string.new_conversation_no_results,
                        constraint.toString()
                    )
                }
            } else {
                emptyTextView.text = ""
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
     * Loads all contacts from the Android contacts provider.
     */
    private fun loadAllContactItems() {
        @Suppress("ConstantConditionIf")
        if (BuildConfig.IS_DEMO) {
            allContactItems.addAll(getNewConversationContacts(activity))
            return
        }

        try {
            val cursor = activity.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    + " COLLATE NOCASE ASC"
            )
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    var index = cursor.getColumnIndex(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    )
                    if (index < 0) {
                        continue
                    }
                    if (cursor.getString(index) == "1") {
                        index = cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone
                                .CONTACT_ID
                        )
                        if (index < 0) {
                            continue
                        }
                        val id = cursor.getLong(index)

                        index = cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds
                                .Phone.NUMBER
                        )
                        if (index < 0) {
                            continue
                        }
                        val phoneNumber = cursor.getString(
                            index
                        ) ?: continue

                        index = cursor.getColumnIndex(
                            ContactsContract.Contacts.DISPLAY_NAME
                        )
                        if (index < 0) {
                            continue
                        }
                        val contact = cursor.getString(
                            index
                        )
                            ?: getFormattedPhoneNumber(phoneNumber)

                        index = cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds
                                .Phone.TYPE
                        )
                        if (index < 0) {
                            continue
                        }
                        val phoneNumberType = getPhoneNumberType(
                            cursor.getInt(
                                index
                            )
                        )

                        index = cursor.getColumnIndex(
                            ContactsContract.Contacts.PHOTO_URI
                        )
                        if (index < 0) {
                            continue
                        }
                        val photoUri = cursor.getString(
                            index
                        )

                        index = cursor.getColumnIndex(
                            ContactsContract.Contacts.DISPLAY_NAME
                        )
                        if (index < 0) {
                            continue
                        }
                        val bitmap = photoUri?.let {
                            getBitmapFromUri(
                                activity,
                                Uri.parse(it),
                                activity.resources.getDimensionPixelSize(
                                    R.dimen.contact_badge
                                )
                            )
                        } ?: getGenericContactPhotoBitmap(
                            activity,
                            cursor.getString(
                                index
                            ),
                            phoneNumber,
                            activity.resources.getDimensionPixelSize(
                                R.dimen.contact_badge
                            )
                        )

                        val previousContactItem =
                            if (allContactItems.size > 0) {
                                allContactItems.last()
                            } else {
                                null
                            }
                        // If multiple phone numbers, show "Multiple" as type
                        if (previousContactItem?.id == id) {
                            val phoneNumbers = previousContactItem
                                .phoneNumbersAndTypes
                                .map { phoneNumberAndType ->
                                    phoneNumberAndType.phoneNumber
                                }
                            if (phoneNumber !in phoneNumbers) {
                                previousContactItem.phoneNumbersAndTypes.add(
                                    PhoneNumberAndType(
                                        phoneNumber, phoneNumberType
                                    )
                                )
                            }
                        } else {
                            allContactItems.add(
                                ContactItem(
                                    id,
                                    contact,
                                    mutableListOf(
                                        PhoneNumberAndType(
                                            phoneNumber, phoneNumberType
                                        )
                                    ),
                                    bitmap
                                )
                            )
                        }
                    }
                }
                cursor.close()
            }
        } catch (ignored: SecurityException) {
            // Do nothing.
        }

        // Sort contact items
        allContactItems.sortBy {
            it.getSortingName()
        }
    }

    /**
     * A container for the views associated with a contact item.
     *
     * @param itemView The primary view of the contact item.
     */
    inner class ContactViewHolder internal constructor(
        // All configurable views on a contact item
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        internal val letterText: TextView =
            itemView.findViewById(R.id.letter)
        internal val contactBadge: QuickContactBadge =
            itemView.findViewById(R.id.photo)
        internal val contactText: TextView =
            itemView.findViewById(R.id.contact)
        internal val phoneNumberText: TextView =
            itemView.findViewById(R.id.phone_number)
        internal val phoneNumberTypeText: TextView =
            itemView.findViewById(R.id.phone_number_type)

        init {
            // Allow the contact view itself to be clickable
            itemView.isClickable = true
            itemView.setOnClickListener(activity)

            // Apply circular mask to and remove overlay from contact badge
            // to match Android Messages aesthetic
            applyCircularMask(contactBadge)
            contactBadge.setOverlay(null)
        }
    }

    /**
     * Represents a contact item.
     */
    abstract class BaseContactItem(val primaryPhoneNumber: String)

    /**
     * Represents the contact item for a typed in phone number.
     */
    class TypedInContactItem(phoneNumber: String) :
        BaseContactItem(phoneNumber)

    /**
     * Represents a phone number and its type.
     */
    class PhoneNumberAndType(val phoneNumber: String, val type: String)

    /**
     * Represents the contact item for a standard contact.
     *
     * @param id The ID of the contact from the Android contacts provider.
     * @param name The name of the contact.
     * @param phoneNumbersAndTypes The contact's phone numbers and
     *                             corresponding types.
     * @param bitmap The photo of the contact.
     */
    class ContactItem(
        val id: Long,
        val name: String,
        val phoneNumbersAndTypes: MutableList<PhoneNumberAndType>,
        val bitmap: Bitmap
    ) :
        BaseContactItem(phoneNumbersAndTypes[0].phoneNumber) {
        /**
         * Returns true if the name and phone number are different.
         */
        fun showSeparateNameAndPhoneNumber(): Boolean {
            return name != primaryPhoneNumber
                && name != getFormattedPhoneNumber(primaryPhoneNumber)
        }

        /**
         * Gets the name to be used for sorting.
         */
        fun getSortingName(): String {
            return if (showSeparateNameAndPhoneNumber()) {
                name
            } else {
                getDigitsOfString(primaryPhoneNumber)
            }
        }
    }
}