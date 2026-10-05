package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PitchRangeTest {
    @Test
    fun levelsRunFromOneAtTheBottomToFiveAtTheTop() {
        val range = PitchRange(lowHz = 100f, highHz = 400f)

        assertEquals(1f, range.level(100f), 1e-4f)
        assertEquals(3f, range.level(200f), 1e-4f)
        assertEquals(5f, range.level(400f), 1e-4f)
        assertEquals(7f, range.level(800f), 1e-4f)
        assertEquals(200f, range.hz(3f), 1e-2f)
    }

    @Test
    fun sameShapeFromLowAndHighVoiceGetsSameLevels() {
        val low = PitchRange(90f, 180f)
        val high = PitchRange(180f, 360f)

        for (fraction in listOf(0f, 0.3f, 0.8f, 1f)) {
            assertEquals(low.level(low.hz(1f + 4f * fraction)), high.level(high.hz(1f + 4f * fraction)), 1e-3f)
        }
        assertEquals(low.level(120f), high.level(240f), 1e-4f)
    }

    @Test
    fun rangeIsFifthToNinetyFifthPercentile() {
        // 200 frames spread evenly in log frequency over one octave, plus two mistracked frames.
        val f0 = FloatArray(200) { 150f * Math.pow(2.0, it / 199.0).toFloat() } + floatArrayOf(75f, 580f)

        val range = (PitchHistogram() + track(f0)).range()!!

        assertEquals(150f * Math.pow(2.0, 0.05).toFloat(), range.lowHz, 3f)
        assertEquals(150f * Math.pow(2.0, 0.95).toFloat(), range.highHz, 6f)
    }

    @Test
    fun noRangeFromTooLittleOrTooFlatSpeech() {
        val brief = PitchHistogram() + track(FloatArray(50) { 150f + it })
        val monotone = PitchHistogram() + track(FloatArray(300) { 200f })

        assertNull(PitchHistogram().range())
        assertNull(brief.range())
        assertNull(monotone.range())
    }

    @Test
    fun unvoicedFramesAreIgnoredAndRecordingsAccumulate() {
        val gappy = FloatArray(120) { if (it % 2 == 0) Float.NaN else 150f + it }

        val once = PitchHistogram() + track(gappy)
        val twice = once + track(gappy)

        assertEquals(60, once.voicedFrames)
        assertNull(once.range())
        assertEquals(120, twice.voicedFrames)
        assertNotNull(twice.range())
    }

    @Test
    fun survivesEncoding() {
        val histogram = PitchHistogram() + track(FloatArray(200) { 120f + it })

        val decoded = PitchHistogram.decode(histogram.encode())

        assertEquals(histogram.voicedFrames, decoded.voicedFrames)
        assertEquals(histogram.range(), decoded.range())
        assertEquals(0, PitchHistogram.decode("").voicedFrames)
        assertEquals(0, PitchHistogram.decode("garbage,9999:5,3:x").voicedFrames)
    }

    private fun track(f0: FloatArray) = PitchTrack(hopMs = 10f, f0 = f0, confidence = FloatArray(f0.size), levelDb = FloatArray(f0.size))
}
