package com.example.mandaring.pitch

import kotlin.math.ceil

/**
 * Single-frame period candidates from YIN's cumulative mean normalised difference
 * (de Cheveigné & Kawahara, 2002).
 *
 * A frame must hold [frameSize] samples: the integration window plus the longest lag searched.
 */
internal class Yin(
    private val sampleRate: Int,
    minHz: Float,
    maxHz: Float,
    private val windowSize: Int,
) {
    private val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(2)
    private val maxLag = ceil(sampleRate / minHz).toInt()

    // One extra lag so the longest lag searched still has a right-hand neighbour to interpolate with.
    private val cmnd = FloatArray(maxLag + 2)

    val frameSize: Int = windowSize + maxLag + 1

    /**
     * Finds the dips of the difference function of [frame], which are its candidate periods.
     * Fills [frequency] (Hz) and [aperiodicity] (0 = perfectly periodic, around 1 = noise) with
     * the deepest dips, deepest first, and returns how many were written.
     */
    fun candidates(frame: FloatArray, frequency: FloatArray, aperiodicity: FloatArray): Int {
        fillCumulativeMeanNormalizedDifference(frame)

        var count = 0
        for (lag in minLag..maxLag) {
            val value = cmnd[lag]
            val isDip = value < cmnd[lag - 1] && value <= cmnd[lag + 1]
            if (!isDip || value >= MAX_APERIODICITY) continue
            if (count == frequency.size && value >= aperiodicity[count - 1]) continue

            // Insertion sort into the fixed-size result, dropping the shallowest dip when full.
            var slot = if (count < frequency.size) count++ else count - 1
            while (slot > 0 && aperiodicity[slot - 1] > value) {
                frequency[slot] = frequency[slot - 1]
                aperiodicity[slot] = aperiodicity[slot - 1]
                slot--
            }
            frequency[slot] = sampleRate / refine(lag)
            aperiodicity[slot] = value
        }
        return count
    }

    private fun fillCumulativeMeanNormalizedDifference(frame: FloatArray) {
        cmnd[0] = 1f
        var runningSum = 0f
        for (lag in 1 until cmnd.size) {
            // Shorter lags start later, so every lag compares samples around the frame's centre.
            val start = (maxLag + 1 - lag) / 2
            var difference = 0f
            for (j in start until start + windowSize) {
                val delta = frame[j] - frame[j + lag]
                difference += delta * delta
            }
            runningSum += difference
            cmnd[lag] = if (runningSum > 0f) difference * lag / runningSum else 1f
        }
    }

    /** Parabolic interpolation around the chosen lag for sub-sample resolution. */
    private fun refine(lag: Int): Float {
        val left = cmnd[lag - 1]
        val centre = cmnd[lag]
        val right = cmnd[lag + 1]
        val curvature = left - 2f * centre + right
        if (curvature <= 0f) return lag.toFloat()
        return lag + (0.5f * (left - right) / curvature).coerceIn(-1f, 1f)
    }

    private companion object {
        const val MAX_APERIODICITY = 0.9f
    }
}
