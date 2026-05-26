package com.example.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class ShakeDetector(
    private var threshold: Float = DEFAULT_THRESHOLD,
    private val onShake: () -> Unit
) : SensorEventListener {

    private var lastShakeTime: Long = 0

    fun setSensitivity(customThreshold: Float) {
        this.threshold = customThreshold
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // gForce will be close to 1.0 when at rest.
            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

            if (gForce > threshold) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                    lastShakeTime = now
                    Log.d("ShakeDetector", "Significant device shake detected! gForce: $gForce")
                    onShake()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val DEFAULT_THRESHOLD = 2.5f // ~2.5g represents a purposeful firm shake
        private const val SHAKE_COOLDOWN_MS = 3000
    }
}
