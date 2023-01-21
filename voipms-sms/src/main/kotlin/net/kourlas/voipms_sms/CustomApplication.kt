/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2021 Michael Kourlas
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

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import net.kourlas.voipms_sms.billing.Billing
import net.kourlas.voipms_sms.database.Database
import net.kourlas.voipms_sms.network.NetworkManager.Companion.getInstance
import net.kourlas.voipms_sms.preferences.fragments.AppearancePreferencesFragment
import net.kourlas.voipms_sms.preferences.getAppTheme
import net.kourlas.voipms_sms.preferences.getSyncInterval
import net.kourlas.voipms_sms.preferences.migrateLegacySecurePreferences
import net.kourlas.voipms_sms.preferences.setRawSyncInterval
import net.kourlas.voipms_sms.sms.ConversationId
import net.kourlas.voipms_sms.sms.workers.SyncWorker
import net.kourlas.voipms_sms.utils.subscribeToDidTopics

class CustomApplication : Application() {
    private var conversationsActivitiesVisible = 0
    private val conversationActivitiesVisible: MutableMap<ConversationId, Int> =
        HashMap()

    fun conversationsActivityVisible(): Boolean {
        return conversationsActivitiesVisible > 0
    }

    fun conversationsActivityIncrementCount() {
        conversationsActivitiesVisible++
    }

    fun conversationsActivityDecrementCount() {
        conversationsActivitiesVisible--
    }

    fun conversationActivityVisible(conversationId: ConversationId): Boolean {
        val count = conversationActivitiesVisible[conversationId]
        return count != null && count > 0
    }

    fun conversationActivityIncrementCount(
        conversationId: ConversationId
    ) {
        var count = conversationActivitiesVisible[conversationId]
        if (count == null) {
            count = 0
        }
        conversationActivitiesVisible[conversationId] = count + 1
    }

    fun conversationActivityDecrementCount(
        conversationId: ConversationId
    ) {
        var count = conversationActivitiesVisible[conversationId]
        if (count == null) {
            count = 0
        }
        conversationActivitiesVisible[conversationId] = count - 1
    }

    override fun onCreate() {
        super.onCreate()

        // Create static reference to self.
        self = this

        // Migrate legacy secure preferences.
        migrateLegacySecurePreferences(applicationContext)

        // Limit synchronization interval to 15 minutes. Previous versions
        // supported a shorter interval.
        if (getSyncInterval(applicationContext) != 0.0
            && getSyncInterval(applicationContext) < (15.0 / (24 * 60))
        ) {
            setRawSyncInterval(applicationContext, "0.01041666666")
        }

        // Update theme
        when (getAppTheme(applicationContext)) {
            AppearancePreferencesFragment.SYSTEM_DEFAULT -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
            AppearancePreferencesFragment.LIGHT -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
            AppearancePreferencesFragment.DARK -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        // Register for network callbacks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connectivityManager = getSystemService(
                CONNECTIVITY_SERVICE
            ) as ConnectivityManager
            connectivityManager.registerDefaultNetworkCallback(
                getInstance()
            )
        }

        // Open the database.
        Database.getInstance(applicationContext)

        // Subscribe to topics for current DIDs.
        subscribeToDidTopics(applicationContext)

        // Schedule a database synchronization, if required.
        SyncWorker.performFullSynchronization(
            applicationContext,
            scheduleOnly = true
        )

        // Initialize the billing service.
        Billing.getInstance(applicationContext)
    }

    companion object {
        private lateinit var self: CustomApplication

        fun getApplication(): CustomApplication {
            return self
        }
    }
}