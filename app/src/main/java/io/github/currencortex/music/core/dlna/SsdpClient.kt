package io.github.currencortex.music.core.dlna

import kotlinx.coroutines.*
import java.net.*
import java.util.Locale

class SsdpClient {
    suspend fun discover(timeoutMs: Long = 3500, networkInterface: NetworkInterface? = null): List<String> = withContext(Dispatchers.IO) {
        val request = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n".toByteArray(Charsets.US_ASCII)
        MulticastSocket(null).use { socket ->
            socket.reuseAddress = true; socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0))
            socket.soTimeout = 300; socket.timeToLive = 2
            if (networkInterface != null) socket.networkInterface = networkInterface
            val packet = DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900)
            repeat(2) { socket.send(packet) }
            val end = System.nanoTime() + timeoutMs.coerceIn(500, 10_000) * 1_000_000
            val locations = linkedSetOf<String>(); val bytes = ByteArray(8192)
            while (System.nanoTime() < end) {
                currentCoroutineContext().ensureActive()
                try {
                    val incoming = DatagramPacket(bytes, bytes.size); socket.receive(incoming)
                    location(String(bytes, 0, incoming.length, Charsets.UTF_8))?.let { locations.add(it) }
                } catch (_: SocketTimeoutException) { }
            }
            locations.toList()
        }
    }
    companion object {
        fun location(response: String): String? {
            if (!response.lineSequence().firstOrNull().orEmpty().startsWith("HTTP/1.1 200", true)) return null
            return response.lineSequence().mapNotNull { line -> val parts = line.split(':', limit = 2)
                if (parts.size == 2 && parts[0].trim().lowercase(Locale.ROOT) == "location") parts[1].trim() else null
            }.firstOrNull()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        }
    }
}
