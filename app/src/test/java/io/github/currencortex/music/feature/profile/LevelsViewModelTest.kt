package io.github.currencortex.music.feature.profile

import androidx.lifecycle.ViewModelStore
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LevelsViewModelTest {
    private class Fake : LevelsApi {
        val rules = ApiJson.decodeFromString<LevelRules>(levelRulesFixture)
        var current = LevelProgress(6, 1000, mapOf("visit" to 1))
        var submitted = 0
        var error: ApiException? = null
        var gate: CompletableDeferred<Unit>? = null
        var progressGate: CompletableDeferred<Unit>? = null
        var progressCalls = 0
        var cancelledProgress = 0
        var submissionResult = LevelTrialResult(60, true, 7)
        override suspend fun rules(owner: RequestSession) = rules
        override suspend fun progress(owner: RequestSession): LevelProgress {
            progressCalls++
            val pending = progressGate
            progressGate = null
            try { pending?.await() }
            catch (e: CancellationException) { cancelledProgress++; throw e }
            return current
        }
        override suspend fun trial(owner: RequestSession, rules: LevelTrialRules) =
            LevelTrial(List(rules.questionCount) { TrialQuestion("Question $it", listOf("A", "B", "C", "D")) })
        override suspend fun submit(owner: RequestSession, trial: LevelTrial, answers: List<Int>): LevelTrialResult {
            submitted++
            gate?.await()
            error?.let { throw it }
            current = current.copy(level = submissionResult.level)
            return submissionResult
        }
    }
    @Test fun passingSubmissionCancelsPendingPollWithoutLeavingRefreshDisabled() =
        submissionDuringPoll(LevelTrialResult(60, true, 7))

    @Test fun unsuccessfulTrialCanBeRetakenAfterCancellingPendingPoll() =
        submissionDuringPoll(LevelTrialResult(40, false, 6))

    @Test fun failedSubmissionKeepsAnswersAndAllowsRefreshAfterCancellingPendingPoll() =
        submissionDuringPoll(LevelTrialResult(60, true, 7), ApiException(ErrorKind.Timeout))

    @Test fun staleQuestionsCanBeReloadedAfterCancellingPendingPoll() =
        submissionDuringPoll(LevelTrialResult(60, true, 7), ApiException(ErrorKind.Unknown, 400))

    private fun submissionDuringPoll(result: LevelTrialResult, error: ApiException? = null) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        var polling: Job? = null
        try {
            val api = Fake().apply { submissionResult = result; this.error = error }
            val vm = LevelsViewModel(api, MutableStateFlow(RequestSession("https://fixture/", "one")))
            store.put("levels", vm); runCurrent()
            vm.startTrial(); runCurrent(); repeat(50) { vm.answer(it, 0) }
            polling = backgroundScope.launch { vm.poll() }; runCurrent()
            val pending = CompletableDeferred<Unit>()
            api.progressGate = pending
            advanceTimeBy(30_000); runCurrent()
            assertTrue(vm.state.value.loading)
            assertTrue(vm.state.value.canSubmit)

            vm.submit()
            assertFalse("Submission must clear the cancelled poll's loading state", vm.state.value.loading)
            runCurrent()
            assertEquals(1, api.cancelledProgress)
            assertEquals(1, api.submitted)
            assertFalse(vm.state.value.loading); assertFalse(vm.state.value.busy)
            if (error == null) {
                assertEquals(result, vm.state.value.result)
                assertEquals(result.level, vm.state.value.progress!!.level)
                assertEquals(!result.passed, vm.state.value.canStartTrial)
            } else if (error.status == 400) {
                assertNull(vm.state.value.trial)
                assertTrue(vm.state.value.canStartTrial)
            } else {
                assertNotNull(vm.state.value.trial)
                assertEquals(50, vm.state.value.answers.count { it >= 0 })
                assertTrue(vm.state.value.canSubmit)
            }
            pending.complete(Unit); runCurrent()
            val calls = api.progressCalls
            vm.refresh(); runCurrent()
            assertEquals("Manual refresh remains available", calls + 1, api.progressCalls)
            assertFalse(vm.state.value.loading)
        } finally { polling?.cancel(); store.clear(); Dispatchers.resetMain() }
    }
    @Test fun eligibilityIncompleteAnswersRetryAndServerResultControlTheFlow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val api = Fake(); api.current = LevelProgress(5, 999)
            val owners = MutableStateFlow(RequestSession("https://fixture/", "account"))
            val vm = LevelsViewModel(api, owners); store.put("levels", vm); runCurrent()
            assertFalse(vm.state.value.canStartTrial); vm.startTrial(); runCurrent(); assertNull(vm.state.value.trial)
            api.current = LevelProgress(6, 1000); vm.refresh(); runCurrent()
            vm.startTrial(); runCurrent()
            vm.submit(); runCurrent(); assertEquals(0, api.submitted)
            repeat(50) { vm.answer(it, it % 4) }
            assertTrue(vm.state.value.canSubmit)
            api.error = ApiException(ErrorKind.Timeout)
            vm.submit(); vm.submit(); runCurrent()
            assertEquals(1, api.submitted); assertNotNull(vm.state.value.trial)
            assertEquals(50, vm.state.value.answers.count { it >= 0 })
            api.error = null; vm.submit(); runCurrent()
            assertEquals(7, vm.state.value.progress!!.level); assertTrue(vm.state.value.result!!.passed)
            assertNull(vm.state.value.trial); assertFalse(vm.state.value.canStartTrial)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun changingAccountClearsPrivateProgressAndCancelsPendingSubmission() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val api = Fake(); val owners = MutableStateFlow(RequestSession("https://fixture/", "one"))
            val vm = LevelsViewModel(api, owners); store.put("levels", vm); runCurrent()
            vm.startTrial(); runCurrent(); repeat(50) { vm.answer(it, 0) }
            api.gate = CompletableDeferred(); vm.submit(); runCurrent(); assertTrue(vm.state.value.busy)
            owners.value = RequestSession("https://fixture/", null); runCurrent()
            api.gate!!.complete(Unit); runCurrent()
            assertFalse(vm.state.value.signedIn); assertNull(vm.state.value.progress)
            assertNull(vm.state.value.trial); assertNull(vm.state.value.result); assertTrue(vm.state.value.answers.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun rejectedOldQuestionsAreDiscardedAndDailyCountsRefreshFromServer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val api = Fake(); val vm = LevelsViewModel(api, MutableStateFlow(RequestSession("https://fixture/", "one")))
            store.put("levels", vm); runCurrent(); vm.startTrial(); runCurrent(); repeat(50) { vm.answer(it, 0) }
            api.error = ApiException(ErrorKind.Unknown, 400); vm.submit(); runCurrent()
            assertNull(vm.state.value.trial); assertTrue(vm.state.value.answers.isEmpty())
            assertTrue(vm.state.value.error!!.contains("重新进入"))
            api.current = LevelProgress(6, 1010, mapOf("visit" to 1, "listen" to 0))
            vm.refresh(); runCurrent(); assertEquals(0, vm.state.value.progress!!.today["listen"])
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
