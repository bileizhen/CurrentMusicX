package io.github.currencortex.music.data.netease.tasks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.concurrent.TimeUnit

class NeteaseTasksTest {
    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val lock = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = lock.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = MockWebServer()
    private lateinit var sessions: NeteaseSessionStore
    private lateinit var repository: NeteaseTaskRepository
    private val store = MemoryStore()
    private val preferences = NeteaseTaskPreferences(store)
    private val foreground = MutableStateFlow(false)
    @Before fun setup() = runBlocking<Unit> {
        server.start()
        sessions = NeteaseSessionStore(object : TokenStore {
            override fun read(): String? = null
            override fun write(token: String) = Unit
            override fun clear() = Unit
        }, scope)
        sessions.ready.await()
        val base = server.url("/").toString()
        repository = NeteaseTaskRepository(NeteaseTransport(sessions, NeteaseEndpoints(base, base, base)))
    }
    @After fun close() { scope.cancel(); server.shutdown() }
    private suspend fun login(uid: Long = 42) = sessions.login(mapOf("MUSIC_U" to "fixture-$uid"), BoundProfile(uid, "Fixture"), sessions.snapshot())
    private fun enqueue(body: String = """{"code":200}""") { server.enqueue(MockResponse().setBody(body)) }
    private fun automation(clock: () -> Long = System::currentTimeMillis) = NeteaseTaskAutomation(repository, preferences, foreground, scope, clock, preferences.enabled)

