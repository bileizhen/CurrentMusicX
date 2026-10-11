package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.data.auth.SavedAccount
import io.github.currencortex.music.data.auth.UserDto
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class UserRoleTest {
    @Test fun liveSquareIntegerFlagsAndProfileBooleanFlagsShareRolePriority() {
        val square = ApiJson.decodeFromString<SquareDto>("""{"users":[
            {"id":5,"is_admin":1,"is_super":1},
            {"id":135,"is_admin":1,"is_super":0},
            {"id":9,"is_admin":0,"is_super":0}]}""")
        assertEquals(listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN, null), square.users.map { it.role })
        assertEquals(UserRole.SUPER_ADMIN, square.users[0].profileUser().role)
        val profile = ApiJson.decodeFromString<ProfileUser>("""{"id":5,"isAdmin":true,"isSuper":true}""")
        assertEquals(UserRole.SUPER_ADMIN, profile.role)
        assertNull(ApiJson.decodeFromString<ProfileUser>("""{"id":9}""").role)
    }

    @Test fun aliasesNullAndUnknownFlagsNeverInventAdministratorStatus() {
        assertEquals(UserRole.ADMIN, ApiJson.decodeFromString<ProfileUser>("""{"is_admin":1,"is_super":0}""").role)
        assertEquals(UserRole.SUPER_ADMIN, ApiJson.decodeFromString<SquareUser>("""{"id":5,"isAdmin":false,"isSuper":true}""").role)
        for (value in listOf("null", "0", "false", "2", "\"admin\"")) {
            assertNull(ApiJson.decodeFromString<ProfileUser>("""{"isAdmin":$value,"isSuper":$value}""").role)
        }
    }

    @Test fun accountRefreshAndSavedMetadataRetainDisplayFlags() {
        val user = ApiJson.decodeFromString<UserDto>("""{"id":5,"isAdmin":true,"isSuper":true}""")
        assertTrue(user.isAdmin && user.isSuper)
        assertTrue(ApiJson.decodeFromString<UserDto>("""{"is_admin":1}""").isAdmin)
        val restored = ApiJson.decodeFromString<SavedAccount>(
            """{"key":"account","id":5,"server":"https://example.com/cm/","isAdmin":true,"isSuper":true}""").account()
        assertTrue(restored.isAdmin && restored.isSuper)
        assertFalse(ApiJson.decodeFromString<SavedAccount>(
            """{"key":"old","id":9,"server":"https://example.com/cm/"}""").account().isAdmin)
    }
}
