package com.example.mandaring.pitch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.log2

/** Checks the pitch tracker against Praat on every recorded sample. */
class PraatComparisonTest {
    @Test
    fun agreesWithPraatOnSamples() {
        val errors = mutableListOf<Float>()
        var frames = 0
        var sameVoicing = 0
        val worst = mutableListOf<Pair<Float, String>>()
        val dump = StringBuilder()

        for (name in Fixtures.sampleNames) {
            val audio = Fixtures.sample(name)
            val track = PitchAnalyzer(audio.sampleRate).analyze(audio.samples)
            val praat = Fixtures.praatTracks.getValue(name)

            val sampleErrors = mutableListOf<Float>()
            var sampleMismatches = 0
            for (i in 0 until track.size) {
                val expected = praat.at(i * track.hopMs)
                frames++
                if (track.isVoiced(i) == !expected.isNaN()) sameVoicing++ else sampleMismatches++
                if (track.isVoiced(i) && !expected.isNaN()) {
                    sampleErrors += abs(12f * log2(track.f0[i] / expected))
                }
            }
            errors += sampleErrors
            dump.appendLine("$name;0;${track.hopMs / 1000f};" + track.f0.joinToString(" ") { if (it.isNaN()) "0" else "%.2f".format(it) })
            val gross = sampleErrors.count { it > GROSS_ERROR_SEMITONES }
            worst += (gross * 10f + sampleMismatches) to
                "$name: $gross gross errors, $sampleMismatches voicing mismatches of ${track.size} frames"
        }

        // Same format as praat_f0.txt, for plotting the two side by side.
        File("build/reports/pitch").mkdirs()
        File("build/reports/pitch/kotlin_f0.txt").writeText(dump.toString())

        errors.sort()
        val median = errors[errors.size / 2]
        val p95 = errors[errors.size * 95 / 100]
        val grossRate = errors.count { it > GROSS_ERROR_SEMITONES }.toFloat() / errors.size
        val voicingAgreement = sameVoicing.toFloat() / frames

        println("samples=${Fixtures.sampleNames.size} frames=$frames bothVoiced=${errors.size}")
        println("median error %.3f st, p95 %.3f st".format(median, p95))
        println("gross errors %.2f %%, voicing agreement %.2f %%".format(grossRate * 100, voicingAgreement * 100))
        worst.sortedByDescending { it.first }.take(8).forEach { println("  ${it.second}") }

        assertTrue("median error $median st", median < 0.5f)
        assertTrue("gross error rate $grossRate", grossRate < 0.02f)
        assertTrue("voicing agreement $voicingAgreement", voicingAgreement > 0.9f)
    }

    private companion object {
        /** Half an octave: beyond this the two trackers disagree about the period, not its precise value. */
        const val GROSS_ERROR_SEMITONES = 6f
    }
}
