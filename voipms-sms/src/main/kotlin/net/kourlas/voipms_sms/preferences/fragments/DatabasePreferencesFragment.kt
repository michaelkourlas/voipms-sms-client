/*
 * VoIP.ms SMS
 * Copyright (C) 2018-2019 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.CATEGORY_OPENABLE
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.preference.Preference
import com.takisoft.preferencex.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.preferences
import net.kourlas.voipms_sms.utils.runOnNewThread
import net.kourlas.voipms_sms.utils.showAlertDialog
import net.kourlas.voipms_sms.utils.showSnackbar

/**
 * Fragment used to display the database preferences.
 */
class DatabasePreferencesFragment : PreferenceFragmentCompat() {
    // Preference listeners
    private val importListener = Preference.OnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        intent.addCategory(CATEGORY_OPENABLE)
        try {
            startActivityForResult(intent,
                                   IMPORT_REQUEST_CODE)
        } catch (_: ActivityNotFoundException) {
            activity?.let {
                showSnackbar(
                    it, R.id.coordinator_layout,
                    getString(
                        R.string.preferences_database_fail_open_document))
            }
        }
        true
    }
    private val exportListener = Preference.OnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.type = "application/octet-stream"
        try {
            startActivityForResult(intent,
                                   EXPORT_REQUEST_CODE)
        } catch (_: ActivityNotFoundException) {
            activity?.let {
                showSnackbar(
                    it, R.id.coordinator_layout,
                    getString(
                        R.string.preferences_database_fail_create_document))
            }
        }
        true
    }
    private val cleanUpListener = Preference.OnPreferenceClickListener {
        cleanUp()
        true
    }
    private val deleteListener = Preference.OnPreferenceClickListener {
        delete()
        true
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_database)

        // Assign handlers to preferences
        for (preference in preferenceScreen.preferences) {
            when (preference.key) {
                getString(
                    R.string.preferences_database_import_key) ->
                    preference.onPreferenceClickListener = importListener
                getString(
                    R.string.preferences_database_export_key) ->
                    preference.onPreferenceClickListener = exportListener
                getString(
                    R.string.preferences_database_clean_up_key) ->
                    preference.onPreferenceClickListener = cleanUpListener
                getString(
                    R.string.preferences_database_delete_key) ->
                    preference.onPreferenceClickListener = deleteListener
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        // Handle ACTION_OPEN_DOCUMENT and ACTION_CREATE_DOCUMENT results
        data?.data?.let {
            if (requestCode == IMPORT_REQUEST_CODE
                && resultCode == RESULT_OK) {
                import(it)
            } else if (requestCode == EXPORT_REQUEST_CODE
                       && resultCode == RESULT_OK) {
                export(it)
            }
        }

    }

    /**
     * Imports the database located at the specified URI.
     */
    private fun import(uri: Uri) {
        activity?.let {
            runOnNewThread {
                try {
                    val importFd = it.contentResolver.openFileDescriptor(
                        uri, "r") ?: throw Exception("Could not open file")
                    Database.getInstance(it).import(importFd)
                    it.runOnUiThread {
                        showSnackbar(it, R.id.coordinator_layout, it.getString(
                            R.string.preferences_database_import_success))
                    }
                } catch (e: Exception) {
                    it.runOnUiThread {
                        showSnackbar(
                            it,
                            R.id.coordinator_layout,
                            getString(
                                R.string.preferences_database_import_fail,
                                "${e.message} (${e.javaClass.simpleName})"))
                    }
                }
            }
        }
    }

    /**
     * Exports the database to the specified URI.
     */
    private fun export(uri: Uri) {
        activity?.let {
            runOnNewThread {
                try {
                    val exportFd = it.contentResolver.openFileDescriptor(
                        uri, "w") ?: throw Exception("Could not open file")
                    Database.getInstance(it).export(exportFd)

                    it.runOnUiThread {
                        showSnackbar(it, R.id.coordinator_layout, it.getString(
                            R.string.preferences_database_export_success))
                    }
                } catch (e: Exception) {
                    it.runOnUiThread {
                        showSnackbar(
                            it,
                            R.id.coordinator_layout,
                            getString(
                                R.string.preferences_database_export_fail,
                                "${e.message} (${e.javaClass.simpleName})"))
                    }
                }
            }
        }
    }

    private fun cleanUp() {
        val activity = activity ?: return

        val options = arrayOf(
            activity.getString(
                R.string.preferences_database_clean_up_deleted_messages),
            activity.getString(
                R.string.preferences_database_clean_up_removed_dids))
        val selectedOptions = mutableListOf<Int>()

        // Ask user which kind of clean up is desired, and then perform that
        // clean up
        AlertDialog.Builder(activity).apply {
            setTitle(context.getString(
                R.string.preferences_database_clean_up_title))
            setMultiChoiceItems(
                options, null) { _, which, isChecked ->
                if (isChecked) {
                    selectedOptions.add(which)
                } else {
                    selectedOptions.remove(which)
                }
            }
            setPositiveButton(
                context.getString(R.string.ok)) { _, _ ->
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
            }
            setNegativeButton(context.getString(R.string.cancel), null)
            setCancelable(false)
            show()
        }
    }

    private fun delete() {
        val activity = activity ?: return

        // Prompt the user before actually deleting the entire database
        showAlertDialog(activity,
                        activity.getString(
                            R.string.preferences_database_delete_confirm_title),
                        activity.getString(
                            R.string.preferences_database_delete_confirm_message),
                        activity.applicationContext
                            .getString(R.string.delete),
                        { _, _ ->
                            runOnNewThread {
                                Database.getInstance(
                                    activity.applicationContext)
                                    .deleteTablesAll()
                            }
                        },
                        activity.getString(R.string.cancel), null)
    }

    companion object {
        // Request codes for file choosers for importing and exporting databases
        const val IMPORT_REQUEST_CODE = 1
        const val EXPORT_REQUEST_CODE = 2
    }
}