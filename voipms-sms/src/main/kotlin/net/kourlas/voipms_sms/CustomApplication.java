/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2018 Michael Kourlas
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

package net.kourlas.voipms_sms;

import android.app.Application;

import net.kourlas.voipms_sms.sms.ConversationId;
import net.kourlas.voipms_sms.sms.Database;

import java.util.HashMap;
import java.util.Map;

import static net.kourlas.voipms_sms.utils.FcmKt.subscribeToDidTopics;

/**
 * Custom application implementation that keeps track of visible activities.
 * <p>
 * Kotlin doesn't seem to work at this level in older versions of Android, so
 * this class is implemented in plain old Java.
 */
public class CustomApplication extends Application {
    private final Map<ConversationId, Integer> conversationActivitiesVisible =
        new HashMap<ConversationId, Integer>();
    private int conversationsActivitiesVisible = 0;

    @SuppressWarnings("UnusedReturnValue")
    public boolean conversationsActivityVisible() {
        return conversationsActivitiesVisible > 0;
    }

    public void conversationsActivityIncrementCount() {
        conversationsActivitiesVisible++;
    }

    public void conversationsActivityDecrementCount() {
        conversationsActivitiesVisible--;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean conversationActivityVisible(ConversationId conversationId) {
        Integer count = conversationActivitiesVisible.get(conversationId);
        return count != null && count > 0;
    }

    public void conversationActivityIncrementCount(
        ConversationId conversationId) {
        Integer count = conversationActivitiesVisible.get(conversationId);
        if (count == null) {
            count = 0;
        }
        conversationActivitiesVisible.put(conversationId, count + 1);
    }

    public void conversationActivityDecrementCount(
        ConversationId conversationId) {
        Integer count = conversationActivitiesVisible.get(conversationId);
        if (count == null) {
            count = 0;
        }
        conversationActivitiesVisible.put(conversationId, count - 1);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Open database
        Database.Companion.getInstance(getApplicationContext());

        // Subscribe to topics for current DIDs
        subscribeToDidTopics(getApplicationContext());
    }
}
