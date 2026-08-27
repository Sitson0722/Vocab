package com.sitson.vocab.domain

enum class MasteryDimension { CONTEXT_COMPREHENSION, ISOLATED_MEANING, PRODUCTION }

data class ReviewState(
    val dimension: MasteryDimension,
    val stabilityDays: Double = 0.5,
    val difficulty: Double = 0.5,
    val consecutiveSuccesses: Int = 0,
    val lapses: Int = 0,
    val mastery: Double = 0.0,
    val distinctMaterials: Int = 0,
)

data class AttemptGrade(
    val correct: Boolean,
    val hintsUsed: Int = 0,
    val responseMillis: Long = 0,
    val elapsedDays: Double = 0.0,
    val novelMaterial: Boolean = false,
)

data class ReviewDecision(
    val state: ReviewState,
    val nextIntervalDays: Double,
)

object MemoryModel {
    /** Stability is the number of days at which predicted retention reaches 90%. */
    fun retention(elapsedDays: Double, stabilityDays: Double): Double {
        if (elapsedDays <= 0.0) return 1.0
        return kotlin.math.exp(kotlin.math.ln(0.9) * elapsedDays / stabilityDays.coerceAtLeast(0.2))
            .coerceIn(0.0, 1.0)
    }
}
