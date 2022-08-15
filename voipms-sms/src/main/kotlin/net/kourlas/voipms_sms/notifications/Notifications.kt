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

package net.kourlas.voipms_sms.notifications

import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.*
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.WorkManager
import net.kourlas.voipms_sms.BuildConfig
import net.kourlas.voipms_sms.CustomApplication
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.conversation.ConversationBubbleActivity
import net.kourlas.voipms_sms.conversations.ConversationsActivity
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.preferences.*
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.Message
import net.kourlas.voipms_sms.sms.receivers.MarkReadReceiver
import net.kourlas.voipms_sms.sms.receivers.SendMessageReceiver
import net.kourlas.voipms_sms.utils.*
import java.util.*

/**
 * Single-instance class used to send notifications when new SMS messages
 * are received.
 */
class Notifications private constructor(private val context: Context) {
    // Helper variables
    private val notificationManager = context.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    // Information associated with active notifications
    private val notificationIds = mutableMapOf<ConversationId, Int>()
    private val notificationMessages =
        mutableMapOf<ConversationId,
            List<NotificationCompat.MessagingStyle.Message>>()
    private var notificationIdCount = MESSAGE_START_NOTIFICATION_ID

    /**
     * Attempts to create a notification channel for the specified DID.
     */
    fun createDidNotificationChannel(did: String, contact: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the default notification channel if it doesn't already
            // exist
            createDefaultNotificationChannel()
            val defaultChannel = notificationManager.getNotificationChannel(
                context.getString(R.string.notifications_channel_default)
            )

            val channelGroup = NotificationChannelGroup(
                context.getString(
                    R.string.notifications_channel_group_did,
                    did
                ),
                getFormattedPhoneNumber(did)
            )
            notificationManager.createNotificationChannelGroup(channelGroup)

            val contactName = getContactName(context, contact)
            val channel = NotificationChannel(
                context.getString(
                    R.string.notifications_channel_contact,
                    did, contact
                ),
                contactName ?: getFormattedPhoneNumber(contact),
                defaultChannel.importance
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                channel.setConversationId(
                    context.getString(R.string.notifications_channel_default),
                    ConversationId(did, contact).getId()
                )
            }
            channel.enableLights(defaultChannel.shouldShowLights())
            channel.lightColor = defaultChannel.lightColor
            channel.enableVibration(defaultChannel.shouldVibrate())
            channel.vibrationPattern = defaultChannel.vibrationPattern
            channel.lockscreenVisibility = defaultChannel.lockscreenVisibility
            channel.setBypassDnd(defaultChannel.canBypassDnd())
            channel.setSound(
                defaultChannel.sound,
                defaultChannel.audioAttributes
            )
            channel.setShowBadge(defaultChannel.canShowBadge())
            channel.group = context.getString(
                R.string.notifications_channel_group_did,
                did
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                channel.setAllowBubbles(true)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Attempts to create the default notification channel.
     */
    fun createDefaultNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createOtherNotificationChannelGroup()

            val channel = NotificationChannel(
                context.getString(R.string.notifications_channel_default),
                context.getString(R.string.notifications_channel_default_title),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.enableLights(true)
            channel.lightColor = Color.RED
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 250, 250, 250)
            channel.setShowBadge(true)
            channel.group = context.getString(
                R.string.notifications_channel_group_other
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                channel.setAllowBubbles(true)
            }

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
                NotificationManager.IMPORTANCE_LOW
            )
            channel.group = context.getString(
                R.string.notifications_channel_group_other
            )
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
                    R.string.notifications_channel_group_other_title
                )
            )
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
                if (channel.id.startsWith(
                        context.getString(
                            R.string.notifications_channel_contact_prefix
                        )
                    )
                ) {
                    val contact = channel.id.split("_")[4]
                    val contactName = getContactName(context, contact)
                    channel.name = contactName ?: getFormattedPhoneNumber(
                        contact
                    )
                    notificationManager.createNotificationChannel(channel)
                }

            }
        }
    }

    /**
     * Delete notification channels for conversations that are no longer active.
     */
    suspend fun deleteNotificationChannelsAndGroups() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Remove any channel for which there is no conversation with
            // notifications enabled in the database
            val conversationIds = Database.getInstance(context)
                .getConversationIds(
                    getDids(context, onlyShowNotifications = true)
                )
            for (channel in notificationManager.notificationChannels) {
                if (channel.id.startsWith(
                        context.getString(
                            R.string.notifications_channel_contact_prefix
                        )
                    )
                ) {
                    val splitId = channel.id.split(":")[0].trim().split("_")
                    val conversationId = ConversationId(splitId[3], splitId[4])
                    if (conversationId !in conversationIds) {
                        notificationManager.deleteNotificationChannel(
                            channel.id
                        )
                        validateGroupNotification()
                    }
                }
            }

            // Remove any channel for which there is no conversation with
            // notifications enabled in the database
            val dids = conversationIds.map { it.did }.toSet()
            for (group in notificationManager.notificationChannelGroups) {
                if (group.id.startsWith(
                        context.getString(
                            R.string.notifications_channel_group_did, ""
                        )
                    )
                ) {
                    val did = group.id.split("_")[3]
                    if (did !in dids) {
                        notificationManager.deleteNotificationChannelGroup(
                            group.id
                        )
                        validateGroupNotification()
                    }
                }
            }
        }
    }

    /**
     * Gets the notification displayed during synchronization.
     */
    fun getSyncDatabaseNotification(id: UUID, progress: Int = 0): Notification {
        createSyncNotificationChannel()

        val builder = NotificationCompat.Builder(
            context, context.getString(R.string.notifications_channel_sync)
        )
        builder.setCategory(NotificationCompat.CATEGORY_PROGRESS)
        builder.setSmallIcon(R.drawable.ic_message_sync_toolbar_24dp)
        builder.setContentTitle(
            context.getString(
                R.string.notifications_sync_database_message
            )
        )
        builder.setContentText("$progress%")
        builder.setProgress(100, progress, false)
        builder.setOngoing(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.priority = Notification.PRIORITY_LOW
        }

        // Cancel action
        val cancelPendingIntent =
            WorkManager.getInstance(context).createCancelPendingIntent(id)
        val cancelAction = NotificationCompat.Action.Builder(
            R.drawable.ic_delete_toolbar_24dp,
            context.getString(R.string.notifications_button_cancel),
            cancelPendingIntent
        )
            .setShowsUserInterface(false)
            .build()
        builder.addAction(cancelAction)

        return builder.build()
    }

    /**
     * Gets the notification displayed during message sending.
     */
    fun getSyncMessageSendNotification(): Notification {
        createSyncNotificationChannel()

        val builder = NotificationCompat.Builder(
            context, context.getString(R.string.notifications_channel_sync)
        )
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
        builder.setSmallIcon(R.drawable.ic_message_sync_toolbar_24dp)
        builder.setContentTitle(
            context.getString(
                R.string.notifications_sync_send_message_message
            )
        )
        builder.setOngoing(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.priority = Notification.PRIORITY_LOW
        }

        return builder.build()
    }

    /**
     * Gets the notification displayed during verification of credentials.
     */
    fun getSyncVerifyCredentialsNotification(): Notification {
        createSyncNotificationChannel()

        val builder = NotificationCompat.Builder(
            context, context.getString(R.string.notifications_channel_sync)
        )
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
        builder.setSmallIcon(R.drawable.ic_message_sync_toolbar_24dp)
        builder.setContentTitle(
            context.getString(
                R.string.notifications_sync_verify_credentials_message
            )
        )
        builder.setOngoing(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.priority = Notification.PRIORITY_LOW
        }

        return builder.build()
    }

    /**
     * Gets the notification displayed during verification of credentials.
     */
    fun getSyncRetrieveDidsNotification(): Notification {
        createSyncNotificationChannel()

        val builder = NotificationCompat.Builder(
            context, context.getString(R.string.notifications_channel_sync)
        )
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
        builder.setSmallIcon(R.drawable.ic_message_sync_toolbar_24dp)
        builder.setContentTitle(
            context.getString(
                R.string.notifications_sync_retrieve_dids_message
            )
        )
        builder.setOngoing(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.priority = Notification.PRIORITY_LOW
        }

        return builder.build()
    }

    /**
     * Gets the notification displayed during verification of credentials.
     */
    fun getSyncRegisterPushNotificationsNotification(): Notification {
        createSyncNotificationChannel()

        val builder = NotificationCompat.Builder(
            context, context.getString(R.string.notifications_channel_sync)
        )
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
        builder.setSmallIcon(R.drawable.ic_message_sync_toolbar_24dp)
        builder.setContentTitle(
            context.getString(
                R.string.notifications_sync_register_push_message
            )
        )
        builder.setOngoing(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.priority = Notification.PRIORITY_LOW
        }

        return builder.build()
    }

    /**
     * Returns whether notifications are enabled globally and for the
     * conversation ID if one is specified.
     */
    fun getNotificationsEnabled(
        conversationId: ConversationId? = null
    ): Boolean {
        // Prior to Android O, check the global notification settings;
        // otherwise we can just rely on the system to block the notifications
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            if (!getNotificationsEnabled(context)) {
                return false
            }

            if (!NotificationManagerCompat.from(
                    context
                ).areNotificationsEnabled()
            ) {
                return false
            }
        } else {
            removePreference(
                context, context.getString(
                    R.string.preferences_notifications_enable_key
                )
            )
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
    suspend fun showNotifications(
        conversationIds: Set<ConversationId>,
        bubbleOnly: Boolean = false,
        autoLaunchBubble: Boolean = false,
        inlineReplyMessages: List<Message>
        = emptyList()
    ) {
        // Do not show notifications when the conversations view is open,
        // unless this is for a bubble only.
        if (CustomApplication.getApplication()
                .conversationsActivityVisible() && !bubbleOnly
        ) {
            return
        }

        for (conversationId in conversationIds) {
            // Do not show notifications when notifications are disabled.
            if (!getNotificationsEnabled(conversationId)) {
                continue
            }

            // Do not show notifications when the conversation view is
            // open, unless this is for a bubble only.
            if (CustomApplication.getApplication()
                    .conversationActivityVisible(conversationId)
                && !bubbleOnly
            ) {
                continue
            }

            showNotification(
                conversationId,
                if (bubbleOnly || inlineReplyMessages.isNotEmpty())
                    emptyList()
                else Database.getInstance(context)
                    .getConversationMessagesUnread(conversationId),
                inlineReplyMessages,
                bubbleOnly,
                autoLaunchBubble
            )
        }
    }

    /**
     * Shows a notification for the specified message, bypassing all normal
     * checks. Only used for demo purposes.
     */
    fun showDemoNotification(message: Message) = showNotification(
        message.conversationId,
        listOf(message),
        inlineReplyMessages = emptyList(),
        bubbleOnly = false,
        autoLaunchBubble = false
    )

    /**
     * Returns true if a notification for the provided DID and contact would
     * be allowed to bubble.
     */
    fun canBubble(did: String, contact: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val channel = getNotificationChannelId(did, contact)
            val notificationManager: NotificationManager =
                context.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager
            val bubblesAllowed =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    notificationManager.bubblePreference == NotificationManager.BUBBLE_PREFERENCE_ALL
                } else {
                    @Suppress("DEPRECATION")
                    notificationManager.areBubblesAllowed()
                }
            if (!bubblesAllowed) {
                val notificationChannel =
                    notificationManager.getNotificationChannel(channel)
                return notificationChannel != null
                    && notificationChannel.canBubble()
            }
            return true
        }
        return false
    }

    /**
     * Shows a notification with the specified messages.
     */
    private fun showNotification(
        conversationId: ConversationId,
        messages: List<Message>,
        inlineReplyMessages: List<Message>,
        bubbleOnly: Boolean,
        autoLaunchBubble: Boolean
    ) {
        // Do not show notification if there are no messages, unless this is
        // for a bubble notification.
        if (messages.isEmpty()
            && inlineReplyMessages.isEmpty()
            && !bubbleOnly
        ) {
            return
        }

        // However, if this is not Android R or later, there is no such thing
        // as a "bubble only" notification, so we should just return.
        if (bubbleOnly && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        // Notification metadata
        val did = conversationId.did
        val contact = conversationId.contact

        // Do not show bubble-only notifications if we're not allowed to
        // bubble.
        if (bubbleOnly && !canBubble(did, contact)) {
            return
        }

        @Suppress("ConstantConditionIf")
        val contactName = if (!BuildConfig.IS_DEMO) {
            getContactName(context, contact) ?: getFormattedPhoneNumber(contact)
        } else {
            net.kourlas.voipms_sms.demo.getContactName(contact)
        }

        val largeIcon = applyCircularMask(
            getContactPhotoBitmap(
                context,
                contactName,
                contact,
                context.resources.getDimensionPixelSize(
                    android.R.dimen.notification_large_icon_height
                )
            )
        )
        val adaptiveIcon = IconCompat.createWithAdaptiveBitmap(
            getContactPhotoAdaptiveBitmap(context, contactName, contact)
        )

        // Notification channel
        val channel = getNotificationChannelId(did, contact)

        // General notification properties
        val notification = NotificationCompat.Builder(context, channel)
        notification.setSmallIcon(R.drawable.ic_chat_toolbar_24dp)
        notification.setLargeIcon(largeIcon)
        notification.setAutoCancel(true)
        notification.addPerson(
            Person.Builder()
                .setName(contactName)
                .setKey(contact)
                .setIcon(adaptiveIcon)
                .setUri("tel:${contact}")
                .build()
        )
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
                notification.setSound(Uri.parse(notificationSound))
            } else {
                notification.setSilent(true)
            }
            @Suppress("DEPRECATION")
            if (getNotificationVibrateEnabled(context)) {
                notification.setVibrate(longArrayOf(0, 250, 250, 250))
            }
        } else {
            removePreference(
                context, context.getString(
                    R.string.preferences_notifications_sound_key
                )
            )
            removePreference(
                context, context.getString(
                    R.string.preferences_notifications_vibrate_key
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notification.setGroup(
                context.getString(
                    R.string.notifications_group_key
                )
            )
        }
        notification.setGroupAlertBehavior(
            NotificationCompat.GROUP_ALERT_CHILDREN
        )
        notification.setShortcutId(conversationId.getId())
        notification.setLocusId(LocusIdCompat(conversationId.getId()))
        if (bubbleOnly || inlineReplyMessages.isNotEmpty()) {
            // This means no new messages were received, so we should avoid
            // notifying the user if we can.
            notification.setOnlyAlertOnce(true)
        }

        // Notification text
        val person = Person.Builder().setName(
            context.getString(R.string.notifications_current_user)
        ).build()
        val style = NotificationCompat.MessagingStyle(person)
        val existingMessages =
            notificationMessages[conversationId] ?: emptyList()
        for (existingMessage in existingMessages) {
            style.addHistoricMessage(existingMessage)
        }

        val messagesToAdd = mutableListOf<Message>()
        if (messages.isNotEmpty()) {
            for (message in messages.reversed()) {
                if (existingMessages.isNotEmpty()
                    && existingMessages.last().text == message.text
                    && existingMessages.last().person != null
                    && existingMessages.last().timestamp == message.date.time
                ) {
                    break
                }
                messagesToAdd.add(message)
            }
            messagesToAdd.reverse()
        }

        if (inlineReplyMessages.isNotEmpty()) {
            for (message in inlineReplyMessages) {
                style.addMessage(message.text, Date().time, null as Person?)
            }
        } else if (messagesToAdd.isNotEmpty()) {
            val notificationMessages = messagesToAdd.map {
                NotificationCompat.MessagingStyle.Message(
                    it.text,
                    it.date.time,
                    if (it.isIncoming)
                        Person.Builder()
                            .setName(contactName)
                            .setKey(contact)
                            .setIcon(adaptiveIcon)
                            .setUri("tel:${it.contact}")
                            .build()
                    else null
                )
            }
            for (message in notificationMessages) {
                style.addMessage(message)
            }
            notification.setShowWhen(true)
            notification.setWhen(messagesToAdd.last().date.time)
        }
        notificationMessages[conversationId] =
            (style.historicMessages.toMutableList()
                + style.messages.toMutableList())
        notification.setStyle(style)

        // Mark as read button
        val markReadIntent = MarkReadReceiver.getIntent(context, did, contact)
        markReadIntent.component = ComponentName(
            context, MarkReadReceiver::class.java
        )
        val markReadFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_CANCEL_CURRENT
            }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, (did + contact + "markRead").hashCode(),
            markReadIntent, markReadFlags
        )
        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_drafts_toolbar_24dp,
            context.getString(R.string.notifications_button_mark_read),
            markReadPendingIntent
        )
            .setSemanticAction(
                NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ
            )
            .setShowsUserInterface(false)
            .build()
        notification.addAction(markReadAction)

        // Reply button
        val replyIntent = SendMessageReceiver.getIntent(
            context, did, contact
        )
        replyIntent.component = ComponentName(
            context, SendMessageReceiver::class.java
        )
        val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_CANCEL_CURRENT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, (did + contact + "reply").hashCode(),
            replyIntent, replyFlags
        )
        val remoteInput = RemoteInput.Builder(
            context.getString(
                R.string.notifications_reply_key
            )
        )
            .setLabel(context.getString(R.string.notifications_button_reply))
            .build()
        val replyActionBuilder = NotificationCompat.Action.Builder(
            R.drawable.ic_reply_toolbar_24dp,
            context.getString(R.string.notifications_button_reply),
            replyPendingIntent
        )
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
            val visibleReplyIntent = Intent(
                context,
                ConversationActivity::class.java
            )
            visibleReplyIntent.putExtra(
                context.getString(
                    R.string.conversation_did
                ), did
            )
            visibleReplyIntent.putExtra(
                context.getString(
                    R.string.conversation_contact
                ), contact
            )
            visibleReplyIntent.putExtra(
                context.getString(
                    R.string.conversation_extra_focus
                ), true
            )
            visibleReplyIntent.flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            val visibleReplyFlags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
            val visibleReplyPendingIntent = PendingIntent.getActivity(
                context, (did + contact + "replyVisible").hashCode(),
                visibleReplyIntent, visibleReplyFlags
            )
            val visibleReplyActionBuilder = NotificationCompat.Action.Builder(
                R.drawable.ic_reply_toolbar_24dp,
                context.getString(R.string.notifications_button_reply),
                visibleReplyPendingIntent
            )
                .setSemanticAction(
                    NotificationCompat.Action.SEMANTIC_ACTION_REPLY
                )
                .setShowsUserInterface(true)
                .addRemoteInput(remoteInput)
            notification.addAction(visibleReplyActionBuilder.build())
        }

        // Group notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val groupNotification = NotificationCompat.Builder(context, channel)
            groupNotification.setSmallIcon(R.drawable.ic_chat_toolbar_24dp)
            groupNotification.setGroup(
                context.getString(
                    R.string.notifications_group_key
                )
            )
            groupNotification.setGroupSummary(true)
            groupNotification.setAutoCancel(true)
            groupNotification.setGroupAlertBehavior(
                NotificationCompat.GROUP_ALERT_CHILDREN
            )

            val intent = Intent(context, ConversationsActivity::class.java)
            val stackBuilder = TaskStackBuilder.create(context)
            stackBuilder.addNextIntentWithParentStack(intent)
            val groupFlags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
            groupNotification.setContentIntent(
                stackBuilder.getPendingIntent(
                    "group".hashCode(),
                    groupFlags
                )
            )

            try {
                NotificationManagerCompat.from(context).notify(
                    GROUP_NOTIFICATION_ID, groupNotification.build()
                )
            } catch (e: SecurityException) {
                Toast.makeText(
                    context,
                    context.getString(R.string.notifications_security_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Primary notification action
        val intent = Intent(context, ConversationActivity::class.java)
        intent.putExtra(
            context.getString(
                R.string.conversation_did
            ), did
        )
        intent.putExtra(
            context.getString(
                R.string.conversation_contact
            ), contact
        )
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addNextIntentWithParentStack(intent)
        val primaryFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_CANCEL_CURRENT
            }
        notification.setContentIntent(
            stackBuilder.getPendingIntent(
                (did + contact).hashCode(),
                primaryFlags
            )
        )

        // Bubble
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bubbleIntent = Intent(
                context,
                ConversationBubbleActivity::class.java
            )
            bubbleIntent.putExtra(
                context.getString(
                    R.string.conversation_did
                ), did
            )
            bubbleIntent.putExtra(
                context.getString(
                    R.string.conversation_contact
                ), contact
            )
            val bubbleFlags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val bubblePendingIntent = PendingIntent.getActivity(
                context, 0,
                bubbleIntent, bubbleFlags
            )
            val bubbleMetadata =
                NotificationCompat.BubbleMetadata.Builder(
                    bubblePendingIntent,
                    adaptiveIcon
                )
                    .setDesiredHeight(600)
                    .setSuppressNotification(bubbleOnly)
                    .setAutoExpandBubble(autoLaunchBubble)
                    .build()
            notification.bubbleMetadata = bubbleMetadata
        }

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
                id, notification.build()
            )
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
        clearNotificationState(conversationId)
    }

    /**
     * Clears our internal state associated with a notification, cancelling
     * the group notification if required.
     */
    fun clearNotificationState(conversationId: ConversationId) {
        notificationMessages.remove(conversationId)
        validateGroupNotification()
    }

    /**
     * Gets the notification channel ID for the provided DID and contact. The
     * channel is guaranteed to exist.
     */
    private fun getNotificationChannelId(did: String, contact: String): String {
        createDefaultNotificationChannel()
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Prior to Android O, this doesn't matter.
            context.getString(R.string.notifications_channel_default)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Prior to Android R, conversations did not have a separate
            // section in the notification settings, so we only create the
            // conversation specific channel if the user requested it.
            val channel = notificationManager.getNotificationChannel(
                context.getString(
                    R.string.notifications_channel_contact,
                    did, contact
                )
            )
            if (channel == null) {
                context.getString(R.string.notifications_channel_default)
            } else {
                context.getString(
                    R.string.notifications_channel_contact,
                    did, contact
                )
            }
        } else {
            // As of Android R, conversations have a separate section in the
            // notification settings, so we always create the conversation
            // specific channel.
            createDidNotificationChannel(did, contact)
            context.getString(
                R.string.notifications_channel_contact,
                did, contact
            )
        }
    }

    /**
     * Cancels the group notification if required.
     */
    private fun validateGroupNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val noOtherNotifications = notificationManager.activeNotifications
                .filter {
                    it.id != SYNC_DATABASE_NOTIFICATION_ID
                        && it.id != SYNC_SEND_MESSAGE_NOTIFICATION_ID
                        && it.id != SYNC_VERIFY_CREDENTIALS_NOTIFICATION_ID
                        && it.id != SYNC_RETRIEVE_DIDS_NOTIFICATION_ID
                        && it.id != SYNC_REGISTER_PUSH_NOTIFICATION_ID
                        && it.id != GROUP_NOTIFICATION_ID
                }
                .none()
            if (noOtherNotifications) {
                NotificationManagerCompat.from(context).cancel(
                    GROUP_NOTIFICATION_ID
                )
            }
        }
    }

    companion object {
        // It is not a leak to store an instance to the application context,
        // since it has the same lifetime as the application itself
        @SuppressLint("StaticFieldLeak")
        private var instance: Notifications? = null

        // Notification ID for the database synchronization notification.
        const val SYNC_DATABASE_NOTIFICATION_ID = 1

        // Notification ID for the send message notification.
        const val SYNC_SEND_MESSAGE_NOTIFICATION_ID =
            SYNC_DATABASE_NOTIFICATION_ID + 1

        // Notification ID for the verify credentials notification.
        const val SYNC_VERIFY_CREDENTIALS_NOTIFICATION_ID =
            SYNC_SEND_MESSAGE_NOTIFICATION_ID + 1

        // Notification ID for the retrieve DIDs notification.
        const val SYNC_RETRIEVE_DIDS_NOTIFICATION_ID =
            SYNC_VERIFY_CREDENTIALS_NOTIFICATION_ID + 1

        // Notification ID for the register for push notifications notification.
        const val SYNC_REGISTER_PUSH_NOTIFICATION_ID =
            SYNC_RETRIEVE_DIDS_NOTIFICATION_ID + 1

        // Notification ID for the group notification, which contains all other
        // notifications
        const val GROUP_NOTIFICATION_ID = SYNC_REGISTER_PUSH_NOTIFICATION_ID + 1

        // Starting notification ID for ordinary message notifications
        const val MESSAGE_START_NOTIFICATION_ID = GROUP_NOTIFICATION_ID + 1

        /**
         * Gets the sole instance of the Notifications class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(context: Context): Notifications =
            instance ?: synchronized(this) {
                instance ?: Notifications(
                    context.applicationContext
                ).also { instance = it }
            }
    }
}
