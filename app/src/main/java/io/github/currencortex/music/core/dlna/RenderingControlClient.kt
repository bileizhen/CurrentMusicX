package io.github.currencortex.music.core.dlna

class RenderingControlClient(private val soap: SoapClient, private val service: DlnaService) {
    private suspend fun action(name: String, extra: Map<String, String> = emptyMap()) = soap.call(service, name,
        mapOf("InstanceID" to "0", "Channel" to "Master") + extra)
    suspend fun volume(): Int = DlnaXml.value(action("GetVolume"), "CurrentVolume").toIntOrNull()?.coerceIn(0, 100) ?: 0
    suspend fun volume(value: Int) { action("SetVolume", mapOf("DesiredVolume" to value.coerceIn(0, 100).toString())) }
}
