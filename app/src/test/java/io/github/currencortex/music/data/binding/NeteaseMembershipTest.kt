package io.github.currencortex.music.data.binding

import io.github.currencortex.music.core.network.ApiException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NeteaseMembershipTest {
    private fun parse(json: String) = parseNeteaseMembership(Json.parseToJsonElement(json).jsonObject, 42, 1000)

    @Test fun activeProductsAndMembershipLevelAreIndependentOfAccountListeningLevel() {
        val vip = parse("""{"userId":42,"redVipLevel":6,"level":10,"associator":{"vipCode":100,"expireTime":2000}}""")
        assertEquals("黑胶 VIP · Lv.6", vip.label)
        val svip = parse("""{"userId":42,"redVipLevel":7,"associator":{"vipCode":100,"expireTime":9000},"redplus":{"vipCode":300,"expireTime":3000}}""")
        assertEquals(NeteaseMembershipTier.SVIP, svip.tier)
        assertEquals("黑胶 SVIP · Lv.7", svip.label)
        assertEquals(3000L, svip.expiresAt)
        assertNull(parse("""{"userId":42,"level":10,"associator":{"vipCode":100,"expireTime":2000}}""").level)
    }
    @Test fun expiredSvipFallsBackToActiveVipAndExpiredAccountsDoNotKeepAnOldLevel() {
        val fallback = parse("""{"userId":42,"redVipLevel":6,"associator":{"vipCode":100,"expireTime":2000},"redplus":{"vipCode":300,"expireTime":1000}}""")
        assertEquals(NeteaseMembershipTier.VIP, fallback.tier)
        assertEquals("普通账号", parse("""{"userId":42,"redVipLevel":6,"associator":{"vipCode":100,"expireTime":1000}}""").label)
        assertEquals("普通账号", parse("""{"userId":42,"redplus":{"vipCode":999,"expireTime":9000}}""").label)
    }
    @Test fun invalidOrMissingLevelIsNotInventedAndWrongAccountIsRejected() {
        assertEquals("黑胶 SVIP", parse("""{"userId":42,"redVipLevel":0,"redplus":{"vipCode":300,"expireTime":2000}}""").label)
        assertEquals("黑胶 VIP · Lv.5", parse("""{"userId":42,"associator":{"vipCode":100,"expireTime":2000,"vipLevel":5}}""").label)
        assertThrows(ApiException::class.java) { parse("""{"userId":99,"redVipLevel":7}""") }
        assertThrows(ApiException::class.java) { parse("""{}""") }
    }
}
