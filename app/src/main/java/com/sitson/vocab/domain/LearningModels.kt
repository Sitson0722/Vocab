package com.sitson.vocab.domain

enum class MasteryDimension { COMPREHENSION, PRODUCTION }

data class ReviewState(
    val dimension: MasteryDimension,
    val stabilityDays: Double = 0.5,
    val difficulty: Double = 0.5,
    val consecutiveSuccesses: Int = 0,
    val lapses: Int = 0,
)

data class AttemptGrade(
    val correct: Boolean,
    val hintsUsed: Int = 0,
    val responseMillis: Long = 0,
)

data class ReviewDecision(
    val state: ReviewState,
    val nextIntervalDays: Double,
)
