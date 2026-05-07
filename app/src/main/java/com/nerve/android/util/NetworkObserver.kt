package com.nerve.android.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Listens for the platform reporting that a network just became validated and
 * fires [onAvailable] each time. Used to wake up reconnect backoff timers
 * promptly after the device transitions from offline → online (e.g. screen
 * turning back on, Wi-Fi reconnecting).
 */
class NetworkObserver(
    context: Context,
    private val onAvailable: () -> Unit,
) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Logger.debug("NetworkObserver", "available", mapOf("network" to network.toString()))
            onAvailable()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                Logger.debug("NetworkObserver", "validated", mapOf("network" to network.toString()))
                onAvailable()
            }
        }
    }

    fun register() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.registerNetworkCallback(request, callback)
        Logger.debug("NetworkObserver", "registered", emptyMap())
    }

    fun unregister() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}
