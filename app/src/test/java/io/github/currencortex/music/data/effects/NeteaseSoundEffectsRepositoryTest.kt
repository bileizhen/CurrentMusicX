package io.github.currencortex.music.data.effects

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class NeteaseSoundEffectsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: NeteaseSoundEffectsRepository
    private var clock = 0L

    @Before fun setup() = runBlocking {
        server = MockWebServer().apply { start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val vault = object : TokenStore {
            private var value: String? = null
            override fun read() = value
            override fun write(token: String) { value = token }
            override fun clear() { value = null }
        }
        sessions = NeteaseSessionStore(vault, scope).also { it.ready.await() }
        val url = server.url("/").toString()
        repository = NeteaseSoundEffectsRepository(NeteaseTransport(sessions, NeteaseEndpoints(url, url, url))) { clock }
    }

    @After fun cleanup() { scope.cancel(); server.shutdown() }

    private fun enqueue(raw: String) = server.enqueue(MockResponse().setBody(raw).setHeader("Content-Type", "application/json"))
    private fun row(id: Long) = """{"song_id":$id,"emotion_label":"平和, 快乐,平和","repbpm":90,"loudness_lufs":-11.5,"vocal_energy_ratio":0.4,"bass_energy_ratio":0.2,"stereo_width_ratio":0.3,"mel_spectrogram_32":[${List(32) { -10 - it }.joinToString()}]}"""
    private fun analysis(id: Long) = """{"code":200,"data":[${row(id)}]}"""
    private fun payload(request: RecordedRequest): JsonObject {
        val encoded = request.body.readUtf8().split('&').associate {
            it.split('=', limit = 2).let { parts -> parts[0] to URLDecoder.decode(parts[1], "UTF-8") }
        }.getValue("params")
        val bytes = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ApiJson.parseToJsonElement(NeteaseCrypto.decryptResponse(bytes).toString(Charsets.UTF_8)
            .split("-36cd479b6b5-")[1]).jsonObject
    }

    @Test fun catalogPreservesMembershipAndNeverLoadsEffectBinaries() = runBlocking {
        enqueue("""{"code":200,"data":[{"id":1001,"name":"3D环绕","subTitle":"介绍","imgUrl":"http://p1.music.126.net/a.jpg","type":3,"soundType":2,"link":"https://d1.music.126.net/a.ncae"},{"id":1001,"name":"duplicate"},{"id":0,"name":"invalid"}]}""")
        val effects = repository.catalog()
        assertEquals(listOf(NeteaseSoundEffect(1001, "3D环绕", "介绍", "https://p1.music.126.net/a.jpg", 3, 2,
            "https://d1.music.126.net/a.ncae")), effects)
        assertEquals(effects, repository.catalog())
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/eapi/usertool/sound/mobile/all", request.path)
        assertNull(request.getHeader("Authorization"))
        assertFalse(request.getHeader("Cookie").orEmpty().contains("MUSIC_U"))
        assertEquals("pc", payload(request)["header"]!!.jsonObject["os"]!!.jsonPrimitive.content)
    }

    @Test fun catalogUsesTheMaterialHashAndDoesNotFallBackAfterAnInvalidMaterialList() = runBlocking {
        enqueue("""{"code":200,"data":[${newOfficial(3595400, 4)},
            {"id":9,"name":"invalid material","type":4,"soundType":1,
             "link":"https://d1.music.126.net/parent.ncae","md5":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
             "soundMaterialList":[{"type":1,"link":"https://evil.invalid/file.ncae","md5":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}]}]}""")
        val rows = repository.catalog()
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", rows[0].presetMd5)
        assertTrue(rows[0].legacyMemberOriginal)
        assertFalse(rows[0].legacyVipOriginal)
        assertEquals(4, rows[0].memberType)
        assertFalse(rows[1].compatibleOriginalResource)
        assertFalse(rows[1].legacyMemberOriginal)
    }

    @Test fun androidModelClientProfileIsExplicitAndPreservesSessionAuthentication() = runBlocking {
        val url = server.url("/").toString()
        val transport = NeteaseTransport(sessions, NeteaseEndpoints(url, url, url))
        enqueue("""{"code":200,"data":{}}""")
        transport.request("/api/dai/distribute/getModelInfoList", clientProfile = NeteaseClientProfile.ANDROID)
        val request = server.takeRequest()
        val header = payload(request)["header"]!!.jsonObject
        assertEquals("android", header["os"]!!.jsonPrimitive.content)
        assertEquals("9.5.30", header["appver"]!!.jsonPrimitive.content)
        assertFalse(header.containsKey("osver"))
        assertNull(request.getHeader("Authorization"))
        assertFalse(request.getHeader("Cookie").orEmpty().contains("MUSIC_U"))
    }

    private fun newOfficial(id: Long, type: Int = 3) = """{"id":$id,"name":"fresh official","type":$type,"soundType":1,
        "md5":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","newVipStrategyCode":null,"vipAuthRespItem":null,
        "soundMaterialList":[{"type":1,"md5":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "link":"http://d1.music.126.net/fresh.ncae"}]}"""

    @Test fun resolveNewIdUsesAndroidProfileAndActualMaterialFileHashWithoutDownloadingIt() = runBlocking {
        enqueue("""{"code":200,"data":{"soundList":[{"soundMobileDetailDTO":${newOfficial(3601400)}}]}}""")
        val effect = repository.resolve(3601400)!!
        assertEquals(3601400L, effect.id)
        assertEquals(3, effect.memberType)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", effect.presetMd5)
        assertEquals("https://d1.music.126.net/fresh.ncae", effect.presetLink)
        assertTrue(effect.legacyVipOriginal)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/eapi/usertool/home/sound/info", request.path)
        val body = payload(request)
        val wanted = Json.parseToJsonElement(body["reqListStr"]!!.jsonPrimitive.content).jsonArray.single().jsonObject
        assertEquals(8, wanted["soundType"]!!.jsonPrimitive.int)
        assertEquals(3601400L, wanted["soundId"]!!.jsonPrimitive.long)
        assertEquals("android", body["header"]!!.jsonObject["os"]!!.jsonPrimitive.content)
    }

    // Captured from the Android home/info API on the test device. This DTO's sole
    // material has no type, and its byte hash differs from the top-level logical hash.
    @Test fun actualPanoramaDtoResolvesItsSingleUntypedMaterialAndPreservesVipRequirement() = runBlocking {
        val raw = checkNotNull(javaClass.getResourceAsStream("/netease-effects/panorama-info.json"))
            .bufferedReader().use { it.readText() }
        enqueue(raw)
        val effect = repository.resolve(3600400)!!
        assertEquals("全景环绕", effect.name)
        assertEquals(3, effect.memberType)
        assertEquals(1, effect.soundType)
        assertTrue(effect.legacyVipOriginal)
        assertFalse(effect.publicOriginal)
        assertEquals("9564dd306959d052bda5694f44fb75b5", effect.presetMd5)
        assertTrue(effect.presetLink.startsWith("https://d1.music.126.net/"))
        assertTrue(effect.presetLink.endsWith("/9564dd306959d052bda5694f44fb75b5.ncae"))
        assertEquals(1, server.requestCount)
    }

    @Test fun untypedMaterialFallbackRejectsAmbiguityExplicitOtherTypeAndUntrustedFiles() {
        val untyped = buildJsonObject {
            put("md5", "b".repeat(32))
            put("link", "https://d1.music.126.net/file.ncae")
        }
        val parent = Json.parseToJsonElement(newOfficial(3600400)).jsonObject
        fun parse(files: List<JsonElement>) = parseOriginalSoundEffect(JsonObject(parent +
            ("soundMaterialList" to JsonArray(files))))
        fun changed(key: String, value: JsonElement) = JsonObject(untyped + (key to value))
        assertNotNull(parse(listOf(untyped)))
        assertNotNull(parse(listOf(changed("type", JsonNull))))
        assertNull(parse(listOf(untyped, untyped)))
        assertNull(parse(listOf(untyped, JsonNull)))
        assertNull(parse(listOf(changed("type", JsonPrimitive(2)))))
        assertNull(parse(listOf(changed("type", JsonPrimitive("invalid")))))
        assertNull(parse(listOf(changed("md5", JsonPrimitive("invalid")))))
        assertNull(parse(listOf(changed("link", JsonPrimitive("https://evil.invalid/file.ncae")))))
        val typed = changed("type", JsonPrimitive(1))
        assertNotNull(parse(listOf(untyped, typed))) // A supported explicit type remains authoritative.
    }

    @Test fun resolveRejectsDifferentOrRemovedIdAndDoesNotDowngradeNewSvipStrategy() = runBlocking {
        enqueue("""{"code":200,"data":{"soundList":[{"soundMobileDetailDTO":${newOfficial(3600400)}}]}}""")
        assertNull(repository.resolve(3601400))
        enqueue("""{"code":200,"data":{"soundList":[]}}""")
        assertNull(repository.resolve(3601400))
        enqueue("""{"code":404}""")
        assertNull(repository.resolve(3601400))
        val changed = Json.parseToJsonElement(newOfficial(3601400, 4)).jsonObject.toMutableMap().apply {
            put("newVipStrategyCode", JsonPrimitive("new-strategy"))
            put("vipAuthRespItem", buildJsonObject { put("canUse", true) })
        }
        enqueue("""{"code":200,"data":{"soundList":[{"soundMobileDetailDTO":${JsonObject(changed)}}]}}""")
        val effect = repository.resolve(3601400)!!
        assertEquals(4, effect.memberType)
        assertEquals("new-strategy", effect.strategyCode)
        assertTrue(effect.hasVipAuthStrategy)
        assertFalse(effect.publicOriginal)
        assertFalse(effect.legacyVipOriginal)
    }

    @Test fun resolveDiscardsResponseFromPreviousAccountRevision() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":200,"data":{"soundList":[{"soundMobileDetailDTO":${newOfficial(3601400)}}]}}""")
            .setBodyDelay(200, TimeUnit.MILLISECONDS))
        val result = scope.async { runCatching { repository.resolve(3601400) } }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        sessions.clear()
        val failure = result.await().exceptionOrNull()
        assertTrue(failure is ApiException && failure.kind == ErrorKind.NeteaseSessionChanged)
    }

    @Test fun analysisMatchesSongIdAndReadsDescriptorsWithoutTreatingMelAsEq() = runBlocking {
        enqueue("""{"code":200,"data":[${row(88)},${row(99)}]}""")
        val result = repository.analyze(99)!!
        assertEquals(99L, result.songId)
        assertEquals(listOf("平和", "快乐"), result.emotions)
        assertEquals(90f, result.bpm)
        assertEquals(32, result.melBandsDb.size)
        assertEquals(-10f, result.melBandsDb.first())
        assertEquals(-41f, result.melBandsDb.last())
        assertEquals(-11.5f, result.loudnessLufs)
        assertEquals(.4f, result.vocalEnergy)
        assertEquals(.2f, result.bassEnergy)
        assertEquals(.3f, result.stereoWidth)
        assertEquals(result, repository.analyze(99))
        val request = server.takeRequest()
        assertEquals("/eapi/vip-rights/emotion/audio/effect/get", request.path)
        assertEquals("[99]", payload(request)["songIds"]!!.jsonPrimitive.content)
        assertEquals(1, server.requestCount)
    }

    @Test fun originalSmartLookupUsesOriginalProtocolAndPreservesAnonymousSession() = runBlocking {
        enqueue("""{"code":200,"data":{"soundKey":"1"},"success":true}""")
        assertEquals("1", repository.originalSmartKey(186016))
        assertEquals("1", repository.originalSmartKey(186016))
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/eapi/usertool/smart/sound/get", request.path)
        assertEquals("186016", payload(request)["songId"]!!.jsonPrimitive.content)
        assertNull(request.getHeader("Authorization"))
        assertFalse(request.getHeader("Cookie").orEmpty().contains("MUSIC_U"))
        assertFalse(sessions.state.value.loggedIn)
    }

    @Test fun originalSmartLookupRejectsDeniedMalformedAndStaleAccountResponses() = runBlocking {
        enqueue("""{"code":403,"data":{"soundKey":"1"}}""")
        assertEquals(ErrorKind.Forbidden,
            (runCatching { repository.originalSmartKey(7) }.exceptionOrNull() as ApiException).kind)
        for (data in listOf("{}", "{\"soundKey\":\"\"}", "{\"soundKey\":\"../1\"}")) {
            enqueue("""{"code":200,"data":$data}""")
            assertEquals(ErrorKind.Parse,
                (runCatching { repository.originalSmartKey(7) }.exceptionOrNull() as ApiException).kind)
        }
        repeat(4) { server.takeRequest() }
        server.enqueue(MockResponse().setBody("""{"code":200,"data":{"soundKey":"0"}}""")
            .setBodyDelay(200, TimeUnit.MILLISECONDS))
        val old = async { runCatching { repository.originalSmartKey(7) } }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        sessions.clear()
        assertEquals(ErrorKind.NeteaseSessionChanged, (old.await().exceptionOrNull() as ApiException).kind)
        enqueue("""{"code":200,"data":{"soundKey":"0"}}""")
        assertEquals("0", repository.originalSmartKey(7))
        clock = 15 * 60_000L
        enqueue("""{"code":200,"data":{"soundKey":"2"}}""")
        assertEquals("2", repository.originalSmartKey(7))
    }

    @Test fun originalSmartRightsRequireRealLoggedInAccountAndActiveBlackVip() = runBlocking {
        assertEquals(NeteaseEffectEntitlement(), repository.originalSmartEntitlement())
        assertEquals(0, server.requestCount)
        sessions.login(mapOf("MUSIC_U" to "test-user-cookie"), BoundProfile(42), sessions.snapshot())
        val expires = System.currentTimeMillis() + 300_000
        enqueue("""{"code":200,"data":{"userId":42,"associator":{"vipCode":100,"expireTime":$expires}}}""")
        assertEquals(NeteaseEffectEntitlement(42, true, expires), repository.originalSmartEntitlement())
        assertEquals(NeteaseEffectEntitlement(42, true, expires), repository.originalSmartEntitlement())
        val request = server.takeRequest()
        assertEquals("/eapi/music-vip-membership/client/vip/info", request.path)
        assertEquals(42L, payload(request)["userId"]!!.jsonPrimitive.long)
        assertNull(request.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
        sessions.clear()
        assertFalse(repository.originalSmartEntitlement().smartAllowed)
        assertEquals(1, server.requestCount)
    }

    @Test fun originalSmartRightsDoNotInferPermissionFromExpiredOrOtherVipProducts() = runBlocking {
        sessions.login(mapOf("MUSIC_U" to "test-user-cookie"), BoundProfile(42), sessions.snapshot())
        val expires = System.currentTimeMillis() + 300_000
        for ((code, expiry) in listOf(100 to 1L, 0 to expires, 200 to expires)) {
            enqueue("""{"code":200,"data":{"userId":42,"associator":{"vipCode":$code,"expireTime":$expiry},"musicPackage":{"vipCode":100,"expireTime":$expires}}}""")
            assertFalse(repository.originalSmartEntitlement().smartAllowed)
            clock += 60_000
        }
        enqueue("""{"code":200,"data":{"userId":42,"associator":null}}""")
        assertFalse(repository.originalSmartEntitlement().smartAllowed)
        sessions.markStale(sessions.snapshot())
        assertEquals(NeteaseEffectEntitlement(), repository.originalSmartEntitlement())
        assertEquals(4, server.requestCount)
    }

    @Test fun originalSmartRightsRejectMismatchedAccountAndAccountChangeInFlight() = runBlocking {
        sessions.login(mapOf("MUSIC_U" to "test-user-cookie"), BoundProfile(42), sessions.snapshot())
        val expires = System.currentTimeMillis() + 300_000
        enqueue("""{"code":200,"data":{"userId":43,"associator":{"vipCode":100,"expireTime":$expires}}}""")
        assertEquals(ErrorKind.Parse,
            (runCatching { repository.originalSmartEntitlement() }.exceptionOrNull() as ApiException).kind)
        server.takeRequest()
        server.enqueue(MockResponse().setBody("""{"code":200,"data":{"userId":42,"associator":{"vipCode":100,"expireTime":$expires}}}""")
            .setBodyDelay(200, TimeUnit.MILLISECONDS))
        val old = async { runCatching { repository.originalSmartEntitlement() } }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        sessions.clear()
        assertEquals(ErrorKind.NeteaseSessionChanged, (old.await().exceptionOrNull() as ApiException).kind)
        assertFalse(repository.originalSmartEntitlement().smartAllowed)
        assertEquals(2, server.requestCount)
    }

    @Test fun absentAnalysisReturnsNullAndInvalidNumbersDoNotCreateFakeMeasurements() = runBlocking {
        enqueue(analysis(100))
        assertNull(repository.analyze(99))
        assertNull(repository.analyze(99))
        assertEquals(1, server.requestCount)
        enqueue("""{"code":200,"data":[{"song_id":88,"repbpm":"NaN","loudness_lufs":"Infinity","vocal_energy_ratio":-1,"bass_energy_ratio":2,"stereo_width_ratio":-1,"mel_spectrogram_32":[1,2,3]}]}""")
        assertEquals(NeteaseSongAnalysis(88), repository.analyze(88))
    }

    @Test fun ttlAndAccountRevisionInvalidatePreviouslyCachedResults() = runBlocking {
        enqueue(analysis(9)); repository.analyze(9)
        clock = 15 * 60_000L
        enqueue(analysis(9)); repository.analyze(9)
        assertEquals(2, server.requestCount)
        sessions.clear()
        enqueue(analysis(9)); repository.analyze(9)
        assertEquals(3, server.requestCount)
    }

    @Test fun permissionErrorsAndMalformedResponsesAreNotCachedOrReplacedWithPresets() = runBlocking {
        enqueue("""{"code":403,"data":[]}""")
        val denied = runCatching { repository.catalog() }.exceptionOrNull() as ApiException
        assertEquals(ErrorKind.Forbidden, denied.kind)
        assertEquals(403, denied.status)
        enqueue("""{"code":200,"data":{}}""")
        assertEquals(ErrorKind.Parse, (runCatching { repository.catalog() }.exceptionOrNull() as ApiException).kind)
        enqueue("""{"code":200,"data":[]}""")
        assertTrue(repository.catalog().isEmpty())
        assertEquals(3, server.requestCount)
    }

    @Test fun anAccountChangeDuringRequestRejectsTheOldResponse() = runBlocking {
        server.enqueue(MockResponse().setBody(analysis(7)).setBodyDelay(200, TimeUnit.MILLISECONDS))
        val pending = async { runCatching { repository.analyze(7) } }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        sessions.clear()
        assertEquals(ErrorKind.NeteaseSessionChanged, (pending.await().exceptionOrNull() as ApiException).kind)
        enqueue(analysis(7))
        assertEquals(7L, repository.analyze(7)!!.songId)
        assertEquals(2, server.requestCount)
    }

    @Test fun memoryCacheEvictsLeastRecentlyUsedSong() = runBlocking {
        for (id in 1L..64L) { enqueue(analysis(id)); repository.analyze(id) }
        repository.analyze(1) // Keep this entry while the second becomes least recently used.
        enqueue(analysis(65)); repository.analyze(65)
        repository.analyze(1)
        enqueue(analysis(2)); repository.analyze(2)
        assertEquals(66, server.requestCount)
    }

    private fun material(bytes: ByteArray = "public preset".toByteArray()) = NeteaseSoundEffect(
        1001, "3D环绕", "", memberType = 1, presetLink = "https://d1.music.126.net/a.ncae",
        presetMd5 = java.security.MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) })

    private fun downloader(body: () -> ResponseBody, beforeResponse: () -> Unit = {}): NeteaseSoundEffectsRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertNull(chain.request().header("Authorization"))
            assertNull(chain.request().header("Cookie"))
            beforeResponse()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body()).build()
        }.build()
        val api = server.url("/").toString()
        return NeteaseSoundEffectsRepository(NeteaseTransport(sessions, NeteaseEndpoints(api, api, api)), client)
    }

    @Test fun originalPresetUsesPublicTopLevelLinkAndVerifiesChecksum() = runBlocking {
        val bytes = "public preset".toByteArray()
        assertArrayEquals(bytes, downloader({ bytes.toResponseBody() }).downloadPreset(material(bytes)))
        assertTrue(runCatching { downloader({ "changed".toByteArray().toResponseBody() }).downloadPreset(material(bytes)) }.isFailure)
    }

    @Test fun paidEffectsAndUntrustedMaterialLinksNeverReachDownloader() = runBlocking {
        val repo = downloader({ error("Invalid material must never be requested") })
        for (effect in listOf(material().copy(memberType = 3), material().copy(soundType = 2),
            material().copy(presetMd5 = ""), material().copy(presetLink = "https://d1.music.126.net.attacker.test/a.ncae"),
            material().copy(presetLink = "http://d1.music.126.net/a.ncae"))) {
            assertFalse(effect.publicOriginal)
            assertTrue(runCatching { repo.downloadPreset(effect) }.isFailure)
        }
        assertNull(originalPresetUrl("https://user@d1.music.126.net/a.ncae"))
        assertNull(originalPresetUrl("https://d1.music.126.net:444/a.ncae"))
        assertNull(originalPresetUrl("https://d1.music.126.net/a.ncae#fragment"))
        assertNotNull(originalPresetUrl("https://d1.music.126.net/dmusic/a.ncae"))
    }

    @Test fun presetRejectsAccountChangeDuringDownload() = runBlocking {
        val repo = downloader({ "public preset".toByteArray().toResponseBody() }, beforeResponse = { runBlocking { sessions.clear() } })
        val failure = runCatching { repo.downloadPreset(material()) }.exceptionOrNull() as ApiException
        assertEquals(ErrorKind.NeteaseSessionChanged, failure.kind)
    }

    @Test fun presetDownloadBoundsBothKnownAndUnknownLengthBodies() = runBlocking {
        val large = ByteArray(2 * 1024 * 1024 + 1)
        assertTrue(runCatching { downloader({ large.toResponseBody() }).downloadPreset(material()) }.isFailure)
        val unknownLength = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = Buffer().write(large)
        }
        assertTrue(runCatching { downloader({ unknownLength }).downloadPreset(material()) }.isFailure)
    }

    @Test fun catalogRetainsUnknownVipStrategiesInsteadOfInferringAccessFromLegacyType() = runBlocking {
        enqueue("""{"code":200,"data":[
          {"id":1,"name":"free","type":1,"link":"https://d1.music.126.net/a.ncae","md5":"${"a".repeat(32)}","newVipStrategyCode":null,"vipAuthRespItem":null},
          {"id":2,"name":"legacy vip","type":3,"link":"https://d1.music.126.net/a.ncae","md5":"${"a".repeat(32)}","newVipStrategyCode":"","vipAuthRespItem":null},
          {"id":3,"name":"strategy","type":1,"link":"https://d1.music.126.net/a.ncae","md5":"${"a".repeat(32)}","newVipStrategyCode":"SVIP"},
          {"id":4,"name":"auth item","type":3,"link":"https://d1.music.126.net/a.ncae","md5":"${"a".repeat(32)}","vipAuthRespItem":{"allowed":true}}
        ]}""")
        val rows = repository.catalog()
        assertTrue(rows[0].publicOriginal)
        assertTrue(rows[1].legacyVipOriginal)
        assertEquals(3, rows[1].memberType)
        assertEquals("SVIP", rows[2].strategyCode)
        assertFalse(rows[2].publicOriginal)
        assertTrue(rows[3].hasVipAuthStrategy)
        assertFalse(rows[3].legacyVipOriginal)
    }

    @Test fun legacyVipDownloadRechecksCurrentBlackVipAndDoesNotAcceptCachedPermissionFromPreviousSelection() = runBlocking {
        sessions.login(mapOf("MUSIC_U" to "test-user-cookie"), BoundProfile(42), sessions.snapshot())
        val expires = System.currentTimeMillis() + 300_000
        var cdnCalls = 0
        val bytes = "public preset".toByteArray()
        val repo = downloader({ bytes.toResponseBody() }, beforeResponse = { cdnCalls++ })
        val effect = material(bytes).copy(memberType = 3)
        enqueue("""{"code":200,"data":{"userId":42,"associator":{"vipCode":100,"expireTime":$expires}}}""")
        assertArrayEquals(bytes, repo.downloadPreset(effect))
        assertEquals(1, cdnCalls)
        assertEquals("/eapi/music-vip-membership/client/vip/info", server.takeRequest().path)
        enqueue("""{"code":200,"data":{"userId":42,"associator":{"vipCode":0,"expireTime":$expires}}}""")
        assertEquals(ErrorKind.Forbidden, (runCatching { repo.downloadPreset(effect) }.exceptionOrNull() as ApiException).kind)
        assertEquals(1, cdnCalls)
        assertEquals(2, server.requestCount)
    }

    @Test fun svipPresetRequiresActiveMatchingRedplusAndCannotUseOnlyBlackVip() = runBlocking {
        sessions.login(mapOf("MUSIC_U" to "test-user-cookie"), BoundProfile(42), sessions.snapshot())
        val expires = System.currentTimeMillis() + 300_000
        var downloads = 0
        val bytes = "original vinyl preset".toByteArray()
        val repo = downloader({ bytes.toResponseBody() }, beforeResponse = { downloads++ })
        val effect = material(bytes).copy(memberType = 4)
        for ((code, expiry) in listOf(300 to 1L, 100 to expires, 0 to expires)) {
            enqueue("""{"code":200,"data":{"userId":42,"associator":{"vipCode":100,"expireTime":$expires},
                "redplus":{"vipCode":$code,"expireTime":$expiry}}}""")
            assertEquals(ErrorKind.Forbidden, (runCatching { repo.downloadPreset(effect) }.exceptionOrNull() as ApiException).kind)
            assertEquals(0, downloads)
        }
        enqueue("""{"code":200,"data":{"userId":42,"redplus":{"vipCode":300,"expireTime":$expires}}}""")
        assertArrayEquals(bytes, repo.downloadPreset(effect))
        assertEquals(1, downloads)
        enqueue("""{"code":200,"data":{"userId":43,"redplus":{"vipCode":300,"expireTime":$expires}}}""")
        assertEquals(ErrorKind.Parse, (runCatching { repo.downloadPreset(effect) }.exceptionOrNull() as ApiException).kind)
        assertEquals(1, downloads)
    }

    @Test fun unknownStrategyCannotDownloadEvenWithRealActiveVip() = runBlocking {
        sessions.login(mapOf("MUSIC_U" to "test-user-cookie"), BoundProfile(42), sessions.snapshot())
        val repo = downloader({ error("Must not load an unsupported authorization strategy") })
        for (candidate in listOf(material().copy(strategyCode = "SVIP"),
            material().copy(memberType = 3, hasVipAuthStrategy = true)))
            assertTrue(runCatching { repo.downloadPreset(candidate) }.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test fun accountSwitchWhileVipProofIsInFlightCannotStartCdnDownload() = runBlocking {
        sessions.login(mapOf("MUSIC_U" to "test-user-cookie"), BoundProfile(42), sessions.snapshot())
        val expires = System.currentTimeMillis() + 300_000
        val repo = downloader({ error("Old account must not reach CDN") })
        server.enqueue(MockResponse().setBody("""{"code":200,"data":{"userId":42,"associator":{"vipCode":100,"expireTime":$expires}}}""")
            .setBodyDelay(200, TimeUnit.MILLISECONDS))
        val pending = async { runCatching { repo.downloadPreset(material().copy(memberType = 3)) } }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        sessions.clear()
        assertEquals(ErrorKind.NeteaseSessionChanged, (pending.await().exceptionOrNull() as ApiException).kind)
    }
}
