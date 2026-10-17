package io.github.currencortex.music.feature.update

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateMenuMotionTest {
    @Test fun reversingWavesRetainsTheirValueAndVelocityUntilTheNextFrame() = runTest {
        val clock = BroadcastFrameClock()
        val motion = UpdateMenuMotion()
        backgroundScope.launch(clock) { motion.animate(true) }
        runCurrent()
        var frame = 0L
        fun advance() { clock.sendFrame(frame * 16_000_000L); frame++; runCurrent() }
        repeat(8) { advance() }
        val waves = listOf(motion.path, motion.scale, motion.opacity)
        val values = waves.map { it.value }
        val velocities = waves.map { it.velocity }
        assertTrue(velocities.all { it > 0f })
        backgroundScope.launch(clock) { motion.animate(false) }
        runCurrent()
        waves.forEachIndexed { i, wave ->
            assertEquals("Retarget value $i", values[i], wave.value, .0001f)
            assertEquals("Retarget velocity $i", velocities[i], wave.velocity, .0001f)
        }
        advance()
        assertTrue(motion.visible)
        assertTrue(motion.scale.value > .01f)
        repeat(60) { advance() }
        waves.forEach { assertEquals(0f, it.value, .001f) }
        assertFalse(motion.visible)
    }
}
