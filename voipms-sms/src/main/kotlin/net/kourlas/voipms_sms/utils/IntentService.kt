/*
 * Copyright (C) 2008 The Android Open Source Project
 * Modifications copyright (C) 2020 Michael Kourlas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.utils

import android.app.Service
import android.content.Intent
import android.os.*
import androidx.annotation.WorkerThread

abstract class IntentService(private val mName: String) : Service() {
    @Volatile
    private lateinit var mServiceLooper: Looper

    @Volatile
    private lateinit var mServiceHandler: ServiceHandler
    private var mRedelivery = false

    private inner class ServiceHandler(looper: Looper?) : Handler(
        looper!!) {
        override fun handleMessage(msg: Message) {
            onHandleIntent(msg.obj as Intent)
            stopSelf(msg.arg1)
        }
    }

    @Suppress("unused")
    fun setIntentRedelivery(enabled: Boolean) {
        mRedelivery = enabled
    }

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("IntentService[$mName]")
        thread.start()
        mServiceLooper = thread.looper
        mServiceHandler = ServiceHandler(mServiceLooper)
    }

    override fun onStart(intent: Intent?, startId: Int) {
        val msg: Message = mServiceHandler.obtainMessage()
        msg.arg1 = startId
        msg.obj = intent
        mServiceHandler.sendMessage(msg)
    }

    override fun onStartCommand(intent: Intent?, flags: Int,
                                startId: Int): Int {
        @Suppress("DEPRECATION")
        onStart(intent, startId)
        return if (mRedelivery) START_REDELIVER_INTENT else START_NOT_STICKY
    }

    override fun onDestroy() {
        mServiceLooper.quit()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @WorkerThread
    protected abstract fun onHandleIntent(intent: Intent?)
}