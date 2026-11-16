package io.github.currencortex.music.data.qqmusic

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttAsyncClient
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.MqttSubscription
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.eclipse.paho.mqttv5.common.packet.UserProperty
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Tencent pushes the QQ Music App scanner's authorization result for each QR ID over MQTT. */
internal class QqMusicQrSubscription(qrId: String) : AutoCloseable {
    sealed interface Event {
        data object Waiting : Event
        data object Scanned : Event
        data object Expired : Event
        data object Cancelled : Event
        data class Failed(val kind: ErrorKind, val message: String) : Event
        class Authorized(val musicId: Long, val token: String) : Event
    }

    private val topic = "management.qrcode_login/$qrId"
    private val current = AtomicReference<Event>(Event.Waiting)
    val event: Event get() = current.get()
    @Volatile private var connected = false

    private val client = MqttAsyncClient(
        "wss://mu.y.qq.com:443/ws/handshake",
        UUID.randomUUID().toString(),
        MemoryPersistence(),
    )
    private val options = MqttConnectionOptions().apply {
        connectionTimeout = 10
        keepAliveInterval = 45
        isHttpsHostnameVerificationEnabled = true
        customWebSocketHeaders = mapOf(
            "Origin" to "https://y.qq.com",
            "Referer" to "https://y.qq.com/",
            "User-Agent" to "Mozilla/5.0",
        )
        authMethod = "pass"
        userProperties = listOf(
            UserProperty("tmeAppID", "qqmusic"),
            UserProperty("business", "management"),
            UserProperty("hashTag", qrId),
            UserProperty("clientTag", "management.user"),
            UserProperty("userID", qrId),
        )
    }

    fun connect() {
        client.setCallback(object : MqttCallback {
            override fun disconnected(response: MqttDisconnectResponse) = connectionLost()
            override fun mqttErrorOccurred(exception: MqttException) = connectionLost()
            override fun deliveryComplete(token: IMqttToken) = Unit
            override fun connectComplete(reconnect: Boolean, serverURI: String) = Unit
            override fun authPacketArrived(reasonCode: Int, properties: MqttProperties) = Unit

            override fun messageArrived(topic: String, message: MqttMessage) {
                if (topic != this@QqMusicQrSubscription.topic) return
                val type = message.properties?.userProperties?.firstOrNull { it.key == "type" }?.value
                val event = when (type) {
                    "scanned" -> Event.Scanned
                    "canceled" -> Event.Cancelled
                    "timeout" -> Event.Expired
                    "loginFailed" -> Event.Failed(ErrorKind.Unauthorized, "QQ 音乐扫码授权失败")
                    "cookies" -> parseAuthorization(message.payload.decodeToString())
                    else -> return
                }
                publish(event)
            }
        })
        try {
            for (attempt in 1..3) {
                try {
                    client.connect(options).waitForCompletion(12_000)
                    connected = true
                    break
                } catch (failure: MqttException) {
                    // Tencent's broker can respond with "Use another server" or "Server moved".
                    if (attempt == 3 || failure.reasonCode !in setOf(0x9c, 0x9d)) throw failure
                }
            }
            val properties = MqttProperties().apply {
                userProperties = listOf(
                    UserProperty("authorization", "tmelogin"),
                    UserProperty("pubsub", "unicast"),
                )
            }
            val subscription = client.subscribe(arrayOf(MqttSubscription(topic, 0)), null, null, properties)
            subscription.waitForCompletion(8_000)
            if (subscription.reasonCodes.singleOrNull() !in setOf(0, 1, 2)) {
                throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未接受二维码状态订阅")
            }
        } catch (failure: MqttException) {
            throw ApiException(ErrorKind.Network, userMessage = "无法连接 QQ 音乐扫码服务")
        }
    }

    private fun parseAuthorization(payload: String): Event = runCatching {
        val data = ApiJson.parseToJsonElement(payload) as? JsonObject
        val cookies = data?.get("cookies") as? JsonObject
        fun value(name: String) = ((cookies?.get(name) as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull
        val musicId = value("qqmusic_uin")?.toLongOrNull()?.takeIf { it > 0 }
        val token = value("qqmusic_key")?.takeIf { it.isNotBlank() }
        if (musicId != null && token != null) Event.Authorized(musicId, token) else null
    }.getOrNull() ?: Event.Failed(ErrorKind.Parse, "QQ 音乐扫码结果缺少授权信息")

    private fun connectionLost() {
        if (connected) publish(Event.Failed(ErrorKind.Network, "QQ 音乐扫码连接已断开，请重新获取二维码"))
    }

    private fun publish(next: Event) {
        current.updateAndGet { if (it == Event.Waiting || it == Event.Scanned) next else it }
    }

    override fun close() {
        connected = false
        runCatching { if (client.isConnected) client.disconnectForcibly(0, 500, false) }
        runCatching { client.close(true) }
    }
}
