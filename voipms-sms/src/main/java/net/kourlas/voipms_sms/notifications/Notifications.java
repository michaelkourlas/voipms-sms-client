/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2016 Michael Kourlas
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

package net.kourlas.voipms_sms.notifications;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import net.kourlas.voipms_sms.*;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationQuickReplyActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.receivers.MarkAsReadReceiver;

import java.util.*;

public class Notifications {
    private static Notifications instance = null;

    private final Context applicationContext;
    private final Database database;
    private final Preferences preferences;

    private final Map<String, Integer> notificationIds;
    private int notificationIdCount;

    private Notifications(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.database = Database.getInstance(applicationContext);
        this.preferences = Preferences.getInstance(applicationContext);

        this.notificationIds = new HashMap<>();
        this.notificationIdCount = 0;
    }

    public static Notifications getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Notifications(applicationContext);
        }
        return instance;
    }

    /**
     * Gets the IDs of the currently active notifications.
     *
     * @return The IDs of the currently active notifications.
     */
    public Map<String, Integer> getNotificationIds() {
        return notificationIds;
    }

    /**
     * Show notifications for new messages for the specified conversations.
     *
     * @param contacts A list of contacts.
     */
    public void showNotifications(List<String> contacts) {
        // Only show notifications if notifications are enabled
        if (!preferences.getNotificationsEnabled()) {
            return;
        }

        // Do not show notifications when the conversations view is open
        if (ActivityMonitor.getInstance().getCurrentActivity()
            instanceof ConversationsActivity)
        {
            return;
        }

        for (String contact : contacts) {
            Activity currentActivity = ActivityMonitor.getInstance()
                                                      .getCurrentActivity();
            // Do not show notifications for a contact when that contact's
            // conversation view is open
            if (currentActivity instanceof ConversationActivity
                && ((ConversationActivity) currentActivity).getContact()
                                                           .equals(
                                                               contact))
            {
                continue;
            }

            Map<String, String> shortTexts = new HashMap<>();
            Map<String, String> longTexts = new HashMap<>();
            Message[] messages = database.getUnreadMessages(
                preferences.getDid(), contact);
            for (Message message : messages) {
                if (shortTexts.get(contact) != null) {
                    longTexts.put(contact, message.getText() + "\n"
                                           + longTexts.get(contact));
                } else {
                    shortTexts.put(contact, message.getText());
                    longTexts.put(contact, message.getText());
                }
            }
            for (Map.Entry<String, String> entry : shortTexts.entrySet()) {
                showNotification(entry.getKey(), entry.getValue(),
                                 longTexts.get(entry.getKey()));
            }
        }
    }


    /**
     * Shows a notification with the specified details.
     *
     * @param contact   The contact that the notification is from.
     * @param shortText The short form of the message text.
     * @param longText  The long form of the message text.
     */
    private void showNotification(String contact,
                                  String shortText,
                                  String longText) {

        String title = Utils.getContactName(applicationContext,
                                            contact);
        if (title == null) {
            title = Utils.getFormattedPhoneNumber(contact);
        }
        NotificationCompat.Builder notification =
            new NotificationCompat.Builder(applicationContext);
        notification.setContentTitle(title);
        notification.setContentText(shortText);
        notification.setSmallIcon(R.drawable.ic_chat_white_24dp);
        notification.setPriority(Notification.PRIORITY_HIGH);
        String notificationSound = Preferences.getInstance(
            applicationContext).getNotificationSound();
        if (!notificationSound.equals("")) {
            notification.setSound(Uri.parse(Preferences.getInstance(
                applicationContext).getNotificationSound()));
        }
        notification.setLights(0xFFAA0000, 1000, 5000);
        if (Preferences.getInstance(applicationContext)
                       .getNotificationVibrateEnabled())
        {
            notification.setVibrate(new long[]{0, 250, 250, 250});
        }
        else {
            notification.setVibrate(new long[]{0});
        }
        notification.setColor(0xFFAA0000);
        notification.setAutoCancel(true);
        notification.setStyle(new NotificationCompat.BigTextStyle()
                                  .bigText(longText));

        Bitmap largeIconBitmap;
        try {
            largeIconBitmap = MediaStore.Images.Media.getBitmap(
                applicationContext.getContentResolver(), Uri.parse(
                    Utils.getContactPhotoUri(applicationContext, contact)));
            largeIconBitmap = Bitmap.createScaledBitmap(largeIconBitmap,
                                                        256, 256, false);
            largeIconBitmap = Utils.applyCircularMask(largeIconBitmap);
            notification.setLargeIcon(largeIconBitmap);
        } catch (Exception ignored) {
            // Do nothing.
        }

        Intent intent = new Intent(applicationContext,
                                   ConversationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(applicationContext.getString(
            R.string.conversation_extra_contact),
                        contact);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(
            applicationContext);
        stackBuilder.addParentStack(ConversationActivity.class);
        stackBuilder.addNextIntent(intent);
        notification.setContentIntent(stackBuilder.getPendingIntent(
            0,PendingIntent.FLAG_CANCEL_CURRENT));

        Intent replyIntent = new Intent(applicationContext,
                                        ConversationQuickReplyActivity.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            replyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
        else {
            //noinspection deprecation
            replyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        }
        replyIntent.putExtra(applicationContext.getString(
            R.string.conversation_extra_contact), contact);
        PendingIntent replyPendingIntent = PendingIntent.getActivity(
            applicationContext, 0, replyIntent,
            PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Action.Builder replyAction =
            new NotificationCompat.Action.Builder(
                R.drawable.ic_reply_white_24dp,
                applicationContext.getString(
                    R.string.notifications_button_reply),
                replyPendingIntent);
        notification.addAction(replyAction.build());

        Intent markAsReadIntent = new Intent(applicationContext,
                                             MarkAsReadReceiver.class);
        markAsReadIntent.putExtra(applicationContext.getString(
            R.string.conversation_extra_contact), contact);
        PendingIntent markAsReadPendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, markAsReadIntent,
            PendingIntent.FLAG_CANCEL_CURRENT);
        NotificationCompat.Action.Builder markAsReadAction =
            new NotificationCompat.Action.Builder(
                R.drawable.ic_drafts_white_24dp,
                applicationContext.getString(
                    R.string.notifications_button_mark_read),
                markAsReadPendingIntent);
        notification.addAction(markAsReadAction.build());

        int id;
        if (notificationIds.get(contact) != null) {
            id = notificationIds.get(contact);
        }
        else {
            id = notificationIdCount++;
            notificationIds.put(contact, id);
        }
        NotificationManager notificationManager = (NotificationManager)
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification.build());
    }

}
