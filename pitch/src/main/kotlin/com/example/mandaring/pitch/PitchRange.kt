package com.example.mandaring.pitch

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow

/**
 * A speaker's usable pitch range, mapped onto the five-level scale used to describe tones
 * (tone 1 = 55, tone 2 = 35, tone 3 = 214, tone 4 = 51).
 */
data class PitchRange(val lowHz: Float, val highHz: Float) {
    init {
        require(lowHz > 0f && highHz > lowHz) { "Invalid range $lowHz..$highHz" }
    }

    /** 1 at [lowHz], 5 at [highHz], linear in log frequency and not clamped. */
    fun level(hz: Float): Float = 1f + 4f * ln(hz / lowHz) / ln(highHz / lowHz)

    fun hz(level: Float): Float = lowHz * (highHz / lowHz).pow((level - 1f) / 4f)
}

/**
 * Distribution of a speaker's voiced pitch, accumulated over recordings. Immutable.
 *
 * The speaker's range is read off as the 5th to 95th percentile, which ignores the odd
 * mistracked frame and settles on the range actually used as more speech is added.
 */
class PitchHistogram private constructor(private val counts: IntArray) {
    constructor() : this(IntArray(BINS))

    val voicedFrames: Int = counts.sum()

    operator fun plus(track: PitchTrack): PitchHistogram {
        val updated = counts.copyOf()
        for (i in 0 until track.size) {
            if (!track.isVoiced(i)) continue
            val bin = floor(BINS_PER_OCTAVE * log2(track.f0[i] / MIN_HZ)).toInt()
            if (bin in updated.indices) updated[bin]++
        }
        return PitchHistogram(updated)
    }

    /** Null until there is enough voiced speech covering a wide enough span to be meaningful. */
    fun range(): PitchRange? {
        if (voicedFrames < MIN_FRAMES) return null
        val low = percentile(0.05f)
        val high = percentile(0.95f)
        if (log2(high / low) < MIN_SPAN_OCTAVES) return null
        return PitchRange(low, high)
    }

    private fun percentile(fraction: Float): Float {
        val target = fraction * voicedFrames
        var seen = 0
        for (bin in counts.indices) {
            if (counts[bin] == 0) continue
            if (seen + counts[bin] >= target) {
                val within = (target - seen) / counts[bin]
                return MIN_HZ * 2f.pow((bin + within) / BINS_PER_OCTAVE)
            }
            seen += counts[bin]
        }
        return MIN_HZ * 2f.pow(BINS.toFloat() / BINS_PER_OCTAVE)
    }

    /** Compact text form: `bin:count` pairs for the non-empty bins. */
    fun encode(): String =
        counts.indices.filter { counts[it] > 0 }.joinToString(",") { "$it:${counts[it]}" }

    companion object {
        private const val MIN_HZ = 50f
        private const val BINS_PER_OCTAVE = 48f
        private const val BINS = 180
        private const val MIN_FRAMES = 100
        private const val MIN_SPAN_OCTAVES = 0.25f

        /** Entries that are malformed or out of range are skipped. */
        fun decode(text: String): PitchHistogram {
            val counts = IntArray(BINS)
            for (entry in text.split(',')) {
                val bin = entry.substringBefore(':').toIntOrNull() ?: continue
                val count = entry.substringAfter(':', "").toIntOrNull() ?: continue
                if (bin in counts.indices && count > 0) counts[bin] = count
            }
            return PitchHistogram(counts)
        }
    }
}
