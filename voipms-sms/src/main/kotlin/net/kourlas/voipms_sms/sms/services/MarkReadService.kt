/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2018 Michael Kourlas
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

package net.kourlas.voipms_sms.sms.services

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.crashlytics.android.Crashlytics
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.isAccountActive
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database

/**
 * Service used to mark the conversation defined by the specified contact
 * using the specified DID as read.
 *
 * This service is an IntentService rather than a JobIntentService because
 * although it is run in the background, the execution time is very short.
 */
class MarkReadService : IntentService(
    MarkReadService::class.java.name) {
    override fun onHandleIntent(intent: Intent?) {
        try {
            // Terminate quietly if intent does not exist or does not contain
            // the mark as read action
            if (intent == null || intent.action != applicationContext.getString(
                    R.string.mark_read_action)) {
                return
            }

            // Terminate quietly if account inactive
            if (!isAccountActive(applicationContext)) {
                return
            }

            // Retrieve the DID and contact from the intent
            val conversationId = getIntentData(intent)

            // Mark the conversation as read
            Database.getInstance(
                applicationContext).markConversationRead(
                conversationId)

            // Cancel existing notification
            Notifications.getInstance(application).cancelNotification(
                conversationId)
        } catch (e: Exception) {
            Crashlytics.logException(e)
        }
    }

    /**
     * Extracts the DID and contact from the specified intent.
     *
     * @param intent The specified intent.
     * @return The data from the intent.
     */
    private fun getIntentData(intent: Intent): ConversationId {
        // Extract the DID and contact from the intent
        val did = intent.getStringExtra(
            applicationContext.getString(R.string.mark_read_did))
                  ?: throw Exception("DID missing")
        val contact = intent.getStringExtra(
            applicationContext.getString(R.string.mark_read_contact))
                      ?: throw Exception("Contact phone number missing")
        return ConversationId(did, contact)
    }

    companion object {
        /**
         * Gets an intent which can be used to launch this service.
         *
         * @param did The DID associated with the conversation.
         * @param contact The contact associated with the conversation.
         */
        fun getIntent(context: Context, did: String, contact: String): Intent {
            val intent = Intent(context, MarkReadService::class.java)
            intent.action = context.getString(R.string.mark_read_action)
            intent.putExtra(context.getString(R.string.mark_read_did), did)
            intent.putExtra(context.getString(R.string.mark_read_contact),
                            contact)
            return intent
        }
    }
}
