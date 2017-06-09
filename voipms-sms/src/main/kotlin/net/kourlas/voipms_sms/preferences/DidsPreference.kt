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

import android.app.Activity
import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import net.kourlas.voipms_sms.R

/**
 * Preference item used to retrieve and select DIDs for use with VoIP.ms SMS.
 */
@Suppress("unused")
class DidsPreference : Preference {
    // Reference to PreferencesFragment
    private val fragment = (context as Activity)
        .fragmentManager.findFragmentById(
        R.id.preference_fragment_content) as PreferencesFragment

    // Additional constructors required for use by PreferencesFragment
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int,
                defStyleRes: Int) : super(context, attrs, defStyleAttr,
                                          defStyleRes)

    override fun onClick() {
        // Implementation is in PreferencesFragment
        fragment.retrieveDids()
    }
}
