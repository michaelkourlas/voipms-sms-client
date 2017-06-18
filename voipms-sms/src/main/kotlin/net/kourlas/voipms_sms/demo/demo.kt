/*
 * VoIP.ms SMS
 * Copyright (C) 2017 Michael Kourlas
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

import net.kourlas.voipms_sms.newconversation.NewConversationRecyclerViewAdapter
import net.kourlas.voipms_sms.sms.Message
import java.util.*

val demo = false
val sending = false

fun getConversationsDemoMessages(): List<Message> {
    val messages = mutableListOf<Message>()
    messages.add(Message(1, 1, Date().time / 1000, 0,
                         "6135559483",
                         "6135556019",
                         "Have you considered asking him whether they...",
                         0, 1, 0, true))
    messages.add(Message(1, 1, 1436976420 + 60093664, 1,
                         "6135559483",
                         "5145553495",
                         "Sounds great!",
                         1, 1, 0))
    messages.add(Message(1, 1, Date().time / 1000 - 138900, 0,
                         "6135559483",
                         "4385557321",
                         "Maybe later.",
                         0, 1, 0))
    messages.add(Message(1, 1, Date().time / 1000 - 1002900, 1,
                         "6135550942",
                         "4165550919",
                         "Thank you for your help! It's much appreciated.",
                         0, 1, 0))
    messages.add(Message(1, 1, Date().time / 1000 - 39623700, 1,
                         "6135559483",
                         "55555",
                         "Your verification code is: 123456.",
                         0, 1, 0))
    return messages
}

fun getConversationDemoMessages(): List<Message> {
    val messages = mutableListOf<Message>()
    messages.add(Message(1, 1, Date().time / 1000 - 120, 1,
                         "6135559483",
                         "5145553495",
                         "Want to grab some lunch?",
                         1, 1, 0))
    messages.add(Message(1, 1, Date().time / 1000 - 70, 0,
                         "6135559483",
                         "5145553495",
                         "Sure! How about that new place down the street?",
                         1, 1, 0))
    messages.add(Message(1, 1, Date().time / 1000 - 60, 0,
                         "6135559483",
                         "5145553495",
                         "Meet you there in a minute.",
                         1, 1, 0))
    messages.add(Message(1, 1, Date().time / 1000 - 30, 1,
                         "6135559483",
                         "5145553495",
                         "Sounds great!",
                         1, 1, 0))
    if (sending) {
        messages.add(Message(1, 1, Date().time / 1000, 0,
                             "6135559483",
                             "5145553495",
                             "Where are you?",
                             1, 0, 1))
    }
    return messages
}

fun getDemoNotification(): Message {
    return Message(1, 1, Date().time / 1000, 1,
                   "6135559483",
                   "6135551242",
                   "Where are you?",
                   1, 1, 0)
}

fun getNewConversationContacts(): List<NewConversationRecyclerViewAdapter
.Companion.ContactItem> {
    val contactItems = mutableListOf<NewConversationRecyclerViewAdapter
    .Companion.ContactItem>()
    contactItems.add(NewConversationRecyclerViewAdapter.Companion.ContactItem(
        1, "Craig Johnson", "4165550919",
        mutableListOf<String>(),
        "Mobile", null))
    contactItems.add(NewConversationRecyclerViewAdapter.Companion.ContactItem(
        3, "Jennifer Morris", "6135556019",
        mutableListOf<String>(),
        "Work", null))
    contactItems.add(NewConversationRecyclerViewAdapter.Companion.ContactItem(
        4, "Martin Wheeler", "6135551242",
        mutableListOf<String>(),
        "Home", null))
    contactItems.add(NewConversationRecyclerViewAdapter.Companion.ContactItem(
        2, "Monica Alexander", "5145553495",
        mutableListOf("5145553496"),
        "Multiple", null))
    return contactItems
}

fun getContactName(phoneNumber: String): String {
    return when (phoneNumber) {
        "4165550919" -> "Craig Johnson"
        "5145553495" -> "Monica Alexander"
        "6135556019" -> "Jennifer Morris"
        "6135551242" -> "Martin Wheeler"
        else -> phoneNumber
    }
}