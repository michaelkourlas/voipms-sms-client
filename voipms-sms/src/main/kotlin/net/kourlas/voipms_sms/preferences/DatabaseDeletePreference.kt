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

package net.kourlas.voipms_sms.preferences

import android.content.Context
import android.content.DialogInterface
import android.preference.Preference
import android.util.AttributeSet
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.showAlertDialog

/**
 * Preference used to delete the entire database.
 */
@Suppress("unused")
class DatabaseDeletePreference : Preference {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onClick() {
        // Prompt the user before actually deleting the entire database
        showAlertDialog(context,
                        context.getString(
                            R.string.preferences_database_delete_confirm_title),
                        context.getString(
                            R.string.preferences_database_delete_confirm_message),
                        context.applicationContext
                            .getString(R.string.delete),
                        DialogInterface.OnClickListener { _, _ ->
                            Database.getInstance(
                                this@DatabaseDeletePreference
                                    .context
                                    .applicationContext)
                                .deleteTablesAll()
                        },
                        context.getString(R.string.cancel), null)
    }
}
