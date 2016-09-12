/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
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

import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * Service that processes requests for GCM token updates.
 */
public class GcmIdService extends InstanceIDListenerService {

    /**
     * Called when the current GCM token has been invalidated. This method simply re-registers for GCM, obtaining a new
     * token in the process.
     */
    @Override
    public void onTokenRefresh() {
        Gcm.getInstance(getApplicationContext()).registerForGcm(null, false, true);
    }
}
