package com.sitson.vocab.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * FSRS-6, fixed default weights. No learning steps, no fuzz; local repair is a separate bounded task.
 * Numerical reference: py-fsrs 9446cb06605c597a063aeee49f7d188d42e34dc2 (MIT).
 * See docs/FSRS.md and THIRD_PARTY_NOTICES.md. Durations and self-assessed speed never alter grades.
 */
class ReviewScheduler {
    fun review(previous: ReviewState, rating: RecallRating, elapsedDays: Double): ReviewDecision {
        require(elapsedDays.isFinite() && elapsedDays >= 0)
        val g = rating.value
        val stability: Double
        val difficulty: Double
        if (!previous.initialized) {
            stability = W[g - 1]
            difficulty = initialDifficulty(g).coerceIn(1.0, 10.0)
        } else {
            val s = previous.stabilityDays
            val d = previous.difficulty
            require(s.isFinite() && s >= 0.001 && d in 1.0..10.0)
            // Match the reference scheduler's complete UTC elapsed days, including its short-term branch.
            val days = kotlin.math.floor(elapsedDays)
            val r = MemoryModel.retention(days, s)
            stability = when {
                days < 1 -> {
                    val increase = exp(W[17] * (g - 3 + W[18])) * s.pow(-W[19])
                    s * if (g >= 2) max(1.0, increase) else increase
                }
                rating == RecallRating.AGAIN -> min(
                    W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp((1 - r) * W[14]),
                    s / exp(W[17] * W[18]),
                )
                else -> s * (1 + exp(W[8]) * (11 - d) * s.pow(-W[9]) *
                    (exp((1 - r) * W[10]) - 1) * if (rating == RecallRating.HARD) W[15] else 1.0)
            }.coerceAtLeast(0.001)
            val damped = d - W[6] * (g - 3) * (10 - d) / 9
            difficulty = (W[7] * initialDifficulty(4) + (1 - W[7]) * damped).coerceIn(1.0, 10.0)
        }
        return ReviewDecision(previous.copy(stabilityDays = stability, difficulty = difficulty, initialized = true),
            round(stability).toInt().coerceIn(1, 36500)) // desired retention fixed at .90 => interval = S
    }

    private fun initialDifficulty(g: Int) = W[4] - exp(W[5] * (g - 1)) + 1

    companion object {
        const val VERSION = "FSRS6-9446cb0-r90"
        private val W = doubleArrayOf(0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194,
            0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658, 0.1542)
    }
}

object MemoryModel {
    private const val DECAY = -0.1542
    private val factor = 0.9.pow(1.0 / DECAY) - 1
    fun retention(elapsedDays: Double, stabilityDays: Double): Double {
        require(elapsedDays.isFinite() && stabilityDays.isFinite())
        return (1 + factor * elapsedDays.coerceAtLeast(0.0) / stabilityDays.coerceAtLeast(0.001)).pow(DECAY)
    }
}
