package com.payme.sdk.ui.miniapp.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.payme.sdk.PayMEMiniApp

internal class ConnectivityMonitor {
    fun isConnected(context: Context): Boolean {
        val connectivityManager = connectivityManager(context)
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )
    }

    fun registerDefaultNetworkCallback(
        context: Context,
        callback: ConnectivityManager.NetworkCallback
    ) {
        connectivityManager(context).registerDefaultNetworkCallback(callback)
    }

    fun unregisterDefaultNetworkCallback(
        context: Context,
        callback: ConnectivityManager.NetworkCallback
    ) {
        try {
            connectivityManager(context).unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "Error unregistering network callback: ${e.message}")
        }
    }

    private fun connectivityManager(context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
}
