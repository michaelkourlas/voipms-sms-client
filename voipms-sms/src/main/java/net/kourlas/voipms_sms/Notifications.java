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

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AlertDialog;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationQuickReplyActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.gcm.Gcm;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Message;
import net.kourlas.voipms_sms.receivers.MarkAsReadReceiver;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Notifications {
    private static Notifications instance = null;
    private final Context applicationContext;
    private final Preferences preferences;
    private final Gcm gcm;

    private final Map<String, Integer> notificationIds;
    private int notificationIdCount;

    private Notifications(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.preferences = Preferences.getInstance(applicationContext);
        this.gcm = Gcm.getInstance(applicationContext);

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

    public void showNotifications(List<String> contacts) {
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
                notificationBuilder.setSound(Uri.parse(
                        Preferences.getInstance(applicationContext).getNotificationSound()));
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
                intent.putExtra(applicationContext.getString(R.string.conversation_extra_contact),
                        conversation.getContact());
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
                replyIntent.putExtra(applicationContext.getString(R.string.conversation_extra_contact),
                        conversation.getContact());
                PendingIntent replyPendingIntent = PendingIntent.getActivity(applicationContext, 0, replyIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
                NotificationCompat.Action.Builder replyAction = new NotificationCompat.Action.Builder(
                        R.drawable.ic_reply_white_24dp,
                        applicationContext.getString(R.string.notifications_button_reply),
                        replyPendingIntent);
                notificationBuilder.addAction(replyAction.build());

                Intent markAsReadIntent = new Intent(applicationContext, MarkAsReadReceiver.class);
                markAsReadIntent.putExtra(applicationContext.getString(R.string.conversation_extra_contact),
                        conversation.getContact());
                PendingIntent markAsReadPendingIntent = PendingIntent.getBroadcast(applicationContext, 0,
                        markAsReadIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                NotificationCompat.Action.Builder markAsReadAction = new NotificationCompat.Action.Builder(
                        R.drawable.ic_drafts_white_24dp,
                        applicationContext.getString(R.string.notifications_button_mark_read),
                        markAsReadPendingIntent);
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

    /**
     * Enable SMS notifications by configuring the VoIP.ms URL callback, registering for GCM and making the appropriate
     * changes to the application preferences.
     *
     * @param activity The source activity.
     */
    public void enableNotifications(final Activity activity) {
        if (preferences.getEmail().equals("") || preferences.getPassword().equals("") ||
                preferences.getDid().equals("")) {
            Utils.showInfoDialog(activity, applicationContext.getString(
                    R.string.notifications_callback_username_password_did));
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(R.string.notifications_callback_progress));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new AsyncTask<Boolean, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Boolean... params) {
                try {
                    String url = "https://www.voip.ms/api/v1/rest.php?" +
                            "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                            "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                            "method=setSMS" + "&" +
                            "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                            "enable=1" + "&" +
                            "url_callback_enable=1" + "&" +
                            "url_callback=" + URLEncoder.encode(
                            "http://voipmssms-kourlas.rhcloud.com/sms_callback?did={TO}", "UTF-8") + "&" +
                            "url_callback_retry=0";

                    JSONObject result = Utils.getJson(url);
                    String status = result.optString("status");
                    return !(status == null || !status.equals("success"));
                } catch (Exception ex) {
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.hide();

                DialogInterface.OnClickListener gcmOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        gcm.registerForGcm(activity, true, true);
                    }
                };

                if (!success) {
                    Utils.showInfoDialogWithAction(activity,
                            applicationContext.getString(R.string.notifications_callback_fail),
                            gcmOnClickListener);
                }
                else {
                    Utils.showInfoDialogWithAction(activity,
                            applicationContext.getString(R.string.notifications_callback_success),
                            gcmOnClickListener);
                }
            }
        }.execute();
    }
}
