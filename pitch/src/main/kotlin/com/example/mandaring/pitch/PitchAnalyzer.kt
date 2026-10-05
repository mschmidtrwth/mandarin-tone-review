package com.example.mandaring.pitch

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Extracts a pitch track from a short speech recording.
 *
 * Each frame yields several candidate periods; the track is the cheapest path through them,
 * where a path pays for aperiodic candidates, for pitch jumps and for switching between
 * voiced and unvoiced. This is the scheme Praat uses, with YIN supplying the candidates.
 */
class PitchAnalyzer(
    private val sampleRate: Int,
    private val config: Config = Config(),
) {
    data class Config(
        val hopMs: Float = 10f,
        val minHz: Float = 60f,
        val maxHz: Float = 500f,
        val windowMs: Float = 20f,
        val candidatesPerFrame: Int = 6,
        /** Cost of calling a frame unvoiced; candidates less periodic than this lose to it. */
        val unvoicedCost: Float = 0.6f,
        /** Cost per octave of pitch movement between neighbouring frames. */
        val jumpCost: Float = 1f,
        /** Cost of switching between voiced and unvoiced. */
        val voicingChangeCost: Float = 0.2f,
        /** Cost per octave below [maxHz], to prefer a period over its multiples. */
        val octaveCost: Float = 0.03f,
        /** Frames this far below the loudest frame of the clip are unvoiced. */
        val silenceDb: Float = -35f,
        /** Voiced stretches shorter than this are discarded as glitches. */
        val minVoicedMs: Float = 50f,
        val medianFrames: Int = 3,
    )

    private val hop = (sampleRate * config.hopMs / 1000f).roundToInt()
    private val windowSize = (sampleRate * config.windowMs / 1000f).roundToInt()
    private val yin = Yin(sampleRate, config.minHz, config.maxHz, windowSize)
    private val minVoicedFrames = (config.minVoicedMs / config.hopMs).roundToInt()

    /** Frame `i` of the result is centred on `i * hopMs`. */
    fun analyze(samples: FloatArray): PitchTrack {
        val frameCount = samples.size / hop + 1
        val slots = config.candidatesPerFrame
        val frequency = Array(frameCount) { FloatArray(slots) }
        val aperiodicity = Array(frameCount) { FloatArray(slots) }
        val candidateCount = IntArray(frameCount)
        val levelDb = FloatArray(frameCount)

        val frame = FloatArray(yin.frameSize)
        for (i in 0 until frameCount) {
            copyCentred(samples, centre = i * hop, into = frame)
            candidateCount[i] = yin.candidates(frame, frequency[i], aperiodicity[i])
            levelDb[i] = levelDb(frame)
        }
        val silence = (levelDb.maxOrNull() ?: 0f) + config.silenceDb
        for (i in 0 until frameCount) {
            if (levelDb[i] < silence) candidateCount[i] = 0
        }

        val chosen = cheapestPath(frequency, aperiodicity, candidateCount)

        val f0 = FloatArray(frameCount) { i -> if (chosen[i] >= 0) frequency[i][chosen[i]] else Float.NaN }
        val confidence = FloatArray(frameCount) { i ->
            val candidate = if (chosen[i] >= 0) chosen[i] else 0
            if (candidateCount[i] > 0) (1f - aperiodicity[i][candidate]).coerceIn(0f, 1f) else 0f
        }
        dropShortRuns(f0)
        return PitchTrack(config.hopMs, medianFilter(f0), confidence)
    }

    private fun copyCentred(samples: FloatArray, centre: Int, into: FloatArray) {
        val start = centre - into.size / 2
        for (j in into.indices) {
            val source = start + j
            into[j] = if (source in samples.indices) samples[source] else 0f
        }
    }

    private fun levelDb(frame: FloatArray): Float {
        // Only the middle of the frame, so that level follows the frame's nominal time closely.
        val from = (frame.size - windowSize) / 2
        var sum = 0f
        for (j in from until from + windowSize) sum += frame[j] * frame[j]
        return 10f * log10(sum / windowSize + 1e-10f)
    }

    /**
     * Viterbi search over the candidates. State `k < candidateCount[i]` is candidate `k` of frame
     * `i`; the last state is "unvoiced". Returns the chosen candidate per frame, -1 for unvoiced.
     */
    private fun cheapestPath(
        frequency: Array<FloatArray>,
        aperiodicity: Array<FloatArray>,
        candidateCount: IntArray,
    ): IntArray {
        val frameCount = candidateCount.size
        val unvoiced = config.candidatesPerFrame
        val states = unvoiced + 1
        val cameFrom = Array(frameCount) { IntArray(states) }
        var cost = FloatArray(states)
        var previousCost = FloatArray(states)

        for (i in 0 until frameCount) {
            val swap = previousCost
            previousCost = cost
            cost = swap

            for (state in 0 until states) {
                val voiced = state != unvoiced
                if (voiced && state >= candidateCount[i]) {
                    cost[state] = Float.POSITIVE_INFINITY
                    continue
                }
                val local = if (voiced) {
                    aperiodicity[i][state] + config.octaveCost * log2(config.maxHz / frequency[i][state])
                } else {
                    config.unvoicedCost
                }
                if (i == 0) {
                    cost[state] = local
                    continue
                }

                var best = Float.POSITIVE_INFINITY
                var bestFrom = unvoiced
                for (from in 0 until states) {
                    val fromVoiced = from != unvoiced
                    if (fromVoiced && from >= candidateCount[i - 1]) continue
                    val transition = when {
                        voiced && fromVoiced ->
                            config.jumpCost * abs(log2(frequency[i][state] / frequency[i - 1][from]))
                        voiced != fromVoiced -> config.voicingChangeCost
                        else -> 0f
                    }
                    val total = previousCost[from] + transition
                    if (total < best) {
                        best = total
                        bestFrom = from
                    }
                }
                cost[state] = best + local
                cameFrom[i][state] = bestFrom
            }
        }

        val chosen = IntArray(frameCount)
        var state = (0 until states).minByOrNull { cost[it] } ?: unvoiced
        for (i in frameCount - 1 downTo 0) {
            chosen[i] = if (state == unvoiced) -1 else state
            state = cameFrom[i][state]
        }
        return chosen
    }

    private fun dropShortRuns(f0: FloatArray) {
        forEachVoicedRun(f0) { from, until ->
            if (until - from < minVoicedFrames) f0.fill(Float.NaN, from, until)
        }
    }

    /** Median over neighbouring frames of the same voiced run. */
    private fun medianFilter(f0: FloatArray): FloatArray {
        val result = f0.copyOf()
        val half = config.medianFrames / 2
        forEachVoicedRun(f0) { from, until ->
            for (i in from until until) {
                val window = f0.copyOfRange(maxOf(from, i - half), minOf(until, i + half + 1))
                window.sort()
                result[i] = window[window.size / 2]
            }
        }
        return result
    }

    private inline fun forEachVoicedRun(f0: FloatArray, action: (from: Int, until: Int) -> Unit) {
        var i = 0
        while (i < f0.size) {
            if (f0[i].isNaN()) {
                i++
                continue
            }
            val from = i
            while (i < f0.size && !f0[i].isNaN()) i++
            action(from, i)
        }
    }
}
