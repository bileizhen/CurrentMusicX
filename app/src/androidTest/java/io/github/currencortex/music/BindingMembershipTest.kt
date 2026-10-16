package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.binding.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.binding.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class BindingMembershipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun membershipBadgeTracksTierAndNeverShowsAnotherAccountOrCallsFailureANonmember() {
        var binding by mutableStateOf(BindingState(bound = true, profile = BoundProfile(42, "测试账号")))
        var member by mutableStateOf(BindingMembershipState())
        compose.setContent { LeiTheme(AppearanceSettings()) {
            BindingAccountCard(binding, false, {}, {}, {}, {}, {}, membership = member)
        } }
        for ((tier, label) in listOf(NeteaseMembershipTier.SVIP to "黑胶 SVIP · Lv.6",
            NeteaseMembershipTier.VIP to "黑胶 VIP · Lv.6", NeteaseMembershipTier.NONE to "普通账号")) {
            compose.runOnIdle { member = BindingMembershipState(42, value = NeteaseMembership(42, tier,
                if (tier == NeteaseMembershipTier.NONE) null else 6)) }
            compose.onNodeWithText(label).assertExists()
        }
        compose.runOnIdle { member = BindingMembershipState(99, value = NeteaseMembership(99, NeteaseMembershipTier.SVIP)) }
        compose.onNodeWithTag("binding_membership").assertDoesNotExist()
        compose.runOnIdle { member = BindingMembershipState(42, error = "会员信息暂不可用") }
        compose.onNodeWithText("普通账号").assertDoesNotExist()
        compose.onNodeWithTag("binding_membership_retry").assertExists()
        compose.runOnIdle { binding = binding.copy(stale = true) }
        compose.onNodeWithTag("binding_membership_retry").assertDoesNotExist()
    }

    /** Real account, read-only; exports product/level metadata but never credentials or UID. */
    @Test fun actualAccountMembershipLoadsThroughTheAccountViewModel(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("membershipLive") == "true")
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName == "com.bileizhen.currentmusic")
        val container = app.container
        container.ready.await(); container.neteaseSessions.ready.await()
        val account = container.neteaseSessions.state.value
        assertTrue(account.loggedIn && !account.stale)
        val models = ViewModelStore()
        lateinit var vm: BindingViewModel
        try {
            compose.runOnIdle { vm = BindingViewModel(container); models.put("member", vm) }
            compose.waitUntil(30_000) { vm.membership.value.value != null || vm.membership.value.error != null }
            assertNull(vm.membership.value.error, vm.membership.value.error)
            val membership = requireNotNull(vm.membership.value.value)
            assertEquals(account.profile!!.uid, membership.userId)
            val raw = container.requestOriginalEffectsPage("/api/music-vip-membership/client/vip/info",
                buildJsonObject { put("userId", account.profile.uid) })["data"]!!.jsonObject
            val report = buildJsonObject {
                put("accountMatched", raw["userId"]?.jsonPrimitive?.longOrNull == membership.userId)
                put("label", membership.label); put("tier", membership.tier.name)
                membership.level?.let { put("level", it) }
                put("fieldNames", JsonArray(raw.keys.sorted().map(::JsonPrimitive)))
                put("productFields", buildJsonObject { for (key in listOf("associator", "redplus")) {
                    val product = raw[key] as? JsonObject ?: continue
                    put(key, JsonArray(product.keys.sorted().map(::JsonPrimitive)))
                } })
            }
            File(app.getExternalFilesDir(null), "membership-verification.json").writeText(report.toString())
            compose.setContent { LeiTheme(AppearanceSettings()) {
                BindingAccountCard(BindingState(bound = true, profile = account.profile), false, {}, {}, {}, {}, {},
                    membership = BindingMembershipState(membership.userId, value = membership))
            } }
            compose.onNodeWithText(membership.label).assertIsDisplayed()
        } finally { compose.runOnIdle { models.clear() } }
    }
}
