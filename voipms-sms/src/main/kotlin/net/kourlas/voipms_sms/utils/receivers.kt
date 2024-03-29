/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2019 Michael Kourlas
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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.IntentFilter
import android.os.Build
import androidx.fragment.app.FragmentActivity

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun registerNonExportedReceiver(
    context: Context,
    receiver: BroadcastReceiver,
    filter: IntentFilter
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
}

/**
 * Unregisters the specified broadcast receiver from the specified activity.
 * Ignores IllegalArgumentExceptions.
 */
fun safeUnregisterReceiver(
    activity: FragmentActivity,
    receiver: BroadcastReceiver
) = try {
    activity.unregisterReceiver(receiver)
} catch (_: IllegalArgumentException) {
    // Do nothing.
}
