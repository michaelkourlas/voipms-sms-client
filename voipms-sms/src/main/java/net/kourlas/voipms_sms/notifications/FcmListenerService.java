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

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.preferences.Preferences;

/**
 * Service that processes FCM messages by showing notifications for new SMS
 * messages.
 */
public class FcmListenerService extends FirebaseMessagingService {
    /**
     * Called when a FCM message is received. If notifications are enabled,
     * this method updates the message database and shows notifications for any
     * new messages.
     *
     * @param message The received message.
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Preferences preferences = Preferences.getInstance(
            getApplicationContext());
        if (preferences.getNotificationsEnabled()
            && preferences.getPushNotificationsEnabled())
        {
            Database.getInstance(getApplicationContext())
                    .synchronize(true, false, null, null);
        }
    }
}
