package com.sitson.vocab.domain

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ReviewSchedulerTest {
    private val scheduler = ReviewScheduler()
    @Test fun `matches pinned official FSRS reference across new short delayed and failed reviews`() {
        val json = javaClass.getResourceAsStream("/fsrs6-reference.json")!!.bufferedReader().use { it.readText() }
        val fixtures = JSONArray(json)
        var state = ReviewState(MasteryDimension.CONTEXT_COMPREHENSION)
        for (i in 0 until fixtures.length()) {
            val row = fixtures.getJSONObject(i)
            if (row.getBoolean("reset")) state = ReviewState(MasteryDimension.CONTEXT_COMPREHENSION)
            val grade = RecallRating.entries.first { it.value == row.getInt("grade") }
            val result = scheduler.review(state, grade, row.getDouble("gap"))
            assertEquals("stability at $i", row.getDouble("stability"), result.state.stabilityDays, 1e-8)
            assertEquals("difficulty at $i", row.getDouble("difficulty"), result.state.difficulty, 1e-8)
            assertEquals("interval at $i", row.getInt("interval"), result.nextIntervalDays)
            state = result.state
        }
    }
    @Test fun `same evidence uses same policy in both dimensions without cross updates`() {
        val c = scheduler.review(ReviewState(MasteryDimension.CONTEXT_COMPREHENSION), RecallRating.GOOD, 0.0)
        val p = scheduler.review(ReviewState(MasteryDimension.PRODUCTION), RecallRating.GOOD, 0.0)
        assertEquals(c.nextIntervalDays, p.nextIntervalDays)
        assertEquals(MasteryDimension.PRODUCTION, p.state.dimension)
    }
    @Test fun `retention falls with time and is ninety percent at stability`() {
        assertEquals(0.9, MemoryModel.retention(12.0, 12.0), 1e-10)
        assertTrue(MemoryModel.retention(24.0, 12.0) < MemoryModel.retention(6.0, 12.0))
    }
    @Test fun `hinted successes are Again and skips never produce a scheduler rating`() {
        assertEquals(Outcome.ASSISTED, GradingPolicy.outcome(true, true, false))
        assertEquals(RecallRating.AGAIN, GradingPolicy.rating(Outcome.ASSISTED))
        assertEquals(RecallRating.HARD, GradingPolicy.rating(GradingPolicy.outcome(true, false, true)))
        listOf(Outcome.SKIP, Outcome.TAUGHT, Outcome.VOID, Outcome.ALTERNATIVE).forEach { assertNull(GradingPolicy.rating(it)) }
    }
    @Test fun `invalid elapsed duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { scheduler.review(ReviewState(MasteryDimension.PRODUCTION), RecallRating.GOOD, Double.NaN) }
    }
}
