package id.mirrorselfie

import android.hardware.camera2.params.RggbChannelVector
import kotlin.math.ln
import kotlin.math.pow

object ProCameraHelper {
    /**
     * Converts Correlated Color Temperature (Kelvin) to RGB Gains.
     * Uses Tanner Helland / Planckian locus approximation algorithm.
     */
    fun kelvinToRggb(kelvin: Int): RggbChannelVector {
        val temp = (kelvin / 100.0).coerceIn(20.0, 80.0)
        
        // Calculate Red
        val red = if (temp <= 66.0) 255.0
        else (329.698727446 * (temp - 60.0).pow(-0.1332047592)).coerceIn(0.0, 255.0)

        // Calculate Green
        val green = if (temp <= 66.0) {
            (99.4708025861 * ln(temp) - 161.1195681661).coerceIn(0.0, 255.0)
        } else {
            (288.1221695283 * (temp - 60.0).pow(-0.0755148492)).coerceIn(0.0, 255.0)
        }

        // Calculate Blue
        val blue = if (temp >= 66.0) 255.0
        else if (temp <= 19.0) 0.0
        else (138.5177312231 * ln(temp - 10.0) - 305.0447927307).coerceIn(0.0, 255.0)

        // Normalize gains relative to Green channel (G = 1.0f)
        val rGain = (red / green).toFloat().coerceIn(0.5f, 4.0f)
        val gGain = 1.0f
        val bGain = (blue / green).toFloat().coerceIn(0.5f, 4.0f)

        return RggbChannelVector(rGain, gGain, gGain, bGain)
    }
}
