package io.github.currencortex.music.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.BindingRepository
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class PrimaryMusicLibraryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var server: MockWebServer
    private lateinit var library: PrimaryMusicLibrary
    private lateinit var current: LibraryRepository
    private lateinit var settings: MusicSettingsRepository
    @Volatile private var nativePlaylists = """[{"id":88,"name":"Live","creator":{"userId":7}},{"id":89,"name":"Subscribed","subscribed":true,"creator":{"userId":9}}]"""
    @Volatile private var nativeFailure = false
    @Volatile private var cloudFailure = false
    @Volatile private var bound = true
    @Volatile private var includeCustom = true

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Before fun setup() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl!!.encodedPath) {
                        "/cm/ncmbind" -> """{"bound":$bound,"profile":{"uid":7}}"""
                        "/cm/ncm/user/playlist" -> if (nativeFailure) return MockResponse().setResponseCode(502)
                            else """{"code":200,"playlist":$nativePlaylists}"""
                        "/cm/playlists" -> if (cloudFailure) return MockResponse().setResponseCode(502)
                            else """{"playlists":[${if (includeCustom) """{"id":88,"name":"Custom","source":"currentmusic","user_id":7},""" else ""}
                                {"id":500,"name":"Old snapshot","source":"ncm","user_id":7},
                                {"id":501,"name":"Other snapshot","source":"netease","user_id":7}]}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setBody(body)
                }
            }
            start()
        }
        val session = RequestSession(server.url("/cm/").toString(), "fixture")
        val api = ApiClient({ session.server }, { session.token }, {})
        val binding = BindingRepository(api, { session }) {}
        current = LibraryRepository(api, { 7L }, { session })
        val native = NeteaseLibraryRepository(api, binding, NeteaseSongActionsRepository(api) { session }) { session }
        settings = MusicSettingsRepository(MemoryPreferences(), scope)
        library = PrimaryMusicLibrary(current, native, binding, settings, scope)
    }

    @After fun cleanup() { scope.cancel(); server.shutdown() }

    @Test fun refreshUsesLiveMembershipAndMetadataWithoutResurrectingSnapshots() = runBlocking {
        val first = library.playlists()
        assertEquals(listOf("currentmusic" to 88L, "netease" to 88L, "netease" to 89L), first.map { it.source to it.id })
        nativePlaylists = """[{"id":88,"name":"Renamed upstream","creator":{"userId":7}}]"""
        assertEquals(listOf("Custom", "Renamed upstream"), library.playlists(fresh = true).map { it.name })
        nativePlaylists = "[]"
        assertEquals(listOf("Custom"), library.playlists(fresh = true).map { it.name })
        // Reading the live collection must not mutate the saved snapshot collection.
        assertEquals(listOf(88L, 500L, 501L), current.playlists().map { it.id })
    }

    @Test fun successfulEmptyNativeLibraryHidesAllOldSnapshots() = runBlocking {
        includeCustom = false; nativePlaylists = "[]"
        assertTrue(library.playlists(fresh = true).isEmpty())
    }

    @Test fun nativeFailureKeepsSavedSnapshotsAvailable() = runBlocking {
        nativeFailure = true
        assertEquals(listOf(88L, 500L, 501L), library.playlists(fresh = true).map { it.id })
    }

    @Test fun disabledNativeLibraryAndUnboundAccountsKeepSnapshots() = runBlocking {
        settings.setNeteaseMainLibrary(false)
        assertEquals(listOf(88L, 500L, 501L), library.playlists(fresh = true).map { it.id })
        settings.setNeteaseMainLibrary(true); bound = false
        // The disabled preference above did not need to load binding status.
        assertEquals(listOf(88L, 500L, 501L), library.playlists(fresh = true).map { it.id })
    }

    @Test fun validEmptyNativeLibraryStillSucceedsWhenCloudIsUnavailable() = runBlocking {
        cloudFailure = true; nativePlaylists = "[]"
        assertTrue(library.playlists(fresh = true).isEmpty())
    }
}
