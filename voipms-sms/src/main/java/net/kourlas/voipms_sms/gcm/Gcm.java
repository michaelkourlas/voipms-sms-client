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
import android.support.v7.app.AlertDialog;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import net.kourlas.voipms_sms.Preferences;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.Utils;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class Gcm {
    private static final String SENDER_ID = "626231576786";
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

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    private String getRegistrationId() {
        String registrationId = preferences.getRegistrationId();
        if (registrationId.isEmpty()) {
            return "";
        }

        int registeredVersion = preferences.getRegistrationIdVersion();
        int currentVersion = getAppVersion(applicationContext);
        if (registeredVersion != currentVersion) {
            return "";
        }

        return registrationId;
    }

    private Boolean checkPlayServices(Activity activity) {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(applicationContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, activity, 9000).show();
            } else {
                return null;
            }
            return false;
        }
        return true;
    }

    private void registerInBackground(final Activity activity, final boolean showFeedback) {
        new AsyncTask<Boolean, Void, List<Object>>() {
            @Override
            protected List<Object> doInBackground(Boolean... params) {
                List<Object> list = new ArrayList<Object>();
                list.add(showFeedback);

                try {
                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(applicationContext);

                    String registrationId = gcm.register(SENDER_ID);
                    sendRegistrationIdToBackend(registrationId);

                    preferences.setRegistrationId(registrationId);
                    preferences.setRegistrationIdVersion(getAppVersion(applicationContext));

                    list.add(true);
                    return list;
                } catch (IOException ex) {
                    list.add(false);
                    return list;
                } catch (ParseException ex) {
                    list.add(false);
                    return list;
                } catch (ClassCastException ex) {
                    list.add(false);
                    return list;
                }
            }

            @Override
            protected void onPostExecute(List<Object> list) {
                boolean showFeedback = (Boolean) list.get(0);
                boolean success = (Boolean) list.get(1);

                if (!success) {
                    if (showFeedback) {
                        showDialog(activity, applicationContext.getResources().getString(R.string.gcm_fail));
                    }
                } else {
                    if (showFeedback) {
                        showDialog(activity, applicationContext.getResources().getString(R.string.gcm_success));

                    }
                }
            }
        }.execute();
    }

    private boolean sendRegistrationIdToBackend(String registrationId) throws IOException, ParseException {
        String registrationBackendUrl = "https://voipmssms-kourlas.rhcloud.com/register?" +
                "did=" + URLEncoder.encode(preferences.getDid(), "UTF-8") + "&" +
                "reg_id=" + URLEncoder.encode(registrationId, "UTF-8");
        JSONObject result = Utils.getJson(registrationBackendUrl);
        String status = (String) result.get("status");
        return status.equals("success");
    }

    public void registerForGcm(Activity activity, boolean showFeedback, boolean force) {
        if (preferences.getNotificationsEnabled()) {
            if (preferences.getDid().equals("")) {
                if (showFeedback) {
                    showDialog(activity, applicationContext.getResources().getString(R.string.gcm_did));
                }
                return;
            }

            Boolean playServices = Gcm.getInstance(applicationContext).checkPlayServices(activity);
            if (playServices == null) {
                if (showFeedback) {
                    showDialog(activity, applicationContext.getResources().getString(R.string.gcm_support));
                }
            } else if (playServices) {
                String registrationId = Gcm.getInstance(applicationContext).getRegistrationId();
                if (registrationId.isEmpty() || force) {
                    Gcm.getInstance(applicationContext).registerInBackground(activity, showFeedback);
                } else {
                    if (showFeedback) {
                        showDialog(activity, applicationContext.getResources().getString(R.string.gcm_success));
                    }
                }
            }
        }
    }

    private void showDialog(Activity activity, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(text);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }
}
