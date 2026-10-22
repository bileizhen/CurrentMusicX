package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.auth.*
import io.github.currencortex.music.data.binding.BindingRepository
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NeteaseAvatarImporterTest {
    private class Fixture : AutoCloseable {
        val server = MockWebServer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val accounts = AccountRepository(object : TokenStore {
            override fun read(): String? = null
            override fun write(token: String) {}
            override fun clear() {}
        }, scope)
        val base = server.url("/cm/").toString()
        var nativeRevision = 1L
        val expected get() = RequestSession(base, "credential")
        fun session() = RequestSession(base, "credential", nativeRevision)
        val api = ApiClient({ base }, { accounts.token }, accounts::expired)
        val profiles = ProfileRepository(api) { expected }
        val binding = BindingRepository(api, ::session) {}
        val auth = AuthRepository(api, accounts, "test") { _, _ -> }
        suspend fun init(avatar: String = "") {
            accounts.server = base
            accounts.save("credential", UserDto(7, nickname = "User", avatar = avatar))
        }
        fun reply(body: String) { server.enqueue(MockResponse().setBody(body)) }
        fun bound() = reply("""{"bound":true,"profile":{"uid":8,"avatar":"https://images.example/netease.jpg"}}""")
        fun own(avatar: String = "") = reply("""{"id":7,"nickname":"User","avatar":"$avatar"}""")
        fun importer(download: suspend (String) -> ByteArray) =
            NeteaseAvatarImporter(accounts, auth, profiles, binding, ::session, download)
        override fun close() { scope.cancel(); server.close() }
    }

    @Test fun defaultAvatarUploadsImageAndPersistsTheReturnedAccount() = runBlocking {
        Fixture().use { f ->
            f.init(); f.bound(); f.own(); f.own(); f.reply("{}"); f.own("7.jpg")
            f.importer { url ->
                assertEquals("https://images.example/netease.jpg", url)
                byteArrayOf(1, 2, 3)
            }.importAfterLogin(f.expected)
            assertEquals("7.jpg", f.accounts.state.value.account?.avatar)
            assertEquals(1L, f.profiles.revision.value)
            val requests = List(5) { f.server.takeRequest() }
            assertEquals(listOf("/cm/ncmbind", "/cm/auth/me", "/cm/auth/me", "/cm/profile/avatar", "/cm/auth/me"), requests.map { it.path })
            assertEquals("PUT", requests[3].method)
            assertEquals("AQID", ApiJson.parseToJsonElement(requests[3].body.readUtf8()).jsonObject["data"]!!.jsonPrimitive.content)
        }
    }

    @Test fun customLocalAvatarSkipsAllRequests() = runBlocking {
        Fixture().use { f ->
            f.init("custom.jpg")
            f.importer { error("Custom avatar must not be downloaded") }.importAfterLogin(f.expected)
            assertEquals(0, f.server.requestCount)
        }
    }

    @Test fun freshServerAvatarWinsOverStaleDefaultLocalState() = runBlocking {
        Fixture().use { f ->
            f.init(); f.bound(); f.own("already-custom.jpg")
            f.importer { error("Existing server avatar must be kept") }.importAfterLogin(f.expected)
            assertEquals(2, f.server.requestCount)
        }
    }

    @Test fun manualAvatarChangeDuringDownloadCancelsTheImport() = runBlocking {
        Fixture().use { f ->
            f.init(); f.bound(); f.own()
            f.importer {
                f.accounts.save("credential", UserDto(7, avatar = "manual.jpg"))
                byteArrayOf(1)
            }.importAfterLogin(f.expected)
            assertEquals(2, f.server.requestCount)
            assertEquals("manual.jpg", f.accounts.state.value.account?.avatar)
        }
    }

    @Test fun eitherAccountSwitchDuringDownloadPreventsTheUpload() = runBlocking {
        for (nativeSwitch in listOf(false, true)) Fixture().use { f ->
            f.init(); f.bound(); f.own()
            f.importer {
                if (nativeSwitch) f.nativeRevision++ else f.accounts.save("other", UserDto(9))
                byteArrayOf(1)
            }.importAfterLogin(f.expected)
            assertEquals(2, f.server.requestCount)
            assertEquals(0L, f.profiles.revision.value)
        }
    }

    @Test fun serverAvatarChangedBeforeUploadIsNotOverwritten() = runBlocking {
        Fixture().use { f ->
            f.init(); f.bound(); f.own(); f.own("new-custom.jpg")
            f.importer { byteArrayOf(1) }.importAfterLogin(f.expected)
            assertEquals(3, f.server.requestCount)
            assertEquals(0L, f.profiles.revision.value)
        }
    }
}
