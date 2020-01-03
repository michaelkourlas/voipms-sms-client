/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2020 Michael Kourlas
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

import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.notifications.Notifications
import net.kourlas.voipms_sms.notifications.services.NotificationsRegistrationService
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
                context) != ConnectionResult.SUCCESS) {
        return
    }

    // Subscribe to topics for current DIDs
    for (did in getDids(context, onlyShowNotifications = true)) {
        FirebaseMessaging.getInstance().subscribeToTopic("did-$did")
    }
}

/**
 * Attempt to enable push notifications, checking beforehand whether Google
 * Play Services are available.
 */
fun enablePushNotificationsWithGoogleCheck(activity: FragmentActivity) {
    if (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(
                activity) != ConnectionResult.SUCCESS) {
        showSnackbar(activity, R.id.coordinator_layout,
                     activity.getString(
                         R.string.push_notifications_fail_google_play))
    }
    enablePushNotifications(activity.application)
}

/**
 * Attempt to enable push notifications.
 */
fun enablePushNotifications(application: Application) {
    // Check if DIDs are configured and that notifications are enabled,
    // and silently quit if not
    if (!didsConfigured(application)
        || !Notifications.getInstance(application).getNotificationsEnabled()) {
        setSetupCompletedForVersion(application, 114)
        return
    }

    // Check if Google Play Services is available
    if (GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(
                application) != ConnectionResult.SUCCESS) {
        setSetupCompletedForVersion(application, 114)
        return
    }

    // Subscribe to DID topics
    subscribeToDidTopics(application)

    // Start push notifications registration service
    NotificationsRegistrationService.startService(application)
}
