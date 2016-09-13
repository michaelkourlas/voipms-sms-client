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

package net.kourlas.voipms_sms;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import net.kourlas.voipms_sms.activities.ConversationActivity;
import net.kourlas.voipms_sms.activities.ConversationQuickReplyActivity;
import net.kourlas.voipms_sms.model.Message;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Api {
    private static final String TAG = "Api";

    /**
     * Sends the SMS message with the specified database ID using the VoIP.ms
     * API.
     *
     * @param sourceActivity The source activity of the send request.
     * @param databaseId     The database ID of the message to send.
     */
    public static void sendMessage(Activity sourceActivity, long databaseId) {
        Context applicationContext = sourceActivity.getApplicationContext();
        Database database = Database.getInstance(applicationContext);
        Preferences preferences = Preferences.getInstance(applicationContext);

        Message message = database.getMessageWithDatabaseId(
            preferences.getDid(), databaseId);
        SendMessageTask task = new SendMessageTask(
            sourceActivity.getApplicationContext(), message,
            sourceActivity);

        if (preferences.getEmail().equals("")
            || preferences.getPassword().equals("")
            || preferences.getDid().equals(""))
        {
            // Do not show an error; this method should never be called
            // unless the email, password and DID are set
            task.cleanup(false);
            return;
        }

        if (!Utils.isNetworkConnectionAvailable(applicationContext)) {
            Toast.makeText(applicationContext,
                           applicationContext.getString(
                               R.string.conversation_send_error_network),
                           Toast.LENGTH_SHORT).show();
            task.cleanup(false);
            return;
        }

        try {
            String voipUrl = "https://www.voip.ms/api/v1/rest.php?"
                             + "api_username=" + URLEncoder.encode(
                preferences.getEmail(), "UTF-8") + "&"
                             + "api_password=" + URLEncoder.encode(
                preferences.getPassword(), "UTF-8") + "&"
                             + "method=sendSMS" + "&"
                             + "did=" + URLEncoder.encode(
                preferences.getDid(), "UTF-8") + "&"
                             + "dst=" + URLEncoder.encode(
                message.getContact(), "UTF-8") + "&"
                             + "message=" + URLEncoder.encode(
                message.getText(), "UTF-8");
            task.start(voipUrl);
        } catch (UnsupportedEncodingException ex) {
            // This should never happen since the encoding (UTF-8) is hardcoded
            throw new Error(ex);
        }
    }

    private static class SendMessageTask {
        private final Context applicationContext;

        private final Message message;
        private final Activity sourceActivity;

        SendMessageTask(Context applicationContext, Message message,
                        Activity sourceActivity)
        {
            this.applicationContext = applicationContext;

            this.message = message;
            this.sourceActivity = sourceActivity;
        }

        void start(String voipUrl) {
            new SendMessageAsyncTask().execute(voipUrl);
        }

        void cleanup(boolean success) {
            if (sourceActivity instanceof ConversationActivity) {
                ((ConversationActivity) sourceActivity)
                    .postSendMessage(success, message.getDatabaseId());
            } else if (sourceActivity instanceof
                ConversationQuickReplyActivity)
            {
                ((ConversationQuickReplyActivity) sourceActivity)
                    .postSendMessage(success, message.getDatabaseId());
            }
        }

        private class SendMessageAsyncTask
            extends AsyncTask<String, String, Boolean>
        {
            @Override
            protected Boolean doInBackground(String... params) {
                JSONObject resultJson;
                try {
                    resultJson = Utils.getJson(params[0]);
                } catch (JSONException ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    publishProgress(applicationContext.getString(
                        R.string.conversation_send_error_api_parse));
                    return false;
                } catch (Exception ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    publishProgress(applicationContext.getString(
                        R.string.conversation_send_error_api_request));
                    return false;
                }

                String status = resultJson.optString("status");
                if (status == null) {
                    publishProgress(applicationContext.getString(
                        R.string.conversation_send_error_api_parse));
                    return false;
                }
                if (!status.equals("success")) {
                    publishProgress(applicationContext.getString(
                        R.string.conversation_send_error_api_error)
                                                      .replace("{error}",
                                                               status));
                    return false;
                }

                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                cleanup(success);
            }

            /**
             * Shows a toast to the user.
             *
             * @param message The message to show. This must be a String
             *                array with a single element containing the
             *                message.
             */
            @Override
            protected void onProgressUpdate(String... message) {
                Toast.makeText(applicationContext, message[0],
                               Toast.LENGTH_SHORT).show();
            }
        }
    }
}
