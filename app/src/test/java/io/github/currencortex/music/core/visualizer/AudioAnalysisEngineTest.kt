package io.github.currencortex.music.core.visualizer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioAnalysisEngineTest {
    private val active = CaptureRequest(true, true, true, true, true, 7)
    @Test fun allGatesPreventCapture() = runTest {
        var starts = 0
        val source = object : AudioCaptureSource {
            override fun frames(audioSessionId: Int) = flow<AudioCapturePacket> { starts++; awaitCancellation() }
        }
        val engine = AudioAnalysisEngine(backgroundScope, source, StandardTestDispatcher(testScheduler))
        val gates = listOf(CaptureRequest(), active.copy(visible = false), active.copy(permission = false),
            active.copy(supported = false), active.copy(playing = false), active.copy(sessionId = 0))
        gates.forEach { engine.request(it); runCurrent(); assertEquals(it.status, engine.status.value) }
        assertEquals(0, starts)
        engine.close()
    }
    @Test fun oldSessionReleasedBeforeNewCaptureAndHideClearsFrames() = runTest {
        val events = mutableListOf<String>()
        val source = object : AudioCaptureSource {
            override fun frames(audioSessionId: Int) = flow {
                events += "start:$audioSessionId"
                try {
                    emit(AudioCapturePacket(1_000_000_000L, 48000, ByteArray(1024), ByteArray(1024) { 128.toByte() }))
                    awaitCancellation()
                } finally { events += "stop:$audioSessionId" }
            }
        }
        val engine = AudioAnalysisEngine(backgroundScope, source, StandardTestDispatcher(testScheduler))
        engine.request(active); runCurrent()
        assertEquals(CaptureStatus.CAPTURING, engine.status.value)
        engine.request(active.copy(sessionId = 9)); runCurrent()
        assertEquals(listOf("start:7", "stop:7", "start:9"), events)
        engine.request(active.copy(visible = false)); runCurrent()
        assertEquals("stop:9", events.last()); assertEquals(0, engine.frames.value.sampleRateHz)
        engine.close()
    }
    @Test fun deniedOrBrokenSourceIsContainedAndCanRestart() = runTest {
        var starts = 0
        val source = object : AudioCaptureSource {
            override fun frames(audioSessionId: Int) = flow<AudioCapturePacket> { starts++; throw SecurityException() }
        }
        val engine = AudioAnalysisEngine(backgroundScope, source, StandardTestDispatcher(testScheduler))
        engine.request(active); runCurrent()
        assertEquals(CaptureStatus.FAILED, engine.status.value)
        engine.request(active.copy(enabled = false)); runCurrent()
        engine.request(active); runCurrent(); assertEquals(2, starts)
        engine.close()
    }
    @Test fun noCallbackTimeoutBecomesFailureRatherThanCancellingEngine() = runTest {
        val source = object : AudioCaptureSource {
            override fun frames(audioSessionId: Int) = flow<AudioCapturePacket> { withTimeout(10) { awaitCancellation() } }
        }
        val engine = AudioAnalysisEngine(backgroundScope, source, StandardTestDispatcher(testScheduler))
        engine.request(active); runCurrent(); advanceTimeBy(20); runCurrent()
        assertEquals(CaptureStatus.FAILED, engine.status.value)
        engine.request(active.copy(playing = false)); runCurrent()
        assertEquals(CaptureStatus.PAUSED, engine.status.value)
        engine.close()
    }
    @Test fun timelineSeekOrTrackChangeRestartsGeneration() = runTest {
        val source = object : AudioCaptureSource {
            override fun frames(audioSessionId: Int) = flow {
                emit(AudioCapturePacket(1_000_000_000L, 48000, ByteArray(1024), ByteArray(1024) { 128.toByte() }))
                awaitCancellation()
            }
        }
        val engine = AudioAnalysisEngine(backgroundScope, source, StandardTestDispatcher(testScheduler))
        engine.request(active.copy(trackId = 10)); runCurrent()
        val old = engine.frames.value.generation
        engine.request(active.copy(trackId = 10, timelineRevision = 1)); runCurrent()
        assertTrue(engine.frames.value.generation > old)
        val seek = engine.frames.value.generation
        engine.request(active.copy(trackId = 20, timelineRevision = 1)); runCurrent()
        assertTrue(engine.frames.value.generation > seek)
        engine.close()
    }
}
