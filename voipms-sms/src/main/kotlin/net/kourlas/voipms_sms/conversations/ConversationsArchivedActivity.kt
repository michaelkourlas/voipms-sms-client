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

package net.kourlas.voipms_sms.conversations

import android.support.v7.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.sms.Database
import net.kourlas.voipms_sms.utils.runOnNewThread

/**
 * Activity that contains a list of archived conversations.
 */
class ConversationsArchivedActivity : ConversationsActivity() {
    override fun setupToolbar() {
        super.setupToolbar()

        // Add title to bar and enable up button
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.title = getString(R.string.conversations_archived_name)
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun setupNewConversationButton() {
        // Remove new conversation button
        findViewById<View>(R.id.new_button).visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)

        // Disable most of the menu
        menu.findItem(R.id.archived_button).isVisible = false
        menu.findItem(R.id.preferences_button).isVisible = false
        menu.findItem(R.id.help_button).isVisible = false
        menu.findItem(R.id.privacy_button).isVisible = false
        menu.findItem(R.id.license_button).isVisible = false
        menu.findItem(R.id.credits_button).isVisible = false
        menu.findItem(R.id.donate_button).isVisible = false

        return ret
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val ret = super.onCreateActionMode(mode, menu)

        // Remove the archive button and replace it with an unarchive button
        menu.findItem(R.id.archive_button).isVisible = false
        menu.findItem(R.id.unarchive_button).isVisible = true

        return ret
    }

    override fun onActionItemClicked(mode: ActionMode,
                                     item: MenuItem): Boolean {
        // Only handle the unarchive button; the rest are handled by the
        // superclass
        when (item.itemId) {
            R.id.unarchive_button -> return unarchiveButtonHandler(mode)
        }

        return super.onActionItemClicked(mode, item)
    }

    /**
     * Handles the unarchive button.
     *
     * @param mode The action mode to use.
     * @return Always returns true.
     */
    private fun unarchiveButtonHandler(mode: ActionMode): Boolean {
        runOnNewThread {
            adapter
                .filter { it.checked }
                .forEach {
                    Database.getInstance(applicationContext)
                        .markConversationUnarchived(it.message.conversationId)
                }
            runOnUiThread {
                mode.finish()
                adapter.refresh()
            }
        }
        return true
    }
}
