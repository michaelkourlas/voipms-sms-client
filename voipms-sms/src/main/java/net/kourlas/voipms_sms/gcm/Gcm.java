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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import net.kourlas.voipms_sms.Preferences;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class Gcm {
    private static Gcm instance = null;
    private final Context applicationContext;
    private final Preferences preferences;

    private Gcm(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.preferences = Preferences.getInstance(applicationContext);
    }

    public static Gcm getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Gcm(applicationContext);
        }
        return instance;
    }

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
                        String status = (String) result.get("status");
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

    private boolean checkPlayServices(Activity activity, boolean showFeedback) {
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (showFeedback) {
                if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode)) {
                    GoogleApiAvailability.getInstance().getErrorDialog(activity, resultCode, 0).show();
                }
                else {
                    Utils.showInfoDialog(activity, applicationContext.getResources().getString(R.string.gcm_support));
                }
            }
            return false;
        }
        return true;
    }
}
