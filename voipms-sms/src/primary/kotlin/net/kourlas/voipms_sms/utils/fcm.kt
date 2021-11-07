/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
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
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.notifications.workers.NotificationsRegistrationWorker
import net.kourlas.voipms_sms.preferences.didsConfigured
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.preferences.setSetupCompletedForVersion

/**
 * Subscribes to FCM topics corresponding to the currently configured DIDs.
 */
fun subscribeToDidTopics(context: Context) {
    // Do not subscribe to DID topics if Google Play Services is unavailable
    if (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(
                context
            ) != ConnectionResult.SUCCESS
    ) {
        return
    }

    // Subscribe to topics for current DIDs
    for (did in getDids(context, onlyShowNotifications = true)) {
        FirebaseMessaging.getInstance().subscribeToTopic("did-$did")
    }
}

/**
 * Attempt to enable push notifications, with the option of showing an error
 * using a snackbar on the specified activity if Google Play Services is
 * unavailable.
 */
fun enablePushNotifications(
    context: Context,
    activityToShowError: FragmentActivity? = null
) {
    // Check if Google Play Services is available
    if (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(
                context
            ) != ConnectionResult.SUCCESS
    ) {
        if (activityToShowError != null) {
            showSnackbar(
                activityToShowError, R.id.coordinator_layout,
                activityToShowError.getString(
                    R.string.push_notifications_fail_google_play
                )
            )
        }
        setSetupCompletedForVersion(context, 134)
        return
    }

    // Check if DIDs are configured and that notifications are enabled,
    // and silently quit if not
    if (!didsConfigured(context)
        || !Notifications.getInstance(context).getNotificationsEnabled()
    ) {
        setSetupCompletedForVersion(context, 134)
        return
    }

    // Subscribe to DID topics
    subscribeToDidTopics(context)

    // Start push notifications registration service
    NotificationsRegistrationWorker.registerForPushNotifications(context)
}
