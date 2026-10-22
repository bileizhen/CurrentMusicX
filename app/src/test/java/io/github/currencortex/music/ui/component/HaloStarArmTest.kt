package io.github.currencortex.music.ui.component

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

class HaloStarArmTest {
    @Test fun enteringArmsDoNotDriftSidewaysFromTheirLandingAxes() {
        for (arm in HaloStarArm.entries) {
            val axisX = arm.points[0] - arm.points[4]
            val axisY = arm.points[1] - arm.points[5]
            for (step in 0..60) {
                val time = arm.startMs + (arm.endMs - arm.startMs) * step / 60f
                val p = arm.progress(time)
                val settle = 1f + 1.1f * (p - 1).pow(3) + .1f * (p - 1).pow(2)
                val dx = arm.travelX * (1f - settle)
                val dy = arm.travelY * (1f - settle)
                val perpendicularDistance = abs(dx * axisY - dy * axisX) / hypot(axisX, axisY)
                assertEquals("$arm drifts across its own axis at $time ms", 0f, perpendicularDistance, .001f)
            }
            assertTrue("Arm must start beyond its leading tip", hypot(arm.travelX, arm.travelY) > hypot(axisX, axisY))
        }
    }

}
