package io.github.currencortex.music.ui.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Quantises a raw gravity vector into a stable light direction for the floating bar highlight.
 *
 * Miuix's `rememberDeviceTilt` publishes a new value for every sensor event (about 50 Hz at
 * `SENSOR_DELAY_GAME`). Feeding that stream straight into composition would invalidate the
 * floating bar on every sample, which both re-uploads the AGSL highlight uniforms continuously and
 * keeps Compose permanently non-idle. This reducer returns a value that only changes when the
 * device was actually tilted by a visible amount, so a resting device produces no recomposition at
 * all while a deliberate tilt still moves the specular light.
 *
 * The class holds no Android or Compose state and is covered by JVM unit tests.
 */
class TiltLightDirection(
    private val stepRadians: Float = DEFAULT_STEP_RADIANS,
) {
    private var directional = false
    private var angle = FLAT_ANGLE

    /** Current quantised light direction in UV space (radians). */
    val angleRadians: Float
        get() = angle

    /**
     * Feeds one gravity sample.
     *
     * @return the quantised direction; identical to the previous value while nothing meaningful
     *   changed, so callers can drop duplicate emissions.
     */
    fun onTilt(gravityX: Float, gravityY: Float): Float {
        val magnitudeSquared = gravityX * gravityX + gravityY * gravityY
        if (directional) {
            // Hysteresis: a nearly flat device keeps the default light instead of jittering
            // between the measured direction and the fallback.
            if (magnitudeSquared < EXIT_MAGNITUDE_SQUARED) {
                directional = false
                angle = FLAT_ANGLE
                return angle
            }
        } else if (magnitudeSquared < ENTER_MAGNITUDE_SQUARED) {
            return angle
        }
        directional = true
        val measured = atan2(gravityY.toDouble(), gravityX.toDouble()).toFloat()
        if (abs(shortestDelta(measured - angle)) > stepRadians * SWITCH_FRACTION) {
            angle = (measured / stepRadians).roundToInt() * stepRadians
        }
        return angle
    }

    private fun shortestDelta(delta: Float): Float {
        val fullTurn = (2 * PI).toFloat()
        var value = delta
        while (value > PI) value -= fullTurn
        while (value < -PI) value += fullTurn
        return value
    }

    companion object {
        /** Light points up (0, -1) while the device lies flat. */
        val FLAT_ANGLE: Float = (-PI / 2).toFloat()

        /** |g_xy| >= 0.15, about 8.6° of tilt, before the measured direction takes over. */
        const val ENTER_MAGNITUDE_SQUARED = 0.0225f

        /** |g_xy| <= 0.08, about 4.6° of tilt, before falling back to the flat light. */
        const val EXIT_MAGNITUDE_SQUARED = 0.0064f

        /** 15° buckets keep the light smooth while staying well above sensor noise. */
        val DEFAULT_STEP_RADIANS: Float = (PI / 12).toFloat()

        /** A bucket only changes after the light moved 60% of a step, which adds a dead band. */
        private const val SWITCH_FRACTION = 0.6f
    }
}
