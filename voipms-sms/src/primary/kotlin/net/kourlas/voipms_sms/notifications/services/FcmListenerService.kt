/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2020 Michael Kourlas
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

package net.kourlas.voipms_sms.notifications.services

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.sms.workers.SyncWorker

/**
 * Service that processes FCM messages by showing notifications for new SMS
 * messages.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class FcmListenerService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Called when a FCM message is received; check to see if topic matches
        // a currently configured DID
        val dids = getDids(applicationContext, onlyShowNotifications = true)
        val match = dids.any { message.from == "/topics/did-$it" }
        if (match) {
            // If so, and if notifications are enabled, update the message
            // database and shows notifications for any new messages
            if (Notifications.getInstance(
                    application
                ).getNotificationsEnabled()
            ) {
                SyncWorker.performPartialSynchronization(applicationContext)
            }
        } else {
            // Otherwise, unsubscribe from this topic
            message.from?.let {
                if (it.startsWith("/topics/")) {
                    val topic = it.removePrefix("/topics/")
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                }
            }
        }
    }
}
