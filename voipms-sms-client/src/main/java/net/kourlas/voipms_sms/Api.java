/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.kourlas.voipms_sms;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.NavUtils;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    private Api(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.preferences = Preferences.getInstance(applicationContext);
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
                } catch (JSONException ex) {
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
                    String status = result.getString("status");
                    if (status.equals("success")) {
                        final List<String> dids = new ArrayList<String>();
                        JSONArray rawDids = result.getJSONArray("dids");
                        for (int i = 0; i < rawDids.length(); i++) {
                            if (rawDids.getJSONObject(i).getString("sms_available").equals("1") &&
                                    rawDids.getJSONObject(i).getString("sms_enabled").equals("1")) {
                                dids.add(rawDids.getJSONObject(i).getString("did"));
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

                            AlertDialog.Builder builder = new AlertDialog.Builder(applicationContext);
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
                                        }
                                    });
                            builder.show();
                        }
                    } else {
                        processError(R.string.api_update_did_api, status);
                    }
                } catch (JSONException ex) {
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
                } catch (JSONException ex) {
                    processError(R.string.api_delete_sms_parse, ex);
                }

                return null;
            }

            @Override
            protected void onPostExecute(JSONObject result) {
                try {
                    String status = result.getString("status");
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
                } catch (JSONException ex) {
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
            }
        }

        private class SendSmsAsyncTask extends AsyncTask<String, Void, JSONObject> {
            @Override
            protected JSONObject doInBackground(String... params) {
                try {
                    return Utils.getJson(params[0]);
                } catch (IOException ex) {
                    processError(R.string.api_send_sms_request, ex);
                } catch (JSONException ex) {
                    processError(R.string.api_send_sms_parse, ex);
                }

                cleanup();

                return null;
            }

            @Override
            protected void onPostExecute(JSONObject result) {
                try {
                    String status = result.getString("status");
                    if (status.equals("success")) {
                        updateSmsDatabase(sourceActivity, true, false);
                        return;
                    } else {
                        processError(R.string.api_send_sms_api, status);
                    }
                } catch (JSONException ex) {
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
            }
        }

        private class UpdateSmsDatabaseAsyncTask extends AsyncTask<String, Void, JSONObject> {
            @Override
            protected JSONObject doInBackground(String... params) {
                try {
                    return Utils.getJson(params[0]);
                } catch (IOException ex) {
                    processError(showErrorToasts, R.string.api_update_smses_request, ex);
                } catch (JSONException ex) {
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
                    String status = result.getString("status");
                    if (status.equals("success")) {
                        // Process API response
                        List<Sms> smses = new ArrayList<Sms>();
                        JSONArray rawSmses = result.getJSONArray("sms");
                        for (int i = 0; i < rawSmses.length(); i++) {
                            String id = rawSmses.getJSONObject(i).getString("id");
                            String date = rawSmses.getJSONObject(i).getString("date");
                            String type = rawSmses.getJSONObject(i).getString("type");
                            String did = rawSmses.getJSONObject(i).getString("did");
                            String contact = rawSmses.getJSONObject(i).getString("contact");
                            String message = rawSmses.getJSONObject(i).getString("message");

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
                                    long smsId = 0;
                                    Sms[] smsArray = conversation.getAllSms();
                                    for (Sms sms : smsArray) {
                                        if (sms.getType() == Sms.Type.INCOMING && sms.isUnread()) {
                                            smsText = smsText + "\n" + sms.getMessage();
                                            if (smsId == 0) {
                                                smsText = sms.getMessage();
                                                smsId = sms.getId();
                                            }
                                        } else {
                                            break;
                                        }
                                    }

                                    NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                                            applicationContext);
                                    notificationBuilder.setSmallIcon(R.drawable.ic_message_white_18dp);
                                    notificationBuilder.setContentTitle(smsContact);
                                    notificationBuilder.setContentText(smsText);
                                    notificationBuilder.setDefaults(Notification.DEFAULT_ALL);

                                    Intent intent = new Intent(applicationContext, ConversationActivity.class);
                                    intent.putExtra("contact", conversation.getContact());

                                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(applicationContext);
                                    stackBuilder.addParentStack(ConversationActivity.class);
                                    stackBuilder.addNextIntent(intent);
                                    PendingIntent resultPendingIntent =
                                            stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

                                    notificationBuilder.setContentIntent(resultPendingIntent);
                                    notificationBuilder.setAutoCancel(true);

                                    NotificationManager notificationManager = (NotificationManager)
                                            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                                    notificationManager.notify((int) smsId, notificationBuilder.build());
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
                } catch (JSONException ex) {
                    processError(showErrorToasts, R.string.api_update_smses_parse, ex);
                } catch (ParseException ex) {
                    processError(showErrorToasts, R.string.api_update_smses_parse, ex);
                }

                cleanup();
            }
        }
    }
}
