package com.sitson.vocab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    private val scheduler = ReviewScheduler()

    @Test fun `correct recall lengthens interval without changing dimension`() {
        val start = ReviewState(MasteryDimension.PRODUCTION, stabilityDays = 2.0)
        val result = scheduler.review(start, AttemptGrade(correct = true, responseMillis = 4_000, elapsedDays = 2.0))
        assertEquals(MasteryDimension.PRODUCTION, result.state.dimension)
        assertTrue(result.nextIntervalDays > 2.0)
        assertEquals(1, result.state.consecutiveSuccesses)
    }

    @Test fun `hinted answer receives less credit than unaided answer`() {
        val start = ReviewState(MasteryDimension.COMPREHENSION, stabilityDays = 3.0)
        val unaided = scheduler.review(start, AttemptGrade(correct = true))
        val hinted = scheduler.review(start, AttemptGrade(correct = true, hintsUsed = 2))
        assertTrue(hinted.nextIntervalDays < unaided.nextIntervalDays)
    }

    @Test fun `failure reduces stability gradually and schedules a bounded retry`() {
        val start = ReviewState(MasteryDimension.PRODUCTION, stabilityDays = 20.0, consecutiveSuccesses = 4)
        val result = scheduler.review(start, AttemptGrade(correct = false, elapsedDays = 20.0))
        assertTrue(result.state.stabilityDays in 10.0..19.9)
        assertEquals(0.25, result.nextIntervalDays, 0.001)
        assertEquals(1, result.state.lapses)
        assertEquals(0, result.state.consecutiveSuccesses)
    }

    @Test fun `production is scheduled more conservatively for equal evidence`() {
        val comprehension = scheduler.review(ReviewState(MasteryDimension.COMPREHENSION, 5.0), AttemptGrade(true, elapsedDays = 5.0))
        val production = scheduler.review(ReviewState(MasteryDimension.PRODUCTION, 5.0), AttemptGrade(true, elapsedDays = 5.0))
        assertTrue(production.nextIntervalDays < comprehension.nextIntervalDays)
    }

    @Test fun `immediate repetition gives much less stability than delayed retrieval`() {
        val start = ReviewState(MasteryDimension.COMPREHENSION, stabilityDays = 4.0)
        val crammed = scheduler.review(start, AttemptGrade(true, elapsedDays = 0.01))
        val delayed = scheduler.review(start, AttemptGrade(true, elapsedDays = 8.0))
        assertTrue(delayed.state.stabilityDays > crammed.state.stabilityDays * 1.5)
    }

    @Test fun `retention is ninety percent at one stability interval`() {
        assertEquals(0.9, MemoryModel.retention(elapsedDays = 12.0, stabilityDays = 12.0), 0.0001)
        assertTrue(MemoryModel.retention(24.0, 12.0) < MemoryModel.retention(6.0, 12.0))
    }
}
