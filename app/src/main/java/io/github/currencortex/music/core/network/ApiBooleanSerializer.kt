package io.github.currencortex.music.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** CurrentMusic's public square uses SQLite 0/1 flags; profiles use JSON booleans. */
object ApiBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("ApiBoolean", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean = if (decoder is JsonDecoder) {
        val value = decoder.decodeJsonElement() as? JsonPrimitive
        value?.content in setOf("true", "1")
    } else decoder.decodeBoolean()
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}
