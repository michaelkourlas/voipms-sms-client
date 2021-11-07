/*
 * VoIP.ms SMS
 * Copyright (C) 2021 Michael Kourlas
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

package net.kourlas.voipms_sms.utils

import android.content.Context
import net.kourlas.voipms_sms.R
import java.text.BreakIterator
import java.util.*

fun getMessageTexts(context: Context, message: String): List<String> {
    // If the message text exceeds the maximum length of an SMS message,
    // split it into multiple message texts
    val maxLength = context.resources.getInteger(
        R.integer.sms_max_length
    )
    val messageTexts = mutableListOf<String>()

    // VoIP.ms uses UTF-8 encoding for text messages; any message
    // exceeding N bytes when encoded using UTF-8 is too long
    val bytes = mutableListOf<Byte>()
    val boundary = BreakIterator.getCharacterInstance(Locale.getDefault())
    boundary.setText(message)
    var current = boundary.first()
    var next = boundary.next()
    while (next != BreakIterator.DONE) {
        val cluster = message.substring(current, next)
        val clusterBytes = cluster.toByteArray(Charsets.UTF_8)
        if (bytes.size + clusterBytes.size > maxLength) {
            messageTexts.add(String(bytes.toByteArray(), Charsets.UTF_8))
            bytes.clear()
        }
        bytes.addAll(clusterBytes.toList())
        current = next
        next = boundary.next()
    }
    messageTexts.add(String(bytes.toByteArray(), Charsets.UTF_8))
    return messageTexts
}