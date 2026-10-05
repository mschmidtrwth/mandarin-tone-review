package com.example.mandaring.pitch

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Pitch over time in a speaker's tone levels (see [PitchRange]), which makes the contours of
 * different voices comparable.
 *
 * @param levels tone level per frame, NaN where the frame is unvoiced
 */
class Contour(val hopMs: Float, val levels: FloatArray) {
    val size: Int get() = levels.size

    val durationMs: Float get() = size * hopMs

    fun isVoiced(frame: Int): Boolean = !levels[frame].isNaN()

    internal val firstVoiced: Int = levels.indexOfFirst { !it.isNaN() }
    internal val lastVoiced: Int = levels.indexOfLast { !it.isNaN() }
}

fun PitchTrack.contour(range: PitchRange): Contour =
    Contour(hopMs, FloatArray(size) { if (isVoiced(it)) range.level(f0[it]) else Float.NaN })

/**
 * An attempt laid over a reference recording of the same word.
 *
 * Both are cut down to the stretch from their first to their last voiced frame, and the attempt
 * is stretched in time so that the two coincide. With the syllables of both given, each syllable
 * of the attempt is stretched onto its counterpart instead, so that a word said with the right
 * tones but a different rhythm still lines up.
 *
 * @property attempt the attempt on the frames of [reference]
 */
class Overlay private constructor(
    val reference: Contour,
    val attempt: Contour,
    private val warp: Warp,
) {
    /** Where a moment of the original attempt falls on the time axis of [reference]. */
    fun referenceMs(attemptMs: Float): Float = warp.toReference(attemptMs / attempt.hopMs) * reference.hopMs

    /** Over the whole word. Null if the two are never voiced at the same time. */
    val similarity: Similarity? = similarity(0, reference.size)

    /** Over one syllable of the reference. Null if the two are never voiced at the same time within it. */
    fun similarity(span: Span): Similarity? = similarity(span.from.coerceAtLeast(0), span.until.coerceAtMost(reference.size))

    private fun similarity(from: Int, until: Int): Similarity? {
        var count = 0
        var sumReference = 0f
        var sumAttempt = 0f
        var referenceVoiced = 0
        for (i in from until until) {
            if (!reference.isVoiced(i)) continue
            referenceVoiced++
            if (!attempt.isVoiced(i)) continue
            count++
            sumReference += reference.levels[i]
            sumAttempt += attempt.levels[i]
        }
        if (count == 0) return null

        val height = (sumAttempt - sumReference) / count
        var squares = 0f
        for (i in from until until) {
            if (!reference.isVoiced(i) || !attempt.isVoiced(i)) continue
            val difference = attempt.levels[i] - reference.levels[i] - height
            squares += difference * difference
        }
        return Similarity(
            heightError = height,
            shapeError = sqrt(squares / count),
            coverage = count.toFloat() / referenceVoiced,
        )
    }

    /** Maps frames of the reference to frames of the attempt and back, linearly between matched points. */
    private class Warp(private val reference: FloatArray, private val attempt: FloatArray) {
        fun toAttempt(frame: Float) = map(frame, reference, attempt)

        fun toReference(frame: Float) = map(frame, attempt, reference)

        /** Beyond the first and last point the nearest stretch is carried on. */
        private fun map(value: Float, from: FloatArray, to: FloatArray): Float {
            var segment = 0
            while (segment < from.size - 2 && value >= from[segment + 1]) segment++
            val fraction = (value - from[segment]) / (from[segment + 1] - from[segment])
            return to[segment] + fraction * (to[segment + 1] - to[segment])
        }
    }

    companion object {
        /**
         * Null if either contour has no voiced stretch to align on.
         *
         * @param referenceSpans the syllables of [reference], and [attemptSpans] those of [attempt];
         * both are needed, and the same number of them, to line the syllables up one by one
         */
        fun of(
            reference: Contour,
            attempt: Contour,
            referenceSpans: List<Span>? = null,
            attemptSpans: List<Span>? = null,
        ): Overlay? {
            if (reference.lastVoiced - reference.firstVoiced <= 0 || attempt.lastVoiced - attempt.firstVoiced <= 0) {
                return null
            }

            val points = mutableListOf(reference.firstVoiced to attempt.firstVoiced)
            if (referenceSpans != null && attemptSpans != null && referenceSpans.size == attemptSpans.size) {
                for (index in referenceSpans.indices) {
                    val inReference = reference.voicedExtent(referenceSpans[index]) ?: continue
                    val inAttempt = attempt.voicedExtent(attemptSpans[index]) ?: continue
                    points += inReference.first to inAttempt.first
                    points += inReference.last to inAttempt.last
                }
            }
            points += reference.lastVoiced to attempt.lastVoiced
            // Both axes must keep moving forward, which drops anything that doubles back or repeats.
            val matched = mutableListOf(points.first())
            for (point in points.drop(1)) {
                if (point.first > matched.last().first && point.second > matched.last().second) matched += point
            }

            val warp = Warp(
                FloatArray(matched.size) { matched[it].first.toFloat() },
                FloatArray(matched.size) { matched[it].second.toFloat() },
            )
            val levels = FloatArray(reference.size) { attempt.levelAt(warp.toAttempt(it.toFloat())) }
            return Overlay(reference, Contour(reference.hopMs, levels), warp)
        }

        /** First and last voiced frame within [span], null unless they are two different frames. */
        private fun Contour.voicedExtent(span: Span): IntRange? {
            val frames = (span.from.coerceAtLeast(0) until span.until.coerceAtMost(size)).filter { isVoiced(it) }
            return if (frames.size >= 2) frames.first()..frames.last() else null
        }

        /** Level at a fractional frame, NaN unless the frames either side of it are voiced. */
        private fun Contour.levelAt(frame: Float): Float {
            val before = floor(frame).toInt()
            if (before !in 0 until size) return Float.NaN
            val fraction = frame - before
            if (fraction < 1e-3f || before + 1 == size) return levels[before]
            return levels[before] + (levels[before + 1] - levels[before]) * fraction
        }
    }
}

/**
 * How closely an attempt follows a reference, in tone levels.
 *
 * @property heightError how much higher (positive) or lower the attempt sits on average
 * @property shapeError RMS difference between the two once [heightError] is taken out
 * @property coverage share of the reference's voiced frames where the attempt is voiced as well
 */
class Similarity(val heightError: Float, val shapeError: Float, val coverage: Float) {
    /**
     * A single figure, 0 for identical contours. Height counts for less than shape: it is the
     * part that depends on how well the speaker's range is calibrated, and getting the movement
     * right matters more than hitting the reference speaker's exact register.
     */
    val distance: Float
        get() {
            val height = HEIGHT_WEIGHT * heightError
            return sqrt(shapeError * shapeError + height * height)
        }

    val rating: Rating
        get() = when {
            coverage < MIN_COVERAGE -> Rating.OFF
            distance < CLOSE_DISTANCE -> Rating.CLOSE
            distance < NEAR_DISTANCE -> Rating.NEAR
            else -> Rating.OFF
        }

    private companion object {
        const val HEIGHT_WEIGHT = 0.5f
        const val MIN_COVERAGE = 0.5f
        const val CLOSE_DISTANCE = 0.5f
        const val NEAR_DISTANCE = 0.9f
    }
}

enum class Rating { CLOSE, NEAR, OFF }
