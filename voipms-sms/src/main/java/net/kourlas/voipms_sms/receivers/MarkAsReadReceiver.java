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

package net.kourlas.voipms_sms.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.notifications.Notifications;
import net.kourlas.voipms_sms.preferences.Preferences;

public class MarkAsReadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Database database =
            Database.getInstance(context.getApplicationContext());
        Preferences preferences =
            Preferences.getInstance(context.getApplicationContext());
        String contact = intent.getExtras().getString(
            context.getString(R.string.conversation_extra_contact));

        NotificationManager manager =
            (NotificationManager) context.getApplicationContext()
                                         .getSystemService(
                                             Context.NOTIFICATION_SERVICE);
        Integer notificationId =
            Notifications.getInstance(context.getApplicationContext())
                         .getNotificationIds().get(
                contact);
        if (notificationId != null) {
            manager.cancel(notificationId);
        }

        database.markConversationAsRead(preferences.getDid(), contact);
    }
}
