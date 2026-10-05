package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.PI
import kotlin.math.sin

class PitchAnalyzerTest {
    private val sampleRate = 16000

    @Test
    fun findsFrequencyOfSteadyTones() {
        for (hz in listOf(80f, 147f, 220f, 331f, 440f)) {
            val track = PitchAnalyzer(sampleRate).analyze(harmonicTone(hz, seconds = 0.5f))

            val middle = track.f0[track.size / 2]
            assertEquals("tone at $hz Hz", hz, middle, hz * 0.005f)
        }
    }

    @Test
    fun followsGlide() {
        val seconds = 0.6f
        val samples = FloatArray((sampleRate * seconds).toInt())
        var phase = 0.0
        for (i in samples.indices) {
            val hz = 150.0 + 150.0 * i / samples.size
            phase += 2 * PI * hz / sampleRate
            samples[i] = (0.5 * sin(phase) + 0.25 * sin(2 * phase)).toFloat()
        }

        val track = PitchAnalyzer(sampleRate).analyze(samples)

        for (i in 10 until track.size - 10) {
            val expected = 150f + 150f * (i * track.hopMs / 1000f) / seconds
            assertEquals("frame $i", expected, track.f0[i], expected * 0.02f)
        }
    }

    @Test
    fun silenceAndNoiseAreUnvoiced() {
        val random = java.util.Random(1)
        val noise = FloatArray(sampleRate / 2) { (random.nextFloat() - 0.5f) * 0.2f }

        val silent = PitchAnalyzer(sampleRate).analyze(FloatArray(sampleRate / 2))
        val noisy = PitchAnalyzer(sampleRate).analyze(noise)

        assertFalse((0 until silent.size).any { silent.isVoiced(it) })
        assertFalse((0 until noisy.size).any { noisy.isVoiced(it) })
    }

    @Test
    fun quietPartsOfAClipAreUnvoiced() {
        val tone = harmonicTone(200f, seconds = 0.3f)
        val samples = FloatArray(tone.size * 2)
        tone.copyInto(samples)
        for (i in tone.indices) samples[tone.size + i] = tone[i] * 0.001f

        val track = PitchAnalyzer(sampleRate).analyze(samples)

        assertTrue(track.isVoiced(track.size / 4))
        assertFalse(track.isVoiced(track.size * 3 / 4))
    }

    @Test
    fun wavRoundTripKeepsSamples() {
        val audio = Audio(sampleRate, harmonicTone(200f, seconds = 0.1f))

        val decoded = WavIo.read(ByteArrayInputStream(WavIo.encode(audio)))

        assertEquals(sampleRate, decoded.sampleRate)
        assertEquals(audio.samples.size, decoded.samples.size)
        for (i in audio.samples.indices) assertEquals(audio.samples[i], decoded.samples[i], 2f / 32768f)
    }

    private fun harmonicTone(hz: Float, seconds: Float) = FloatArray((sampleRate * seconds).toInt()) { i ->
        val phase = 2 * PI * hz * i / sampleRate
        (0.5 * sin(phase) + 0.25 * sin(2 * phase) + 0.1 * sin(3 * phase)).toFloat()
    }
}
