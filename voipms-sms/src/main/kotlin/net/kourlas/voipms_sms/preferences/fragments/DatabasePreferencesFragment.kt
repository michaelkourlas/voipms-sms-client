/*
 * VoIP.ms SMS
 * Copyright (C) 2018-2022 Michael Kourlas
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
import android.content.Context
import android.content.Intent
import android.content.Intent.CATEGORY_OPENABLE
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.preferences.getDids
import net.kourlas.voipms_sms.utils.preferences
import net.kourlas.voipms_sms.utils.showAlertDialog
import net.kourlas.voipms_sms.utils.showSnackbar

/**
 * Fragment used to display the database preferences.
 */
class DatabasePreferencesFragment : PreferenceFragmentCompat() {
    // Preference activity launchers
    private val importActivityResultLauncher =
        registerForActivityResult(
            object : ActivityResultContracts.OpenDocument() {
                override fun createIntent(
                    context: Context,
                    input: Array<String>
                ): Intent {
                    val intent = super.createIntent(context, input)
                    intent.addCategory(CATEGORY_OPENABLE)
                    return intent
                }
            }) {
            if (it != null) {
                import(it)
            }
        }
    private val exportActivityResultLauncher =
        registerForActivityResult(object : CreateDocument(SQLITE_MIME_TYPE) {
            override fun createIntent(
                context: Context,
                input: String
            ): Intent {
                val intent = super.createIntent(context, input)
                intent.type = SQLITE_MIME_TYPE
                return intent
            }
        }) {
            if (it != null) {
                export(it)
            }
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        // Add preferences
        addPreferencesFromResource(R.xml.preferences_database)

        // Assign listeners to preferences
        for (preference in preferenceScreen.preferences) {
            when (preference.key) {
                getString(R.string.preferences_database_import_key) ->
                    preference.onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            try {
                                importActivityResultLauncher.launch(
                                    arrayOf(
                                        SQLITE_MIME_TYPE
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                activity?.let {
                                    showSnackbar(
                                        it,
                                        R.id.coordinator_layout,
                                        getString(
                                            R.string.preferences_database_fail_open_document
                                        )
                                    )
                                }
                            }
                            true
                        }
                getString(
                    R.string.preferences_database_export_key
                ) ->
                    preference.onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            try {
                                exportActivityResultLauncher.launch("sms.db")
                            } catch (_: ActivityNotFoundException) {
                                activity?.let {
                                    showSnackbar(
                                        it,
                                        R.id.coordinator_layout,
                                        getString(
                                            R.string.preferences_database_fail_create_document
                                        )
                                    )
                                }
                            }
                            true
                        }
                getString(
                    R.string.preferences_database_clean_up_key
                ) ->
                    preference.onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            cleanUp()
                            true
                        }
                getString(
                    R.string.preferences_database_delete_key
                ) ->
                    preference.onPreferenceClickListener =
                        Preference.OnPreferenceClickListener {
                            delete()
                            true
                        }
            }
        }
    }

    /**
     * Imports the database located at the specified URI.
     */
    private fun import(uri: Uri) {
        activity?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val importFd = it.contentResolver.openFileDescriptor(
                        uri, "r"
                    )
                        ?: throw Exception("Could not open file")
                    Database.getInstance(it).import(importFd)
                } catch (e: Exception) {
                    ensureActive()

                    lifecycleScope.launch(Dispatchers.Main) {
                        showSnackbar(
                            it,
                            R.id.coordinator_layout,
                            getString(
                                R.string.preferences_database_import_fail,
                                "${e.message} (${e.javaClass.simpleName})"
                            )
                        )
                    }
                    return@launch
                }

                ensureActive()

                lifecycleScope.launch(Dispatchers.Main) {
                    showSnackbar(
                        it, R.id.coordinator_layout, it.getString(
                            R.string.preferences_database_import_success
                        )
                    )
                }
            }
        }
    }

    /**
     * Exports the database to the specified URI.
     */
    private fun export(uri: Uri) {
        activity?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val exportFd = it.contentResolver.openFileDescriptor(
                        uri, "w"
                    )
                        ?: throw Exception("Could not open file")
                    Database.getInstance(it).export(exportFd)
                } catch (e: Exception) {
                    ensureActive()

                    withContext(Dispatchers.Default) {
                        showSnackbar(
                            it,
                            R.id.coordinator_layout,
                            getString(
                                R.string.preferences_database_export_fail,
                                "${e.message} (${e.javaClass.simpleName})"
                            )
                        )

                    }
                    return@launch
                }

                ensureActive()

                withContext(Dispatchers.Default) {
                    showSnackbar(
                        it, R.id.coordinator_layout, it.getString(
                            R.string.preferences_database_export_success
                        )
                    )

                }
            }
        }
    }

    private fun cleanUp() {
        val activity = activity ?: return

        val options = arrayOf(
            activity.getString(
                R.string.preferences_database_clean_up_deleted_messages
            ),
            activity.getString(
                R.string.preferences_database_clean_up_removed_dids
            )
        )
        val selectedOptions = mutableListOf<Int>()

        // Ask user which kind of clean up is desired, and then perform that
        // clean up
        AlertDialog.Builder(activity).apply {
            setTitle(
                context.getString(
                    R.string.preferences_database_clean_up_title
                )
            )
            setMultiChoiceItems(
                options, null
            ) { _, which, isChecked ->
                if (isChecked) {
                    selectedOptions.add(which)
                } else {
                    selectedOptions.remove(which)
                }
            }
            setPositiveButton(
                context.getString(R.string.ok)
            ) { _, _ ->
                val deletedMessages = selectedOptions.contains(0)
                val removedDids = selectedOptions.contains(1)

                lifecycleScope.launch(Dispatchers.Default) {
                    if (deletedMessages) {
                        Database.getInstance(context)
                            .deleteTableDeletedContents()
                    }
                    if (removedDids) {
                        Database.getInstance(context).deleteMessagesWithoutDids(
                            getDids(context)
                        )
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
        showAlertDialog(
            activity,
            activity.getString(
                R.string.preferences_database_delete_confirm_title
            ),
            activity.getString(
                R.string.preferences_database_delete_confirm_message
            ),
            activity.applicationContext
                .getString(R.string.delete),
            { _, _ ->
                lifecycleScope.launch(Dispatchers.Default) {
                    Database.getInstance(
                        activity.applicationContext
                    )
                        .deleteTablesContents()
                }
            },
            activity.getString(R.string.cancel), null
        )
    }

    companion object {
        private const val SQLITE_MIME_TYPE = "application/vnd.sqlite3"
    }
}