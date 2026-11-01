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
        return isNetworkConnected
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