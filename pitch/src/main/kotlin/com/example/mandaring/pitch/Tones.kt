package com.example.mandaring.pitch

import kotlin.math.abs
import kotlin.math.log2

/** Frames [from] until [until] of a track or contour, holding one syllable. */
data class Span(val from: Int, val until: Int) {
    val size: Int get() = until - from
}

/**
 * Splits the voiced part of a recording of [count] syllables into one span per syllable.
 *
 * Syllables are taken to be the voiced stretches. Where there are too many, the two that look most
 * like one syllable broken by creak are joined: close together and at a similar pitch. Where there
 * are too few, the longest is cut at its deepest dip in loudness, which is where a syllable
 * boundary without a voiceless consonant shows.
 *
 * This is for single words and tone pairs. In running speech boundaries are too often invisible
 * to it, see tools/sentence_feasibility.
 *
 * @return null if there is no voiced speech, or too little of it to hold [count] syllables
 */
fun PitchTrack.syllables(count: Int): List<Span>? {
    require(count > 0) { "count must be positive" }
    val stretches = mutableListOf<Stretch>()
    for (run in voicedRuns()) {
        val previous = stretches.lastOrNull()
        if (previous != null && isOctaveSlip(previous.pitched, run)) {
            // Voiced, so part of the syllable, but its pitch says nothing about where the next one starts.
            stretches[stretches.lastIndex] = previous.copy(until = run.until)
        } else {
            stretches += Stretch(run.from, run.until, pitched = run)
        }
    }
    if (stretches.isEmpty()) return null

    while (stretches.size > count) {
        val first = (0 until stretches.size - 1).minByOrNull { joinCost(stretches[it], stretches[it + 1]) }!!
        val second = stretches.removeAt(first + 1)
        stretches[first] = Stretch(stretches[first].from, second.until, second.pitched)
    }
    val spans = stretches.mapTo(mutableListOf()) { Span(it.from, it.until) }
    while (spans.size < count) {
        val longest = spans.indices.maxByOrNull { spans[it].size }!!
        val span = spans[longest]
        val cut = deepestDip(span) ?: return null
        spans[longest] = Span(span.from, cut)
        spans.add(longest + 1, Span(cut, span.until))
    }
    return spans
}

/**
 * The track with octave slips made unvoiced: stretches where the tracker locked onto half or double
 * the period, as happens in creaky voice and where a syllable trails off.
 */
fun PitchTrack.withoutOctaveSlips(): PitchTrack {
    val cleaned = f0.copyOf()
    var kept: Span? = null
    for (run in voicedRuns()) {
        if (kept != null && isOctaveSlip(kept, run)) {
            cleaned.fill(Float.NaN, run.from, run.until)
        } else {
            kept = run
        }
    }
    return PitchTrack(hopMs, cleaned, confidence, levelDb)
}

/** A voiced stretch of one syllable; [pitched] is the last part of it whose pitch can be trusted. */
private data class Stretch(val from: Int, val until: Int, val pitched: Span)

private fun PitchTrack.voicedRuns(): List<Span> {
    val runs = mutableListOf<Span>()
    var frame = 0
    while (frame < size) {
        if (!isVoiced(frame)) {
            frame++
            continue
        }
        val from = frame
        while (frame < size && isVoiced(frame)) frame++
        runs += Span(from, frame)
    }
    return runs
}

/**
 * Whether [run] is [previous] carrying on at the wrong octave. Real pitch does not move that far in
 * a few frames, and a new syllable that starts so soon after is longer than a slip gets.
 */
private fun PitchTrack.isOctaveSlip(previous: Span, run: Span): Boolean =
    run.from - previous.until <= SLIP_GAP_FRAMES &&
        abs(log2(f0[run.from] / f0[previous.until - 1])) >= SLIP_OCTAVES &&
        run.size <= SLIP_LENGTH_SHARE * previous.size

/** Low for stretches likely to be one syllable: close in time, close in pitch. */
private fun PitchTrack.joinCost(first: Stretch, second: Stretch): Float {
    val gap = second.from - first.until
    val jump = abs(log2(f0[second.from] / f0[first.pitched.until - 1]))
    return gap + JUMP_FRAMES_PER_OCTAVE * jump
}

/**
 * The frame within [span] where loudness dips furthest below the peaks on both sides of it, null
 * if the span is too short to hold two syllables. Measuring against both sides keeps the cut away
 * from the end of the span, where loudness is low simply because the voice trails off.
 */
