/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas and other contributors
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

package net.kourlas.voipms_sms;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationQuickReplyActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.receivers.MarkAsReadReceiver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Notifications {
    private static Notifications instance = null;
    private final Context applicationContext;
    private final Preferences preferences;

    private final Map<String, Integer> notificationIds;
    private int notificationIdCount;

    private Notifications(Context context) {
        this.applicationContext = context.getApplicationContext();
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

    public Map<String, Integer> getNotificationIds() {
        return notificationIds;
    }

    public void showNotification(List<String> contacts) {
        if (!(ActivityMonitor.getInstance().getCurrentActivity() instanceof ConversationsActivity)) {
            Conversation[] conversations = Database.getInstance(applicationContext).getConversations(
                    preferences.getDid());
            for (Conversation conversation : conversations) {
                if (!conversation.isUnread() || !contacts.contains(conversation.getContact()) ||
                        (ActivityMonitor.getInstance().getCurrentActivity() instanceof ConversationActivity &&
                                ((ConversationActivity)
                                        ActivityMonitor.getInstance().getCurrentActivity()).getContact().equals(
                                        conversation.getContact()))) {
                    continue;
                }

                String smsContact = Utils.getContactName(applicationContext, conversation.getContact());
                if (smsContact == null) {
                    smsContact = Utils.getFormattedPhoneNumber(conversation.getContact());
                }

                String allSmses = "";
                String mostRecentSms = "";
                boolean initial = true;
                for (Message message : conversation.getMessages()) {
                    if (message.getType() == Message.Type.INCOMING && message.isUnread()) {
                        if (initial) {
                            allSmses = message.getText();
                            mostRecentSms = message.getText();
                            initial = false;
                        }
                        else {
                            allSmses = message.getText() + "\n" + allSmses;
                        }
                    }
                    else {
                        break;
                    }
                }

                NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                        applicationContext);

                notificationBuilder.setContentTitle(smsContact);
                notificationBuilder.setContentText(mostRecentSms);
                notificationBuilder.setSmallIcon(R.drawable.ic_chat_white_24dp);
                notificationBuilder.setPriority(Notification.PRIORITY_HIGH);
                notificationBuilder.setSound(Uri.parse(Preferences.getInstance(applicationContext).getNotificationSound()));
                notificationBuilder.setLights(0xFFAA0000, 1000, 5000);
                if (Preferences.getInstance(applicationContext).getNotificationVibrateEnabled()) {
                    notificationBuilder.setVibrate(new long[]{0, 250, 250, 250});
                }
                else {
                    notificationBuilder.setVibrate(new long[]{0});
                }
                notificationBuilder.setColor(0xFFAA0000);
                notificationBuilder.setAutoCancel(true);
                notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(allSmses));

                Bitmap largeIconBitmap;
                try {
                    largeIconBitmap = MediaStore.Images.Media.getBitmap(applicationContext.getContentResolver(),
                            Uri.parse(Utils.getContactPhotoUri(applicationContext, conversation.getContact())));
                    largeIconBitmap = Bitmap.createScaledBitmap(largeIconBitmap, 256, 256, false);
                    largeIconBitmap = Utils.applyCircularMask(largeIconBitmap);
                    notificationBuilder.setLargeIcon(largeIconBitmap);
                } catch (Exception ignored) {

                }

                Intent intent = new Intent(applicationContext, ConversationActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra("contact", conversation.getContact());
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(applicationContext);
                stackBuilder.addParentStack(ConversationActivity.class);
                stackBuilder.addNextIntent(intent);
                notificationBuilder.setContentIntent(stackBuilder.getPendingIntent(0,
                        PendingIntent.FLAG_CANCEL_CURRENT));

                Intent replyIntent = new Intent(applicationContext, ConversationQuickReplyActivity.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    replyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                }
                else {
                    //noinspection deprecation
                    replyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                }
                replyIntent.putExtra("contact", conversation.getContact());
                PendingIntent replyPendingIntent = PendingIntent.getActivity(applicationContext, 0, replyIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
                NotificationCompat.Action.Builder replyAction = new NotificationCompat.Action.Builder(
                        R.drawable.ic_reply_white_24dp, "Reply", replyPendingIntent);
                notificationBuilder.addAction(replyAction.build());

                Intent markAsReadIntent = new Intent(applicationContext, MarkAsReadReceiver.class);
                markAsReadIntent.putExtra("contact", conversation.getContact());
                PendingIntent markAsReadPendingIntent = PendingIntent.getBroadcast(applicationContext, 0,
                        markAsReadIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                NotificationCompat.Action.Builder markAsReadAction = new NotificationCompat.Action.Builder(
                        R.drawable.ic_drafts_white_24dp, "Mark Read", markAsReadPendingIntent);
                notificationBuilder.addAction(markAsReadAction.build());

                int id;
                if (notificationIds.get(conversation.getContact()) != null) {
                    id = notificationIds.get(conversation.getContact());
                }
                else {
                    id = notificationIdCount++;
                    notificationIds.put(conversation.getContact(), id);
                }
                NotificationManager notificationManager = (NotificationManager)
                        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(id, notificationBuilder.build());
            }
        }
    }
}
