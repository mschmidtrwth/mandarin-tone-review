package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WordTest {
    @Test
    fun splitsNumberedPinyinIntoSyllables() {
        assertEquals(Word(listOf(Syllable("an", 1), Syllable("jing", 4))), Word.parse("an1jing4"))
        assertEquals(Word(listOf(Syllable("ma", 2), Syllable("fan", 0))), Word.parse("ma2fan0"))
        assertEquals(Word(listOf(Syllable("e", 4))), Word.parse("e4"))
        assertEquals(Word(listOf(Syllable("lü", 4), Syllable("de", 0))), Word.parse("lv4de5"))
    }

    @Test
    fun rejectsAnythingElse() {
        assertNull(Word.parse(""))
        assertNull(Word.parse("hao"))
        assertNull(Word.parse("hao3 "))
        assertNull(Word.parse("3hao"))
        assertNull(Word.parse("hao7"))
    }

    @Test
    fun putsToneMarkOnTheRightVowel() {
        val expected = mapOf(
            "hao3" to "hǎo",
            "gui4" to "guì",
            "liu2xing2" to "liú xíng",
            "shou3" to "shǒu",
            "xiong2" to "xióng",
            "nuan3he0" to "nuǎn he",
            "piao4liang0" to "piào liang",
            "jue2" to "jué",
            "e4" to "è",
            "nv3" to "nǚ",
            "zi4si1" to "zì sī",
        )
        for ((numbered, marked) in expected) {
            assertEquals(marked, Word.parse(numbered)?.pinyin)
        }
    }

    @Test
    fun everySampleNameIsAWord() {
        for (name in Fixtures.sampleNames) {
            assertNotNull(name, Word.parse(name))
        }
    }
}
