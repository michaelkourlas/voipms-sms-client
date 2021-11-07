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

package net.kourlas.voipms_sms.demo

import android.content.Context
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.newConversation.NewConversationRecyclerViewAdapter
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber
import net.kourlas.voipms_sms.utils.getGenericContactPhotoBitmap
import java.util.*

fun getConversationsDemoMessages(): List<Message> {
    val messages = mutableListOf<Message>()
    messages.add(
        Message(
            1, 1, Date().time / 1000, 0,
            "6135559483",
            "6135556019",
            "Have you considered asking him whether they...",
            0, 1, 0, true
        )
    )
    messages.add(
        Message(
            1, 1, Date().time / 1000, 1,
            "6135559483",
            "5145553495",
            "Sounds great!",
            1, 1, 0
        )
    )
    messages.add(
        Message(
            1, 1, Date().time / 1000 - 138900, 0,
            "6135559483",
            "4385557321",
            "Maybe later.",
            0, 1, 0
        )
    )
    messages.add(
        Message(
            1, 1, Date().time / 1000 - 1002900, 1,
            "6135550942",
            "4165550919",
            "Thank you for your help! It's much appreciated.",
            0, 1, 0
        )
    )
    messages.add(
        Message(
            1, 1, Date().time / 1000 - 39623700, 1,
            "6135559483",
            "55555",
            "Your verification code is: 123456.",
            0, 1, 0
        )
    )
    return messages
}

fun getConversationDemoMessages(bubble: Boolean): List<Message> {
    if (bubble) {
        return listOf(getDemoNotification())
    }

    val messages = mutableListOf<Message>()
    messages.add(
        Message(
            1, 1, Date().time / 1000 - 120, 1,
            "6135559483",
            "5145553495",
            "Want to grab some lunch?",
            1, 1, 0
        )
    )
    messages.add(
        Message(
            1, 1, Date().time / 1000 - 70, 0,
            "6135559483",
            "5145553495",
            "Sure! How about that new place down the street?",
            1, 1, 0
        )
    )
    messages.add(
        Message(
            1, 1, Date().time / 1000 - 60, 0,
            "6135559483",
            "5145553495",
            "Meet you there in a minute.",
            1, 1, 0
        )
    )
    messages.add(
        Message(
            1, 1, Date().time / 1000 - 30, 1,
            "6135559483",
            "5145553495",
            "Sounds great!",
            1, 1, 0
        )
    )
    @Suppress("ConstantConditionIf")
    if (BuildConfig.IS_DEMO_SENDING) {
        messages.add(
            Message(
                1, 1, Date().time / 1000, 0,
                "6135559483",
                "5145553495",
                "Where are you?",
                1, 0, 1
            )
        )
    }
    return messages
}

fun getDemoNotification(): Message = Message(
    1, 1, Date().time / 1000, 1,
    "6135559483",
    "6135551242",
    "Hey, how are you? Haven't seen you in a while.",
    1, 1, 0
)

fun getNewConversationContacts(
    context: Context
): List<NewConversationRecyclerViewAdapter.ContactItem> {
    val contactItems =
        mutableListOf<NewConversationRecyclerViewAdapter.ContactItem>()
    contactItems.add(
        NewConversationRecyclerViewAdapter.ContactItem(
            1,
            "Craig Johnson",
            mutableListOf(
                NewConversationRecyclerViewAdapter.PhoneNumberAndType(
                    "4165550919", "Mobile"
                )
            ),
            getGenericContactPhotoBitmap(
                context,
                "Craig Johnson",
                "4165550919",
                context.resources.getDimensionPixelSize(
                    R.dimen.contact_badge
                )
            )
        )
    )
    contactItems.add(
        NewConversationRecyclerViewAdapter.ContactItem(
            3,
            "Jennifer Morris",
            mutableListOf(
                NewConversationRecyclerViewAdapter.PhoneNumberAndType(
                    "6135556019", "Work"
                )
            ),
            getGenericContactPhotoBitmap(
                context,
                "Jennifer Morris",
                "6135556019",
                context.resources.getDimensionPixelSize(
                    R.dimen.contact_badge
                )
            )
        )
    )
    contactItems.add(
        NewConversationRecyclerViewAdapter.ContactItem(
            4,
            "Martin Wheeler",
            mutableListOf(
                NewConversationRecyclerViewAdapter.PhoneNumberAndType(
                    "6135551242", "Home"
                )
            ),
            getGenericContactPhotoBitmap(
                context,
                "Martin Wheeler",
                "6135551242",
                context.resources.getDimensionPixelSize(
                    R.dimen.contact_badge
                )
            )
        )
    )
    contactItems.add(
        NewConversationRecyclerViewAdapter.ContactItem(
            2,
            "Monica Alexander",
            mutableListOf(
                NewConversationRecyclerViewAdapter.PhoneNumberAndType(
                    "5145553495", "Home"
                ),
                NewConversationRecyclerViewAdapter.PhoneNumberAndType(
                    "5145553496", "Work"
                )
            ),
            getGenericContactPhotoBitmap(
                context,
                "Monica Alexander",
                "5145553495",
                context.resources.getDimensionPixelSize(
                    R.dimen.contact_badge
                )
            )
        )
    )
    return contactItems
}

fun getContactName(phoneNumber: String): String = when (phoneNumber) {
    "4165550919" -> "Craig Johnson"
    "5145553495" -> "Monica Alexander"
    "6135556019" -> "Jennifer Morris"
    "6135551242" -> "Martin Wheeler"
    else -> getFormattedPhoneNumber(phoneNumber)
}
