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

/**
 * Stub for F-Droid builds.
 */
fun getInstallationId(): String {
    return "Not supported"
}

/**
 * Stub for F-Droid builds.
 */
@Suppress("UNUSED_PARAMETER")
fun subscribeToDidTopics(context: Context) {
    // Do nothing.
}

/**
 * Stub for F-Droid builds.
 */
@Suppress("UNUSED_PARAMETER")
fun enablePushNotifications(
    context: Context,
    activityToShowError: FragmentActivity? = null
) {
    // Do nothing.
}