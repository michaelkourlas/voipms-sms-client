/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2016 Michael Kourlas
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
import android.preference.Preference;
import android.util.AttributeSet;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.db.Database;
import net.kourlas.voipms_sms.utils.Utils;

@SuppressWarnings("unused")
public class DatabaseDeletePreference extends Preference {
    public DatabaseDeletePreference(Context context) {
        super(context);
    }

    public DatabaseDeletePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DatabaseDeletePreference(Context context, AttributeSet attrs,
                                    int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        Utils.showAlertDialog(getContext(), getContext()
                                  .getString(R.string
                                                 .preferences_database_delete_confirm_title),
                              getContext().getString(
                                  R.string
                                      .preferences_database_delete_confirm_message),
                              getContext().getApplicationContext()
                                          .getString(R.string.delete),
                              (dialog, which) -> Database.getInstance(
                                  getContext().getApplicationContext())
                                                         .removeAllMessages(),
                              getContext().getString(R.string.cancel), null);
    }
}