    @Test fun loggedOutTasksNeverSendRequests() = runBlocking<Unit> {
        val result = appResult { repository.load(NeteaseTaskGroup.VIP) }
        assertEquals(ErrorKind.NeteaseBindingRequired, (result as AppResult.Failure).kind)
        assertEquals(0, server.requestCount)
    }
    @Test fun taskQueriesUseOfficialPathsAndOnlyNeteaseCredentials() = runBlocking<Unit> {
        login()
        val paths = listOf("point/signed/get", "usertool/task/list/all", "vipnewcenter/app/level/task/list", "nmusician/workbench/mission/cycle/list")
        NeteaseTaskGroup.entries.forEachIndexed { index, group ->
            enqueue("""{"code":200,"data":[]}""")
            if (group in setOf(NeteaseTaskGroup.YUNBEI, NeteaseTaskGroup.VIP)) enqueue()
            repository.load(group)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/weapi/${paths[index]}", request.path)
            assertNull(request.getHeader("Authorization"))
            assertTrue(request.getHeader("Cookie")!!.contains("MUSIC_U=fixture-42"))
            if (group in setOf(NeteaseTaskGroup.YUNBEI, NeteaseTaskGroup.VIP)) server.takeRequest()
        }
    }
    @Test fun expiredLoginStopsFurtherTaskCallsAndPreservesUpstreamReason() = runBlocking<Unit> {
        login(); enqueue("""{"code":301,"message":"请重新登录"}""")
        val result = appResult { repository.load(NeteaseTaskGroup.VIP) } as AppResult.Failure
        assertEquals("请重新登录", result.message)
        assertTrue(sessions.state.value.stale)
        assertTrue(appResult { repository.load(NeteaseTaskGroup.YUNBEI) } is AppResult.Failure)
        assertEquals(1, server.requestCount)
    }
    @Test fun rewardsMustComeFromAFreshServerListAndOnlyClaimableIdsAreUsed() = runBlocking<Unit> {
        login()
        enqueue("""{"code":200,"data":{"unGetIds":["123_1","456_2","https://bad"],"tasks":[{"taskId":7,"taskName":"真实听歌","currentProgress":2,"targetCount":3}]}}""")
        enqueue()
        assertEquals("已领取可用成长值", repository.claimVipRewards())
        assertEquals("/weapi/vipnewcenter/app/level/task/list", server.takeRequest().path)
        assertEquals("/weapi/vipnewcenter/app/level/task/reward/get", server.takeRequest().path)
        enqueue("""{"code":200,"data":{"unGetIds":[]}}""")
        assertEquals("暂无可领取的成长值", repository.claimVipRewards())
        assertEquals(3, server.requestCount)
    }
    @Test fun taskProgressNeverTreatsUnknownNumericStatusAsCompleted() {
        val raw = Json.parseToJsonElement("""{"data":[{"userTaskId":3,"taskName":"听歌","status":1,"currentCount":2,"targetCount":5},{"userTaskId":4,"taskName":"未知状态","status":2}]}""").jsonObject
        val page = NeteaseTaskRepository.parsePage(NeteaseTaskGroup.YUNBEI, raw)
        assertEquals("2 / 5", page.items[0].progress)
        assertEquals("以网易云任务状态为准", page.items[1].progress)
    }
    @Test fun checkInIsSerializedDeduplicatedAndOwnedByUidAcrossRestarts() = runBlocking<Unit> {
        login(); enqueue()
        val engine = automation()
        coroutineScope { List(5) { async { engine.sign() } }.awaitAll() }
        assertEquals(1, server.requestCount)
        assertEquals(NeteaseTaskPreferences.day(), preferences.snapshot(42).signedDay)
        assertEquals(1, preferences.snapshot(42).logs.size)
        val restarted = automation()
        assertEquals("今日已签到", restarted.sign())
        login(43); enqueue("""{"code":-2,"msg":"重复签到"}""")
        assertEquals("今日已签到", restarted.sign())
        assertEquals(2, server.requestCount)
        assertEquals(1, preferences.snapshot(43).logs.size)
    }
    @Test fun failedCheckInIsNotPersistedAsSuccessAndManualRetryCanRecover() = runBlocking<Unit> {
        login(); enqueue("""{"code":403,"message":"暂不可用"}""")
        val engine = automation()
        assertTrue(appResult { engine.sign() } is AppResult.Failure)
        assertEquals("", preferences.snapshot(42).signedDay)
        assertFalse(preferences.snapshot(42).logs.single().success)
        enqueue(); engine.sign()
        assertEquals(2, server.requestCount)
        assertTrue(preferences.snapshot(42).logs.first().success)
    }
    @Test fun automaticCheckInRequiresMasterSwitchForegroundAndExplicitAccountOptIn() = runBlocking<Unit> {
        login(); preferences.automatic(42, true)
        automation(); foreground.value = true
        delay(150); assertEquals(0, server.requestCount)
        enqueue(); enqueue(); preferences.enable(true)
        withTimeout(5_000) { preferences.options(42).first { it.signedDay.isNotBlank() && it.yunbeiDay.isNotBlank() } }
        assertEquals(2, server.requestCount)
        foreground.value = false; delay(40); foreground.value = true
        delay(150); assertEquals(2, server.requestCount)
        preferences.enable(false)
        login(43); preferences.automatic(43, true)
        delay(150); assertEquals(2, server.requestCount)
        foreground.value = false; preferences.enable(true)
        delay(150); assertEquals(2, server.requestCount)
    }
    @Test fun journalIsBoundedSanitizedAndPreferencesRemainAccountIsolated() = runBlocking<Unit> {
        preferences.enable(true); preferences.automatic(42, true)
        repeat(25) { preferences.record(42, "result|$it\nokay", true, now = it.toLong()) }
        val restored = NeteaseTaskPreferences(store)
        assertTrue(restored.enabled.first())
        assertEquals(20, restored.snapshot(42).logs.size)
        assertEquals(24L, restored.snapshot(42).logs.first().time)
        assertFalse(restored.snapshot(42).logs.first().label.contains('|'))
        assertEquals(NeteaseTaskOptions(), restored.snapshot(43))
    }
    @Test fun dailyBoundaryUsesNeteaseChinaDate() {
        assertEquals("2026-10-20", NeteaseTaskPreferences.day(Instant.parse("2026-10-20T15:59:59Z").toEpochMilli()))
        assertEquals("2026-10-21", NeteaseTaskPreferences.day(Instant.parse("2026-10-20T16:00:00Z").toEpochMilli()))
    }
    @Test fun nativeTaskShapesPreserveZeroUserTaskIdsAndVipActionTitles() {
        val cloud = Json.parseToJsonElement("""{"data":[{"userTaskId":0,"taskId":11,"taskName":"关注歌手"},{"userTaskId":0,"taskId":12,"taskName":"听歌"}]}""").jsonObject
        assertEquals(listOf("11", "12"), NeteaseTaskRepository.parsePage(NeteaseTaskGroup.YUNBEI, cloud).items.map { it.id })
        val vip = Json.parseToJsonElement("""{"data":{"taskList":[{"taskItems":[{"taskId":"null_7","name":"","action":"浏览黑胶时光机","targetWorth":15,"currentProgress":3}]}]}}""").jsonObject
        val item = NeteaseTaskRepository.parsePage(NeteaseTaskGroup.VIP, vip).items.single()
        assertEquals("浏览黑胶时光机", item.title); assertEquals("3 / 15", item.progress)
    }
    @Test fun cloudClaimsOnlyCompletedTasksAndUsesServerDepositAndPeriod() = runBlocking<Unit> {
        login()
        enqueue("""{"code":200,"data":[{"userTaskId":88,"completed":true,"period":1,"depositCode":1304},{"userTaskId":89,"completed":false,"period":1,"depositCode":1304},{"userTaskId":90,"completed":true,"period":1}]}""")
        enqueue("""{"code":200,"data":true}""")
        assertEquals("已领取 1 项任务奖励", repository.claimRewards(NeteaseTaskGroup.YUNBEI))
        assertEquals("/weapi/usertool/task/todo/query", server.takeRequest().path)
        assertEquals("/weapi/usertool/task/point/receive", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }
    @Test fun musicianClaimsOnlyCompletedUnclaimedMissions() = runBlocking<Unit> {
        login()
        enqueue("""{"code":200,"data":{"taskList":[{"userMissionId":7,"status":1,"period":1},{"userMissionId":8,"status":2,"period":1},{"userMissionId":9,"status":3,"period":1}]}}""")
        enqueue()
        assertEquals("已领取 1 项任务奖励", repository.claimRewards(NeteaseTaskGroup.MUSICIAN))
        assertEquals("/weapi/nmusician/workbench/mission/cycle/list", server.takeRequest().path)
        assertEquals("/weapi/nmusician/workbench/mission/reward/obtain/new", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }
    @Test fun unsupportedSuccessEnvelopeAndFalseVipSignDoNotMarkTheDayComplete() = runBlocking<Unit> {
        login(); val engine = automation()
        enqueue("""{"code":200,"message":"功能暂不支持"}""")
        assertTrue(appResult { engine.sign() } is AppResult.Failure)
        assertEquals("", preferences.snapshot(42).signedDay)
        enqueue("""{"code":200,"data":false}""")
        assertTrue(appResult { engine.sign(NeteaseCheckIn.VIP) } is AppResult.Failure)
        assertEquals("", preferences.snapshot(42).vipDay)
    }
    @Test fun rejectedRewardReceiptIsLoggedAsAFailure() = runBlocking<Unit> {
        login()
        enqueue("""{"code":200,"data":{"unGetIds":["123_1"]}}""")
        enqueue("""{"code":200,"data":false}""")
        assertTrue(appResult { automation().claim(NeteaseTaskGroup.VIP) } is AppResult.Failure)
        assertFalse(preferences.snapshot(42).logs.single().success)
    }
}
