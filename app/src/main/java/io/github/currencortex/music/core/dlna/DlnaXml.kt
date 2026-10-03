package io.github.currencortex.music.core.dlna

import org.w3c.dom.Element
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object DlnaXml {
    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;").filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
    fun parse(text: String): Document {
        require(text.length <= 2 * 1024 * 1024)
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
        // DTDs are rejected before parsing, including on Android parsers lacking these features.
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return builder.parse(InputSource(StringReader(text)))
    }
    fun Element.text(name: String): String = getElementsByTagNameNS("*", name).item(0)?.textContent.orEmpty().trim()
    fun value(document: Document, name: String) = document.documentElement.text(name)
    fun device(location: String, xml: String): DlnaDevice? {
        val url = location.toHttpUrlOrNull() ?: return null
        require(url.username.isEmpty() && url.password.isEmpty())
        val document = parse(xml)
        val root = document.documentElement
        val base = root.text("URLBase").takeIf { it.isNotBlank() }?.toHttpUrlOrNull() ?: url
        val services = root.getElementsByTagNameNS("*", "service")
        fun service(prefix: String): DlnaService? {
            for (i in 0 until services.length) {
                val item = services.item(i) as? Element ?: continue
                val type = item.text("serviceType")
                if (type.startsWith(prefix)) {
                    val control = base.resolve(item.text("controlURL")) ?: continue
                    if (control.username.isNotBlank() || control.password.isNotBlank()) continue
                    return DlnaService(type, control.toString())
                }
            }
            return null
        }
        val transport = service("urn:schemas-upnp-org:service:AVTransport:") ?: return null
        return DlnaDevice(root.text("UDN").ifBlank { location }, root.text("friendlyName").ifBlank { "媒体设备" },
            location, transport, service("urn:schemas-upnp-org:service:RenderingControl:"))
    }
}
