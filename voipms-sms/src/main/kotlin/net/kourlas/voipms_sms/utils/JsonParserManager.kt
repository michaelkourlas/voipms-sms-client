package net.kourlas.voipms_sms.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import java.util.*

class JsonParserManager {
    val parser: Moshi = Moshi.Builder().add(
        Date::class.java,
        Rfc3339DateJsonAdapter()
    ).build()

    companion object {
        private var instance: JsonParserManager? = null

        /**
         * Gets the sole instance of the JsonParserManager class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(): JsonParserManager = instance ?: synchronized(this) {
            instance ?: JsonParserManager().also { instance = it }
        }
    }
}