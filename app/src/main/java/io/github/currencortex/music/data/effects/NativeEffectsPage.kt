package io.github.currencortex.music.data.effects

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Presentation fields only. Applying an effect still resolves its material and account rights. */
data class NativeEffectTile(val id: Long, val name: String, val description: String,
    val category: Int, val membership: Int, val lightImage: String, val darkImage: String,
    val lightAnimation: String, val darkAnimation: String, val useCount: Long? = null, val trackCount: Long? = null) {
    val smart get() = category == 2 || id == 100L
    fun image(dark: Boolean) = if (dark) darkImage.ifBlank { lightImage } else lightImage.ifBlank { darkImage }
    fun animation(dark: Boolean) = if (dark) darkAnimation else lightAnimation
}

fun parseNativeEffectsPage(raw: JsonObject): List<NativeEffectTile> {
    require((raw["code"] as? JsonPrimitive)?.intOrNull == 200) { "音效目录暂时不可用" }
    val rows = raw["data"] as? JsonArray ?: error("音效目录格式无效")
    return rows.mapNotNull { item ->
        val row = item as? JsonObject ?: return@mapNotNull null
        fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().takeUnless { it == "null" }.orEmpty()
        fun media(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
            text(key).replace("http://", "https://").toHttpUrlOrNull()?.takeIf {
                it.scheme == "https" && it.host.endsWith(".music.126.net") && it.username.isEmpty() && it.password.isEmpty()
            }?.toString()
        }.orEmpty()
        val id = (row["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
        val name = text("name").take(100)
        if (id <= 0 || name.isBlank()) return@mapNotNull null
        NativeEffectTile(id, name, text("exposeDescText").ifBlank { text("subTitle") }.take(300),
            (row["soundCategory"] as? JsonPrimitive)?.intOrNull ?: 1,
            (row["type"] as? JsonPrimitive)?.intOrNull ?: 0,
            media("newLightImg", "lightImg", "imgUrl"), media("newDarkImg", "darkImg", "imgUrl"),
            media("newLightGif", "lightGif"), media("newDarkGif", "darkGif"),
            (row["useCount"] as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0),
            (row["trackCount"] as? JsonPrimitive)?.longOrNull?.coerceAtLeast(0))
    }.distinctBy { it.id }.take(300)
}
