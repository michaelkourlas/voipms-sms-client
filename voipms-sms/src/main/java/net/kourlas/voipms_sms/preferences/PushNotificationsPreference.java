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

import android.app.Activity;
import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import net.kourlas.voipms_sms.notifications.PushNotifications;

@SuppressWarnings("unused")
public class PushNotificationsPreference extends SwitchPreference {

    public PushNotificationsPreference(Context context, AttributeSet attrs,
                                       int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    public PushNotificationsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PushNotificationsPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        super.onClick();

        PushNotifications pushNotifications = PushNotifications.getInstance(
            getContext().getApplicationContext());
        pushNotifications.enablePushNotifications((Activity) getContext(), this);
    }
}
