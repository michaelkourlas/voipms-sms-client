/*
 * Copyright (C) 2017 The Android Open Source Project
 * Modifications copyright (C) 2017-2019 Michael Kourlas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.preferences.controls

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import net.kourlas.voipms_sms.R

open class TwoTargetPreference : Preference {
    protected open val secondTargetResId: Int = 0

    constructor(
        context: Context, attrs: AttributeSet,
        defStyleAttr: Int, defStyleRes: Int
    ) : super(
        context, attrs,
        defStyleAttr,
        defStyleRes
    ) {
        init()
    }

    constructor(
        context: Context, attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        layoutResource = R.layout.preference_two_target
        val secondTargetResId = secondTargetResId
        if (secondTargetResId != 0) {
            widgetLayoutResource = secondTargetResId
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val divider = holder.findViewById(R.id.two_target_divider)
        val widgetFrame = holder.findViewById(android.R.id.widget_frame)
        val shouldHideSecondTarget = secondTargetResId == 0
        if (divider != null) {
            divider.visibility = if (shouldHideSecondTarget) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
        if (widgetFrame != null) {
            widgetFrame.visibility = if (shouldHideSecondTarget) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }
}