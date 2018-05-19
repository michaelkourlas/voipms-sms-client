/*
 * VoIP.ms SMS
 * Copyright (C) 2018 Michael Kourlas
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

import android.app.Activity.RESULT_OK
import android.content.DialogInterface
import android.content.Intent
import android.content.Intent.CATEGORY_OPENABLE
import android.net.Uri
import android.os.Bundle
import android.support.v4.provider.DocumentFile
import android.support.v7.preference.Preference
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.preferences
import net.kourlas.voipms_sms.utils.showAlertDialog
import net.kourlas.voipms_sms.utils.showInfoDialog

/**
 * Fragment used to display the database preferences.
 */
class DatabasePreferencesFragment : PreferenceFragmentCompatDividers() {
    private val importListener = Preference.OnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        intent.addCategory(CATEGORY_OPENABLE)
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
        true
    }

    private val exportListener = Preference.OnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
        true
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?,
                                        rootKey: String?) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_database)

        // Assign handlers to preferences
        for (preference in preferenceScreen.preferences) {
            if (preference.key == getString(
                    R.string.preferences_database_import_key)) {
                preference.onPreferenceClickListener = importListener
            } else if (preference.key == getString(
                    R.string.preferences_database_export_key)) {
                preference.onPreferenceClickListener = exportListener
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        try {
            return super.onCreateView(inflater, container, savedInstanceState)
        } finally {
            setDividerPreferences(DIVIDER_NONE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        if (requestCode == IMPORT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                import(data.data)
            }
        } else if (requestCode == EXPORT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                export(data.data)
            }
        }
    }

    /**
     * Imports the database located at the specified URI.
     */
    private fun import(uri: Uri) {
        val activity = activity ?: return
        try {
            val importFd = activity.contentResolver.openFileDescriptor(
                uri, "r") ?: throw Exception("Could not open file")
            Database.getInstance(activity).import(importFd)
        } catch (e: Exception) {
            showInfoDialog(activity, getString(
                R.string.preferences_database_import_fail),
                           "${e.message} (${e.javaClass.simpleName})")
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
                try {
                    val directory = DocumentFile.fromTreeUri(activity, uri)
                                    ?: throw Exception(
                                        "Could not process directory")
                    val file = directory.createFile(
                        "text/plain",
                        "voipmssms-${System.currentTimeMillis()}")
                               ?: throw Exception("Could not create file")
                    val exportFd = activity.contentResolver.openFileDescriptor(
                        file.uri, "w")
                                   ?: throw Exception("Could not open file")
                    Database.getInstance(activity).export(exportFd)
                } catch (e: Exception) {
                    showInfoDialog(activity, getString(
                        R.string.preferences_database_export_fail),
                                   "${e.message} (${e.javaClass.simpleName})")
                }
            },
            activity.getString(R.string.cancel),
            null)
    }

    companion object {
        // Request codes for file choosers for importing and exporting databases
        const val IMPORT_REQUEST_CODE = 1
        const val EXPORT_REQUEST_CODE = 2
    }
}