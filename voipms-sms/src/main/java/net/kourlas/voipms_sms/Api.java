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
import android.graphics.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.app.NavUtils;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.gcm.Gcm;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Api {
    private static final String TAG = "Api";
    private static Api instance = null;
    private final Context applicationContext;
    private final Preferences preferences;

    public Map<String, Integer> getNotificationIds() {
        return notificationIds;
    }

    private final Map<String, Integer> notificationIds;
    private int notificationIdCount;

    private Api(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.preferences = Preferences.getInstance(applicationContext);
        this.notificationIds = new HashMap<String, Integer>();
        this.notificationIdCount = 0;
    }

    public static Api getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Api(applicationContext);
        }
        return instance;
    }

    public void showSelectDidDialog(Activity sourceActivity) {
        SelectDidTask task = new SelectDidTask(sourceActivity);

        if (preferences.getEmail().equals("")) {
            processError(R.string.api_update_did_email);
        } else if (preferences.getPassword().equals("")) {
            processError(R.string.api_update_did_password);
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getDIDsInfo";
                task.start(voipUrl);
            } catch (UnsupportedEncodingException ex) {
                processError(R.string.api_update_did_unsupported_encoding_exception, ex);
            }
        } else {
            processError(R.string.api_update_did_network);
        }
    }

    public void deleteSms(Activity sourceActivity, long smsId) {
        DeleteSmsTask task = new DeleteSmsTask(sourceActivity, smsId);

        if (preferences.getEmail().equals("")) {
            processError(R.string.api_delete_sms_email);
        } else if (preferences.getPassword().equals("")) {
            processError(R.string.api_delete_sms_password);
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=deleteSMS" + "&" +
                        "id=" + smsId;
                task.start(voipUrl);
            } catch (UnsupportedEncodingException ex) {
                processError(R.string.api_delete_sms_unsupported_encoding_exception, ex);
            }
        } else {
            processError(R.string.api_delete_sms_network);
        }
    }

    public void sendSms(Activity sourceActivity, String contact, String message) {
        SendSmsTask task = new SendSmsTask(sourceActivity);

        if (preferences.getEmail().equals("")) {
            processError(R.string.api_send_sms_email);
        } else if (preferences.getPassword().equals("")) {
            processError(R.string.api_send_sms_password);
        } else if (preferences.getDid().equals("")) {
            processError(R.string.api_send_sms_did);
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=sendSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "dst=" + URLEncoder.encode(contact, "UTF-8") + "&" +
                        "message=" + URLEncoder.encode(message, "UTF-8");
                task.start(voipUrl);
                return;
            } catch (UnsupportedEncodingException ex) {
                processError(R.string.api_send_sms_unsupported_encoding_exception, ex);
            }
        } else {
            processError(R.string.api_send_sms_network);
        }

        task.cleanup();
    }

    public void updateSmsDatabase(Activity sourceActivity, boolean showErrorToasts, boolean showSmsNotifications) {
        UpdateSmsDatabaseTask task = new UpdateSmsDatabaseTask(sourceActivity, showErrorToasts, showSmsNotifications);

        if (preferences.getEmail().equals("")) {
            processError(showErrorToasts, R.string.api_update_smses_email);
        } else if (preferences.getPassword().equals("")) {
            processError(showErrorToasts, R.string.api_update_smses_password);
        } else if (preferences.getDid().equals("")) {
            processError(showErrorToasts, R.string.api_update_smses_did);
        } else if (isNetworkConnectionAvailable()) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.add(Calendar.DAY_OF_YEAR, -preferences.getDaysToSync());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "limit=" + URLEncoder.encode("1000000", "UTF-8") + "&" +
                        "from=" + URLEncoder.encode(sdf.format(calendar.getTime()), "UTF-8") + "&" +
                        "to=" + URLEncoder.encode(sdf.format(Calendar.getInstance(
                        TimeZone.getTimeZone("UTC")).getTime()), "UTF-8") + "&" +
                        "timezone=-1";
                task.start(voipUrl);
                return;
            } catch (UnsupportedEncodingException ex) {
                processError(showErrorToasts, R.string.api_update_smses_unsupported_encoding_exception, ex);
            }
        } else {
            processError(showErrorToasts, R.string.api_update_smses_network);
        }

        task.cleanup();
    }

    private boolean isNetworkConnectionAvailable() {
        ConnectivityManager connMgr = (ConnectivityManager) applicationContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void processError(int toastTextResId) {
        processError(true, toastTextResId, null, null);
    }

    private void processError(int toastTextResId, Exception ex) {
        processError(true, toastTextResId, null, Log.getStackTraceString(ex));
    }

    private void processError(int toastTextResId, String toastTextExtension) {
        processError(true, toastTextResId, toastTextExtension, null);
    }

    private void processError(boolean showToast, int toastTextResId) {
        processError(showToast, toastTextResId, null, null);
    }

    private void processError(boolean showToast, int toastTextResId, String toastTextExtension) {
        processError(showToast, toastTextResId, toastTextExtension, null);
    }

    private void processError(boolean showToast, int toastTextResId, Exception ex) {
        processError(showToast, toastTextResId, null, Log.getStackTraceString(ex));
    }

    private void processError(boolean showToast, int toastTextResId, String toastTextExtension,
                              String additionalLogInfo) {
        String toastText;
        if (toastTextExtension != null) {
            toastText = applicationContext.getString(toastTextResId) + toastTextExtension + ")";
        } else {
            toastText = applicationContext.getString(toastTextResId);
        }

        if (showToast) {
            Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show();
        }

        Log.w(TAG, toastText);
        if (additionalLogInfo != null) {
            Log.w(TAG, additionalLogInfo);
        }
    }

    private class SelectDidTask {
        private final Activity sourceActivity;

        public SelectDidTask(Activity sourceActivity) {
            this.sourceActivity = sourceActivity;
        }

        public void start(String voipUrl) {
            new UpdateDidAsyncTask().execute(voipUrl);
        }

        private class UpdateDidAsyncTask extends AsyncTask<String, Void, JSONObject> {
            @Override
            protected JSONObject doInBackground(String... params) {
                try {
                    return Utils.getJson(params[0]);
                } catch (IOException ex) {
                    processError(R.string.api_update_did_request, ex);
                } catch (org.json.simple.parser.ParseException ex) {
                    processError(R.string.api_update_did_parse, ex);
                }

                return null;
            }

            @Override
            protected void onPostExecute(JSONObject result) {
                if (result == null) {
                    return;
                }

                try {
                    String status = (String) result.get("status");
                    if (status.equals("success")) {
                        final List<String> dids = new ArrayList<String>();
                        JSONArray rawDids = (JSONArray) result.get("dids");
                        for (Object rawDidObj : rawDids) {
                            JSONObject rawDid = (JSONObject) rawDidObj;
                            if (rawDid.get("sms_available").equals("1") &&
                                    rawDid.get("sms_enabled").equals("1")) {
                                dids.add((String) rawDid.get("did"));
                            }
                        }

                        if (dids.size() == 0) {
                            Toast.makeText(applicationContext, applicationContext.getResources().getString(
                                    R.string.api_update_did_no_dids), Toast.LENGTH_LONG).show();
                        } else {
                            // A list is used instead of a simple integer because the variable must be made final
                            final List<Integer> selectedItemList = new ArrayList<Integer>();
                            selectedItemList.add(0);

                            final String[] didsArray = new String[dids.size()];
                            dids.toArray(didsArray);

                            AlertDialog.Builder builder = new AlertDialog.Builder(sourceActivity);
                            builder.setTitle(applicationContext.getString(
                                    R.string.conversations_select_did_dialog_title));
                            builder.setSingleChoiceItems(didsArray, 0, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    selectedItemList.clear();
                                    selectedItemList.add(which);
                                }
                            });
                            builder.setNegativeButton(applicationContext.getString(R.string.cancel), null);
                            builder.setPositiveButton(applicationContext.getString(R.string.ok),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            int selectedItem = selectedItemList.get(0);
                                            preferences.setDid(didsArray[selectedItem]);

                                            Database.getInstance(applicationContext).deleteAllSMS();

                                            if (sourceActivity instanceof ConversationsActivity) {
                                                ConversationsActivity conversationsActivity = (ConversationsActivity)
                                                        sourceActivity;
                                                conversationsActivity.getConversationsListViewAdapter().refresh();
                                                updateSmsDatabase(sourceActivity, true, false);
                                            }

                                            Gcm.getInstance(applicationContext).registerForGcm(sourceActivity, false);
                                        }
                                    });
                            builder.show();
                        }
                    } else {
                        processError(R.string.api_update_did_api, status);
                    }
                } catch (ClassCastException ex) {
                    processError(R.string.api_update_did_parse, ex);
                }
            }
        }
    }

    public class DeleteSmsTask {
        private final Activity sourceActivity;
        private final long smsId;

        public DeleteSmsTask(Activity sourceActivity, long smsId) {
            this.sourceActivity = sourceActivity;
            this.smsId = smsId;
        }

        public void start(String voipUrl) {
            new DeleteSmsAsyncTask().execute(voipUrl);
        }

        private class DeleteSmsAsyncTask extends AsyncTask<String, Void, JSONObject> {
            @Override
            protected JSONObject doInBackground(String... params) {
                try {
                    return Utils.getJson(params[0]);
                } catch (IOException ex) {
                    processError(R.string.api_delete_sms_request, ex);
                } catch (org.json.simple.parser.ParseException ex) {
                    processError(R.string.api_delete_sms_parse, ex);
                }

                return null;
            }

            @Override
            protected void onPostExecute(JSONObject result) {
                try {
                    String status = (String) result.get("status");
                    if (status.equals("success")) {
                        Database.getInstance(applicationContext).deleteSMS(smsId);

                        if (sourceActivity instanceof ConversationsActivity) {
                            ConversationsActivity conversationsActivity = (ConversationsActivity) sourceActivity;
                            conversationsActivity.getConversationsListViewAdapter().refresh();
                        } else if (sourceActivity instanceof ConversationActivity) {
                            ConversationActivity conversationActivity = (ConversationActivity) sourceActivity;
                            conversationActivity.getConversationListViewAdapter().refresh();

                            if (Database.getInstance(applicationContext).getConversation(
                                    conversationActivity.getContact()).getAllSms().length == 0) {
                                NavUtils.navigateUpFromSameTask(conversationActivity);
                            }
                        }
                    } else {
                        processError(R.string.api_delete_sms_api, status);
                    }
                } catch (ClassCastException ex) {
                    processError(R.string.api_delete_sms_parse, ex);
                }
            }
        }

    }

    public class SendSmsTask {
        private final Activity sourceActivity;

        public SendSmsTask(Activity sourceActivity) {
            this.sourceActivity = sourceActivity;
        }

        public void start(String voipUrl) {
            new SendSmsAsyncTask().execute(voipUrl);
        }

        public void cleanup() {
            if (sourceActivity instanceof ConversationActivity) {
                ProgressBar progressBar = (ProgressBar) sourceActivity.findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.INVISIBLE);

                ImageButton sendButton = (ImageButton) sourceActivity.findViewById(R.id.send_button);
                sendButton.setEnabled(true);
            }
        }

        private class SendSmsAsyncTask extends AsyncTask<String, Void, JSONObject> {
            @Override
            protected JSONObject doInBackground(String... params) {
                try {
                    return Utils.getJson(params[0]);
                } catch (IOException ex) {
                    processError(R.string.api_send_sms_request, ex);
                } catch (org.json.simple.parser.ParseException ex) {
                    processError(R.string.api_send_sms_parse, ex);
                }

                cleanup();

                return null;
            }

            @Override
            protected void onPostExecute(JSONObject result) {
                try {
                    String status = (String) result.get("status");
                    if (status.equals("success")) {
                        updateSmsDatabase(sourceActivity, true, false);

                        return;
                    } else {
                        processError(R.string.api_send_sms_api, status);
                    }
                } catch (ClassCastException ex) {
                    processError(R.string.api_send_sms_parse, ex);
                }

                cleanup();
            }
        }
    }

    private class UpdateSmsDatabaseTask {
        private final Activity sourceActivity;
        private final boolean showErrorToasts;
        private final boolean showSmsNotifications;

        public UpdateSmsDatabaseTask(Activity sourceActivity, boolean showErrorToasts,
                                     boolean showSmsNotifications) {
            this.sourceActivity = sourceActivity;
            this.showErrorToasts = showErrorToasts;
            this.showSmsNotifications = showSmsNotifications;
        }

        public void start(String voipUrl) {
            new UpdateSmsDatabaseAsyncTask().execute(voipUrl);
        }

        public void cleanup() {
            if (sourceActivity instanceof ConversationsActivity) {
                SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) sourceActivity.findViewById(
                        R.id.swipe_refresh_layout);
                swipeRefreshLayout.setRefreshing(false);
            } else if (sourceActivity instanceof ConversationActivity) {
                ProgressBar progressBar = (ProgressBar) sourceActivity.findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.INVISIBLE);

                ImageButton sendButton = (ImageButton) sourceActivity.findViewById(R.id.send_button);
                sendButton.setEnabled(true);
            }
        }

        private class UpdateSmsDatabaseAsyncTask extends AsyncTask<String, Void, JSONObject> {
            @Override
            protected JSONObject doInBackground(String... params) {
                try {
                    return Utils.getJson(params[0]);
                } catch (IOException ex) {
                    processError(showErrorToasts, R.string.api_update_smses_request, ex);
                } catch (org.json.simple.parser.ParseException ex) {
                    processError(showErrorToasts, R.string.api_update_smses_parse, ex);
                }

                cleanup();

                return null;
            }

            @Override
            protected void onPostExecute(JSONObject result) {
                if (result == null) {
                    return;
                }

                try {
                    String status = (String) result.get("status");
                    if (status.equals("success")) {
                        // Process API response
                        List<Sms> smses = new ArrayList<Sms>();
                        JSONArray rawSmses = (JSONArray) result.get("sms");
                        for (Object rawSmsObj : rawSmses) {
                            JSONObject rawSms = (JSONObject) rawSmsObj;
                            String id = (String) rawSms.get("id");
                            String date = (String) rawSms.get("date");
                            String type = (String) rawSms.get("type");
                            String did = (String) rawSms.get("did");
                            String contact = (String) rawSms.get("contact");
                            String message = (String) rawSms.get("message");

                            Sms sms = new Sms(id, date, type, did, contact, message);
                            smses.add(sms);
                        }

                        // Remove SMSes that have been deleted from the server
                        Sms[] oldSmses = Database.getInstance(applicationContext).getAllSmses();
                        for (Sms sms : oldSmses) {
                            if (!smses.contains(sms)) {
                                Database.getInstance(applicationContext).deleteSMS(sms.getId());
                            }
                        }

                        // Add new SMSes from the server
                        List<Sms> newSmses = new ArrayList<Sms>();
                        for (Sms sms : smses) {
                            if (!Database.getInstance(applicationContext).smsExists(sms.getId())) {
                                newSmses.add(sms);
                                Database.getInstance(applicationContext).addSms(sms);
                            }
                        }

                        // Show notifications for new SMSes
                        if (showSmsNotifications && !(App.getInstance().getCurrentActivity() instanceof ConversationsActivity)) {
                            Conversation[] conversations = Database.getInstance(
                                    applicationContext).getAllConversations();
                            for (Conversation conversation : conversations) {
                                if (conversation.isUnread() && newSmses.contains(conversation.getMostRecentSms())) {
                                    if (App.getInstance().getCurrentActivity() instanceof ConversationActivity &&
                                            ((ConversationActivity) App.getInstance().getCurrentActivity()).getContact().equals(
                                                    conversation.getContact())) {
                                        continue;
                                    }

                                    String smsContact = Utils.getContactName(applicationContext,
                                            conversation.getContact());
                                    if (smsContact == null) {
                                        smsContact = Utils.getFormattedPhoneNumber(conversation.getContact());
                                    }

                                    String smsText = "";
                                    String last = "";
                                    boolean initial = true;
                                    for (Sms sms : conversation.getAllSms()) {
                                        if (sms.getType() == Sms.Type.INCOMING && sms.isUnread()) {
                                            if (initial) {
                                                smsText = sms.getMessage();
                                                last = sms.getMessage();
                                                initial = false;
                                            }
                                            else {
                                                smsText = sms.getMessage() + "\n" + smsText;
                                            }
                                        } else {
                                            break;
                                        }
                                    }

                                    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                                            applicationContext);
                                    notificationBuilder.setSmallIcon(R.drawable.ic_message_white_18dp);
                                    notificationBuilder.setContentTitle(smsContact);
                                    notificationBuilder.setContentText(last);

                                    notificationBuilder.setSound(Uri.parse(preferences.getNotificationSound()));
                                    notificationBuilder.setLights(0xFFAA0000, 1000, 5000);
                                    if (preferences.getNotificationVibrateEnabled()) {
                                        notificationBuilder.setVibrate(new long[]{0, 250, 250, 250});
                                    } else {
                                        notificationBuilder.setVibrate(new long[]{0});
                                    }
                                    notificationBuilder.setPriority(Notification.PRIORITY_HIGH);
                                    notificationBuilder.setColor(0xFFAA0000);

                                    try {
                                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(applicationContext.getContentResolver(), Uri.parse(Utils.getContactPhotoUri(applicationContext, conversation.getContact())));
                                        bitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, false);
                                        bitmap = getCroppedBitmap(bitmap);
                                        notificationBuilder.setLargeIcon(bitmap);
                                    }
                                    catch (Exception ex) {

                                    }

                                    Intent intent = new Intent(applicationContext, ConversationActivity.class);
                                    intent.putExtra("contact", conversation.getContact());

                                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(applicationContext);
                                    stackBuilder.addParentStack(ConversationActivity.class);
                                    stackBuilder.addNextIntent(intent);
                                    PendingIntent resultPendingIntent =
                                            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

                                    notificationBuilder.setContentIntent(resultPendingIntent);
                                    notificationBuilder.setAutoCancel(true);

                                    int id;
                                    if (notificationIds.get(conversation.getContact()) != null) {
                                        id = notificationIds.get(conversation.getContact());
                                    } else {
                                        id = notificationIdCount++;
                                        notificationIds.put(conversation.getContact(), id);
                                    }

                                    notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(smsText));

                                    NotificationManager notificationManager = (NotificationManager)
                                            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                                    notificationManager.notify(id, notificationBuilder.build());
                                }
                            }
                        }

                        if (sourceActivity instanceof ConversationsActivity) {
                            ConversationsActivity conversationsActivity = (ConversationsActivity) sourceActivity;
                            conversationsActivity.getConversationsListViewAdapter().refresh();
                        } else if (sourceActivity instanceof ConversationActivity) {
                            ConversationActivity conversationActivity = (ConversationActivity) sourceActivity;
                            conversationActivity.getConversationListViewAdapter().refresh();

                            EditText messageText = (EditText) conversationActivity.findViewById(R.id.message_edit_text);
                            messageText.setText("");

                            conversationActivity.getConversationListViewAdapter().requestScrollToBottom();
                        } else if (sourceActivity == null) {
                            if (App.getInstance().getCurrentActivity() instanceof ConversationsActivity) {
                                final ConversationsActivity conversationsActivity = (ConversationsActivity)
                                        App.getInstance().getCurrentActivity();
                                conversationsActivity.getConversationsListViewAdapter().requestScrollToTop();
                                conversationsActivity.getConversationsListViewAdapter().refresh();
                            } else if (App.getInstance().getCurrentActivity() instanceof ConversationActivity) {
                                final ConversationActivity conversationActivity = (ConversationActivity)
                                        App.getInstance().getCurrentActivity();
                                conversationActivity.getConversationListViewAdapter().requestScrollToBottom();
                                conversationActivity.getConversationListViewAdapter().refresh();
                            }
                        }

                        cleanup();

                        return;
                    } else {
                        processError(showErrorToasts, R.string.api_update_smses_api, status);
                    }
                } catch (ParseException ex) {
                    processError(showErrorToasts, R.string.api_update_smses_parse, ex);
                } catch (ClassCastException ex) {
                    processError(showErrorToasts, R.string.api_update_smses_parse, ex);
                }

                cleanup();
            }
        }
    }

    public static Bitmap getCroppedBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        // canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        //Bitmap _bmp = Bitmap.createScaledBitmap(output, 60, 60, false);
        //return _bmp;
        return output;
    }
}
