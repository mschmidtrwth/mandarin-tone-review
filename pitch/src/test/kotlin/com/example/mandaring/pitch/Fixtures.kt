package com.example.mandaring.pitch

import kotlin.math.roundToInt

/** Praat's F0 track for one sample, as written by tools/praat_reference.py. */
class PraatTrack(private val t0: Float, private val dt: Float, private val f0: FloatArray) {
    /** Praat's F0 at the frame nearest to [timeMs], NaN if unvoiced or outside the track. */
    fun at(timeMs: Float): Float {
        val frame = ((timeMs / 1000f - t0) / dt).roundToInt()
        val value = f0.getOrElse(frame) { 0f }
        return if (value > 0f) value else Float.NaN
    }
}

object Fixtures {
    val sampleNames: List<String> by lazy { praatTracks.keys.sorted() }

    val praatTracks: Map<String, PraatTrack> by lazy {
        resource("/praat_f0.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { line ->
                val (name, t0, dt, values) = line.split(';')
                val f0 = values.trim().split(' ').map { it.toFloat() }.toFloatArray()
                name to PraatTrack(t0.toFloat(), dt.toFloat(), f0)
            }
        }
    }

    fun sample(name: String): Audio = resource("/samples/$name.wav").use { WavIo.read(it) }

    private fun resource(path: String) =
        requireNotNull(Fixtures::class.java.getResourceAsStream(path)) { "Missing test resource $path" }
}
