/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2017 Michael Kourlas
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

package net.kourlas.voipms_sms.utils

import android.content.Context
import android.net.ConnectivityManager
import net.kourlas.voipms_sms.preferences.getConnectTimeout
import net.kourlas.voipms_sms.preferences.getReadTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Retrieves a JSON object from the specified URL.
 *
 * @param context The specified context.
 * @param url The specified URL.
 * @return The JSON object at the specified URL.
 */
fun getJson(context: Context, url: String): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.readTimeout = getReadTimeout(context) * 1000
    connection.connectTimeout = getConnectTimeout(context) * 1000
    connection.connect()

    val data = connection.inputStream.bufferedReader().readText()
    return JSONObject(data)
}

/**
 * Checks if the Internet connection is available.
 *
 * @param context The specified context.
 * @return Whether the Internet connection is available.
 */
fun isNetworkConnectionAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(
        Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkInfo = connectivityManager.activeNetworkInfo
    return networkInfo != null && networkInfo.isConnected
}
