package com.example.mandaring.pitch

/** One syllable: [letters] is pinyin without a tone, [tone] is 1 to 4, or 0 for the neutral tone. */
data class Syllable(val letters: String, val tone: Int) {
    /** Pinyin with the tone written as a mark, e.g. `hǎo`. */
    val pinyin: String
        get() {
            val index = markedVowel()
            if (tone == 0 || index < 0) return letters
            return letters.replaceRange(index, index + 1, MARKED.getValue(letters[index])[tone - 1].toString())
        }

    /** The mark goes on a or e if there is one, on the o of ou, and otherwise on the last vowel. */
    private fun markedVowel(): Int {
        val a = letters.indexOf('a')
        if (a >= 0) return a
        val e = letters.indexOf('e')
        if (e >= 0) return e
        val ou = letters.indexOf("ou")
        if (ou >= 0) return ou
        return letters.indexOfLast { it in MARKED }
    }

    private companion object {
        val MARKED = mapOf(
            'a' to "āáǎà", 'e' to "ēéěè", 'i' to "īíǐì", 'o' to "ōóǒò", 'u' to "ūúǔù", 'ü' to "ǖǘǚǜ",
        )
    }
}

/**
 * A word or phrase. The changed tones of 不 and 一 are written as spoken (`bu2cuo4`, not `bu4cuo4`),
 * third tones as in the dictionary (`hao3dong3`).
 */
data class Word(val syllables: List<Syllable>) {
    /** E.g. `ān jìng`. */
    val pinyin: String get() = syllables.joinToString(" ") { it.pinyin }

    /** The tone of each syllable as it is said: a third tone before another third tone becomes a second. */
    val spokenTones: List<Int>
        get() = syllables.mapIndexed { index, syllable ->
            if (syllable.tone == 3 && syllables.getOrNull(index + 1)?.tone == 3) 2 else syllable.tone
        }

    companion object {
        private val SYLLABLE = Regex("([a-zü]+)([0-5])")

        /**
         * Reads numbered pinyin such as `an1jing4`, the form the sample files are named in.
         * A neutral tone is 0 or 5 and ü may be written as v. Null if [name] is anything else.
         */
        fun parse(name: String): Word? {
            val text = name.lowercase().replace('v', 'ü')
            val syllables = mutableListOf<Syllable>()
            var position = 0
            while (position < text.length) {
                val match = SYLLABLE.matchAt(text, position) ?: return null
                syllables += Syllable(match.groupValues[1], match.groupValues[2].toInt() % 5)
                position = match.range.last + 1
            }
            return if (syllables.isEmpty()) null else Word(syllables)
        }
    }
}
