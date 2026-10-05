package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchTrackTest {
    @Test
    fun nanFramesAreUnvoiced() {
        val track = PitchTrack(
            hopMs = 10f,
            f0 = floatArrayOf(Float.NaN, 220f, 225f),
            confidence = floatArrayOf(0f, 0.9f, 0.9f),
        )

        assertFalse(track.isVoiced(0))
        assertTrue(track.isVoiced(1))
        assertEquals(30f, track.durationMs, 0f)
    }
}
