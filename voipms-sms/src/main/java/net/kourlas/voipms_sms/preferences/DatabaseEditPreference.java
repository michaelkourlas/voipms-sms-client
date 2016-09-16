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

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;
import net.kourlas.voipms_sms.activities.EditDatabaseActivity;

@SuppressWarnings("unused")
public class DatabaseEditPreference extends Preference {
    public DatabaseEditPreference(Context context) {
        super(context);
    }

    public DatabaseEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DatabaseEditPreference(Context context, AttributeSet attrs,
                                  int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        Intent preferencesIntent =
            new Intent(getContext(), EditDatabaseActivity.class);
        getContext().startActivity(preferencesIntent);
    }
}
