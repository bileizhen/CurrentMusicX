package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class UserLevelTest {
    @Test fun olderServersRemainUnknownAndPlusIsNotLevelSeven() {
        assertNull(ApiJson.decodeFromString<UserDto>("""{"id":7}""").level)
        assertNull(ApiJson.decodeFromString<ProfileUser>("""{"id":7}""").level)
        assertNull(ApiJson.decodeFromString<SavedAccount>("""{"key":"x","id":7,"server":"a"}""").points)
        assertNull(userLevelLabel(null)); assertEquals("Lv.6 Plus", userLevelLabel(7))
        assertEquals("Lv.6", userLevelLabel(6))
        val user = ApiJson.decodeFromString<SquareUser>("""{"id":7,"level":7}""")
        assertEquals(7, user.profileUser().level); assertNull(user.role)
    }
    @Test fun profileConversionAndOfflineAccountRetainServerProgress() {
        val profile = ProfileUser(id = 7, level = 7, points = 2147483647L)
        val repo = ProfileRepository(ApiClient({ "https://example/cm/" }, { null }, {})) { RequestSession("https://example/cm/", null) }
        val account = repo.account(profile)
        assertEquals(7, account.level); assertEquals(2147483647L, account.points)
        val saved = SavedAccount("a", 7, "https://example/cm/", level = account.level, points = account.points)
        assertEquals(7, saved.account().level); assertEquals(account.points, saved.account().points)
    }
    @Test fun newSortOptionsReachServerWithoutClientReordering() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repo = ProfileRepository(ApiClient({ base }, { null }, {})) { RequestSession(base, null) }
            for (sort in listOf("composite", "active", "level", "reg")) {
                server.enqueue(MockResponse().setBody("""{"users":[{"id":1,"level":2},{"id":2,"level":7}],"total":2}"""))
                assertEquals(listOf(1L, 2L), repo.square("name", sort, 30, true).users.map { it.id })
                val request = server.takeRequest()
                assertEquals(sort, request.requestUrl!!.queryParameter("sort"))
                assertEquals("30", request.requestUrl!!.queryParameter("offset"))
                assertEquals("1", request.requestUrl!!.queryParameter("listening"))
                assertNull(request.getHeader("Authorization"))
            }
        }
    }
}
