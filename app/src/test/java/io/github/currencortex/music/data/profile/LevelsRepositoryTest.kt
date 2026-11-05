package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

internal val levelRulesFixture = """{"dailyPoints":10,"maxPoints":2147483647,"timeZone":"Asia/Shanghai",
    "pointSources":[{"id":"visit","points":10,"dailyLimit":1},{"id":"listen","points":2,"dailyLimit":5},
    {"id":"post","points":5,"dailyLimit":1},{"id":"comment","points":2,"dailyLimit":2}],"listenSeconds":60,
    "levels":[{"level":1,"points":0},{"level":2,"points":50},{"level":6,"points":1000}],
    "trial":{"level":6,"plusLevel":7,"questionCount":50,"passScore":60,"maxScore":100}}"""

class LevelsRepositoryTest {
    @Test fun rulesArePublicButProgressAndZeroBasedAnswersBelongToTheCurrentAccount() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val owner = RequestSession(base, "fixture-token")
            val repository = LevelsRepository(ApiClient({ base }, { owner.token }, {}))
            server.enqueue(MockResponse().setBody(levelRulesFixture))
            val rules = repository.rules(owner)
            assertEquals(29L, rules.dailyMaximum)
            assertEquals("Asia/Shanghai", rules.timeZone)
            val public = server.takeRequest()
            assertEquals("/cm/levels/rules", public.path); assertNull(public.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"level":6,"points":1000,"today":{"visit":1,"listen":2}}"""))
            assertEquals(2, repository.progress(owner).today["listen"])
            assertEquals("Bearer fixture-token", server.takeRequest().getHeader("Authorization"))
            val question = """{"prompt":"Test","options":["A","B","C","D"]}"""
            server.enqueue(MockResponse().setBody("""{"questions":[${List(50) { question }.joinToString(",")}]}"""))
            val trial = repository.trial(owner, rules.trial)
            assertEquals(50, trial.questions.size); assertEquals("/cm/levels/trial", server.takeRequest().path)
            try { repository.submit(owner, trial, List(49) { 0 }); fail() } catch (_: IllegalArgumentException) { }
            try { repository.submit(owner, trial, List(50) { 4 }); fail() } catch (_: IllegalArgumentException) { }
            assertEquals(3, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"score":60,"passed":true,"level":7}"""))
            val result = repository.submit(owner, trial, List(50) { it % 4 })
            assertTrue(result.passed); assertEquals(7, result.level)
            val sent = server.takeRequest()
            assertEquals("POST", sent.method); assertEquals("Bearer fixture-token", sent.getHeader("Authorization"))
            assertEquals("{\"answers\":[${List(50) { it % 4 }.joinToString(",")}]}", sent.body.readUtf8())
        }
    }
    @Test fun oldQuestionCountAndStaleAccountAreRejected() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repository = LevelsRepository(ApiClient({ base }, { "current" }, {}))
            val rules = ApiJson.decodeFromString<LevelRules>(levelRulesFixture)
            server.enqueue(MockResponse().setBody("""{"questions":[{"prompt":"old","options":["a","b"]}]}"""))
            assertEquals(ErrorKind.Parse, (appResult { repository.trial(RequestSession(base, "current"), rules.trial) } as AppResult.Failure).kind)
            assertEquals(ErrorKind.Unauthorized, (appResult { repository.progress(RequestSession(base, "old")) } as AppResult.Failure).kind)
            assertEquals(1, server.requestCount)
        }
    }
}
