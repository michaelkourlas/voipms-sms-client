/*
 * Copyright (C) 2017 The Android Open Source Project
 * Modifications copyright (C) 2017-2020 Michael Kourlas
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
import android.view.View.OnClickListener
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceViewHolder
import net.kourlas.voipms_sms.R

open class MasterSwitchPreference : TwoTargetPreference {
    private var switch: SwitchCompat? = null
    private var mChecked: Boolean = false
    private var mEnableSwitch = true

    var isChecked: Boolean
        get() = switch?.isEnabled == true && mChecked
        set(checked) {
            mChecked = checked
            switch?.isChecked = checked
        }

    @Suppress("unused")
    constructor(
        context: Context, attrs: AttributeSet,
        defStyleAttr: Int, defStyleRes: Int
    ) : super(
        context, attrs,
        defStyleAttr,
        defStyleRes
    )

    @Suppress("unused")
    constructor(
        context: Context, attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet) : super(
        context,
        attrs
    )

    constructor(context: Context) : super(context)

    override val secondTargetResId: Int
        get() = R.layout.preference_master_switch

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val widgetView = holder.findViewById(android.R.id.widget_frame)
        widgetView?.setOnClickListener(OnClickListener {
            if (switch?.isEnabled != true) {
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
        switch?.let {
            it.contentDescription = title
            it.isChecked = mChecked
            it.isEnabled = mEnableSwitch
        }
    }

    @Suppress("unused")
    fun setSwitchEnabled(enabled: Boolean) {
        mEnableSwitch = enabled
        switch?.isEnabled = enabled
    }
}