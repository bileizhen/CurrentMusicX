package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class NeteaseMedal(val code: String, val name: String, val image: String)
@Serializable data class NeteaseMedals(val userId: Long, val total: Int,
    val medals: List<NeteaseMedal> = emptyList())

/** Only earned medals are shown; the catalog also contains locked and unclaimed medals. */
internal fun parseNeteaseMedals(data: JsonObject, userId: Long): NeteaseMedals {
    if (userId <= 0) throw ApiException(ErrorKind.Parse)
    val earned = data["obtainMedals"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
    fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    val medals = earned.mapNotNull { element ->
        val medal = element as? JsonObject ?: return@mapNotNull null
        val name = medal.text("medalName").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        NeteaseMedal(medal.text("medalCode"), name, medal.text("medalPicUrl").replaceFirst("http://", "https://"))
    }.distinctBy { it.code.ifBlank { it.name } }
    val total = (data["medalNum"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(medals.size) ?: medals.size
    return NeteaseMedals(userId, total, medals)
}
