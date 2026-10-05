package com.example.mandaring.pitch

/**
 * Fundamental frequency over time, one value per analysis frame.
 *
 * @param hopMs time between consecutive frames
 * @param f0 frequency in Hz per frame, NaN where the frame is unvoiced
 * @param confidence 0..1 per frame, higher means a clearer periodicity
 * @param levelDb loudness per frame in dB, on an arbitrary reference
 */
class PitchTrack(
    val hopMs: Float,
    val f0: FloatArray,
    val confidence: FloatArray,
    val levelDb: FloatArray,
) {
    init {
        require(f0.size == confidence.size && f0.size == levelDb.size) {
            "f0, confidence and levelDb must have the same length"
        }
    }

    val size: Int get() = f0.size

    val durationMs: Float get() = size * hopMs

    fun isVoiced(frame: Int): Boolean = !f0[frame].isNaN()

    val voicedFrames: Int get() = f0.count { !it.isNaN() }
}
