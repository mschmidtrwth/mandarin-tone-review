package com.example.mandaring.pitch

import kotlin.math.abs

/**
 * This audio made louder for listening, never quieter.
 *
 * The gain is set so that all but the loudest [CLIPPED_SHARE] of the samples fit under [target];
 * those are clipped. Going by that and not by the peak keeps a click or a bump of the microphone
 * from deciding the level. The gain is capped at [MAX_GAIN] so that a recording of little more
 * than noise is not blown up.
 */
fun Audio.louder(target: Float = 0.8f): Audio {
    if (samples.isEmpty()) return this
    val magnitudes = FloatArray(samples.size) { abs(samples[it]) }.also { it.sort() }
    val level = magnitudes[((magnitudes.size - 1) * (1 - CLIPPED_SHARE)).toInt()]
    if (level <= 0f) return this
    val gain = (target / level).coerceIn(1f, MAX_GAIN)
    if (gain == 1f) return this
    return Audio(sampleRate, FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) })
}

private const val CLIPPED_SHARE = 0.005f
private const val MAX_GAIN = 30f
