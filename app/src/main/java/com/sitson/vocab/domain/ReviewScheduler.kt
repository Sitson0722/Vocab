package com.sitson.vocab.domain

import kotlin.math.max
import kotlin.math.min

/** Deterministic local policy. AI content can never alter this state directly. */
class ReviewScheduler {
    fun review(previous: ReviewState, grade: AttemptGrade): ReviewDecision {
        val hintPenalty = min(0.6, grade.hintsUsed * 0.2)
        val slowPenalty = if (grade.responseMillis > 20_000) 0.15 else 0.0
        val quality = if (grade.correct) max(0.25, 1.0 - hintPenalty - slowPenalty) else 0.0

        val stability = if (grade.correct) {
            previous.stabilityDays * (1.35 + quality * 0.9)
        } else {
            max(0.2, previous.stabilityDays * 0.55)
        }.coerceIn(0.2, 365.0)

        val difficulty = (previous.difficulty + if (grade.correct) -0.04 * quality else 0.12)
            .coerceIn(0.1, 0.95)
        val state = previous.copy(
            stabilityDays = stability,
            difficulty = difficulty,
            consecutiveSuccesses = if (grade.correct) previous.consecutiveSuccesses + 1 else 0,
            lapses = previous.lapses + if (grade.correct) 0 else 1,
        )
        // Failed items return soon, but never enter a rapid-fire loop.
        val interval = if (grade.correct) max(1.0, stability * quality) else 0.25
        return ReviewDecision(state, interval)
    }
}
