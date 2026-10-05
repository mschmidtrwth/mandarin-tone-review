package com.example.mandaring.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private fun pass(timeMs: Long) = Result(timeMs, true)
    private fun miss(timeMs: Long) = Result(timeMs, false)

    @Test
    fun passesOnDifferentDaysClimbBoxes() {
        assertEquals(0, Schedule.box(emptyList()))
        assertEquals(1, Schedule.box(listOf(pass(0))))
        assertEquals(3, Schedule.box(listOf(pass(0), pass(day), pass(3 * day))))
    }

    @Test
    fun aMissSendsAWordBackToTheStart() {
        assertEquals(0, Schedule.box(listOf(pass(0), pass(day), miss(3 * day))))
        assertEquals(1, Schedule.box(listOf(miss(0), pass(day))))
    }

    @Test
    fun passesInQuickSuccessionCountOnce() {
        val burst = List(10) { pass(it * 60_000L) }

        assertEquals(1, Schedule.box(burst))
    }

    @Test
    fun boxesStopAtTheTop() {
        val history = List(20) { pass(it * 30 * day) }

        assertEquals(4, Schedule.box(history))
    }

    @Test
    fun dueTimeFollowsTheLastAttemptAndBox() {
        assertNull(Schedule.dueMs(emptyList()))
        assertEquals(day, Schedule.dueMs(listOf(pass(0))))
        // Three passes put a word in box 3, which rests for a week.
        assertEquals(9 * day, Schedule.dueMs(listOf(pass(0), pass(day), pass(2 * day))))
        // A miss is due straight away.
        assertEquals(2 * day, Schedule.dueMs(listOf(pass(0), miss(2 * day))))
    }

    @Test
    fun queueOrdersDueThenUnseenThenResting() {
        val now = 10 * day
        val history = mapOf(
            "worst" to listOf(miss(9 * day)),
            "overdue" to listOf(pass(0), pass(day)),
            "resting" to listOf(pass(now - hour)),
        )

        val queue = Schedule.queue(
            candidates = listOf("resting", "new1", "overdue", "new2", "worst"),
            history = history,
            nowMs = now,
            size = 10,
        )

        // "worst" is in box 0, "overdue" in box 2; both are due.
        assertEquals(listOf("worst", "overdue", "new1", "new2", "resting"), queue)
    }

    @Test
    fun queueIsCutToSize() {
        val queue = Schedule.queue(listOf("a", "b", "c"), emptyMap(), nowMs = 0, size = 2)

        assertEquals(listOf("a", "b"), queue)
    }

    @Test
    fun sessionWalksTheQueue() {
        var session = Session(listOf("a", "b"))
        assertEquals("a", session.current)

        session = session.advance(Outcome.PASSED).advance(Outcome.PASSED)

        assertTrue(session.done)
        assertNull(session.current)
        assertEquals(2, session.passed)
        assertTrue(session.missed.isEmpty())
    }

    @Test
    fun aMissedWordComesRoundOnceMore() {
        var session = Session(listOf("a", "b"))

        session = session.advance(Outcome.FAILED)
        assertEquals(listOf("a", "b", "a"), session.queue)
        session = session.advance(Outcome.PASSED)
        assertEquals("a", session.current)
        // Missing it again does not queue it a third time.
        session = session.advance(Outcome.FAILED)

        assertTrue(session.done)
        assertEquals(listOf("a", "b", "a"), session.queue)
        assertEquals(1, session.passed)
        assertEquals(listOf("a"), session.missed)
    }

    @Test
    fun aSkippedWordIsNotRepeated() {
        val session = Session(listOf("a")).advance(Outcome.SKIPPED)

        assertTrue(session.done)
        assertEquals(listOf("a"), session.missed)
        assertFalse(session.queue.size > 1)
    }
}
