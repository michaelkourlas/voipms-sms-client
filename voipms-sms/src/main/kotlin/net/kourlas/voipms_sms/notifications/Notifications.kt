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

package net.kourlas.voipms_sms.notifications

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput
import android.support.v4.app.TaskStackBuilder
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.conversations.ConversationsActivity
import net.kourlas.voipms_sms.demo.demo
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.sms.*
import net.kourlas.voipms_sms.utils.*

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

    // Information associated with active notifications
    private val notificationIds = mutableMapOf<ConversationId, Int>()
    private var notificationIdCount = 3

    /**
     * Attempts to create a notification channel for the specified DID.
     * Does nothing if this channel does not already exist.
     */
    fun createDidNotificationChannel(contact: String, did: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the default notification channel if it doesn't already
            // exist
            createDefaultNotificationChannel()
            val notificationManager = context.getSystemService(
                NotificationManager::class.java)
            val defaultChannel = notificationManager.getNotificationChannel(
                context.getString(R.string.notifications_channel_default))

            val channelGroup = NotificationChannelGroup(
                context.getString(R.string.notifications_channel_group_did,
                                  did),
                getFormattedPhoneNumber(did))
            notificationManager.createNotificationChannelGroup(channelGroup)

            val contactName = getContactName(context, did)
            val channel = NotificationChannel(
                context.getString(R.string.notifications_channel_contact,
                                  contact, did),
                contactName ?: getFormattedPhoneNumber(did),
                defaultChannel.importance)
            channel.enableLights(defaultChannel.shouldShowLights())
            channel.lightColor = defaultChannel.lightColor
            channel.enableVibration(defaultChannel.shouldVibrate())
            channel.vibrationPattern = defaultChannel.vibrationPattern
            channel.lockscreenVisibility = defaultChannel.lockscreenVisibility
            channel.setBypassDnd(defaultChannel.canBypassDnd())
            channel.setSound(defaultChannel.sound,
                             defaultChannel.audioAttributes)
            channel.setShowBadge(defaultChannel.canShowBadge())
            channel.group = context.getString(
                R.string.notifications_channel_group_did,
                did)

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Attempts to create the default notification channel. This does nothing
     * if the channel already exists.
     */
    fun createDefaultNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createOtherNotificationChannelGroup()

            val channel = NotificationChannel(
                context.getString(R.string.notifications_channel_default),
                context.getString(R.string.notifications_channel_default_title),
                NotificationManager.IMPORTANCE_HIGH)
            channel.enableLights(true)
            channel.lightColor = Color.RED
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 250, 250, 250)
            channel.setShowBadge(true)
            channel.group = context.getString(
                R.string.notifications_channel_group_other)

            val notificationManager = context.getSystemService(
                NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Creates the notification channel for the notification displayed during
     * database synchronization.
     */
    private fun createSyncNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createOtherNotificationChannelGroup()

            val notificationManager = context.getSystemService(
                NotificationManager::class.java)
            val channel = NotificationChannel(
                context.getString(R.string.notifications_channel_sync),
                context.getString(R.string.notifications_channel_sync_title),
                NotificationManager.IMPORTANCE_LOW)
            channel.group = context.getString(
                R.string.notifications_channel_group_other)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Creates the notification channel group for non-conversation related
     * notifications.
     */
    private fun createOtherNotificationChannelGroup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelGroup = NotificationChannelGroup(
                context.getString(R.string.notifications_channel_group_other),
                context.getString(
                    R.string.notifications_channel_group_other_title))

            val notificationManager = context.getSystemService(
                NotificationManager::class.java)
            notificationManager.createNotificationChannelGroup(channelGroup)
        }
    }

    /**
     * Delete notification channels for DIDs that are no longer active.
     */
    fun deleteNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dids = getDids(context)
            val notificationManager = context.getSystemService(
                NotificationManager::class.java)
            for (notificationChannel in notificationManager.notificationChannels) {
                val index = notificationChannel.id.indexOf(
                    context.getString(R.string.notifications_channel_group_did,
                                      ""))
                if (index != -1) {
                    val did = notificationChannel.id.substring(index)
                    if (did !in dids) {

                    }
                }
            }
        }
    }

    /**
     * Gets the notification displayed during database synchronization.
     */
    fun getSyncNotification(progress: Int = 0): Notification {
        createSyncNotificationChannel()

        val builder = NotificationCompat.Builder(
            context, context.getString(R.string.notifications_channel_sync))
        builder.setCategory(NotificationCompat.CATEGORY_PROGRESS)
        builder.setSmallIcon(R.drawable.ic_message_sync_white_24dp)
        builder.setContentTitle(context.getString(
            R.string.notifications_sync_message))
        builder.setContentText("$progress%")
        builder.setProgress(100, progress, false)

        // Primary notification action
        val intent = Intent(context, ConversationsActivity::class.java)
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addNextIntentWithParentStack(intent)
        builder.setContentIntent(stackBuilder.getPendingIntent(
            ConversationsActivity::class.java.hashCode(),
            PendingIntent.FLAG_CANCEL_CURRENT))

        return builder.build()
    }

    /**
     * Returns whether notifications are enabled globally and for the
     * conversation ID if one is specified.
     */
    fun getNotificationsEnabled(
        conversationId: ConversationId? = null): Boolean {
        // Prior to Android O, check the global notification settings;
        // otherwise we can just rely on the system to block the notifications
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            if (!getNotificationsEnabled(context)) {
                return false
            }

            if (!NotificationManagerCompat.from(
                    context).areNotificationsEnabled()) {
                return false
            }
        }

        if (conversationId != null) {
            if (!getDidShowNotifications(context, conversationId.did)) {
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(
                    NotificationManager::class.java)
                val channel = notificationManager.getNotificationChannel(
                    context.getString(R.string.notifications_channel_contact,
                                      conversationId.contact,
                                      conversationId.did))
                if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * Show notifications for new messages for the specified conversations.
     */
    fun showNotifications(conversationIds: Set<ConversationId>) {
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
            .filter {
                getNotificationsEnabled(it)
            }
            .forEach {
                showNotification(
                    Database.getInstance(context).getMessagesUnread(it))
            }
    }

    /**
     * Shows a notification for the specified message, bypassing all normal
     * checks. Only used for demo purposes.
     */
    fun showDemoNotification(message: Message) = showNotification(
        listOf(message))

    /**
     * Shows a notification with the specified messages.
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
        @Suppress("ConstantConditionIf")
        var contactName = if (!demo) {
            getContactName(context, contact)
        } else {
            net.kourlas.voipms_sms.demo.getContactName(contact)
        }
        if (contactName == null) {
            contactName = getFormattedPhoneNumber(contact)
        }

        // Notification channel
        createDefaultNotificationChannel()
        val channel = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            context.getString(R.string.notifications_default_channel_name)
        } else {
            val notificationManager = context.getSystemService(
                NotificationManager::class.java)
            val channel = notificationManager.getNotificationChannel(
                context.getString(R.string.notifications_channel_contact,
                                  did, contact))
            if (channel == null) {
                context.getString(R.string.notifications_default_channel_name)
            } else {
                context.getString(R.string.notifications_channel_contact,
                                  did, contact)
            }
        }

        // General notification properties
        val notification = NotificationCompat.Builder(context, channel)
        notification.setSmallIcon(R.drawable.ic_chat_white_24dp)
        notification.setLargeIcon(getLargeIconBitmap(contact))
        notification.setAutoCancel(true)
        notification.addPerson("tel:$contact")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.setCategory(Notification.CATEGORY_MESSAGE)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            notification.priority = Notification.PRIORITY_HIGH
            notification.setLights(0xFFAA0000.toInt(), 1000, 5000)
            @Suppress("DEPRECATION")
            val notificationSound = getNotificationSound(context)
            if (notificationSound != "") {
                @Suppress("DEPRECATION")
                notification.setSound(Uri.parse(getNotificationSound(context)))
            }
            @Suppress("DEPRECATION")
            if (getNotificationVibrateEnabled(context)) {
                notification.setVibrate(longArrayOf(0, 250, 250, 250))
            }
            notification.color = 0xFFAA0000.toInt()
        }
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
            notificationIds[conversationId] = id
        }

        // Group notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val groupNotification = NotificationCompat.Builder(context, channel)
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
     */
    private fun getLargeIconBitmap(contact: String): Bitmap? = try {
        var largeIconBitmap = getContactPhotoBitmap(context, contact)
        largeIconBitmap = Bitmap.createScaledBitmap(largeIconBitmap,
                                                    256, 256, false)
        largeIconBitmap = applyCircularMask(largeIconBitmap)
        largeIconBitmap
    } catch (_: Exception) {
        null
    }

    /**
     * Enables push notifications by starting the push notifications
     * registration service.
     *
     * @param activity The activity on which to display messages.
     */
    fun enablePushNotifications(activity: Activity) {
        // Check if account is active and that notifications are enabled,
        // and silently quit if not
        if (!isAccountActive(activity) || !getNotificationsEnabled()) {
            setSetupCompletedForVersion(activity, 114)
            return
        }

        // Check if Google Play Services is available
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(
                    activity) != ConnectionResult.SUCCESS) {
            showSnackbar(activity, R.id.coordinator_layout,
                         application.getString(
                             R.string.push_notifications_fail_google_play))
            setSetupCompletedForVersion(activity, 114)
            return
        }

        // Subscribe to DID topics
        subscribeToDidTopics(activity)

        // Start push notifications registration service
        activity.startService(
            NotificationsRegistrationService.getIntent(activity))
    }

    companion object {
        // It is not a leak to store an instance to the Application object,
        // since it has the same lifetime as the application itself
        @SuppressLint("StaticFieldLeak")
        private var instance: Notifications? = null

        // Notification ID for the database synchronization notification
        const val SYNC_NOTIFICATION_ID = 1

        // Notification ID for the group notification, which contains all other
        // notifications
        const val GROUP_NOTIFICATION_ID = 2

        /**
         * Gets the sole instance of the Notifications class. Initializes the
         * instance if it does not already exist.
         *
         * @param application The custom application used to initialize the
         * object instance.
         */
        fun getInstance(application: Application): Notifications {
            if (instance == null) {
                instance = Notifications(application as CustomApplication)
            }
            return instance!!
        }
    }
}
