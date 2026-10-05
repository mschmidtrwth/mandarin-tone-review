package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonesTest {
    private val nan = Float.NaN

    /**
     * Recognition over every sample. All one clean speaker, so this validates the logic, not how
     * a learner's recordings will fare.
     */
    @Test
    fun recognisesTonesOfSamples() {
        val tracks = Fixtures.sampleNames.associateWith {
            val audio = Fixtures.sample(it)
            PitchAnalyzer(audio.sampleRate).analyze(audio.samples)
        }
        val range = tracks.values.fold(PitchHistogram()) { histogram, track -> histogram + track }.range()!!

        val tones = listOf(1, 2, 3, 4, 0)
        val confusion = Array(5) { IntArray(5) }
        val wrong = mutableListOf<String>()
        for ((name, track) in tracks) {
            val expected = Word.parse(name)!!.spokenTones
            val heard = track.tones(range, expected.size)
            if (heard == null) {
                wrong += "$name: not split"
                continue
            }
            for (i in expected.indices) confusion[tones.indexOf(expected[i])][tones.indexOf(heard[i])]++
            if (heard != expected) wrong += "$name: ${heard.joinToString("")}"
        }

        println("said\\heard " + tones.joinToString("") { "%5d".format(it) })
        for (row in tones.indices) {
            val recall = 100f * confusion[row][row] / confusion[row].sum()
            println("    %d      ".format(tones[row]) + confusion[row].joinToString("") { "%5d".format(it) } + "   %.0f %%".format(recall))
        }
        val total = confusion.sumOf { it.sum() }
        val correct = tones.indices.sumOf { confusion[it][it] }
        println("$correct of $total syllables, ${tracks.size - wrong.size} of ${tracks.size} words")
        println(wrong.joinToString("\n  ", prefix = "  "))

        // The rules were tuned on these very samples, so this guards against regressions and
        // says little about new voices.
        assertTrue("only $correct of $total syllables", correct >= 0.95f * total)
    }

    @Test
    fun voicedStretchesAreTheSyllables() {
        val track = track(floatArrayOf(nan, 200f, 200f, 200f, nan, nan, nan, nan, nan, nan, 150f, 150f, 150f, 150f, nan))

        assertEquals(listOf(Span(1, 4), Span(10, 14)), track.syllables(2))
    }

    @Test
    fun syllableBrokenByCreakIsJoinedRatherThanTwoSyllablesAtDifferentPitch() {
        val f0 = FloatArray(60) { nan }
        f0.fill(300f, 0, 20)
        // The second syllable: falls into creak, comes back at much the same pitch.
        f0.fill(140f, 28, 40)
        f0.fill(150f, 48, 60)

        assertEquals(listOf(Span(0, 20), Span(28, 60)), track(f0).syllables(2))
    }

    @Test
    fun unbrokenVoicingIsCutAtTheDipInLoudness() {
        val f0 = FloatArray(60) { 200f }
        // Two humps of loudness, and a tail quieter than the dip between them.
        val levelDb = FloatArray(60) { i ->
            when {
                i < 25 -> -10f
                i < 30 -> -20f
                i < 50 -> -12f
                else -> -30f
            }
        }

        val spans = track(f0, levelDb).syllables(2)!!

        assertEquals(2, spans.size)
        assertTrue("cut at ${spans[0].until}", spans[0].until in 25 until 30)
        assertEquals(spans[0].until, spans[1].from)
    }

    @Test
    fun nothingToSplitWithoutEnoughVoicedSpeech() {
        assertNull(track(floatArrayOf(nan, nan, nan)).syllables(1))
        assertNull(track(floatArrayOf(nan, 200f, 200f, 200f, 200f, nan)).syllables(2))
    }

    @Test
    fun octaveSlipBelongsToItsSyllableButLosesItsPitch() {
        val f0 = FloatArray(50) { nan }
        f0.fill(200f, 0, 20)
        // The end of the syllable tracked an octave up.
        f0.fill(400f, 21, 28)
        f0.fill(250f, 40, 50)
        val track = track(f0)

        assertEquals(listOf(Span(0, 28), Span(40, 50)), track.syllables(2))
        val cleaned = track.withoutOctaveSlips()
        assertTrue((21 until 28).none { cleaned.isVoiced(it) })
        assertEquals(30, cleaned.voicedFrames)
    }

    @Test
    fun namesTypicalShapes() {
        val shapes = mapOf(
            1 to listOf(4.8f, 4.8f),
            2 to listOf(2.2f, 2.4f, 4.2f),
            3 to listOf(2.2f, 0.8f, 2.6f),
            4 to listOf(5.2f, 2f),
        )
        for ((tone, levels) in shapes) {
            assertEquals("tone $tone", tone, classify(glide(levels, frames = 40)))
        }
        // Half third tone, as before another syllable.
        assertEquals(3, classify(glide(listOf(2.3f, 1.2f), frames = 40)))
        // A first tone that drops into the next syllable only at its very end.
        assertEquals(1, classify(glide(listOf(4.7f, 4.7f, 4.7f, 4.7f, 4.7f, 4.7f, 4.7f, 4.7f, 4.7f, 3f), frames = 40)))
    }

    @Test
    fun shortLowSecondSyllableIsNeutral() {
        val fall = glide(listOf(2.6f, 0.8f), frames = 12)

        assertEquals(0, ToneClassifier.classify(fall, Span(0, fall.size), first = false, longestFrames = 40))
        assertEquals(3, ToneClassifier.classify(fall, Span(0, fall.size), first = true, longestFrames = 40))
    }

    private fun classify(contour: Contour) =
        ToneClassifier.classify(contour, Span(0, contour.size), first = true, longestFrames = contour.size)

    /** A contour moving in straight lines through [levels], evenly spaced. */
    private fun glide(levels: List<Float>, frames: Int) = Contour(
        hopMs = 10f,
        levels = FloatArray(frames) { i ->
            val position = i * (levels.size - 1f) / (frames - 1)
            val before = position.toInt().coerceAtMost(levels.size - 2)
            levels[before] + (levels[before + 1] - levels[before]) * (position - before)
        },
    )

    private fun track(f0: FloatArray, levelDb: FloatArray = FloatArray(f0.size)) =
        PitchTrack(hopMs = 10f, f0 = f0, confidence = FloatArray(f0.size), levelDb = levelDb)
}
