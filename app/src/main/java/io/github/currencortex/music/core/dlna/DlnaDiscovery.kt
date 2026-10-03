package io.github.currencortex.music.core.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.net.NetworkInterface

class DlnaDiscovery(context: Context, private val ssdp: SsdpClient = SsdpClient(), private val soap: SoapClient = SoapClient()) {
    private val app = context.applicationContext
    @Volatile private var activeLock: WifiManager.MulticastLock? = null
    val multicastHeld get() = activeLock?.isHeld == true
    suspend fun scan(): List<DlnaDevice> {
        val wifi = app.getSystemService(WifiManager::class.java)
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val network = manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
            ?: throw java.io.IOException("请先连接 Wi-Fi，再搜索投屏设备")
        val interfaceName = manager.getLinkProperties(network)?.interfaceName
        val networkInterface = interfaceName?.let { NetworkInterface.getByName(it) }
        val lock = wifi.createMulticastLock("CurrentMusic:DLNA").apply { setReferenceCounted(false); acquire() }
        activeLock = lock
        try {
            return coroutineScope { ssdp.discover(networkInterface = networkInterface).take(30).map { url ->
                async { try { soap.description(url) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } }
            }.awaitAll().filterNotNull().distinctBy { it.id } }
        } finally { if (lock.isHeld) lock.release(); if (activeLock === lock) activeLock = null }
    }
}
