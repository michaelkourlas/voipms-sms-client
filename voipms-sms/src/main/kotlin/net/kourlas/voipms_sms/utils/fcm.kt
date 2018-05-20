/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2018 Michael Kourlas
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

package net.kourlas.voipms_sms.utils

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import net.kourlas.voipms_sms.preferences.getDids

/**
 * Subscribes to FCM topics corresponding to the currently configured DIDs.
 *
 * @param context The context to use.
 */
fun subscribeToDidTopics(context: Context) {
    // Do not subscribe to DID topics if Google Play Services is unavailable
    if (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(
                context) != ConnectionResult.SUCCESS) {
        return
    }

    // Generate token but don't do anything with it
    FirebaseInstanceId.getInstance().token

    // Subscribe to topics for current DIDs
    for (did in getDids(context, onlyShowNotifications = true)) {
        FirebaseMessaging.getInstance().subscribeToTopic("did-$did")
    }
}