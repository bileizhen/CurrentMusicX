package io.github.currencortex.music.core.media

import org.junit.Assert.*
import org.junit.Test

class FluidBackgroundMotionTest {
    private fun sample(bpm: Float, pulse: Float = 0f) = DockAudioLevel.Sample(.5f, .3f, 1, bpm, pulse)
    private fun settle(motion: FluidBackgroundMotion, sample: DockAudioLevel.Sample?, frames: Int = 300, dt: Float = 1f / 60) {
        repeat(frames) { motion.update(sample, dt) }
    }

    @Test fun continuouslyRespondsToTempoAndAttacksRatherThanSelectingPresets() {
        val motion = FluidBackgroundMotion()
        settle(motion, sample(91f)); val first = motion.speed
        settle(motion, sample(92f)); assertTrue(motion.speed > first)
        val beforeAttack = motion.speed
        motion.update(sample(92f, 1f), .04f)
        assertTrue(motion.speed > beforeAttack)
        assertTrue("Acceleration must be smoothed", motion.speed - beforeAttack < .1f)
        settle(motion, sample(150f)); val fast = motion.speed
        settle(motion, sample(50f)); assertTrue(motion.speed < fast * .7f)
    }

    @Test fun missingSamplesSilenceAndInvalidValuesRemainBounded() {
        val motion = FluidBackgroundMotion()
        settle(motion, sample(200f))
        settle(motion, null, 600); assertEquals(.35f, motion.speed, .001f)
        settle(motion, DockAudioLevel.Sample(), 600); assertEquals(.18f, motion.speed, .001f)
        settle(motion, DockAudioLevel.Sample(Float.NaN, Float.NaN, 1, Float.NaN, Float.NaN))
        assertTrue(motion.update(sample(200f), Float.NaN, Float.NaN).isFinite())
    }

    @Test fun smoothingDoesNotDependOnRefreshRate() {
        val low = FluidBackgroundMotion(); val high = FluidBackgroundMotion()
        settle(low, sample(130f), 60, 1f / 30)
        settle(high, sample(130f), 240, 1f / 120)
        assertEquals(low.speed, high.speed, .001f)
    }

    @Test fun pauseGraduallySlowsButNeverFreezesAndResumeAcceleratesSmoothly() {
        val motion = FluidBackgroundMotion()
        settle(motion, sample(150f))
        val playing = motion.speed
        motion.update(sample(150f), .016f, playing = false)
        assertTrue(motion.speed < playing && motion.speed > .12f)
        repeat(300) { motion.update(sample(150f), 1f / 60, playing = false) }
        assertEquals(.12f, motion.speed, .001f)
        val paused = motion.speed
        motion.update(sample(150f), .016f)
        assertTrue(motion.speed > paused && motion.speed < playing)
    }

    @Test fun consumersCannotDisableEachOtherAndCloseIsIdempotent() {
        val meter = DockAudioLevel()
        val dock = meter.acquire(); val player = meter.acquire(); val preview = meter.acquire()
        meter.publish(.7f, .2f, 123, 140f, 1f)
        dock.close(); dock.close(); player.close()
        assertTrue(meter.enabled); assertEquals(140f, meter.sample.bpm)
        meter.enabled = false
        assertTrue(meter.enabled)
        preview.close()
        assertFalse(meter.enabled); assertEquals(0L, meter.sample.timestampNanos)
    }
}
