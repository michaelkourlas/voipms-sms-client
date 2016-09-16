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

package net.kourlas.voipms_sms.billing;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import com.android.vending.billing.IInAppBillingService;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.utils.Utils;
import org.json.JSONObject;

import java.util.List;

public class Billing {
    private static Billing instance = null;

    private Context applicationContext;
    private IInAppBillingService billingService;

    /**
     * Initializes an instance of the Billing class.
     *
     * @param applicationContext The application context.
     */
    public Billing(Context applicationContext) {
        this.applicationContext = applicationContext;

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name,
                                           IBinder service)
            {
                billingService = IInAppBillingService.Stub.asInterface(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                billingService = null;
            }
        };

        Intent serviceIntent =
            new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        applicationContext.bindService(serviceIntent, serviceConnection,
                                       Context.BIND_AUTO_CREATE);
    }

    /**
     * Gets the sole instance of the Billing class. Initializes the instance
     * if it does not already exist.
     *
     * @return The single instance of the Billing class.
     */
    public synchronized static Billing getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new Billing(applicationContext);
        }
        return instance;
    }

    public void preDonation(final Activity sourceActivity) {
        try {
            Bundle ownedItems = billingService
                .getPurchases(3, applicationContext.getPackageName(), "inapp",
                              null);
            int response = ownedItems.getInt("RESPONSE_CODE");
            if (response == 0) {
                List<String> purchaseDataList =
                    ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                if (purchaseDataList != null) {
                    for (String purchaseData : purchaseDataList) {
                        JSONObject json = new JSONObject(purchaseData);
                        String pid = json.getString("productId");
                        final String token = json.getString("purchaseToken");

                        if (pid.equals(applicationContext.getString(
                            R.string.billing_pid_donation)))
                        {
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... params) {
                                    try {
                                        billingService.consumePurchase(3,
                                                                       sourceActivity
                                                                           .getPackageName(),
                                                                       token);
                                    } catch (Exception ignored) {
                                        // Do nothing.
                                    }
                                    return null;
                                }

                                @Override
                                protected void onPostExecute(Void aVoid) {
                                    showDonationDialog(sourceActivity);
                                }
                            }.execute();
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Do nothing.
        }

        showDonationDialog(sourceActivity);
    }

    private void showDonationDialog(Activity sourceActivity) {
        try {
            Bundle buyIntentBundle = billingService
                .getBuyIntent(3, applicationContext.getPackageName(),
                              applicationContext
                                  .getString(R.string.billing_pid_donation),
                              "inapp", null);
            PendingIntent pendingIntent =
                buyIntentBundle.getParcelable("BUY_INTENT");
            if (pendingIntent != null) {
                sourceActivity
                    .startIntentSenderForResult(pendingIntent.getIntentSender(),
                                                1001, new Intent(), 0, 0, 0);
            } else {
                throw new Exception();
            }
        } catch (Exception ex) {
            Utils.showInfoDialog(sourceActivity, applicationContext
                .getString(R.string.billing_failure));
        }
    }

    public void postDonation(final String token,
                             final Activity sourceActivity)
    {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    billingService
                        .consumePurchase(3, sourceActivity.getPackageName(),
                                         token);
                } catch (Exception ignored) {
                    // Do nothing.
                }
                return null;
            }
        }.execute();
    }
}
