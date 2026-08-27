package com.sitson.vocab.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    private val scheduler = ReviewScheduler()

    @Test fun `correct recall lengthens interval without changing dimension`() {
        val start = ReviewState(MasteryDimension.PRODUCTION, stabilityDays = 2.0)
        val result = scheduler.review(start, AttemptGrade(correct = true, responseMillis = 4_000))
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
        val result = scheduler.review(start, AttemptGrade(correct = false))
        assertEquals(11.0, result.state.stabilityDays, 0.001)
        assertEquals(0.25, result.nextIntervalDays, 0.001)
        assertEquals(1, result.state.lapses)
        assertEquals(0, result.state.consecutiveSuccesses)
    }

    @Test fun `production is scheduled more conservatively for equal evidence`() {
        val comprehension = scheduler.review(ReviewState(MasteryDimension.COMPREHENSION, 5.0), AttemptGrade(true))
        val production = scheduler.review(ReviewState(MasteryDimension.PRODUCTION, 5.0), AttemptGrade(true))
        assertTrue(production.nextIntervalDays < comprehension.nextIntervalDays)
    }
}
