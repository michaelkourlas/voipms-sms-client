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

import android.content.DialogInterface
import android.content.Intent
import android.content.Intent.CATEGORY_OPENABLE
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.takisoft.preferencex.PreferenceFragmentCompat
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.preferences
import net.kourlas.voipms_sms.utils.runOnNewThread
import net.kourlas.voipms_sms.utils.showAlertDialog
import net.kourlas.voipms_sms.utils.showInfoDialog

/**
 * Fragment used to display the database preferences.
 */
class DatabasePreferencesFragment : PreferenceFragmentCompat() {
    private val importListener = Preference.OnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        intent.addCategory(CATEGORY_OPENABLE)
        startActivityForResult(intent,
                               IMPORT_REQUEST_CODE)
        true
    }

    private val exportListener = Preference.OnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent,
                               EXPORT_REQUEST_CODE)
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
            when {
                preference.key == getString(
                    R.string.preferences_database_import_key) -> preference.onPreferenceClickListener = importListener
                preference.key == getString(
                    R.string.preferences_database_export_key) -> preference.onPreferenceClickListener = exportListener
                preference.key == getString(
                    R.string.preferences_database_clean_up_key) -> preference.onPreferenceClickListener = cleanUpListener
                preference.key == getString(
                    R.string.preferences_database_delete_key) -> preference.onPreferenceClickListener = deleteListener
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        val d = data?.data
        if (requestCode == IMPORT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && d != null) {
                import(d)
            }
        } else if (requestCode == EXPORT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && d != null) {
                export(d)
            }
        }
    }

    /**
     * Imports the database located at the specified URI.
     */
    private fun import(uri: Uri) {
        val activity = activity ?: return
        runOnNewThread {
            try {
                val importFd = activity.contentResolver.openFileDescriptor(
                    uri, "r") ?: throw Exception("Could not open file")
                Database.getInstance(activity).import(importFd)
            } catch (e: Exception) {
                activity.runOnUiThread {
                    showInfoDialog(activity, getString(
                        R.string.preferences_database_import_fail),
                                   "${e.message} (${e.javaClass.simpleName})")
                }
            }
        }
    }

    /**
     * Exports the database to the directory at the specified URI.
     */
    private fun export(uri: Uri) {
        val activity = activity ?: return

        val exportFilename = "voipmssms-${System.currentTimeMillis()}"

        showAlertDialog(
            activity,
            activity.getString(
                R.string.preferences_database_export_confirm_title),
            activity.getString(
                R.string.preferences_database_export_confirm_text,
                exportFilename),
            activity.getString(R.string.ok),
            DialogInterface.OnClickListener { _, _ ->
                runOnNewThread {
                    try {
                        val directory = DocumentFile.fromTreeUri(
                            activity, uri)
                                        ?: throw Exception(
                                            "Could not process directory")
                        val file = directory.createFile(
                            "text/plain",
                            "voipmssms-${System.currentTimeMillis()}")
                                   ?: throw Exception("Could not create file")
                        val exportFd = activity.contentResolver
                                           .openFileDescriptor(file.uri, "w")
                                       ?: throw Exception("Could not open file")
                        Database.getInstance(activity).export(exportFd)
                    } catch (e: Exception) {
                        activity.runOnUiThread {
                            showInfoDialog(
                                activity,
                                getString(
                                    R.string.preferences_database_export_fail),
                                   "${e.message} (${e.javaClass.simpleName})")
                        }
                    }
                }
            },
            activity.getString(R.string.cancel),
            null)
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
        MaterialAlertDialogBuilder(activity).apply {
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
                        DialogInterface.OnClickListener { _, _ ->
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