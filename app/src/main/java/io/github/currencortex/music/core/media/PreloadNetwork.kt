package io.github.currencortex.music.core.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun preloadNetwork(context: Context) = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    fun update() {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        trySend(if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) 2 else 1 else 0)
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update()
        override fun onLost(network: Network) = update()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update()
    }
    manager.registerDefaultNetworkCallback(callback)
    update()
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
