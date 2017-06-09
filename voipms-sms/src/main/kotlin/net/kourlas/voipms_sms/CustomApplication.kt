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

package net.kourlas.voipms_sms

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.support.multidex.MultiDexApplication
import net.kourlas.voipms_sms.conversation.ConversationActivity
import net.kourlas.voipms_sms.conversations.ConversationsActivity
import net.kourlas.voipms_sms.conversations.ConversationsArchivedActivity
import net.kourlas.voipms_sms.sms.ConversationId
import java.lang.ref.WeakReference

/**
 * Custom application implementation that keeps track of visible activities.
 */
class CustomApplication : MultiDexApplication(),
    Application.ActivityLifecycleCallbacks {
    private val conversationsActivitiesVisible =
        mutableSetOf<WeakReference<ConversationsActivity>>()
    private val conversationsArchivedActivitiesVisible =
        mutableSetOf<WeakReference<ConversationsArchivedActivity>>()
    private val conversationActivitiesVisible =
        mutableSetOf<WeakReference<ConversationActivity>>()

    /**
     * Returns true if the conversation activity is visible.
     *
     * @return True if the conversation activity is visible.
     */
    fun conversationsActivityVisible(): Boolean {
        return conversationsActivitiesVisible
                   .filter { it.get() != null }
                   .isNotEmpty()
               || conversationsArchivedActivitiesVisible
                   .filter { it.get() != null }
                   .isNotEmpty()
    }

    /**
     * Returns true if the conversation activity for the specified conversation
     * ID is visible.
     *
     * @param conversationId The specified conversation ID.
     * @return True if the conversation activity for the specified conversation
     * ID is visible.
     */
    fun conversationActivityVisible(
        conversationId: ConversationId): Boolean {
        return conversationActivitiesVisible
            .filter { it.get()?.conversationId == conversationId }
            .isNotEmpty()
    }

    override fun onCreate() {
        super.onCreate()

        // Register for activity lifecycle callbacks
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity?,
                                   savedInstanceState: Bundle?) {
        // Do nothing.
    }

    override fun onActivityStarted(activity: Activity?) {
        // Do nothing.
    }

    override fun onActivityResumed(activity: Activity?) {
        // Add weak references for activity
        if (activity is ConversationsActivity) {
            conversationsActivitiesVisible.add(WeakReference(activity))
        } else if (activity is ConversationsArchivedActivity) {
            conversationsArchivedActivitiesVisible.add(WeakReference(activity))
        } else if (activity is ConversationActivity) {
            conversationActivitiesVisible.add(WeakReference(activity))
        }

        // Clear invalidated weak references
        conversationsActivitiesVisible.removeIf { it.get() == null }
        conversationActivitiesVisible.removeIf { it.get() == null }
    }

    override fun onActivityPaused(activity: Activity?) {
        // Remove weak references for activity
        if (activity is ConversationsActivity) {
            conversationsActivitiesVisible.removeIf { it.get() == activity }
        } else if (activity is ConversationActivity) {
            conversationActivitiesVisible.removeIf { it.get() == activity }
        }

        // Clear invalidated weak references
        conversationsActivitiesVisible.removeIf { it.get() == null }
        conversationActivitiesVisible.removeIf { it.get() == null }
    }

    override fun onActivityStopped(activity: Activity?) {
        // Do nothing.
    }

    override fun onActivityDestroyed(activity: Activity?) {
        // Do nothing.
    }

    override fun onActivitySaveInstanceState(activity: Activity?,
                                             outState: Bundle?) {
        // Do nothing.
    }
}
