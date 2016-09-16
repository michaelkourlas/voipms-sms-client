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

package net.kourlas.voipms_sms.preferences;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.preference.Preference;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.utils.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class DidPreference extends Preference {
    private static final String TAG = "DidPreference";

    private final Context applicationContext;
    private final Preferences preferences;
    private ProgressDialog progressDialog;

    public DidPreference(Context context) {
        super(context);
        applicationContext = getContext().getApplicationContext();
        preferences =
            Preferences.getInstance(getContext().getApplicationContext());
    }

    public DidPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        applicationContext = getContext().getApplicationContext();
        preferences =
            Preferences.getInstance(getContext().getApplicationContext());
    }

    public DidPreference(Context context, AttributeSet attrs,
                         int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        applicationContext = getContext().getApplicationContext();
        preferences =
            Preferences.getInstance(getContext().getApplicationContext());
    }

    @Override
    protected void onClick() {
        progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage(getContext().getApplicationContext()
                                              .getString(
                                                  R.string
                                                      .preferences_account_did_status));
        progressDialog.setCancelable(false);
        progressDialog.show();

        SelectDidTask task = new SelectDidTask(this);

        if (preferences.getEmail().equals("")) {
            task.cleanup(false, null, applicationContext
                .getString(R.string.preferences_account_did_error_email));
            return;
        }

        if (preferences.getPassword().equals("")) {
            task.cleanup(false, null, applicationContext
                .getString(R.string.preferences_account_did_error_password));
            return;
        }

        if (!Utils.isNetworkConnectionAvailable(applicationContext)) {
            task.cleanup(false, null, applicationContext
                .getString(R.string.preferences_account_did_error_network));
            return;
        }

        try {
            String voipUrl = "https://www.voip.ms/api/v1/rest.php?" +
                             "api_username=" + URLEncoder
                                 .encode(preferences.getEmail(), "UTF-8") + "&"
                             +
                             "api_password=" + URLEncoder
                                 .encode(preferences.getPassword(), "UTF-8")
                             + "&" +
                             "method=getDIDsInfo";
            task.start(voipUrl);
        } catch (UnsupportedEncodingException ex) {
            // This should never happen since the encoding (UTF-8) is hardcoded
            throw new Error(ex);
        }
    }

    private void showSelectDidDialog(boolean success, final String[] dids,
                                     String message)
    {
        if (progressDialog != null) {
            progressDialog.hide();
            progressDialog = null;
        }

        if (success) {
            AlertDialog.Builder builder =
                new AlertDialog.Builder(getContext(), R.style.DialogTheme);
            builder.setTitle(getContext().getString(
                R.string.preferences_account_did_dialog_title));
            builder.setItems(dids,
                             (dialog, which) -> Preferences
                                 .getInstance(getContext())
                                 .setDid(dids[which].replaceAll("[^0-9]", "")));
            builder.show();
        } else {
            Utils.showInfoDialog(getContext(), message);
        }
    }

    private static class SelectDidTask {
        private final Context applicationContext;
        private final DidPreference didPreference;

        SelectDidTask(DidPreference didPreference) {
            this.applicationContext =
                didPreference.getContext().getApplicationContext();
            this.didPreference = didPreference;
        }

        public void start(String voipUrl) {
            new UpdateDidAsyncTask().execute(voipUrl);
        }

        private void cleanup(boolean success, String[] dids, String message) {
            didPreference.showSelectDidDialog(success, dids, message);
        }

        /**
         * Class used to return data from the background component of the
         * AsyncTask. This class is necessary because
         * the doInBackground method of the AsyncTask object can only return
         * a single object.
         */
        private static class ResultObject {
            private final JSONObject jsonObject;
            private final String errorMessage;

            /**
             * Initializes a new instance of the ResultObject class. Used if
             * the VoIP.ms API request was successful.
             *
             * @param jsonObject The JSON object representing the data
             *                   returned from the VoIP.ms API.
             */
            ResultObject(JSONObject jsonObject) {
                this.jsonObject = jsonObject;
                this.errorMessage = null;
            }

            /**
             * Initializes a new instance of the ResultObject class. Used if
             * the VoIP.ms API request was not successful.
             *
             * @param errorMessage The error message to display after a
             *                     failed request to the VoIP.ms API.
             */
            ResultObject(String errorMessage) {
                this.jsonObject = null;
                this.errorMessage = errorMessage;
            }

            /**
             * Gets the JSON object representing the data returned from the
             * VoIP.ms API.
             *
             * @return The JSON object representing the data returned from
             * the VoIP.ms API.
             */
            JSONObject getJsonObject() {
                return jsonObject;
            }

            /**
             * Gets the error message to display after a failed request to
             * the VoIP.ms API.
             *
             * @return The error message to display after a failed request to
             * the VoIP.ms API.
             */
            String getErrorMessage() {
                return errorMessage;
            }
        }

        private class UpdateDidAsyncTask
            extends AsyncTask<String, Void, ResultObject>
        {
            @Override
            protected ResultObject doInBackground(String... params) {
                try {
                    return new ResultObject(Utils.getJson(params[0]));
                } catch (JSONException ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    return new ResultObject(applicationContext.getString(
                        R.string.preferences_account_did_error_api_parse));
                } catch (Exception ex) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                    return new ResultObject(applicationContext.getString(
                        R.string.preferences_account_did_error_api_request));
                }
            }

            @Override
            protected void onPostExecute(ResultObject resultObject) {
                if (resultObject.getJsonObject() == null) {
                    cleanup(false, null, resultObject.getErrorMessage());
                    return;
                }

                String status =
                    resultObject.getJsonObject().optString("status");
                if (status == null) {
                    cleanup(false, null,
                            applicationContext.getString(
                                R.string
                                    .preferences_account_did_error_api_parse));
                    return;
                }
                if (!status.equals("success")) {
                    cleanup(false, null, applicationContext.getString(
                        R.string.preferences_account_did_error_api_error)
                                                           .replace("{error}",
                                                                    status));
                    return;
                }

                final List<String> dids = new ArrayList<>();

                JSONArray rawDids =
                    resultObject.getJsonObject().optJSONArray("dids");
                if (rawDids == null) {
                    cleanup(false, null,
                            applicationContext.getString(
                                R.string
                                    .preferences_account_did_error_api_parse));
                    return;
                }
                for (int i = 0; i < rawDids.length(); i++) {
                    JSONObject rawDid = rawDids.optJSONObject(i);
                    if (rawDid == null
                        || rawDid.optString("sms_available") == null ||
                        rawDid.optString("did") == null)
                    {
                        cleanup(false, null,
                                applicationContext.getString(
                                    R.string
                                        .preferences_account_did_error_api_parse));
                        return;
                    }
                    if (rawDid.optString("sms_available").equals("1")) {
                        if (rawDid.optString("sms_enabled") == null) {
                            cleanup(false, null,
                                    applicationContext.getString(
                                        R.string
                                            .preferences_account_did_error_api_parse));
                            return;
                        }
                        if (rawDid.optString("sms_enabled").equals("1")) {
                            dids.add(rawDid.optString("did"));
                        }
                    }
                }

                if (dids.size() == 0) {
                    cleanup(false, null, applicationContext.getString(
                        R.string.preferences_account_did_error_no_dids));
                    return;
                }

                final String[] didsArray = new String[dids.size()];
                dids.toArray(didsArray);
                for (int i = 0; i < didsArray.length; i++) {
                    didsArray[i] = Utils.getFormattedPhoneNumber(didsArray[i]);
                }

                cleanup(true, didsArray, null);
            }
        }
    }
}
