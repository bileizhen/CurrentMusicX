package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.effects.NeteaseSongAnalysis
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SmartTuningControllerTest {
    private val saved = EqualizerSettings(true, EqualizerPreset.ROCK, EqualizerPreset.ROCK.gains)
    private fun analysis(id: Long, bass: Float = .1f) = NeteaseSongAnalysis(id, bpm = 120f,
        vocalEnergy = .2f, bassEnergy = bass)

    @Test fun oldNonCancellableResponseCannotReplaceNewTrackOrDelayItsRequest() = runTest {
        val manual = MutableStateFlow(saved)
        val config = MutableStateFlow(SoundEffectsPreferences(true))
        val song = MutableStateFlow<Song?>(Song(1, "first"))
        val old = CompletableDeferred<NeteaseSongAnalysis?>()
        val calls = mutableListOf<Long>()
        val controller = SmartTuningController(manual, config, song, { id ->
            calls += id
            if (id == 1L) withContext(NonCancellable) { old.await() } else analysis(id, .9f)
        }, backgroundScope)
        try {
            runCurrent()
            assertTrue(controller.state.value.loading)
            song.value = Song(2, "second")
            runCurrent()
            assertEquals(listOf(1L, 2L), calls)
            assertEquals(2L, controller.state.value.analysis!!.songId)
            val effective = controller.effective.value
            old.complete(analysis(1))
            runCurrent()
            assertEquals(2L, controller.state.value.analysis!!.songId)
            assertEquals(effective, controller.effective.value)
            assertEquals(saved, manual.value)
        } finally { old.complete(null); controller.close() }
    }

    @Test fun disablingDuringRequestRestoresManualAndRejectsLateAnalysis() = runTest {
        val manual = MutableStateFlow(saved)
        val config = MutableStateFlow(SoundEffectsPreferences(true))
        val song = MutableStateFlow<Song?>(Song(1, "first"))
        val late = CompletableDeferred<NeteaseSongAnalysis?>()
        val controller = SmartTuningController(manual, config, song,
            { withContext(NonCancellable) { late.await() } }, backgroundScope)
        try {
            runCurrent()
            config.value = SoundEffectsPreferences(false)
            runCurrent()
            assertEquals(saved, controller.effective.value)
            assertFalse(controller.state.value.active)
            late.complete(analysis(1))
            runCurrent()
            assertEquals(saved, controller.effective.value)
            assertNull(controller.state.value.analysis)
        } finally { late.complete(null); controller.close() }
    }

    @Test fun missingDataDifferentSourceAndVideoUseManualWithoutWrongRequests() = runTest {
        val manual = MutableStateFlow(saved.copy(enabled = false))
        val config = MutableStateFlow(SoundEffectsPreferences(true))
        val song = MutableStateFlow<Song?>(Song(7, "other", musicSource = MusicSource.NAVIDROME))
        val calls = mutableListOf<Long>()
        val controller = SmartTuningController(manual, config, song, { calls += it; null }, backgroundScope)
        try {
            runCurrent()
            assertTrue(calls.isEmpty())
            song.value = Song(7, "video", video = true)
            runCurrent()
            assertTrue(calls.isEmpty())
            song.value = Song(7, "NetEase")
            runCurrent()
            assertEquals(listOf(7L), calls)
            assertEquals(manual.value, controller.effective.value)
            assertFalse(controller.state.value.active)
            song.value = Song(7, "different source, same id", musicSource = MusicSource.QQ_MUSIC)
            runCurrent()
            assertEquals(listOf(7L), calls)
            assertEquals(manual.value, controller.effective.value)
        } finally { controller.close() }
    }

    @Test fun enabledResultDoesNotPersistOverManualAndOffRestoresLatestSavedCurve() = runTest {
        val manual = MutableStateFlow(saved.copy(enabled = false))
        val config = MutableStateFlow(SoundEffectsPreferences(true))
        val song = MutableStateFlow<Song?>(Song(1, "first"))
        val controller = SmartTuningController(manual, config, song, { analysis(it) }, backgroundScope)
        try {
            runCurrent()
            assertTrue(controller.effective.value.enabled)
            assertEquals(saved.copy(enabled = false), manual.value)
            val next = saved.withBand(4, -600).copy(enabled = false)
            manual.value = next
            runCurrent()
            assertNotEquals(next, controller.effective.value)
            config.value = SoundEffectsPreferences(false)
            runCurrent()
            assertEquals(next, controller.effective.value)
        } finally { controller.close() }
    }

    @Test fun invalidFieldsCannotBecomeGainAndEmotionLabelsAloneNeverChooseACurve() {
        assertNull(localSmartCurve(NeteaseSongAnalysis(1, emotions = listOf("摇滚", "低音"))))
        assertNull(localSmartCurve(NeteaseSongAnalysis(1, bpm = Float.POSITIVE_INFINITY,
            bassEnergy = Float.NaN, vocalEnergy = -1f)))
        listOf(0f, .1f, .5f, 1f).forEach { bass ->
            listOf(0f, 1f).forEach { vocal ->
                val gains = localSmartCurve(NeteaseSongAnalysis(1, bpm = 300f,
                    bassEnergy = bass, vocalEnergy = vocal))!!
                assertEquals(5, gains.size)
                assertTrue(gains.all { it in -300..300 && it % 100 == 0 })
            }
        }
    }

    @Test fun mismatchedReturnedSongCannotApplyItsCurve() = runTest {
        val manual = MutableStateFlow(saved)
        val controller = SmartTuningController(manual, MutableStateFlow(SoundEffectsPreferences(true)),
            MutableStateFlow(Song(1, "first")), { analysis(999) }, backgroundScope)
        try {
            runCurrent()
            assertNull(controller.state.value.analysis)
            assertEquals(saved, controller.effective.value)
        } finally { controller.close() }
    }

    @Test fun manualPreviewWinsBeforePreferenceWriteAndSmartCanBeEnabledAgain() = runTest {
        val manual = MutableStateFlow(saved)
        val config = MutableStateFlow(SoundEffectsPreferences(true))
        val controller = SmartTuningController(manual, config, MutableStateFlow(Song(1, "song")),
            { analysis(it) }, backgroundScope)
        try {
            runCurrent()
            assertTrue(controller.state.value.active)
            val preview = saved.withBand(1, -900)
            controller.previewManual(preview)
            // Persistence has not published yet; the service must nevertheless see the preview.
            runCurrent()
            assertTrue(config.value.smartEnabled)
            assertEquals(preview, controller.effective.value)
            config.value = SoundEffectsPreferences(false)
            runCurrent()
            assertEquals(preview, controller.effective.value)
            manual.value = preview
            runCurrent()
            assertEquals(preview, controller.effective.value)
            config.value = SoundEffectsPreferences(true)
            runCurrent()
            assertTrue(controller.state.value.active)
            assertNotEquals(preview, controller.effective.value)
            assertEquals(preview, manual.value)
        } finally { controller.close() }
    }

    @Test fun disabledDefaultMakesNoAnalysisRequestsOnPlaybackChanges() = runTest {
        val config = MutableStateFlow(SoundEffectsPreferences())
        val song = MutableStateFlow<Song?>(Song(1, "first"))
        val calls = mutableListOf<Long>()
        val controller = SmartTuningController(MutableStateFlow(saved), config, song,
            { calls += it; analysis(it) }, backgroundScope)
        try {
            runCurrent()
            song.value = Song(2, "next")
            runCurrent()
            assertTrue(calls.isEmpty())
            assertEquals(saved, controller.effective.value)
            config.value = SoundEffectsPreferences(true)
            runCurrent()
            assertEquals(listOf(2L), calls)
        } finally { controller.close() }
    }
}
