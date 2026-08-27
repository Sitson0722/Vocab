package com.sitson.vocab.domain

import kotlin.math.max
import kotlin.math.min

/** Deterministic local policy. AI content can never alter this state directly. */
class ReviewScheduler {
    fun review(previous: ReviewState, grade: AttemptGrade): ReviewDecision {
        val hintPenalty = min(0.6, grade.hintsUsed * 0.2)
        val slowPenalty = if (grade.responseMillis > 20_000) 0.15 else 0.0
        val quality = if (grade.correct) max(0.25, 1.0 - hintPenalty - slowPenalty) else 0.0
        val retrievability = MemoryModel.retention(grade.elapsedDays, previous.stabilityDays)
        // A successful retrieval near forgetting is stronger evidence than immediate repetition.
        val spacingEvidence = (1.0 - retrievability).coerceIn(0.005, 1.0)

        val stability = if (grade.correct) {
            previous.stabilityDays * (1.0 + quality * (0.02 + 4.0 * spacingEvidence) * (1.05 - previous.difficulty * 0.35))
        } else {
            // A lapse is evidence of overestimated stability, but does not erase durable history.
            max(0.2, previous.stabilityDays * (0.55 + 0.15 * retrievability))
        }.coerceIn(0.2, 365.0)

        val difficulty = (previous.difficulty + if (grade.correct) -0.04 * quality else 0.12)
            .coerceIn(0.1, 0.95)
        val diversityWeight = if (grade.novelMaterial) 1.0 else 0.2
        val masteryGain = quality * diversityWeight * (0.05 + spacingEvidence) * (1.0 - previous.mastery) * 0.55
        val evidenceCount = previous.distinctMaterials + if (grade.novelMaterial) 1 else 0
        val evidenceCap = when (evidenceCount) { 0 -> 0.35; 1 -> 0.55; 2 -> 0.75; else -> 1.0 }
        val mastery = if (grade.correct) min(evidenceCap, previous.mastery + masteryGain)
            else max(0.0, previous.mastery - 0.08 * (0.5 + retrievability))
        val state = previous.copy(
            stabilityDays = stability,
            difficulty = difficulty,
            consecutiveSuccesses = if (grade.correct) previous.consecutiveSuccesses + 1 else 0,
            lapses = previous.lapses + if (grade.correct) 0 else 1,
            mastery = mastery,
            distinctMaterials = evidenceCount,
        )
        // Failed items return soon, but never enter a rapid-fire loop.
        // Productive recall is harder and receives a deliberately shorter interval for equal evidence.
        val dimensionFactor = if (previous.dimension == MasteryDimension.PRODUCTION) 0.8 else 1.0
        val interval = if (grade.correct) max(1.0, stability * quality * dimensionFactor) else 0.25
        return ReviewDecision(state, interval)
    }
}
