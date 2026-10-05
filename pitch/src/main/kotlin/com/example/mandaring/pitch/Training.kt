package com.example.mandaring.pitch

/** How one attempt at a word turned out. */
class Result(val timeMs: Long, val passed: Boolean)

/**
 * Decides which words are worth practising next, from nothing but the results so far.
 *
 * A word moves up a box with each pass and back to the bottom with a miss. The higher the box,
 * the longer it is left alone. Passes in quick succession count once, so repeating a word ten
 * times in a row does not push it out for weeks.
 */
object Schedule {
    private const val HOUR = 60L * 60 * 1000
    private const val DAY = 24 * HOUR

    /** How long a word rests in each box after its last attempt. */
    private val REST = longArrayOf(0, DAY, 3 * DAY, 7 * DAY, 21 * DAY)

    /** Passes closer together than this count as one. */
    private const val MIN_GAP = 6 * HOUR

    /** Stands for "no pass yet"; far enough back not to overflow when subtracted from. */
    private const val LONG_AGO = Long.MIN_VALUE / 2

    /** The box a word is in given its [history], oldest result first. Zero for a word never passed. */
    fun box(history: List<Result>): Int {
        var box = 0
        var lastPass = LONG_AGO
        for (result in history) {
            if (!result.passed) {
                box = 0
                lastPass = LONG_AGO
            } else if (result.timeMs - lastPass >= MIN_GAP) {
                box = (box + 1).coerceAtMost(REST.size - 1)
                lastPass = result.timeMs
            }
        }
        return box
    }

    /** When a word with [history] is next due, or null if it has never been attempted. */
    fun dueMs(history: List<Result>): Long? =
        history.lastOrNull()?.let { it.timeMs + REST[box(history)] }

    /**
     * Up to [size] of [candidates] to practise at [nowMs]: first the words that are due, the
     * ones that went worst first, then words not yet attempted, then whatever comes due soonest.
     *
     * @param history results per word, oldest first
     */
    fun queue(candidates: List<String>, history: Map<String, List<Result>>, nowMs: Long, size: Int): List<String> {
        val due = mutableListOf<Pair<String, Long>>()
        val unseen = mutableListOf<String>()
        val resting = mutableListOf<Pair<String, Long>>()
        for (word in candidates) {
            val results = history[word].orEmpty()
            val dueMs = dueMs(results)
            when {
                dueMs == null -> unseen += word
                dueMs <= nowMs -> due += word to dueMs
                else -> resting += word to dueMs
            }
        }
        return (
            due.sortedWith(compareBy({ box(history.getValue(it.first)) }, { it.second })).map { it.first } +
                unseen +
                resting.sortedBy { it.second }.map { it.first }
            ).take(size)
    }
}

/** What became of a word in a [Session]. */
enum class Outcome { PASSED, FAILED, SKIPPED }

/**
 * A run through a list of words. A word that was not passed comes round once more at the end,
 * so a session does not finish on a miss.
 */
data class Session(
    val queue: List<String>,
    val position: Int = 0,
    val retried: Set<String> = emptySet(),
    val passed: Int = 0,
    /** Words that were missed or skipped at least once, in order. */
    val missed: List<String> = emptyList(),
) {
    val current: String? get() = queue.getOrNull(position)

    val done: Boolean get() = position >= queue.size

    /** Moves on from the current word. */
    fun advance(outcome: Outcome): Session {
        val word = current ?: return this
        val next = copy(position = position + 1)
        return when (outcome) {
            Outcome.PASSED -> next.copy(passed = passed + 1)
            Outcome.FAILED, Outcome.SKIPPED -> next.copy(
                queue = if (outcome == Outcome.FAILED && word !in retried) queue + word else queue,
                retried = if (outcome == Outcome.FAILED) retried + word else retried,
                missed = if (word in missed) missed else missed + word,
            )
        }
    }
}
