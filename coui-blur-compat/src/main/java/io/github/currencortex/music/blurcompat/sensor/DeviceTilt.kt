// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.currencortex.music.blurcompat.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Device tilt as Euler angles plus the screen-plane projection of the unit gravity vector.
 *
 * @property pitch Tilt around the device X axis (radians); positive = top tilts away.
 * @property roll Tilt around the device Y axis (radians); positive = right tilts toward.
 * @property gravityX Gravity X in device space (`+X` = right). Sweeps the unit circle
 *  with `gravityY` as the device rotates around its screen normal — info Euler angles
 *  lose. `0` when no sensor.
 * @property gravityY Gravity Y in device space (`+Y` = top). `0` when no sensor.
 */
@Immutable
data class DeviceTilt(
    val pitch: Float,
    val roll: Float,
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
) {

    companion object {

        @Stable
        val Zero: DeviceTilt = DeviceTilt(0f, 0f, 0f, 0f)
    }
}

/**
 * Returns a live [DeviceTilt] driven by the platform's rotation sensor; without sensor
 * support, returns [DeviceTilt.Zero].
 *
 * @param smoothing Low-pass alpha applied to each sensor sample (0 < a ≤ 1).
 *  1.0 = no smoothing (raw samples), 0.1 = heavy smoothing. Default 0.15.
 */
@Composable
fun rememberDeviceTilt(smoothing: Float = 0.15f): State<DeviceTilt> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(DeviceTilt.Zero) }

    DisposableEffect(context, lifecycleOwner, smoothing) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothPitch = 0f
        var smoothRoll = 0f
        var smoothGravityX = 0f
        var smoothGravityY = 0f
        var initialized = false
        var registered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // orientation: [azimuth, pitch, roll] in radians
                // Gravity in device space = R^T * (0, 0, -1)_world = -(R[2][0], R[2][1], R[2][2])
                val gravityX = -rotationMatrix[6]
                val gravityY = -rotationMatrix[7]
                if (!initialized) {
                    smoothPitch = orientation[1]
                    smoothRoll = orientation[2]
                    smoothGravityX = gravityX
                    smoothGravityY = gravityY
                    initialized = true
                } else {
                    smoothPitch += (orientation[1] - smoothPitch) * smoothing
                    smoothRoll += (orientation[2] - smoothRoll) * smoothing
                    smoothGravityX += (gravityX - smoothGravityX) * smoothing
                    smoothGravityY += (gravityY - smoothGravityY) * smoothing
                }
                tilt.value = DeviceTilt(
                    pitch = smoothPitch,
                    roll = smoothRoll,
                    gravityX = smoothGravityX,
                    gravityY = smoothGravityY,
                )
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }

        fun register() {
            if (!registered) {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                sensorManager.unregisterListener(listener)
                registered = false
                initialized = false
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }

        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            unregister()
        }
    }

    return tilt
}
