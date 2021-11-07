package net.kourlas.voipms_sms.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build

class NetworkManager : ConnectivityManager.NetworkCallback() {
    private var isNetworkConnected = false

    override fun onLost(network: Network) {
        super.onLost(network)

        isNetworkConnected = false
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)

        isNetworkConnected = true
    }

    fun isNetworkConnectionAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isNetworkConnected
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo != null && networkInfo.isConnected
        }
    }

    companion object {
        private var instance: NetworkManager? = null

        /**
         * Gets the sole instance of the NetworkManager class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(): NetworkManager = instance ?: synchronized(this) {
            instance ?: NetworkManager().also { instance = it }
        }
    }
}