package com.example.mandaring.pitch

/**
 * Fundamental frequency over time, one value per analysis frame.
 *
 * @param hopMs time between consecutive frames
 * @param f0 frequency in Hz per frame, NaN where the frame is unvoiced
 * @param confidence 0..1 per frame, higher means a clearer periodicity
 */
class PitchTrack(
    val hopMs: Float,
    val f0: FloatArray,
    val confidence: FloatArray,
) {
    init {
        require(f0.size == confidence.size) { "f0 and confidence must have the same length" }
    }

    val size: Int get() = f0.size

    val durationMs: Float get() = size * hopMs

    fun isVoiced(frame: Int): Boolean = !f0[frame].isNaN()

    val hasVoicedFrames: Boolean get() = f0.any { !it.isNaN() }
}
