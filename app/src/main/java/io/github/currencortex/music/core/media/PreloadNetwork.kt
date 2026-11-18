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
    var lastKind = PlaybackNetworkKind.UNKNOWN
    fun update() {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        val kind = when {
            capabilities == null -> PlaybackNetworkKind.UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> PlaybackNetworkKind.WIRELESS
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> PlaybackNetworkKind.MOBILE
            else -> PlaybackNetworkKind.UNKNOWN
        }
        if (kind != PlaybackNetworkKind.UNKNOWN) lastKind = kind
        trySend(PreloadNetworkStatus(
            online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true,
            kind = if (kind == PlaybackNetworkKind.UNKNOWN) lastKind else kind,
            connected = kind != PlaybackNetworkKind.UNKNOWN,
        ))
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

enum class PlaybackNetworkKind { UNKNOWN, MOBILE, WIRELESS }

data class PreloadNetworkStatus(val online: Boolean = false, val connected: Boolean = false, val metered: Boolean = true,
    val kind: PlaybackNetworkKind = PlaybackNetworkKind.UNKNOWN)
