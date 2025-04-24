package com.payme.sdk.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lớp theo dõi kết nối mạng để cung cấp thông tin về loại kết nối và trạng thái kết nối
 */
class NetworkMonitor(private val context: Context) {
    
    enum class ConnectionType(val description: String) {
        WIFI("WiFi"),
        CELLULAR("Mobile Data"),
        ETHERNET("Ethernet"),
        UNKNOWN("Unknown");
        
        override fun toString(): String {
            return description
        }
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionType = MutableStateFlow(ConnectionType.UNKNOWN)
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkNetworkState()
        }
        
        override fun onLost(network: Network) {
            checkNetworkState()
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            checkNetworkState()
        }
    }
    
    init {
        registerNetworkCallback()
        checkNetworkState()
    }
    
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    
    fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Xử lý trường hợp callback chưa được đăng ký
        }
    }
    
    @Suppress("DEPRECATION")
    private fun checkNetworkState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (capabilities != null) {
                _isConnected.value = true
                _connectionType.value = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                    else -> ConnectionType.UNKNOWN
                }
            } else {
                _isConnected.value = false
                _connectionType.value = ConnectionType.UNKNOWN
            }
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            _isConnected.value = activeNetworkInfo != null && activeNetworkInfo.isConnected
            
            _connectionType.value = when {
                activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI -> ConnectionType.WIFI
                activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE -> ConnectionType.CELLULAR
                activeNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
        }
    }
    
    companion object {
        private var instance: NetworkMonitor? = null
        
        fun initialize(context: Context) {
            if (instance == null) {
                instance = NetworkMonitor(context.applicationContext)
            }
        }
        
        val shared: NetworkMonitor
            get() {
                if (instance == null) {
                    throw IllegalStateException("NetworkMonitor must be initialized before using shared instance")
                }
                return instance!!
            }
    }
}
