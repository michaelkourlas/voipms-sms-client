/*
 * VoIP.ms SMS
 * Copyright (C) 2020 Michael Kourlas
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
import com.google.firebase.appindexing.FirebaseAppIndex
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.sms.services.AppIndexingService

/**
 * Adds the specified message to the app index.
 */
fun addMessageToIndexOnNewThread(context: Context, message: Message) {
    val applicationContext = context.applicationContext
    FirebaseAppIndex.getInstance(applicationContext).update(
        AppIndexingService.getMessageBuilder(
            applicationContext, message).build())
}

/**
 * Remove the entry with the specified URI from the app index.
 */
fun removeFromIndex(context: Context, string: String) {
    FirebaseAppIndex.getInstance(context).remove(string)
}

/**
 * Remove the all entries from the app index.
 */
fun removeAllFromIndex(context: Context) {
    FirebaseAppIndex.getInstance(context).removeAll()
}

/**
 * Replace the app index with the conversations in the database.
 */
fun replaceIndexOnNewThread(context: Context) {
    val applicationContext = context.applicationContext
    runOnNewThread {
        AppIndexingService.replaceIndex(applicationContext)
    }
}
