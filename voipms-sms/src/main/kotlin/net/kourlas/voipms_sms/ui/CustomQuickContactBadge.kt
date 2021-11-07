/*
 * VoIP.ms SMS
 * Copyright (C) 2020 Michael Kourlas
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

package net.kourlas.voipms_sms.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.QuickContactBadge
import android.widget.Toast
import net.kourlas.voipms_sms.R

class CustomQuickContactBadge : QuickContactBadge {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(
        context,
        attrs
    )

    constructor(
        context: Context?, attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(
        context: Context?, attrs: AttributeSet?, defStyleAttr: Int,
        defStyleRes: Int
    ) : super(
        context, attrs, defStyleAttr,
        defStyleRes
    )

    override fun onClick(v: View) {
        try {
            super.onClick(v)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(
                context, context.getString(
                    R.string.conversations_no_contact_app
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}