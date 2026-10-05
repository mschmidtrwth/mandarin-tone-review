package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonTest {
    private val nan = Float.NaN

    private fun contour(vararg levels: Float) = Contour(hopMs = 10f, levels = levels)

    @Test
    fun syllablesAreAlignedOneByOne() {
        // Level then falling, with a short pause between.
        val reference = contour(4f, 4f, 4f, 4f, 4f, nan, nan, 5f, 4f, 3f, 2f, 1f, nan)
        // The same, but with a long first syllable and a longer pause: the second would be late.
        val attempt = contour(4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, nan, nan, nan, nan, 5f, 4f, 3f, 2f, 1f)
        val referenceSpans = listOf(Span(0, 5), Span(7, 12))
        val attemptSpans = listOf(Span(0, 8), Span(12, 17))

        val stretched = Overlay.of(reference, attempt)!!.similarity!!
        val aligned = Overlay.of(reference, attempt, referenceSpans, attemptSpans)!!

        assertTrue("stretched ${stretched.distance}", stretched.distance > 0.2f)
        assertEquals(0f, aligned.similarity!!.distance, 1e-4f)
        assertEquals(0f, aligned.similarity(referenceSpans[0])!!.distance, 1e-4f)
        assertEquals(0f, aligned.similarity(referenceSpans[1])!!.distance, 1e-4f)
        // The cursor follows the same alignment: the start of the second syllable in each.
        assertEquals(70f, aligned.referenceMs(120f), 1e-3f)
    }

    @Test
    fun aWrongSyllableShowsInItsOwnScore() {
        val reference = contour(4f, 4f, 4f, 4f, nan, 5f, 4f, 3f, 2f, 1f)
        val attempt = contour(4f, 4f, 4f, 4f, nan, 1f, 2f, 3f, 4f, 5f)
        val spans = listOf(Span(0, 4), Span(5, 10))

        val overlay = Overlay.of(reference, attempt, spans, spans)!!
        val syllables = spans.map { SyllableScore(it, overlay.similarity(it)) }

        assertEquals(Rating.CLOSE, syllables[0].rating)
        assertEquals(Rating.OFF, syllables[1].rating)
        assertEquals(Rating.OFF, Comparison(overlay, syllables).rating)
    }

    @Test
    fun mismatchedSpanCountsFallBackToAWholeWordStretch() {
        val reference = contour(nan, 1f, 2f, 3f, 4f, 5f, nan)

        val plain = Overlay.of(reference, reference)!!
        val mismatched = Overlay.of(reference, reference, listOf(Span(1, 6)), listOf(Span(1, 3), Span(3, 6)))!!

        assertEquals(plain.similarity!!.distance, mismatched.similarity!!.distance, 1e-5f)
    }

    /**
     * Over the two-syllable samples, a syllable said with the reference's tone should rate as a
     * match and one with another tone should not. One clean speaker, so this checks the measure,
     * not how a learner will fare. Different words with the same tones are only a proxy for the
     * same word said again: the pitch of a neutral tone follows the tone before it, so it is left
     * out of the same-tone figure.
     */
    @Test
    fun syllableRatingsSeparateTonesOfTwoSyllableSamples() {
        val tracks = Fixtures.sampleNames.associateWith {
            val audio = Fixtures.sample(it)
            PitchAnalyzer(audio.sampleRate).analyze(audio.samples)
        }
        val range = tracks.values.fold(PitchHistogram()) { histogram, track -> histogram + track }.range()!!
        val pairs = Fixtures.sampleNames.filter { Word.parse(it)!!.syllables.size == 2 }
        val spans = pairs.associateWith { tracks.getValue(it).syllables(2) }
        val unsplit = pairs.filter { spans[it] == null }
        println("two-syllable samples: ${pairs.size}, not split: $unsplit")

        val sameTone = mutableListOf<Rating>()
        val otherTone = mutableListOf<Rating>()
        val neutral = mutableListOf<Rating>()
        var sameWords = 0
        var sameWordsMatched = 0
        for (reference in pairs) {
            val referenceContour = tracks.getValue(reference).withoutOctaveSlips().contour(range)
            for (attempt in pairs) {
                if (reference == attempt) continue
                val comparison = Comparison.of(referenceContour, spans[reference], tracks.getValue(attempt), range)
                    ?: continue
                if (comparison.syllables.isEmpty()) continue
                val expected = Word.parse(reference)!!.spokenTones
                val said = Word.parse(attempt)!!.spokenTones
                for (i in 0..1) {
                    val rating = comparison.syllables[i].rating
                    when {
                        expected[i] != said[i] -> otherTone += rating
                        expected[i] == 0 -> neutral += rating
                        else -> sameTone += rating
                    }
                }
                if (expected == said) {
                    sameWords++
                    if (comparison.rating != Rating.OFF) sameWordsMatched++
                }
            }
        }

        val sameMatched = sameTone.count { it != Rating.OFF }.toFloat() / sameTone.size
        val otherMatched = otherTone.count { it != Rating.OFF }.toFloat() / otherTone.size
        println("neutral tone with a neutral tone: %.0f %% match".format(100f * neutral.count { it != Rating.OFF } / neutral.size))
        println(
            "syllable, same tone: %.0f %% match (%d), other tone: %.0f %% match (%d); words with the same tones: %d of %d match"
                .format(sameMatched * 100, sameTone.size, otherMatched * 100, otherTone.size, sameWordsMatched, sameWords),
        )

        assertNotNull(range)
        assertTrue("same tone matched $sameMatched", sameMatched > 0.8f)
        assertTrue("other tone matched $otherMatched", otherMatched < 0.2f)
    }
}
