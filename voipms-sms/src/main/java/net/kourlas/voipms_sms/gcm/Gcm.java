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

package net.kourlas.voipms_sms.gcm;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import net.kourlas.voipms_sms.Preferences;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import org.json.JSONObject;

import java.net.URLEncoder;

/**
 * Handles registration for Google Cloud Messaging, which is necessary for push notifications.
 */
public class Gcm {
    private static Gcm instance = null;
    private final Context applicationContext;
    private final Preferences preferences;

    /**
     * Initializes a new instance of the GCM class.
     *
     * @param applicationContext The application context.
     */
    private Gcm(Context applicationContext) {
        this.applicationContext = applicationContext;
        this.preferences = Preferences.getInstance(applicationContext);
    }

    /**
     * Gets the sole instance of the GCM class. Initializes the instance if it does not already exist.
     *
     * @param applicationContext The application context.
     * @return The sole instance of the GCM class.
     */
    public static Gcm getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Gcm(applicationContext);
        }
        return instance;
    }

    /**
     * Registers for Google Cloud Messaging. Sends the registration token to the application servers.
     *
     * @param activity     The activity that initiated the registration.
     * @param showFeedback If true, shows a dialog at the end of the registration process indicating the success or
     *                     failure of the process.
     * @param force        If true, retrieves a new registration token even if one is already stored.
     */
    public void registerForGcm(final Activity activity, final boolean showFeedback, boolean force) {
        if (!preferences.getNotificationsEnabled()) {
            return;
        }
        if (preferences.getDid().equals("")) {
            if (showFeedback) {
                Utils.showInfoDialog(activity, applicationContext.getResources().getString(R.string.gcm_did));
            }
            return;
        }
        if (!checkPlayServices(activity, showFeedback)) {
            return;
        }

        final InstanceID instanceIdObj = InstanceID.getInstance(applicationContext);
        final String instanceId = instanceIdObj.getId();
        if (preferences.getGcmToken().equals("") || !instanceId.equals(preferences.getGcmInstanceId()) || force) {
            new AsyncTask<Boolean, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Boolean... params) {
                    try {
                        String token = instanceIdObj.getToken(applicationContext.getString(R.string.gcm_sender_id),
                                GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

                        String registrationBackendUrl = "https://voipmssms-kourlas.rhcloud.com/register?" +
                                "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                                "reg_id=" + URLEncoder.encode(token, "UTF-8");
                        JSONObject result = Utils.getJson(registrationBackendUrl);
                        String status = result.optString("status");
                        if (status == null || !status.equals("success")) {
                            return false;
                        }

                        preferences.setGcmInstanceId(instanceId);
                        preferences.setGcmToken(token);

                        return true;
                    } catch (Exception ex) {
                        return false;
                    }
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    if (showFeedback) {
                        if (!success) {
                            Utils.showInfoDialog(activity, applicationContext.getResources().getString(
                                    R.string.gcm_fail));
                        }
                        else {
                            Utils.showInfoDialog(activity, applicationContext.getResources().getString(
                                    R.string.gcm_success));
                        }
                    }
                }
            }.execute();
        }
        else if (showFeedback) {
            Utils.showInfoDialog(activity, applicationContext.getResources().getString(R.string.gcm_success));
        }
    }

    /**
     * Returns true if Google Play Services is set up properly on the device.
     *
     * @param activity     The activity that initiated the check.
     * @param showFeedback If true, shows a dialog at the end of the check indicating the success or failure of the
     *                     process.
     * @return True if Google Play Services is set up properly on the device.
     */
    private boolean checkPlayServices(Activity activity, boolean showFeedback) {
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (showFeedback) {
                if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode)) {
                    GoogleApiAvailability.getInstance().getErrorDialog(activity, resultCode, 0).show();
                }
                else {
                    Utils.showInfoDialog(activity, applicationContext.getResources().getString(R.string.gcm_play_services));
                }
            }
            return false;
        }
        return true;
    }
}
