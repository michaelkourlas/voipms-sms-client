/*
 * VoIP.ms SMS
 * Copyright (C) 2015 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences;

import android.app.DatePickerDialog;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.DatePicker;

import java.util.Calendar;

@SuppressWarnings("unused")
public class StartDatePreference extends Preference
    implements DatePickerDialog.OnDateSetListener
{
    private Preferences preferences;

    public StartDatePreference(Context context) {
        super(context);
        preferences = Preferences.getInstance(context);
    }

    public StartDatePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = Preferences.getInstance(context);
    }

    public StartDatePreference(Context context, AttributeSet attrs,
                               int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        preferences = Preferences.getInstance(context);
    }

    @Override
    protected void onClick() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(preferences.getStartDate());
        new DatePickerDialog(getContext(), this, calendar.get(Calendar.YEAR),
                             calendar.get(Calendar.MONTH),
                             calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear,
                          int dayOfMonth)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, monthOfYear, dayOfMonth);
        preferences.setStartDate(calendar.getTime());
    }
}