private fun PitchTrack.deepestDip(span: Span): Int? {
    if (span.size < 2 * MIN_SYLLABLE_FRAMES) return null
    val peakBefore = FloatArray(span.size)
    val peakAfter = FloatArray(span.size)
    for (i in 0 until span.size) {
        peakBefore[i] = maxOf(levelDb[span.from + i], if (i > 0) peakBefore[i - 1] else Float.NEGATIVE_INFINITY)
    }
    for (i in span.size - 1 downTo 0) {
        peakAfter[i] = maxOf(levelDb[span.from + i], if (i < span.size - 1) peakAfter[i + 1] else Float.NEGATIVE_INFINITY)
    }
    val candidates = MIN_SYLLABLE_FRAMES until span.size - MIN_SYLLABLE_FRAMES
    val cut = candidates.maxByOrNull { minOf(peakBefore[it], peakAfter[it]) - levelDb[span.from + it] }
        ?: (span.size / 2)
    return span.from + cut
}

private const val MIN_SYLLABLE_FRAMES = 5
private const val SLIP_GAP_FRAMES = 5
private const val SLIP_OCTAVES = 0.5f
private const val SLIP_LENGTH_SHARE = 1.5f
private const val JUMP_FRAMES_PER_OCTAVE = 20f

/**
 * Names the tone of a syllable from the shape of its contour, by rules on where it starts, how far
 * it falls and how far it rises again.
 *
 * The rules describe careful, isolated words by a speaker whose range is known. Fluent speech bends
 * tones well away from these shapes.
 */
object ToneClassifier {
    /**
     * @param span the syllable within [contour]
     * @param first whether this is the first syllable of the word, which cannot be neutral
     * @param longestFrames voiced frames of the longest syllable of the word; a neutral tone is short
     * @return 1 to 4, or 0 for the neutral tone
     */
    fun classify(contour: Contour, span: Span, first: Boolean, longestFrames: Int): Int {
        val voiced = voicedLevels(contour, span)
        // Too little pitch to go by is usually the creak at the bottom of a third tone.
        val shape = shape(voiced) ?: return 3
        val short = !first && voiced.size < SHORT_SHARE * longestFrames

        val lowest = shape.indices.minByOrNull { shape[it] }!!
        val low = shape[lowest]
        val top = shape.take(lowest + 1).max()
        val descent = top - low
        val ascent = shape.drop(lowest).max() - low
        val mean = shape.average().toFloat()

        return when {
            // Rises from where it starts.
            ascent >= RISE && descent < DIP && low <= RISE_FROM_BELOW -> 2
            descent >= FALL && top >= HIGH_START -> when {
                // High and level until a drop into the next syllable.
                top - shape[LATE_POINT] < LATE_DROP && mean >= HIGH_LEVEL -> 1
                else -> 4
            }
            // Falls from low down, or dips and comes back up.
            descent >= FALL || (descent >= DIP && ascent >= RISE) -> if (short && ascent < RISE) 0 else 3
            // What is left is more or less level. Cut short, a fourth tone shows only its high start.
            short -> if (mean >= SHORT_FOURTH_LEVEL) 4 else 0
            mean >= HIGH_LEVEL -> 1
            else -> 3
        }
    }

    internal fun voicedLevels(contour: Contour, span: Span): List<Float> =
        (span.from until span.until).filter { contour.isVoiced(it) }.map { contour.levels[it] }

    /** The syllable's level at [POINTS] evenly spaced moments, null if there is too little of it. */
    private fun shape(voiced: List<Float>): FloatArray? {
        if (voiced.size < 3) return null
        // The start of a syllable is still coming from wherever the pitch was before.
        val settled = voiced.drop((voiced.size * ONSET_SHARE).toInt())
        if (settled.size < 2) return null
        return FloatArray(POINTS) { point ->
            val position = point * (settled.size - 1f) / (POINTS - 1)
            val before = position.toInt().coerceAtMost(settled.size - 2)
            settled[before] + (settled[before + 1] - settled[before]) * (position - before)
        }
    }

    private const val POINTS = 8
    private const val ONSET_SHARE = 0.15f

    /** Point of the shape, about 70 % in, by which a fourth tone is well on its way down. */
    private const val LATE_POINT = 5
    private const val LATE_DROP = 0.5f

    // In tone levels.
    private const val RISE = 0.8f
    private const val FALL = 0.8f
    private const val DIP = 0.55f
    private const val RISE_FROM_BELOW = 3.6f
    private const val HIGH_START = 3.2f
    private const val HIGH_LEVEL = 3.2f
    private const val SHORT_FOURTH_LEVEL = 4.4f

    private const val SHORT_SHARE = 0.66f
}

/**
 * The tones heard in a recording known to hold [count] syllables, in the levels of [range].
 *
 * @return null if the recording cannot be split into that many syllables
 */
fun PitchTrack.tones(range: PitchRange, count: Int): List<Int>? {
    val spans = syllables(count) ?: return null
    val contour = withoutOctaveSlips().contour(range)
    val longest = spans.maxOf { ToneClassifier.voicedLevels(contour, it).size }
    return spans.mapIndexed { index, span ->
        ToneClassifier.classify(contour, span, first = index == 0, longestFrames = longest)
    }
}
