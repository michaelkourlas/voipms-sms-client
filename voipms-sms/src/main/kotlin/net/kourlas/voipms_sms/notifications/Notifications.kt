/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2020 Michael Kourlas
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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.*
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.conversations.ConversationsActivity
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.sms.receivers.MarkReadReceiver
import net.kourlas.voipms_sms.sms.receivers.SendMessageReceiver
import net.kourlas.voipms_sms.sms.receivers.SyncCancelReceiver
import net.kourlas.voipms_sms.sms.services.MarkReadService
import net.kourlas.voipms_sms.sms.services.SendMessageService
import net.kourlas.voipms_sms.sms.services.SyncService
import net.kourlas.voipms_sms.utils.*

/**
 * Single-instance class used to send notifications when new SMS messages
 * are received.
 */
class Notifications private constructor(
    private val application: CustomApplication) {
    // Helper variables
    private val context = application.applicationContext
    private val notificationManager = context.getSystemService(
        Context.NOTIFICATION_SERVICE) as NotificationManager

    // Information associated with active notifications
    private val notificationIds = mutableMapOf<ConversationId, Int>()
    private var notificationIdCount = MESSAGE_START_NOTIFICATION_ID

    /**
     * Attempts to create a notification channel for the specified DID.
     * Does nothing if this channel does not already exist.
     */
    fun createDidNotificationChannel(did: String, contact: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the default notification channel if it doesn't already
            // exist
            createDefaultNotificationChannel()
            val defaultChannel = notificationManager.getNotificationChannel(
                context.getString(R.string.notifications_channel_default))

            val channelGroup = NotificationChannelGroup(
                context.getString(R.string.notifications_channel_group_did,
                                  did),
                getFormattedPhoneNumber(did))
            notificationManager.createNotificationChannelGroup(channelGroup)

            val contactName = getContactName(context, contact)
            val channel = NotificationChannel(
                context.getString(R.string.notifications_channel_contact,
                                  did, contact),
                contactName ?: getFormattedPhoneNumber(contact),
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
            notificationManager.createNotificationChannelGroup(channelGroup)
        }
    }

    /**
     * Rename notification channels for changed contact numbers.
     */
    fun renameNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Rename all channels
            for (channel in notificationManager.notificationChannels) {
                if (channel.id.startsWith(context.getString(
                        R.string.notifications_channel_contact_prefix))) {
                    val contact = channel.id.split("_")[4]
                    val contactName = getContactName(context, contact)
                    channel.name = contactName ?: getFormattedPhoneNumber(
                        contact)
                    notificationManager.createNotificationChannel(channel)
                }

            }
        }
    }

    /**
     * Delete notification channels for conversations that are no longer active.
     */
    fun deleteNotificationChannelsAndGroups() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Remove any channel for which there is no conversation with
            // notifications enabled in the database
            val conversationIds = Database.getInstance(context)
                .getConversationIds(
                    getDids(context, onlyShowNotifications = true))
            for (channel in notificationManager.notificationChannels) {
                if (channel.id.startsWith(context.getString(
                        R.string.notifications_channel_contact_prefix))) {
                    val splitId = channel.id.split("_")
                    val conversationId = ConversationId(splitId[3], splitId[4])
                    if (conversationId !in conversationIds) {
                        notificationManager.deleteNotificationChannel(
                            channel.id)
                    }
                }
            }

            // Remove any channel for which there is no conversation with
            // notifications enabled in the database
            val dids = conversationIds.map { it.did }.toSet()
            for (group in notificationManager.notificationChannelGroups) {
                if (group.id.startsWith(context.getString(
                        R.string.notifications_channel_group_did, ""))) {
                    val did = group.id.split("_")[3]
                    if (did !in dids) {
                        notificationManager.deleteNotificationChannelGroup(
                            group.id)
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
        builder.setSmallIcon(R.drawable.ic_message_sync_toolbar_24dp)
        builder.setContentTitle(context.getString(
            R.string.notifications_sync_message))
        builder.setContentText("$progress%")
        builder.setProgress(100, progress, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.priority = Notification.PRIORITY_LOW
        }

        // Cancel action
        val cancelIntent = SyncService.getCancelIntent(context)
        cancelIntent.component = ComponentName(
            context, SyncCancelReceiver::class.java)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, SyncCancelReceiver::class.java.toString().hashCode(),
            cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val cancelAction = NotificationCompat.Action.Builder(
            R.drawable.ic_delete_toolbar_24dp,
            context.getString(R.string.notifications_button_cancel),
            cancelPendingIntent)
            .setShowsUserInterface(false)
            .build()
        builder.addAction(cancelAction)

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
        } else {
            removePreference(context, context.getString(
                R.string.preferences_notifications_enable_key))
        }

        // However, we do check to see if notifications are enabled for a
        // particular DID, since the Android O interface doesn't really work
        // with this use case
        if (conversationId != null) {
            if (!getDidShowNotifications(context, conversationId.did)) {
                return false
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

        runOnNewThread {
            for (conversationId in conversationIds) {
                if (application.conversationActivityVisible(conversationId)
                    || !getNotificationsEnabled(conversationId)) {
                    continue
                }

                showNotification(
                    Database.getInstance(context).getMessagesUnread(
                        conversationId))
            }
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
        var contactName = if (!BuildConfig.IS_DEMO) {
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
            context.getString(R.string.notifications_channel_default)
        } else {
            val channel = notificationManager.getNotificationChannel(
                context.getString(R.string.notifications_channel_contact,
                                  did, contact))
            if (channel == null) {
                context.getString(R.string.notifications_channel_default)
            } else {
                context.getString(R.string.notifications_channel_contact,
                                  did, contact)
            }
        }

        // General notification properties
        val notification = NotificationCompat.Builder(context, channel)
        notification.setSmallIcon(R.drawable.ic_chat_toolbar_24dp)
        notification.setLargeIcon(getLargeIconBitmap(contact))
        notification.setAutoCancel(true)
        notification.addPerson("tel:$contact")
        notification.setCategory(Notification.CATEGORY_MESSAGE)
        notification.color = 0xFFAA0000.toInt()
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
        } else {
            removePreference(context, context.getString(
                R.string.preferences_notifications_sound_key))
            removePreference(context, context.getString(
                R.string.preferences_notifications_vibrate_key))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notification.setGroup(context.getString(
                R.string.notifications_group_key))
        }
        notification.setGroupAlertBehavior(
            NotificationCompat.GROUP_ALERT_CHILDREN)

        // Notification text
        val person = Person.Builder().setName(
            context.getString(R.string.notifications_current_user)).build()
        val style = NotificationCompat.MessagingStyle(person)
        for (message in messages) {
            style.addMessage(
                message.text,
                message.date.time,
                if (message.isIncoming)
                    Person.Builder().setName(contactName).build()
                else null)
        }
        notification.setStyle(style)

        // Reply button
        val replyIntent = SendMessageService.getIntent(
            context, did, contact)
        replyIntent.component = ComponentName(
            context, SendMessageReceiver::class.java)
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, (did + contact + "reply").hashCode(),
            replyIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val remoteInput = RemoteInput.Builder(context.getString(
            R.string.notifications_reply_key))
            .setLabel(context.getString(R.string.notifications_button_reply))
            .build()
        val replyActionBuilder = NotificationCompat.Action.Builder(
            R.drawable.ic_reply_toolbar_24dp,
            context.getString(R.string.notifications_button_reply),
            replyPendingIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .addRemoteInput(remoteInput)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notification.addAction(replyActionBuilder.build())
        } else {
            notification.addInvisibleAction(replyActionBuilder.build())

            // Inline reply is not supported, so just show the conversation
            // activity
            val visibleReplyIntent = Intent(context,
                                            ConversationActivity::class.java)
            visibleReplyIntent.putExtra(context.getString(
                R.string.conversation_did), did)
            visibleReplyIntent.putExtra(context.getString(
                R.string.conversation_contact), contact)
            visibleReplyIntent.putExtra(context.getString(
                R.string.conversation_extra_focus), true)
            visibleReplyIntent.flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            val visibleReplyPendingIntent = PendingIntent.getActivity(
                context, (did + contact + "replyVisible").hashCode(),
                visibleReplyIntent, PendingIntent.FLAG_CANCEL_CURRENT)
            val visibleReplyActionBuilder = NotificationCompat.Action.Builder(
                R.drawable.ic_reply_toolbar_24dp,
                context.getString(R.string.notifications_button_reply),
                visibleReplyPendingIntent)
                .setSemanticAction(
                    NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(true)
                .addRemoteInput(remoteInput)
            notification.addAction(visibleReplyActionBuilder.build())
        }

        // Mark as read button
        val markReadIntent = MarkReadService.getIntent(context, did, contact)
        markReadIntent.component = ComponentName(
            context, MarkReadReceiver::class.java)
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, (did + contact + "markRead").hashCode(),
            markReadIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_drafts_toolbar_24dp,
            context.getString(R.string.notifications_button_mark_read),
            markReadPendingIntent)
            .setSemanticAction(
                NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        notification.addAction(markReadAction)

        // Group notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val groupNotification = NotificationCompat.Builder(context, channel)
            groupNotification.setSmallIcon(R.drawable.ic_chat_toolbar_24dp)
            groupNotification.setGroup(context.getString(
                R.string.notifications_group_key))
            groupNotification.setGroupSummary(true)
            groupNotification.setAutoCancel(true)
            groupNotification.setGroupAlertBehavior(
                NotificationCompat.GROUP_ALERT_CHILDREN)

            val intent = Intent(context, ConversationsActivity::class.java)
            val stackBuilder = TaskStackBuilder.create(context)
            stackBuilder.addNextIntentWithParentStack(intent)
            groupNotification.setContentIntent(stackBuilder.getPendingIntent(
                "group".hashCode(),
                PendingIntent.FLAG_CANCEL_CURRENT))

            try {
                NotificationManagerCompat.from(context).notify(
                    GROUP_NOTIFICATION_ID, groupNotification.build())
            } catch (e: SecurityException) {
                Toast.makeText(
                    context,
                    context.getString(R.string.notifications_security_error),
                    Toast.LENGTH_LONG).show()
            }
        }

        // Primary notification action
        val intent = Intent(context, ConversationActivity::class.java)
        intent.putExtra(context.getString(
            R.string.conversation_did), did)
        intent.putExtra(context.getString(
            R.string.conversation_contact), contact)
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addNextIntentWithParentStack(intent)
        notification.setContentIntent(stackBuilder.getPendingIntent(
            (did + contact).hashCode(),
            PendingIntent.FLAG_CANCEL_CURRENT))

        // Notification ID
        var id = notificationIds[conversationId]
        if (id == null) {
            id = notificationIdCount++
            if (notificationIdCount == Int.MAX_VALUE) {
                notificationIdCount = MESSAGE_START_NOTIFICATION_ID
            }
            notificationIds[conversationId] = id
        }

        try {
            NotificationManagerCompat.from(context).notify(
                id, notification.build())
        } catch (e: SecurityException) {
            logException(e)
        }
    }

    /**
     * Cancels the notification associated with the specified conversation ID.
     */
    fun cancelNotification(conversationId: ConversationId) {
        val id = notificationIds[conversationId] ?: return
        NotificationManagerCompat.from(context).cancel(id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val noOtherNotifications = notificationManager.activeNotifications
                .filter {
                    id != GROUP_NOTIFICATION_ID
                    && id != SYNC_NOTIFICATION_ID
                }
                .none()
            if (noOtherNotifications) {
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
        if (largeIconBitmap != null) {
            largeIconBitmap = Bitmap.createScaledBitmap(largeIconBitmap,
                                                        256, 256, false)
            largeIconBitmap = applyCircularMask(largeIconBitmap)
            largeIconBitmap
        } else {
            null
        }
    } catch (_: Exception) {
        null
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

        // Starting notification ID for ordinary message notifications
        const val MESSAGE_START_NOTIFICATION_ID = GROUP_NOTIFICATION_ID + 1

        /**
         * Gets the sole instance of the Notifications class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(application: Application): Notifications =
            instance ?: synchronized(this) {
                instance ?: Notifications(
                    application as CustomApplication).also { instance = it }
            }
    }
}
