package com.example.mandaring.data

import android.content.res.AssetManager
import com.example.mandaring.pitch.Audio
import com.example.mandaring.pitch.Contour
import com.example.mandaring.pitch.PitchAnalyzer
import com.example.mandaring.pitch.PitchHistogram
import com.example.mandaring.pitch.Span
import com.example.mandaring.pitch.WavIo
import com.example.mandaring.pitch.Word
import com.example.mandaring.pitch.contour
import com.example.mandaring.pitch.syllables
import com.example.mandaring.pitch.withoutOctaveSlips

/**
 * A bundled recording of a native speaker saying [word]. [contour] is free of octave slips.
 * [spans] are its syllables, null if they could not be told apart.
 */
class Reference(val name: String, val word: Word, val audio: Audio, val contour: Contour, val spans: List<Span>?)

/**
 * The recordings bundled as assets, shortest words first. They are all by one speaker, whose
 * range is taken from the whole set to put their contours on that speaker's tone levels.
 */
class ReferenceLibrary(val items: List<Reference>) {
    companion object {
        private const val DIRECTORY = "samples"

        /** Reads and analyses every recording, which takes a moment. Files are named by [Word.parse]. */
        fun load(assets: AssetManager): ReferenceLibrary {
            val analysed = assets.list(DIRECTORY).orEmpty().filter { it.endsWith(".wav") }.mapNotNull { file ->
                val name = file.removeSuffix(".wav")
                val word = Word.parse(name) ?: return@mapNotNull null
                val audio = assets.open("$DIRECTORY/$file").use { WavIo.read(it) }
                Triple(name, word, audio) to PitchAnalyzer(audio.sampleRate).analyze(audio.samples)
            }
            val range = analysed.fold(PitchHistogram()) { histogram, (_, track) -> histogram + track }.range()
                ?: return ReferenceLibrary(emptyList())
            val items = analysed.map { (item, track) ->
                val (name, word, audio) = item
                Reference(name, word, audio, track.withoutOctaveSlips().contour(range), track.syllables(word.syllables.size))
            }
            return ReferenceLibrary(items.sortedWith(compareBy({ it.word.syllables.size }, { it.name })))
        }
    }
}
