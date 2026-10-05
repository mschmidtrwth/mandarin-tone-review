package com.example.mandaring.pitch

/**
 * How one syllable of an attempt compares with the same syllable of the reference.
 *
 * @property span the syllable within the reference's frames
 * @property similarity null if the attempt is never voiced where the reference is
 */
class SyllableScore(val span: Span, val similarity: Similarity?) {
    val rating: Rating get() = similarity?.rating ?: Rating.OFF
}

/**
 * An attempt at a word laid over its reference, and how each syllable fared.
 *
 * The word is only as good as its worst syllable: one wrong tone is not made up for by the others.
 *
 * @property syllables empty if the syllables of either recording could not be told apart, in which
 * case the word is judged as a whole
 */
class Comparison(val overlay: Overlay, val syllables: List<SyllableScore>) {
    /** Over the whole word. */
    val similarity: Similarity? get() = overlay.similarity

    val rating: Rating
        get() = if (syllables.isEmpty()) {
            similarity?.rating ?: Rating.OFF
        } else {
            syllables.maxOf { it.rating }
        }

    companion object {
        /**
         * @param reference the reference's contour, expected without octave slips like the attempt's is here
         * @param referenceSpans the syllables of [reference], null if they could not be told apart
         * @return null if there is no voiced speech to compare
         */
        fun of(reference: Contour, referenceSpans: List<Span>?, attempt: PitchTrack, range: PitchRange): Comparison? {
            val attemptSpans = referenceSpans?.let { attempt.syllables(it.size) }
            val overlay = Overlay.of(reference, attempt.withoutOctaveSlips().contour(range), referenceSpans, attemptSpans) ?: return null
            val syllables = if (referenceSpans != null && attemptSpans != null) {
                referenceSpans.map { SyllableScore(it, overlay.similarity(it)) }
            } else {
                emptyList()
            }
            return Comparison(overlay, syllables)
        }
    }
}
