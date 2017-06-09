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

import android.app.DatePickerDialog
import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import android.widget.DatePicker
import java.util.*

/**
 * Preference used to select start date for synchronization.
 */
@Suppress("unused")
class StartDatePreference : Preference, DatePickerDialog.OnDateSetListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onClick() {
        // Show date picker
        val calendar = Calendar.getInstance()
        calendar.time = getStartDate(context)
        DatePickerDialog(context, this, calendar.get(Calendar.YEAR),
                         calendar.get(Calendar.MONTH),
                         calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDateSet(view: DatePicker, year: Int, monthOfYear: Int,
                           dayOfMonth: Int) {
        // Save date from date picker
        val calendar = Calendar.getInstance()
        calendar.set(year, monthOfYear, dayOfMonth)
        setStartDate(context, calendar.time)
    }
}
