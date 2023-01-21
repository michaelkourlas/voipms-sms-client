/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2022 Michael Kourlas
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

package net.kourlas.voipms_sms.preferences.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.mukesh.MarkDown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.conversations.ConversationsActivity
import net.kourlas.voipms_sms.utils.abortActivity

/**
 * Activity that houses a Markdown renderer.
 */
class MarkdownPreferencesActivity : AppCompatActivity() {
    private lateinit var documentType: DocumentType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load activity layout
        setContentView(R.layout.preferences_markdown)

        val documentType = DocumentType.fromName(
            intent.getStringExtra(
                applicationContext.getString(R.string.preferences_markdown_extra)
            )
        )
        if (documentType == null) {
            abortActivity(this, Exception("Invalid document type"))
            return
        }
        this.documentType = documentType

        // Configure toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayHomeAsUpEnabled(true)
            it.title = getString(documentType.resourceString)
        }

        val markdown = findViewById<ComposeView>(R.id.compose_view)
        markdown.setContent {
            MarkDown(
                text = applicationContext.assets.open(documentType.fileName)
                    .bufferedReader()
                    .use { it.readText() }
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                // Override standard "up" behaviour because this activity
                // has multiple parents
                lifecycleScope.launch(Dispatchers.Default) {
                    val clazz = if (documentType == DocumentType.HELP) {
                        ConversationsActivity::class.java
                    } else {
                        AboutPreferencesActivity::class.java
                    }

                    ensureActive()

                    withContext(Dispatchers.Main) {
                        val intent = Intent(applicationContext, clazz)
                        // Simulate normal behaviour of up button with these particular
                        // flags
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        enum class DocumentType(val resourceString: Int, val fileName: String) {
            HELP(R.string.conversations_action_help, "HELP.md"),
            CHANGELOG(R.string.preferences_about_changelog_title, "CHANGES.md"),
            LICENSE(R.string.preferences_about_license_title, "LICENSE.md"),
            NOTICE(
                R.string.preferences_about_third_party_embedded_title,
                "NOTICE"
            ),
            PACKAGE_LICENSES(
                R.string.preferences_about_third_party_packages_title,
                "PACKAGE_LICENSES.md"
            ),
            PRIVACY(R.string.preferences_about_privacy_title, "PRIVACY.md");

            companion object {
                fun fromName(name: String?) =
                    values().firstOrNull { it.name == name }
            }
        }
    }
}