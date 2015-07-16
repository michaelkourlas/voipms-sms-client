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

package net.kourlas.voipms_sms.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import net.kourlas.voipms_sms.Database;
import net.kourlas.voipms_sms.Preferences;

public class SynchronizationIntervalReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Database.getInstance(context.getApplicationContext()).synchronize(false, false, null);
    }

    public static void setupSynchronizationInterval(Context applicationContext)
    {
        Preferences preferences = Preferences.getInstance(applicationContext);
        AlarmManager alarmManager = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(applicationContext, SynchronizationIntervalReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, 0);

        alarmManager.cancel(pendingIntent);

        long syncInterval = preferences.getSyncInterval() * (24 * 60 * 60 * 1000);
        if (syncInterval != 0) {
            long nextSyncTime = preferences.getLastCompleteSyncTime() + syncInterval;

            long now = System.currentTimeMillis();
            if (nextSyncTime <= now) {
                Database.getInstance(applicationContext).synchronize(false, false, null);
                nextSyncTime = now + syncInterval;
            }

            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextSyncTime, syncInterval, pendingIntent);
        }
    }
}
