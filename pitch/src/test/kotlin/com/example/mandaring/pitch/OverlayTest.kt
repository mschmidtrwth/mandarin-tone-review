package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTest {
    private val nan = Float.NaN

    @Test
    fun identicalContoursAreAPerfectMatch() {
        val rising = contour(nan, 1f, 2f, 3f, 4f, 5f, nan)

        val similarity = Overlay.of(rising, rising)!!.similarity!!

        assertEquals(0f, similarity.distance, 1e-5f)
        assertEquals(1f, similarity.coverage, 1e-5f)
        assertEquals(Rating.CLOSE, similarity.rating)
    }

    @Test
    fun attemptIsTrimmedAndStretchedOntoTheReference() {
        val reference = contour(nan, 1f, 2f, 3f, 4f, 5f, nan)
        // The same rise at half the speed, after a longer silence.
        val attempt = contour(nan, nan, nan, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, nan, nan)

        val overlay = Overlay.of(reference, attempt)!!

        assertEquals(reference.size, overlay.attempt.size)
        for (i in 0 until reference.size) {
            assertEquals(reference.levels[i], overlay.attempt.levels[i], 1e-4f)
        }
        // The attempt starts at 30 ms and ends at 110 ms, the reference at 10 ms and 50 ms.
        assertEquals(10f, overlay.referenceMs(30f), 1e-3f)
        assertEquals(30f, overlay.referenceMs(70f), 1e-3f)
        assertEquals(50f, overlay.referenceMs(110f), 1e-3f)
    }

    @Test
    fun heightAndShapeAreMeasuredSeparately() {
        val reference = contour(3f, 3f, 3f, 3f, 3f)
        val higher = contour(4f, 4f, 4f, 4f, 4f)
        val falling = contour(5f, 4f, 3f, 2f, 1f)

        val offset = Overlay.of(reference, higher)!!.similarity!!
        val wrongTone = Overlay.of(reference, falling)!!.similarity!!

        assertEquals(1f, offset.heightError, 1e-5f)
        assertEquals(0f, offset.shapeError, 1e-5f)
        assertEquals(0f, wrongTone.heightError, 1e-5f)
        assertEquals(1.414f, wrongTone.shapeError, 1e-3f)
        assertTrue(offset.distance < wrongTone.distance)
        assertEquals(Rating.OFF, wrongTone.rating)
    }

    @Test
    fun gapsAreKeptAndCountAgainstCoverage() {
        val reference = contour(2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f)
        val broken = contour(2f, nan, nan, nan, nan, nan, 2f, 2f)

        val overlay = Overlay.of(reference, broken)!!

        assertTrue(overlay.attempt.levels[3].isNaN())
        assertEquals(3f / 8f, overlay.similarity!!.coverage, 1e-5f)
        assertEquals(Rating.OFF, overlay.similarity!!.rating)
    }

    @Test
    fun nothingToAlignWithoutVoicedSpeech() {
        val voiced = contour(1f, 2f, 3f)
        val silent = contour(nan, nan, nan)

        assertNull(Overlay.of(voiced, silent))
        assertNull(Overlay.of(silent, voiced))
    }

    /**
     * On the samples the rating should tell the tones apart: recordings of one tone match each
     * other, recordings of different tones do not. All one speaker, so this checks the measure
     * and its thresholds, not how a learner will fare.
     */
    @Test
    fun ratingSeparatesTonesOfMonosyllabicSamples() {
        val tracks = Fixtures.sampleNames.associateWith {
            val audio = Fixtures.sample(it)
            PitchAnalyzer(audio.sampleRate).analyze(audio.samples)
        }
        val range = tracks.values.fold(PitchHistogram()) { histogram, track -> histogram + track }.range()!!
        val monosyllables = Fixtures.sampleNames.filter { Word.parse(it)!!.syllables.size == 1 }

        val sameTone = mutableListOf<Rating>()
        val otherTone = mutableListOf<Rating>()
        for (reference in monosyllables) {
            for (attempt in monosyllables) {
                if (reference == attempt) continue
                val overlay = Overlay.of(tracks.getValue(reference).contour(range), tracks.getValue(attempt).contour(range))
                val rating = overlay!!.similarity!!.rating
                (if (tone(reference) == tone(attempt)) sameTone else otherTone) += rating
            }
        }

        val sameMatched = sameTone.count { it != Rating.OFF }.toFloat() / sameTone.size
        val otherMatched = otherTone.count { it != Rating.OFF }.toFloat() / otherTone.size
        println("same tone: %.0f %% match, other tone: %.0f %% match".format(sameMatched * 100, otherMatched * 100))

        assertTrue("same tone matched $sameMatched", sameMatched > 0.85f)
        assertTrue("other tone matched $otherMatched", otherMatched < 0.15f)
        assertEquals(0, otherTone.count { it == Rating.CLOSE })
    }

    private fun tone(name: String) = Word.parse(name)!!.syllables.single().tone

    private fun contour(vararg levels: Float) = Contour(hopMs = 10f, levels = levels)
}
