package net.kourlas.voipms_sms.utils

import okhttp3.OkHttpClient

class HttpClientManager {
    val client = OkHttpClient()

    companion object {
        private var instance: HttpClientManager? = null

        /**
         * Gets the sole instance of the HttpClientManager class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(): HttpClientManager = instance ?: synchronized(this) {
            instance ?: HttpClientManager().also { instance = it }
        }
    }
}