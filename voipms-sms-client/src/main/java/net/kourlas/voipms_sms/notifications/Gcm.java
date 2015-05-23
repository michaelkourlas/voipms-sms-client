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

package net.kourlas.voipms_sms.notifications;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

public class Gcm {
    private static String SENDER_ID = "626231576786";
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

    private void registerInBackground(final Activity activity) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(applicationContext);

                    String registrationId = gcm.register(SENDER_ID);
                    preferences.setRegistrationId(registrationId);
                    preferences.setRegistrationIdVersion(getAppVersion(applicationContext));

                    return sendRegistrationIdToBackend();
                } catch (IOException ex) {
                    return false;
                } catch (JSONException ex) {
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (!success) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setCancelable(false);
                    builder.setMessage("Google Cloud Messaging registration failed. Please try again later.");
                    builder.setPositiveButton(R.string.ok, null);
                    builder.show();
                }
            }
        }.execute();
    }

    private boolean sendRegistrationIdToBackend() throws IOException, JSONException {
        String registrationBackendUrl = "https://voipmssms-kourlas.rhcloud.com/register?" +
                "did=" + URLEncoder.encode(preferences.getDid()) + "&" +
                "reg_id=" + URLEncoder.encode(preferences.getRegistrationId());
        JSONObject result = Utils.getJson(registrationBackendUrl);
        String status = result.getString("status");
        return status.equals("success");
    }

    public void registerForGcm(Activity activity) {
        if (preferences.getNotificationsEnabled()) {
            if (preferences.getDid().equals("")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage("Google Cloud Messaging registration requires that a DID be set.");
                builder.setPositiveButton(R.string.ok, null);
                builder.show();
            }

            Boolean playServices = Gcm.getInstance(applicationContext).checkPlayServices(activity);
            if (playServices == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setMessage("This device does not support Google Play Services.");
                builder.setPositiveButton(R.string.ok, null);
                builder.show();
            } else if (playServices) {
                String registrationId = Gcm.getInstance(applicationContext).getRegistrationId();
                if (registrationId.isEmpty()) {
                    Gcm.getInstance(applicationContext).registerInBackground(activity);
                }
            }
        }
    }
}
