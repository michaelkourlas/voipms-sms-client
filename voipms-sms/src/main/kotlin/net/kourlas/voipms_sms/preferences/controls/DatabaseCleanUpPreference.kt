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

package net.kourlas.voipms_sms.preferences.controls

import android.content.Context
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.util.AttributeSet
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.runOnNewThread

/**
 * Preference used to clean up the database.
 */
@Suppress("unused")
class DatabaseCleanUpPreference : Preference {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet,
                defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int,
                defStyleRes: Int) : super(context, attrs, defStyleAttr,
                                          defStyleRes)

    override fun onClick() {
        val options = arrayOf(
            context.getString(
                R.string.preferences_database_clean_up_deleted_messages),
            context.getString(
                R.string.preferences_database_clean_up_removed_dids))
        val selectedOptions = mutableListOf<Int>()

        // Ask user which kind of clean up is desired, and then perform that
        // clean up
        AlertDialog.Builder(context, R.style.DialogTheme).apply {
            setTitle(context.getString(
                R.string.preferences_database_clean_up_title))
            setMultiChoiceItems(
                options, null,
                { _, which, isChecked ->
                    if (isChecked) {
                        selectedOptions.add(which)
                    } else {
                        selectedOptions.remove(which)
                    }
                })
            setPositiveButton(
                context.getString(R.string.ok),
                { _, _ ->
                    val deletedMessages = selectedOptions.contains(0)
                    val removedDids = selectedOptions.contains(1)

                    runOnNewThread {
                        if (deletedMessages) {
                            Database.getInstance(context).deleteTableDeleted()
                        }
                        if (removedDids) {
                            Database.getInstance(context).deleteMessages(
                                getDids(
                                    context))
                        }
                    }
                })
            setNegativeButton(context.getString(R.string.cancel), null)
            setCancelable(false)
            show()
        }
    }
}
