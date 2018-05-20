/*
 * Copyright (C) 2017 The Android Open Source Project
 * Modifications copyright (C) 2017 Michael Kourlas
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
import android.support.v7.preference.PreferenceViewHolder
import android.support.v7.widget.SwitchCompat
import android.util.AttributeSet
import android.view.View.OnClickListener
import net.kourlas.voipms_sms.R

@Suppress("unused")
open class MasterSwitchPreference : TwoTargetPreference {
    private var switch: SwitchCompat? = null
    private var mChecked: Boolean = false
    private var mEnableSwitch = true

    var isChecked: Boolean
        get() = switch != null && switch!!.isEnabled && mChecked
        set(checked) {
            mChecked = checked
            if (switch != null) {
                switch!!.isChecked = checked
            }
        }

    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int, defStyleRes: Int) : super(context, attrs,
                                                             defStyleAttr,
                                                             defStyleRes)

    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet) : super(context,
                                                               attrs)

    constructor(context: Context) : super(context)

    override val secondTargetResId: Int
        get() = R.layout.preference_master_switch

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val widgetView = holder.findViewById(android.R.id.widget_frame)
        widgetView?.setOnClickListener(OnClickListener {
            if (switch != null && !switch!!.isEnabled) {
                return@OnClickListener
            }
            isChecked = !mChecked
            if (!callChangeListener(mChecked)) {
                isChecked = !mChecked
            } else {
                persistBoolean(mChecked)
            }
        })

        switch = holder.findViewById(R.id.switch_widget) as SwitchCompat?
        if (switch != null) {
            switch!!.contentDescription = title
            switch!!.isChecked = mChecked
            switch!!.isEnabled = mEnableSwitch
        }
    }

    fun setSwitchEnabled(enabled: Boolean) {
        mEnableSwitch = enabled
        if (switch != null) {
            switch!!.isEnabled = enabled
        }
    }
}