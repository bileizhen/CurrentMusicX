package io.github.currencortex.music.core.dlna

import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.w3c.dom.Document

class SoapClient(private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).followRedirects(false).proxy(java.net.Proxy.NO_PROXY).build(),
    private val networkSocketFactory: (() -> javax.net.SocketFactory?)? = null) {
    private suspend fun response(request: Request): String = suspendCancellableCoroutine { continuation ->
            val routed = networkSocketFactory?.invoke()?.let { client.newBuilder().socketFactory(it).build() } ?: client
            val call = routed.newCall(request); continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) throw IOException("设备请求失败（HTTP ${it.code}）")
                            val source = it.body?.source() ?: throw IOException("设备没有返回响应")
                            val buffer = Buffer()
                            // Keep the continuation active until the body is read, so cancellation closes a slow socket.
                            while (source.read(buffer, 8192) != -1L) {
                                if (buffer.size > 2L * 1024 * 1024) throw IOException("设备响应过大")
                            }
                            if (!continuation.isCancelled) continuation.resume(buffer.readUtf8())
                        } catch (e: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                    }
                }
            })
    }
    suspend fun description(url: String) = DlnaXml.device(url, response(Request.Builder().url(url).get().build()))
    suspend fun call(service: DlnaService, action: String, args: Map<String, String>): Document {
        require(action.matches(Regex("[A-Za-z]+")) && args.keys.all { it.matches(Regex("[A-Za-z]+")) })
        val fields = args.entries.joinToString("") { "<${it.key}>${DlnaXml.escape(it.value)}</${it.key}>" }
        val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="${DlnaXml.escape(service.type)}">$fields</u:$action></s:Body></s:Envelope>"""
        val xml = DlnaXml.parse(response(Request.Builder().url(service.controlUrl)
            .header("SOAPACTION", "\"${service.type}#$action\"")
            .post(body.toRequestBody("text/xml; charset=utf-8".toMediaType())).build()))
        if (xml.getElementsByTagNameNS("*", "Fault").length != 0) throw IOException("设备拒绝操作")
        return xml
    }
}
