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

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationQuickReplyActivity;
import net.kourlas.voipms_sms.activities.ConversationsActivity;
import net.kourlas.voipms_sms.model.Sms;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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

    /**
     * Gets the sole instance of the Api class. Initializes this instance if it does not already exist.
     *
     * @param applicationContext The application context.
     * @return The sole instance of the Api class.
     */
    public static Api getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Api(applicationContext);
        }
        return instance;
    }

    /**
     * Shows the "Select DIDs" dialog. This method can only be called from a ConversationsActivity.
     *
     * @param conversationsActivity The ConversationsActivity from which this method is called.
     */
    public void showSelectDidDialog(ConversationsActivity conversationsActivity) {
        SelectDidTask task = new SelectDidTask(conversationsActivity);

        if (preferences.getEmail().equals("")) {
            processError(true, R.string.api_update_did_email, null, null);
        } else if (preferences.getPassword().equals("")) {
            processError(true, R.string.api_update_did_password, null, null);
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getDIDsInfo";
                task.start(voipUrl);
            } catch (UnsupportedEncodingException ex) {
                processError(true, R.string.api_update_did_other, null,
                        Log.getStackTraceString(ex));
            }
        } else {
            processError(true, R.string.api_update_did_network, null, null);
        }
    }

    public void deleteSms(Activity sourceActivity, long smsId) {
        DeleteSmsTask task = new DeleteSmsTask(sourceActivity, smsId);

        if (preferences.getEmail().equals("")) {
            processError(true, R.string.api_delete_sms_email, null, null);
        } else if (preferences.getPassword().equals("")) {
            processError(true, R.string.api_delete_sms_password, null, null);
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=deleteSMS" + "&" +
                        "id=" + smsId;
                task.start(voipUrl);
                return;
            } catch (UnsupportedEncodingException ex) {
                processError(true, R.string.api_delete_sms_other, null,
                        Log.getStackTraceString(ex));
            }
        } else {
            processError(true, R.string.api_delete_sms_network, null, null);
        }

        task.cleanup();
    }

    public void sendSms(Activity sourceActivity, String contact, String message) {
        SendSmsTask task = new SendSmsTask(sourceActivity);

        if (preferences.getEmail().equals("")) {
            processError(true, R.string.api_send_sms_email, null, null);
        } else if (preferences.getPassword().equals("")) {
            processError(true, R.string.api_send_sms_password, null, null);
        } else if (preferences.getDid().equals("")) {
            processError(true, R.string.api_send_sms_did, null, null);
        } else if (isNetworkConnectionAvailable()) {
            try {
                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=sendSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "dst=" + URLEncoder.encode(contact, "UTF-8") + "&" +
                        "message=" + URLEncoder.encode(message, "UTF-8");
                task.start(voipUrl, new Sms(Sms.ID_NULL, Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis()/1000,
                        0, preferences.getDid(), contact, message, 0)); //Time in seconds
                return;
            } catch (UnsupportedEncodingException ex) {
                processError(true, R.string.api_send_sms_other, null, Log.getStackTraceString(ex));
            }
        } else {
            processError(true, R.string.api_send_sms_network, null, null);
        }

        task.cleanup(false, Sms.ID_NULL);
    }

    public void updateSmsDatabase(Activity sourceActivity, boolean showErrors, boolean showNotifications) {
        UpdateSmsDatabaseTask task = new UpdateSmsDatabaseTask(sourceActivity, showErrors, showNotifications);

        if (preferences.getEmail().equals("")) {
            processError(showErrors, R.string.api_update_smses_email, null, null);
        } else if (preferences.getPassword().equals("")) {
            processError(showErrors, R.string.api_update_smses_password, null, null);
        } else if (preferences.getDid().equals("")) {
            processError(showErrors, R.string.api_update_smses_did, null, null);
        } else if (isNetworkConnectionAvailable()) {
            try {
                Calendar calendarThen = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                calendarThen.add(Calendar.DAY_OF_YEAR, -preferences.getDaysToSync());
                Date then = calendarThen.getTime();

                Date now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "limit=" + URLEncoder.encode("1000000", "UTF-8") + "&" +
                        "from=" + URLEncoder.encode(sdf.format(then), "UTF-8") + "&" +
                        "to=" + URLEncoder.encode(sdf.format(now), "UTF-8") + "&" +
                        "timezone=-1";
                task.start(voipUrl);
                return;
            } catch (UnsupportedEncodingException ex) {
                processError(showErrors, R.string.api_update_smses_other, null, Log.getStackTraceString(ex));
            }
        } else {
            processError(showErrors, R.string.api_update_smses_network, null, null);
        }

        task.cleanup();
    }

    public void getReceivedSms(Activity sourceActivity, boolean showErrors, boolean showNotifications) {
        GetReceivedSmsTask task = new GetReceivedSmsTask(sourceActivity, showErrors, showNotifications);

        if (preferences.getEmail().equals("")) {
            processError(showErrors, R.string.api_update_smses_email, null, null);
        } else if (preferences.getPassword().equals("")) {
            processError(showErrors, R.string.api_update_smses_password, null, null);
        } else if (preferences.getDid().equals("")) {
            processError(showErrors, R.string.api_update_smses_did, null, null);
        } else if (isNetworkConnectionAvailable()) {
            try {
                Date then = new Date(preferences.getLastSync());

                Date now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                        "api_username=" + URLEncoder.encode(preferences.getEmail(), "UTF-8") + "&" +
                        "api_password=" + URLEncoder.encode(preferences.getPassword(), "UTF-8") + "&" +
                        "method=getSMS" + "&" +
                        "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                        "limit=" + URLEncoder.encode("1000000", "UTF-8") + "&" +
                        "from=" + URLEncoder.encode(sdf.format(then), "UTF-8") + "&" +
                        "to=" + URLEncoder.encode(sdf.format(now), "UTF-8") + "&" +
                        "type=" + URLEncoder.encode("1", "UTF-8") + "&" +
                        "timezone=-1";
                task.start(voipUrl);
                return;
            } catch (UnsupportedEncodingException ex) {
                processError(showErrors, R.string.api_update_smses_other, null, Log.getStackTraceString(ex));
            }
        } else {
            processError(showErrors, R.string.api_update_smses_network, null, null);
        }

        task.cleanup();
    }

    public void writeSmsDatabase(Activity sourceActivity, Sms sms) {
        if (!Database.getInstance(applicationContext).smsExists(sms.getId())) {
            Database.getInstance(applicationContext).addSms(sms);
        }

        if (sourceActivity instanceof ConversationsActivity) {
            ((ConversationsActivity) sourceActivity).postUpdate();
        } else if (sourceActivity instanceof ConversationActivity) {
            ((ConversationActivity) sourceActivity).postUpdate();
        } else if (sourceActivity == null) {
            if (App.getInstance().getCurrentActivity() instanceof ConversationsActivity) {
                ((ConversationsActivity) App.getInstance().getCurrentActivity()).postUpdate();
            } else if (App.getInstance().getCurrentActivity() instanceof ConversationActivity) {
                ((ConversationActivity) App.getInstance().getCurrentActivity()).postUpdate();
            }
        }
    }

    private boolean isNetworkConnectionAvailable() {
        ConnectivityManager connMgr = (ConnectivityManager) applicationContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void processError(boolean showToast, int errorResource, String errorExtension, String logExtension) {
        String toastText;
        if (errorExtension != null) {
            toastText = applicationContext.getString(errorResource) + errorExtension + ")";
        } else {
            toastText = applicationContext.getString(errorResource);
        }

        if (showToast) {
            Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show();
        }

        Log.w(TAG, toastText);
        if (logExtension != null) {
            Log.w(TAG, logExtension);
        }
    }

    private class AsyncTaskResultObject {
        private final JSONObject jsonObject;
        private final Integer messageResource;
        private final Exception exception;

        public AsyncTaskResultObject(JSONObject jsonObject, Integer messageResource, Exception exception) {
            this.jsonObject = jsonObject;
            this.messageResource = messageResource;
            this.exception = exception;
        }

        public JSONObject getJsonObject() {
            return jsonObject;
        }

        public Integer getMessageResource() {
            return messageResource;
        }

        public Exception getException() {
            return exception;
        }
    }

    private class SelectDidTask {
        private final ConversationsActivity conversationsActivity;

        public SelectDidTask(ConversationsActivity conversationsActivity) {
            this.conversationsActivity = conversationsActivity;
        }

        public void start(String voipUrl) {
            new UpdateDidAsyncTask().execute(voipUrl);
        }

        private class UpdateDidAsyncTask extends AsyncTask<String, Void, AsyncTaskResultObject> {
            @Override
            protected AsyncTaskResultObject doInBackground(String... params) {
                try {
                    return new AsyncTaskResultObject(Utils.getJson(params[0]), null, null);
                } catch (org.json.simple.parser.ParseException ex) {
                    return new AsyncTaskResultObject(null, R.string.api_update_did_parse, ex);
                } catch (Exception ex) {
                    return new AsyncTaskResultObject(null, R.string.api_update_did_request, ex);
                }
            }

            @Override
            protected void onPostExecute(AsyncTaskResultObject resultObject) {
                if (resultObject.getJsonObject() == null) {
                    processError(true, resultObject.getMessageResource(), null,
                            Log.getStackTraceString(resultObject.getException()));
                    return;
                }

                String status = (String) resultObject.getJsonObject().get("status");
                if (status == null) {
                    processError(true, R.string.api_update_did_parse, null, null);
                    return;
                }
                if (!status.equals("success")) {
                    processError(true, R.string.api_update_did_api, status, null);
                    return;
                }

                final List<String> dids = new ArrayList<>();

                JSONArray rawDids = (JSONArray) resultObject.getJsonObject().get("dids");
                if (rawDids == null) {
                    processError(true, R.string.api_update_did_parse, null, null);
                    return;
                }
                for (Object rawDidObj : rawDids) {
                    JSONObject rawDid = (JSONObject) rawDidObj;
                    if (rawDid == null || rawDid.get("sms_available") == null || rawDid.get("did") == null ||
                            !(rawDid.get("did") instanceof String)) {
                        processError(true, R.string.api_update_did_parse, null, null);
                        return;
                    }
                    if (rawDid.get("sms_available").equals("1")) {
                        if (rawDid.get("sms_enabled") == null) {
                            processError(true, R.string.api_update_did_parse, null, null);
                            return;
                        }
                        if (rawDid.get("sms_enabled").equals("1")) {
                            dids.add((String) rawDid.get("did"));
                        }
                    }
                }

                if (dids.size() == 0) {
                    processError(true, R.string.api_update_did_no_dids, null, null);
                    return;
                }

                final String[] didsArray = new String[dids.size()];
                dids.toArray(didsArray);
                for (int i = 0; i < didsArray.length; i++) {
                    didsArray[i] = Utils.getFormattedPhoneNumber(didsArray[i]);
                }

                conversationsActivity.showSelectDidDialog(didsArray);
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

        public void cleanup() {
            if (sourceActivity instanceof ConversationsActivity) {
                ((ConversationsActivity) sourceActivity).postDeleteSms();
            } else if (sourceActivity instanceof ConversationActivity) {
                ((ConversationActivity) sourceActivity).postDeleteSms();
            }
        }

        private class DeleteSmsAsyncTask extends AsyncTask<String, Void, AsyncTaskResultObject> {
            @Override
            protected AsyncTaskResultObject doInBackground(String... params) {
                try {
                    return new AsyncTaskResultObject(Utils.getJson(params[0]), null, null);
                } catch (org.json.simple.parser.ParseException ex) {
                    return new AsyncTaskResultObject(null, R.string.api_delete_sms_parse, ex);
                } catch (Exception ex) {
                    return new AsyncTaskResultObject(null, R.string.api_delete_sms_request, ex);
                }
            }

            @Override
            protected void onPostExecute(AsyncTaskResultObject resultObject) {
                if (resultObject.getJsonObject() == null) {
                    processError(true, resultObject.getMessageResource(), null,
                            Log.getStackTraceString(resultObject.getException()));
                    cleanup();
                    return;
                }

                String status = (String) resultObject.getJsonObject().get("status");
                if (status == null) {
                    processError(true, R.string.api_delete_sms_parse, null, null);
                    cleanup();
                    return;
                }
                if (!status.equals("success")) {
                    processError(true, R.string.api_delete_sms_api, status, null);
                    cleanup();
                    return;
                }

                Database.getInstance(applicationContext).deleteSMS(smsId);
                cleanup();
            }
        }
    }

    public class SendSmsTask {
        private final Activity sourceActivity;
        private Sms sms;

        public SendSmsTask(Activity sourceActivity) {
            this.sourceActivity = sourceActivity;
        }

        public void start(String voipUrl, Sms sms) {
            this.sms = sms;
            new SendSmsAsyncTask().execute(voipUrl);
        }

        public void cleanup(boolean success, long id) {
            sms.setId(id);
            if (sourceActivity instanceof ConversationActivity) {
                ((ConversationActivity) sourceActivity).postSendSms(success, sms);
            } else if (sourceActivity instanceof ConversationQuickReplyActivity) {
                ((ConversationQuickReplyActivity) sourceActivity).postSendSms(success);
            }
        }

        private class SendSmsAsyncTask extends AsyncTask<String, Void, AsyncTaskResultObject> {
            @Override
            protected AsyncTaskResultObject doInBackground(String... params) {
                try {
                    return new AsyncTaskResultObject(Utils.getJson(params[0]), null, null);
                } catch (org.json.simple.parser.ParseException ex) {
                    return new AsyncTaskResultObject(null, R.string.api_send_sms_parse, ex);
                } catch (Exception ex) {
                    return new AsyncTaskResultObject(null, R.string.api_send_sms_request, ex);
                }
            }

            @Override
            protected void onPostExecute(AsyncTaskResultObject resultObject) {
                if (resultObject.getJsonObject() == null) {
                    processError(true, resultObject.getMessageResource(), null,
                            Log.getStackTraceString(resultObject.getException()));
                    cleanup(false, Sms.ID_NULL);
                    return;
                }

                String status = (String) resultObject.getJsonObject().get("status");
                long id = (long) resultObject.getJsonObject().get("sms");
                if (status == null) {
                    processError(true, R.string.api_send_sms_parse, null, null);
                    cleanup(false, Sms.ID_NULL);
                    return;
                }
                if (!status.equals("success")) {
                    processError(true, R.string.api_send_sms_api, status, null);
                    cleanup(false, Sms.ID_NULL);
                    return;
                }

                cleanup(true, id);
            }
        }
    }

    private class UpdateSmsDatabaseTask {
        private final Activity sourceActivity;
        private final boolean showErrors;
        private final boolean showNotifications;

        public UpdateSmsDatabaseTask(Activity sourceActivity, boolean showErrors, boolean showNotifications) {
            this.sourceActivity = sourceActivity;
            this.showErrors = showErrors;
            this.showNotifications = showNotifications;
        }

        public void start(String voipUrl) {
            new UpdateSmsDatabaseAsyncTask().execute(voipUrl);
        }

        public void cleanup() {
            if (sourceActivity instanceof ConversationsActivity) {
                ((ConversationsActivity) sourceActivity).postUpdate();
            } else if (sourceActivity instanceof ConversationActivity) {
                ((ConversationActivity) sourceActivity).postUpdate();
            } else if (sourceActivity == null) {
                if (App.getInstance().getCurrentActivity() instanceof ConversationsActivity) {
                    ((ConversationsActivity) App.getInstance().getCurrentActivity()).postUpdate();
                } else if (App.getInstance().getCurrentActivity() instanceof ConversationActivity) {
                    ((ConversationActivity) App.getInstance().getCurrentActivity()).postUpdate();
                }
            }
        }

        private class UpdateSmsDatabaseAsyncTask extends AsyncTask<String, Void, AsyncTaskResultObject> {
            @Override
            protected AsyncTaskResultObject doInBackground(String... params) {
                try {
                    return new AsyncTaskResultObject(Utils.getJson(params[0]), null, null);
                } catch (org.json.simple.parser.ParseException ex) {
                    return new AsyncTaskResultObject(null, R.string.api_update_smses_parse, ex);
                } catch (Exception ex) {
                    return new AsyncTaskResultObject(null, R.string.api_update_smses_request, ex);
                }
            }

            @Override
            protected void onPostExecute(AsyncTaskResultObject resultObject) {
                if (resultObject.getJsonObject() == null) {
                    processError(true, resultObject.getMessageResource(), null,
                            Log.getStackTraceString(resultObject.getException()));
                    cleanup();
                    return;
                }

                String status = (String) resultObject.getJsonObject().get("status");
                if (status == null) {
                    processError(showErrors, R.string.api_update_smses_parse, null, null);
                    cleanup();
                    return;
                }
                if (!status.equals("success")) {
                    processError(showErrors, R.string.api_update_smses_api, status, null);
                    cleanup();
                    return;
                }

                List<Sms> smses = new ArrayList<>();

                JSONArray rawSmses = (JSONArray) resultObject.getJsonObject().get("sms");
                if (rawSmses == null) {
                    processError(showErrors, R.string.api_update_smses_parse, null, null);
                    cleanup();
                    return;
                }

                for (Object rawSmsObj : rawSmses) {
                    JSONObject rawSms = (JSONObject) rawSmsObj;
                    if (rawSms == null || rawSms.get("id") == null || rawSms.get("date") == null ||
                            rawSms.get("type") == null || rawSms.get("did") == null || rawSms.get("contact") == null ||
                            rawSms.get("message") == null || !(rawSms.get("id") instanceof String) ||
                            !(rawSms.get("date") instanceof String) || !(rawSms.get("type") instanceof String) ||
                            !(rawSms.get("did") instanceof String) || !(rawSms.get("contact") instanceof String) ||
                            !(rawSms.get("message") instanceof String)) {
                        processError(true, R.string.api_update_did_parse, null, null);
                        cleanup();
                        return;
                    }

                    String id = (String) rawSms.get("id");
                    String date = (String) rawSms.get("date");
                    String type = (String) rawSms.get("type");
                    String did = (String) rawSms.get("did");
                    String contact = (String) rawSms.get("contact");
                    String message = (String) rawSms.get("message");

                    try {
                        Sms sms = new Sms(id, date, type, did, contact, message);
                        smses.add(sms);
                    } catch (ParseException ex) {
                        processError(showErrors, R.string.api_update_did_parse, null, Log.getStackTraceString(ex));
                        cleanup();
                        return;
                    }
                }

                // Remove SMSes that have been deleted from the server
                Sms[] oldSmses = Database.getInstance(applicationContext).getAllSmses();
                for (Sms sms : oldSmses) {
                    if (!smses.contains(sms)) {
                        Database.getInstance(applicationContext).deleteSMS(sms.getId());
                    }
                }

                // Add new SMSes from the server
                List<Sms> newSmses = new ArrayList<>();
                for (Sms sms : smses) {
                    if (!Database.getInstance(applicationContext).smsExists(sms.getId())) {
                        newSmses.add(sms);
                        Database.getInstance(applicationContext).addSms(sms);
                    }
                }

                // Show notifications for new SMSes
                if (showNotifications) {
                    Notifications.getInstance(applicationContext).showNotification(newSmses);
                }

                cleanup();
            }
        }
    }

    private class GetReceivedSmsTask {
        private final Activity sourceActivity;
        private final boolean showErrors;
        private final boolean showNotifications;

        public GetReceivedSmsTask(Activity sourceActivity, boolean showErrors, boolean showNotifications) {
            this.sourceActivity = sourceActivity;
            this.showErrors = showErrors;
            this.showNotifications = showNotifications;
        }

        public void start(String voipUrl) {
            new GetReceivedSmsAsyncTask().execute(voipUrl);
        }

        public void cleanup() {
            if (sourceActivity instanceof ConversationsActivity) {
                ((ConversationsActivity) sourceActivity).postUpdate();
            } else if (sourceActivity instanceof ConversationActivity) {
                ((ConversationActivity) sourceActivity).postUpdate();
            } else if (sourceActivity == null) {
                if (App.getInstance().getCurrentActivity() instanceof ConversationsActivity) {
                    ((ConversationsActivity) App.getInstance().getCurrentActivity()).postUpdate();
                } else if (App.getInstance().getCurrentActivity() instanceof ConversationActivity) {
                    ((ConversationActivity) App.getInstance().getCurrentActivity()).postUpdate();
                }
            }
        }

        private class GetReceivedSmsAsyncTask extends AsyncTask<String, Void, AsyncTaskResultObject> {
            @Override
            protected AsyncTaskResultObject doInBackground(String... params) {
                try {
                    return new AsyncTaskResultObject(Utils.getJson(params[0]), null, null);
                } catch (org.json.simple.parser.ParseException ex) {
                    return new AsyncTaskResultObject(null, R.string.api_update_smses_parse, ex);
                } catch (Exception ex) {
                    return new AsyncTaskResultObject(null, R.string.api_update_smses_request, ex);
                }
            }

            @Override
            protected void onPostExecute(AsyncTaskResultObject resultObject) {
                if (resultObject.getJsonObject() == null) {
                    processError(true, resultObject.getMessageResource(), null,
                            Log.getStackTraceString(resultObject.getException()));
                    cleanup();
                    return;
                }

                String status = (String) resultObject.getJsonObject().get("status");
                if (status == null) {
                    processError(showErrors, R.string.api_update_smses_parse, null, null);
                    cleanup();
                    return;
                }
                if (!status.equals("success")) {
                    processError(showErrors, R.string.api_update_smses_api, status, null);
                    cleanup();
                    return;
                }

                List<Sms> receivedSmses = new ArrayList<>();

                JSONArray rawSmses = (JSONArray) resultObject.getJsonObject().get("sms");
                if (rawSmses == null) {
                    processError(showErrors, R.string.api_update_smses_parse, null, null);
                    cleanup();
                    return;
                }

                for (Object rawSmsObj : rawSmses) {
                    JSONObject rawSms = (JSONObject) rawSmsObj;
                    if (rawSms == null || rawSms.get("id") == null || rawSms.get("date") == null ||
                            rawSms.get("type") == null || rawSms.get("did") == null || rawSms.get("contact") == null ||
                            rawSms.get("message") == null || !(rawSms.get("id") instanceof String) ||
                            !(rawSms.get("date") instanceof String) || !(rawSms.get("type") instanceof String) ||
                            !(rawSms.get("did") instanceof String) || !(rawSms.get("contact") instanceof String) ||
                            !(rawSms.get("message") instanceof String)) {
                        processError(true, R.string.api_update_did_parse, null, null);
                        cleanup();
                        return;
                    }

                    String id = (String) rawSms.get("id");
                    String date = (String) rawSms.get("date");
                    String type = (String) rawSms.get("type");
                    String did = (String) rawSms.get("did");
                    String contact = (String) rawSms.get("contact");
                    String message = (String) rawSms.get("message");

                    try {
                        Sms sms = new Sms(id, date, type, did, contact, message);
                        receivedSmses.add(sms);
                    } catch (ParseException ex) {
                        processError(showErrors, R.string.api_update_did_parse, null, Log.getStackTraceString(ex));
                        cleanup();
                        return;
                    }
                }

                /* Remove SMSes that have been deleted from the server since the last GCM sync
                 This is necessary when GCM was turned off momentarily and deletions occurred at the server
                */
                Sms[] oldSmses = Database.getInstance(applicationContext).getReceivedSmses(preferences.getLastSync());
                for (Sms sms : oldSmses) {
                    if (!receivedSmses.contains(sms)) {
                        Database.getInstance(applicationContext).deleteSMS(sms.getId());
                    }
                }

                // Add new SMSes from the server
                List<Sms> newSmses = new ArrayList<>();
                for (Sms sms : receivedSmses) {
                    if (!Database.getInstance(applicationContext).smsExists(sms.getId())) {
                        newSmses.add(sms);
                        Database.getInstance(applicationContext).addSms(sms);
                    }
                }

                // Sets the new lastSync field
                preferences.setLastSync(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());

                // Show notifications for new SMSes
                if (showNotifications) {
                    Notifications.getInstance(applicationContext).showNotification(newSmses);
                }

                cleanup();
            }
        }
    }
}
