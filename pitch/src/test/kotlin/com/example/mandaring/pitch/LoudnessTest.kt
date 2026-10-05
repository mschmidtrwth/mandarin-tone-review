package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LoudnessTest {
    private fun tone(amplitude: Float, frames: Int = 16_000) =
        Audio(16_000, FloatArray(frames) { amplitude * sin(2 * PI * 200 * it / 16_000).toFloat() })

    private fun Audio.peak() = samples.maxOf { abs(it) }

    @Test
    fun quietAudioIsBroughtUpToTheTarget() {
        val louder = tone(0.05f).louder(target = 0.8f)

        assertEquals(0.8f, louder.peak(), 0.02f)
    }

    @Test
    fun loudAudioIsLeftAlone() {
        val audio = tone(0.9f)

        assertSame(audio, audio.louder(target = 0.8f))
    }

    @Test
    fun aSingleClickDoesNotSetTheLevel() {
        val audio = tone(0.05f, frames = 32_000)
        audio.samples[100] = 1f

        val louder = audio.louder(target = 0.8f)

        // Judged by the tone, not the click, which ends up clipped.
        assertEquals(1f, louder.samples[100], 0f)
        assertEquals(0.8f, louder.samples.drop(1000).maxOf { abs(it) }, 0.02f)
    }

    @Test
    fun gainIsCappedForNearSilence() {
        val louder = tone(0.0005f).louder(target = 0.8f)

        assertEquals(0.0005f * 30f, louder.peak(), 1e-3f)
    }

    @Test
    fun silenceAndEmptyAudioPassThrough() {
        val silence = Audio(16_000, FloatArray(100))
        val empty = Audio(16_000, FloatArray(0))

        assertSame(silence, silence.louder())
        assertSame(empty, empty.louder())
    }
}
