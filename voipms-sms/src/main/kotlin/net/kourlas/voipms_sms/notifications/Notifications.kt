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

package net.kourlas.voipms_sms.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput
import android.support.v4.app.TaskStackBuilder
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.demo.demo
import net.kourlas.voipms_sms.preferences.getNotificationSound
import net.kourlas.voipms_sms.preferences.getNotificationVibrateEnabled
import net.kourlas.voipms_sms.preferences.getNotificationsEnabled
import net.kourlas.voipms_sms.preferences.getNotificationUntilDismissedEnabled
import net.kourlas.voipms_sms.sms.*
import net.kourlas.voipms_sms.utils.applyCircularMask
import net.kourlas.voipms_sms.utils.getContactName
import net.kourlas.voipms_sms.utils.getContactPhotoBitmap
import net.kourlas.voipms_sms.utils.getFormattedPhoneNumber

/**
 * Single-instance class used to send notifications when new SMS messages
 * are received.
 *
 * @param application This application.
 */
class Notifications private constructor(
    private val application: CustomApplication) {

    // Helper variables
    private val context = application.applicationContext

    // Notification ID for the group notification, which contains all other
    // notifications
    private val GROUP_NOTIFICATION_ID = 0

    // Information associated with active notifications
    private val notificationIds = mutableMapOf<ConversationId, Int>()
    private var notificationIdCount = 1

    /**
     * Show notifications for new messages for the specified conversations.
     *
     * @param conversationIds A list of conversation IDs.
     */
    fun showNotifications(conversationIds: Set<ConversationId>) {
        // Only show notifications if notifications are enabled
        if (!getNotificationsEnabled(context)) {
            return
        }

        // Do not show notifications when the conversations view is open
        if (application.conversationsActivityVisible()) {
            return
        }

        conversationIds
            .filterNot {
                // Do not show notifications for a contact when that contact's
                // conversation view is open
                application.conversationActivityVisible(it)
            }
            .forEach {
                showNotification(
                    Database.getInstance(context).getMessagesUnread(it))
            }
    }

    fun showDemoNotification(message: Message) {
        showNotification(listOf(message))
    }

    /**
     * Shows a notification with the specified messages.
     *
     * @param messages The specified messages.
     */
    private fun showNotification(messages: List<Message>) {
        // Do not show notification if there are no messages
        if (messages.isEmpty()) {
            return
        }

        // Notification metadata
        val conversationId = messages[0].conversationId
        val did = conversationId.did
        val contact = conversationId.contact
        var contactName = if (!demo) {
            getContactName(context, contact)
        } else {
            net.kourlas.voipms_sms.demo.getContactName(contact)
        }
        if (contactName == null) {
            contactName = getFormattedPhoneNumber(contact)
        }

        // General notification properties
        val notification = android.support.v7.app.NotificationCompat.Builder(
            context)
        notification.setSmallIcon(R.drawable.ic_chat_white_24dp)
        notification.setLargeIcon(getLargeIconBitmap(contact))
        notification.priority = Notification.PRIORITY_HIGH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.setCategory(Notification.CATEGORY_MESSAGE)
        }
        notification.addPerson("tel:" + contact)
        notification.setLights(0xFFAA0000.toInt(), 1000, 5000)
        if(getNotificationUntilDismissedEnabled(context)) {
            notification.setOngoing(true);
        }
        val notificationSound = getNotificationSound(context)
        if (notificationSound != "") {
            notification.setSound(Uri.parse(getNotificationSound(context)))
        }
        if (getNotificationVibrateEnabled(context)) {
            notification.setVibrate(longArrayOf(0, 250, 250, 250))
        }
        notification.color = 0xFFAA0000.toInt()
        notification.setAutoCancel(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notification.setGroup(context.getString(
                R.string.notifications_group_key))
        }

        // Notification text
        val style = NotificationCompat.MessagingStyle(
            context.getString(R.string.notifications_current_user))
        for (message in messages) {
            style.addMessage(message.text, message.date.time,
                             if (message.isIncoming) contactName else null)
        }
        notification.setStyle(style)

        // Reply button
        val replyPendingIntent: PendingIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Send reply string directly to SendMessageService
            val replyIntent = SendMessageService.getIntent(context, did,
                                                           contact)
            replyPendingIntent = PendingIntent.getService(
                context, (did + contact).hashCode(),
                replyIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        } else {
            // Inline reply is not supported, so just show the conversation
            // activity
            val replyIntent = Intent(context, ConversationActivity::class.java)
            replyIntent.putExtra(context.getString(
                R.string.conversation_did), did)
            replyIntent.putExtra(context.getString(
                R.string.conversation_contact), contact)
            replyIntent.putExtra(context.getString(
                R.string.conversation_extra_focus), true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                replyIntent.flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            } else {
                @Suppress("DEPRECATION")
                replyIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
            }
            replyPendingIntent = PendingIntent.getActivity(
                context, (did + contact + "reply").hashCode(),
                replyIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        }
        val remoteInput = RemoteInput.Builder(context.getString(
            R.string.notifications_reply_key))
            .setLabel(context.getString(R.string.notifications_button_reply))
            .build()
        val replyActionBuilder = NotificationCompat.Action.Builder(
            R.drawable.ic_reply_white_24dp,
            context.getString(R.string.notifications_button_reply),
            replyPendingIntent)
            .setAllowGeneratedReplies(true)
            .addRemoteInput(remoteInput)
        notification.addAction(replyActionBuilder.build())

        // Mark as read button
        val markReadIntent = MarkReadService.getIntent(context, did, contact)
        val markReadPendingIntent = PendingIntent.getService(
            context, (did + contact).hashCode(),
            markReadIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_drafts_white_24dp,
            context.getString(R.string.notifications_button_mark_read),
            markReadPendingIntent)
            .build()
        notification.addAction(markReadAction)

        // Primary notification action
        val intent = Intent(context, ConversationActivity::class.java)
        intent.putExtra(context.getString(
            R.string.conversation_did), did)
        intent.putExtra(context.getString(
            R.string.conversation_contact), contact)
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addNextIntentWithParentStack(intent)
        notification.setContentIntent(stackBuilder.getPendingIntent(
            (did + contact).hashCode(), PendingIntent.FLAG_CANCEL_CURRENT))

        // Notification ID
        val id: Int
        if (notificationIds[conversationId] != null) {
            id = notificationIds[conversationId]!!
        } else {
            id = notificationIdCount++
            notificationIds.put(conversationId, id)
        }

        // Group notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val groupNotification = android.support.v7.app.NotificationCompat
                .Builder(context)
            groupNotification.setSmallIcon(R.drawable.ic_chat_white_24dp)
            groupNotification.setGroup(context.getString(
                R.string.notifications_group_key))
            groupNotification.setGroupSummary(true)
            NotificationManagerCompat.from(context).notify(
                GROUP_NOTIFICATION_ID, groupNotification.build())
        }

        NotificationManagerCompat.from(context).notify(id, notification.build())
    }

    /**
     * Cancels the notification associated with the specified conversation ID.
     *
     * @param conversationId The specified conversation ID.
     */
    fun cancelNotification(conversationId: ConversationId) {
        val id = notificationIds[conversationId] ?: return
        NotificationManagerCompat.from(context).cancel(id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val notificationManager = context.getSystemService(
                NotificationManager::class.java)
            val activeNotifications = notificationManager.activeNotifications
            if (activeNotifications.size == 1
                && activeNotifications[0].id == GROUP_NOTIFICATION_ID) {
                NotificationManagerCompat.from(context).cancel(
                    GROUP_NOTIFICATION_ID)
            }
        }
    }

    /**
     * Get the large icon bitmap for the specified contact.
     *
     * @param contact The specified contact.
     * @return The large icon bitmap for the specified contact.
     */
    private fun getLargeIconBitmap(contact: String): Bitmap? {
        try {
            var largeIconBitmap = getContactPhotoBitmap(context, contact)
            largeIconBitmap = Bitmap.createScaledBitmap(largeIconBitmap,
                                                        256, 256, false)
            largeIconBitmap = applyCircularMask(largeIconBitmap)
            return largeIconBitmap
        } catch (_: Exception) {
            return null
        }
    }

    companion object {
        private var instance: Notifications? = null

        /**
         * Gets the sole instance of the Notifications class. Initializes the
         * instance if it does not already exist.
         *
         * @param application The custom application used to initialize the
         * object instance.
         * @return The sole instance of the Notifications class.
         */
        fun getInstance(application: Application): Notifications {
            if (instance == null) {
                instance = Notifications(application as CustomApplication)
            }
            return instance!!
        }
    }
}
