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
 * is stretched linearly so that the two stretches coincide.
 *
 * @property attempt the attempt on the frames of [reference]
 */
class Overlay private constructor(
    val reference: Contour,
    val attempt: Contour,
    private val attemptStartMs: Float,
    /** Reference time per unit of attempt time. */
    private val stretch: Float,
) {
    /** Where a moment of the original attempt falls on the time axis of [reference]. */
    fun referenceMs(attemptMs: Float): Float =
        reference.firstVoiced * reference.hopMs + (attemptMs - attemptStartMs) * stretch

    /** Null if the two are never voiced at the same time. */
    val similarity: Similarity? = run {
        var count = 0
        var sumReference = 0f
        var sumAttempt = 0f
        for (i in 0 until reference.size) {
            if (!reference.isVoiced(i) || !attempt.isVoiced(i)) continue
            count++
            sumReference += reference.levels[i]
            sumAttempt += attempt.levels[i]
        }
        if (count == 0) return@run null

        val height = (sumAttempt - sumReference) / count
        var squares = 0f
        for (i in 0 until reference.size) {
            if (!reference.isVoiced(i) || !attempt.isVoiced(i)) continue
            val difference = attempt.levels[i] - reference.levels[i] - height
            squares += difference * difference
        }
        Similarity(
            heightError = height,
            shapeError = sqrt(squares / count),
            coverage = count.toFloat() / reference.levels.count { !it.isNaN() },
        )
    }

    companion object {
        /** Null if either contour has no voiced stretch to align on. */
        fun of(reference: Contour, attempt: Contour): Overlay? {
            val referenceSpan = reference.lastVoiced - reference.firstVoiced
            val attemptSpan = attempt.lastVoiced - attempt.firstVoiced
            if (referenceSpan <= 0 || attemptSpan <= 0) return null

            val step = attemptSpan.toFloat() / referenceSpan
            val levels = FloatArray(reference.size) { i ->
                attempt.levelAt(attempt.firstVoiced + (i - reference.firstVoiced) * step)
            }
            return Overlay(
                reference = reference,
                attempt = Contour(reference.hopMs, levels),
                attemptStartMs = attempt.firstVoiced * attempt.hopMs,
                stretch = reference.hopMs / (step * attempt.hopMs),
            )
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
