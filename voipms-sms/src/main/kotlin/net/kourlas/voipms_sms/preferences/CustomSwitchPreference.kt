/*
 * VoIP.ms SMS
 * Copyright (C) 2017 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences

import android.content.Context
import android.preference.SwitchPreference
import android.util.AttributeSet

/**
 * Custom switch preference created to work around bug documented in
 * https://code.google.com/p/android/issues/detail?id=26194.
 *
 * This can be removed once pre-Lollipop versions of Android are no longer
 * supported.
 */
@Suppress("unused")
class CustomSwitchPreference : SwitchPreference {
    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet) : super(
        context, attrs, android.R.attr.switchPreferenceStyle)

    constructor(context: Context) : super(context)
}
