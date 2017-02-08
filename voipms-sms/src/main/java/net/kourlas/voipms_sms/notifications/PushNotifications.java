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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.iid.FirebaseInstanceId;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.preferences.CustomSwitchPreference;
import net.kourlas.voipms_sms.preferences.Preferences;
import net.kourlas.voipms_sms.utils.Utils;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * Handles push notification setup. This includes registering for the VoIP.ms
 * callback and Firebase Cloud Messaging.
 */
public class PushNotifications {
    private static PushNotifications instance = null;
    private final Context applicationContext;
    private final Preferences preferences;

    /**
     * Initializes a new instance of the PushNotifications class.
     *
     * @param applicationContext The application context.
     */
    private PushNotifications(Context applicationContext) {
        this.applicationContext = applicationContext;
        this.preferences = Preferences.getInstance(applicationContext);
    }

    /**
     * Gets the sole instance of the PushNotifications class. Initializes the
     * instance if it does not already exist.
     *
     * @param applicationContext The application context.
     * @return The sole instance of the PushNotifications class.
     */
    public static PushNotifications getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new PushNotifications(applicationContext);
        }
        return instance;
    }

    /**
     * Enable push notifications by configuring the VoIP.ms URL callback,
     * registering for FCM and making the appropriate changes to the
     * application preferences.
     *
     * @param activity The source activity.
     */
    public void enablePushNotifications(
        final Activity activity,
        final CustomSwitchPreference preference)
    {
        if (preferences.getEmail().equals("")
            || preferences.getPassword().equals("")
            || preferences.getDid().equals(""))
        {
            Utils.showInfoDialog(activity, applicationContext.getString(
                R.string.notifications_callback_username_password_did));
            preference.setChecked(false);
            preferences.setPushNotificationsEnabled(false);
            return;
        }

        registerForVoipCallback(activity, preference);
    }

    private void registerForVoipCallback(
        final Activity activity,
        final CustomSwitchPreference preference)
    {
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(
            R.string.notifications_callback_progress));
        progressDialog.setCancelable(false);
        progressDialog.show();

        new AsyncTask<Boolean, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Boolean... params) {
                try {
                    String url =
                        "https://www.voip.ms/api/v1/rest.php?"
                        + "api_username=" + URLEncoder.encode(
                            preferences.getEmail(), "UTF-8") + "&"
                        + "api_password=" + URLEncoder.encode(
                            preferences.getPassword(), "UTF-8") + "&"
                        + "method=setSMS" + "&"
                        + "did=" + URLEncoder.encode(preferences.getDid(),
                                                     "UTF-8") + "&"
                        + "enable=1" + "&"
                        + "url_callback_enable=1" + "&"
                        + "url_callback=" + URLEncoder.encode(
                            "http://voipmssms-kourlas.rhcloud.com/"
                            + "sms_callback?did={TO}", "UTF-8") + "&"
                        + "url_callback_retry=0";

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

                DialogInterface.OnClickListener fcmOnClickListener =
                    (dialog, which) -> registerForFcm(activity, preference,
                                                      true, true);

                if (!success) {
                    Utils.showAlertDialog(
                        activity, null,
                        applicationContext.getString(
                            R.string.notifications_callback_fail),
                        applicationContext.getString(R.string.ok),
                        fcmOnClickListener, null, null);
                } else {
                    Utils.showAlertDialog(
                        activity, null,
                        applicationContext.getString(
                            R.string.notifications_callback_success),
                        applicationContext.getString(R.string.ok),
                        fcmOnClickListener, null, null);
                }
            }
        }.execute();
    }

    /**
     * Registers for Firebase Cloud Messaging. Sends the registration token to
     * the application servers.
     *
     * @param activity     The activity that initiated the registration, if any.
     * @param preference   The switch preference that initiated the
     *                     registration, if any.
     * @param showFeedback If true, shows a dialog at the end of the
     *                     registration process indicating the success or
     *                     failure of the process.
     * @param force        If true, retrieves a new registration token even
     *                     if one is already stored.
     */
    public void registerForFcm(
        final Activity activity,
        final CustomSwitchPreference preference,
        final boolean showFeedback,
        boolean force)
    {
        if (!preferences.getPushNotificationsEnabled()) {
            return;
        }
        if (preferences.getDid().equals("")) {
            // Do not show an error; this method should never be called
            // unless a DID is set
            return;
        }
        if (!checkPlayServices(activity, showFeedback)) {
            // Prevent continuous Google Play Services checks
            if (preference != null) {
                preference.setChecked(false);
            }
            preferences.setPushNotificationsEnabled(false);
            return;
        }

        final ProgressDialog progressDialog;
        if (showFeedback) {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage(applicationContext.getString(
                R.string.notifications_fcm_progress));
            progressDialog.setCancelable(false);
            progressDialog.show();
        } else {
            progressDialog = null;
        }
        if (preferences.getFcmToken().equals("") || force) {
            new AsyncTask<Boolean, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Boolean... params) {
                    try {
                        String token = FirebaseInstanceId.getInstance()
                                                         .getToken();
                        String registrationBackendUrl =
                            "https://voipmssms-kourlas.rhcloud.com/register?" +
                            "did=" + URLEncoder
                                .encode(preferences.getDid(), "UTF-8") + "&" +
                            "reg_id=" + URLEncoder.encode(token, "UTF-8");
                        JSONObject result =
                            Utils.getJson(registrationBackendUrl);
                        String status = result.optString("status");
                        if (status == null || !status.equals("success")) {
                            return false;
                        }
                        preferences.setFcmToken(token);
                        return true;
                    } catch (Exception ex) {
                        return false;
                    }
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    if (showFeedback) {
                        progressDialog.hide();
                        if (!success) {
                            Utils.showInfoDialog(activity, applicationContext
                                .getResources().getString(
                                    R.string.notifications_fcm_fail));

                        } else {
                            Utils.showInfoDialog(activity, applicationContext
                                .getResources().getString(
                                    R.string.notifications_fcm_success));
                        }
                    }

                    if (!success) {
                        if (preference != null) {
                            preference.setChecked(false);
                        }
                        preferences.setPushNotificationsEnabled(false);
                    }
                }
            }.execute();
        } else if (showFeedback) {
            Utils.showInfoDialog(activity, applicationContext.getResources()
                                                             .getString(
                                                                 R.string
                                                                     .notifications_fcm_success));
        }
    }

    /**
     * Returns true if Google Play Services is set up properly on the device.
     *
     * @param activity     The activity that initiated the check.
     * @param showFeedback If true, shows a dialog at the end of the check
     *                     indicating the success or failure of the
     *                     process.
     * @return True if Google Play Services is set up properly on the device.
     */
    private boolean checkPlayServices(Activity activity, boolean showFeedback) {
        int resultCode = GoogleApiAvailability.getInstance()
                                              .isGooglePlayServicesAvailable(
                                                  applicationContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (showFeedback) {
                if (GoogleApiAvailability.getInstance()
                                         .isUserResolvableError(resultCode))
                {
                    GoogleApiAvailability.getInstance()
                                         .getErrorDialog(activity, resultCode,
                                                         0).show();
                } else {
                    Utils.showInfoDialog(activity,
                                         applicationContext.getResources()
                                                           .getString(
                                                               R.string
                                                                   .notifications_fcm_play_services));
                }
            }
            return false;
        }
        return true;
    }
}
