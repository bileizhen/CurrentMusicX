package io.github.currencortex.music.core.dlna

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class DlnaProtocolTest {
    private fun envelope(fields: String) = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>$fields</s:Body></s:Envelope>"""
    @Test fun descriptionResolvesUrlBaseServiceVersionsAndDeduplicatedIdentity() {
        val device = DlnaXml.device("http://192.168.1.8:8080/device.xml", """<root xmlns="urn:schemas-upnp-org:device-1-0"><URLBase>http://192.168.1.8:9090/base/</URLBase><device><friendlyName>TV &amp; Audio</friendlyName><UDN>uuid:tv</UDN><serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:2</serviceType><controlURL>av</controlURL></service></serviceList></device></root>""")!!
        assertEquals("TV & Audio", device.name); assertEquals("uuid:tv", device.id)
        assertEquals("http://192.168.1.8:9090/base/av", device.transport.controlUrl)
        assertNull(device.rendering)
    }
    @Test fun untrustedDeviceXmlCannotExpandEntitiesOrReadFiles() {
        assertThrows(IllegalArgumentException::class.java) { DlnaXml.parse("""<!DOCTYPE root [<!ENTITY secret SYSTEM "file:///private/token">]><root>&secret;</root>""") }
        assertThrows(IllegalArgumentException::class.java) { DlnaXml.parse("<root>" + "a".repeat(2 * 1024 * 1024) + "</root>") }
    }
    @Test fun ssdpHeadersAreCaseInsensitiveAndNonSuccessIsIgnored() {
        assertEquals("http://192.168.1.8/desc.xml", SsdpClient.location("HTTP/1.1 200 OK\r\nLoCaTiOn: http://192.168.1.8/desc.xml\r\n\r\n"))
        assertNull(SsdpClient.location("NOTIFY * HTTP/1.1\r\nLOCATION: http://192.168.1.8/desc.xml"))
        assertNull(SsdpClient.location("HTTP/1.1 200 OK\r\nLOCATION: file:///secret"))
    }
    @Test fun soapEmulatorVerifiesUriMetadataTransportPositionAndVolumeWithoutCredentials() = runBlocking {
        MockWebServer().use { server ->
            val soap = SoapClient(); val service = DlnaService("urn:schemas-upnp-org:service:AVTransport:1", server.url("/av").toString())
            val av = AvTransportClient(soap, service)
            val url = "https://cdn.test/song.flac?a=1&b=2"
            val metadata = DidlMetadataBuilder.build(Song(7, "<Song> & \"Title\"", "Artist", durationMs = 125000), url, "audio/flac")
            server.enqueue(MockResponse().setBody(envelope("<ok/>")))
            av.setUri(url, metadata)
            val set = server.takeRequest()
            assertEquals("\"${service.type}#SetAVTransportURI\"", set.getHeader("SOAPACTION")); assertNull(set.getHeader("Authorization"))
            val parsed = DlnaXml.parse(set.body.readUtf8())
            assertEquals(url, DlnaXml.value(parsed, "CurrentURI"))
            val didl = DlnaXml.parse(DlnaXml.value(parsed, "CurrentURIMetaData"))
            assertEquals("<Song> & \"Title\"", DlnaXml.value(didl, "title"))
            assertEquals(url, DlnaXml.value(didl, "res"))
            server.enqueue(MockResponse().setBody(envelope("<RelTime>00:01:02.500</RelTime><TrackDuration>00:02:05</TrackDuration><TrackURI>$url</TrackURI>".replace("&", "&amp;"))))
            val position = av.position(); assertEquals(62500L, position.positionMs); assertEquals(125000L, position.durationMs); server.takeRequest()
            server.enqueue(MockResponse().setBody(envelope("<ok/>"))); av.seek(62500)
            assertEquals("00:01:02", DlnaXml.value(DlnaXml.parse(server.takeRequest().body.readUtf8()), "Target"))
            val volume = RenderingControlClient(soap, service.copy(type = "urn:schemas-upnp-org:service:RenderingControl:1"))
            server.enqueue(MockResponse().setBody(envelope("<ok/>"))); volume.volume(200)
            assertEquals("100", DlnaXml.value(DlnaXml.parse(server.takeRequest().body.readUtf8()), "DesiredVolume"))
            server.enqueue(MockResponse().setBody(envelope("<CurrentVolume>47</CurrentVolume>"))); assertEquals(47, volume.volume())
        }
    }
    @Test fun soapFaultIsFailureEvenWhenDeviceReturnsHttp200() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(envelope("<s:Fault><faultcode>s:Client</faultcode></s:Fault>")))
            val result = runCatching { AvTransportClient(SoapClient(), DlnaService("urn:test", server.url("/").toString())).play() }
            assertTrue(result.isFailure)
        }
    }
    @Test fun cancellationWhileReadingSlowSoapBodyClosesTheRequestPromptly() = runBlocking {
        MockWebServer().use { server ->
            // QueueDispatcher also applies throttling while reading the POST body. Dispatch only
            // after the complete request so this fixture delays the response, not the request.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setBody(envelope("<RelTime>00:01:00</RelTime>"))
                    .throttleBody(1, 500, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            val service = DlnaService("urn:test", server.url("/").toString())
            val operation = launch(Dispatchers.Default) { AvTransportClient(SoapClient(), service).position() }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS) })
            delay(50)
            withTimeout(1000) { operation.cancelAndJoin() }
        }
    }
}
