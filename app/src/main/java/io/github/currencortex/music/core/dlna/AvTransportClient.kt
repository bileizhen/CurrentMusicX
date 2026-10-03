package io.github.currencortex.music.core.dlna

class AvTransportClient(private val soap: SoapClient, private val service: DlnaService) {
    private suspend fun action(name: String, args: Map<String, String> = emptyMap()) = soap.call(service, name, mapOf("InstanceID" to "0") + args)
    suspend fun setUri(url: String, metadata: String) { action("SetAVTransportURI", mapOf("CurrentURI" to url, "CurrentURIMetaData" to metadata)) }
    suspend fun play() { action("Play", mapOf("Speed" to "1")) }
    suspend fun pause() { action("Pause") }
    suspend fun stop() { action("Stop") }
    suspend fun seek(position: Long) { action("Seek", mapOf("Unit" to "REL_TIME", "Target" to dlnaTime(position))) }
    suspend fun state() = DlnaXml.value(action("GetTransportInfo"), "CurrentTransportState")
    suspend fun position(): DlnaPosition {
        val xml = action("GetPositionInfo")
        return DlnaPosition(dlnaMillis(DlnaXml.value(xml, "RelTime")), dlnaMillis(DlnaXml.value(xml, "TrackDuration")), DlnaXml.value(xml, "TrackURI"))
    }
}
