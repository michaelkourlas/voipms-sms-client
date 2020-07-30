/*
 * VoIP.ms SMS
 * Copyright (C) 2015-2020 Michael Kourlas
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
import com.squareup.moshi.Moshi
import net.kourlas.voipms_sms.preferences.getConnectTimeout
import net.kourlas.voipms_sms.preferences.getReadTimeout
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends a POST request with a multipart/form-data encoded request body to the
 * specified URL, and retrieves a JSON response body.
 */
inline fun <reified T> httpPostWithMultipartFormData(
    context: Context, okHttp: OkHttpClient, moshi: Moshi, url: String,
    formData: Map<String, String> = emptyMap()): T? {
    val requestBodyBuilder = MultipartBody.Builder()
    requestBodyBuilder.setType(MultipartBody.FORM)
    for ((key, value) in formData) {
        requestBodyBuilder.addFormDataPart(key, value)
    }
    val requestBody = requestBodyBuilder.build()

    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    val requestClient = okHttp.newBuilder()
        .readTimeout(getReadTimeout(context) * 1000L, TimeUnit.MILLISECONDS)
        .connectTimeout(getConnectTimeout(context) * 1000L,
                        TimeUnit.MILLISECONDS)
        .build()

    val adapter = moshi.adapter(T::class.java)
    requestClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response")
        }

        return adapter.fromJson(response.body!!.source())
    }
}
