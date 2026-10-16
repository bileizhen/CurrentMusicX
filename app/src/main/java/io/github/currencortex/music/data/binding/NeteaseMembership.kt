package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable enum class NeteaseMembershipTier { NONE, VIP, SVIP }

@Serializable data class NeteaseMembership(val userId: Long, val tier: NeteaseMembershipTier,
    val level: Int? = null, val expiresAt: Long = 0) {
    val label: String get() = when (tier) {
        NeteaseMembershipTier.NONE -> "普通账号"
        NeteaseMembershipTier.VIP -> "黑胶 VIP"
        NeteaseMembershipTier.SVIP -> "黑胶 SVIP"
    } + (level?.let { " · Lv.$it" } ?: "")
}

/** Account listening level is deliberately not interpreted as a membership level. */
internal fun parseNeteaseMembership(data: JsonObject, userId: Long,
    now: Long = System.currentTimeMillis()): NeteaseMembership {
    fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
    if (userId <= 0 || data.number("userId") != userId) throw ApiException(ErrorKind.Parse)
    fun active(name: String, code: Long) = (data[name] as? JsonObject)?.takeIf {
        it.number("vipCode") == code && (it.number("expireTime") ?: 0) > now
    }
    val plus = active("redplus", 300)
    val black = active("associator", 100)
    val tier = when { plus != null -> NeteaseMembershipTier.SVIP; black != null -> NeteaseMembershipTier.VIP
        else -> NeteaseMembershipTier.NONE }
    val product = plus ?: black
    val level = if (product == null) null else
        (data.number("redVipLevel") ?: product.number("vipLevel"))?.takeIf { it in 1..99 }?.toInt()
    return NeteaseMembership(userId, tier, level, product?.number("expireTime") ?: 0)
}
