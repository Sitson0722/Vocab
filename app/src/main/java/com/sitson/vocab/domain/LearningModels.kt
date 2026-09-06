package com.sitson.vocab.domain

/** ISOLATED_MEANING survives only as archived database history, never an active dimension. */
enum class MasteryDimension(val label: String) {
    CONTEXT_COMPREHENSION("看得懂"), PRODUCTION("用得出"),
}
enum class RecallRating(val value: Int) { AGAIN(1), HARD(2), GOOD(3) }
enum class Outcome { GOOD, HARD, AGAIN, ASSISTED, ALTERNATIVE, TAUGHT, SKIP, VOID }
enum class EvidenceSource { OBSERVED, SELF_REPORTED, NONE, LEGACY_UNKNOWN }
enum class ExerciseKind { TEACH, SELF, INPUT, CHOICE }
enum class EvidenceScope { CONTEXT_MEANING, FORM_RECALL, PATTERN_COMPLETION, ENCOUNTER }

data class ReviewState(
    val dimension: MasteryDimension,
    val stabilityDays: Double = 0.0,
    val difficulty: Double = 0.0,
    val initialized: Boolean = false,
)
data class ReviewDecision(val state: ReviewState, val nextIntervalDays: Int)

object GradingPolicy {
    fun outcome(correct: Boolean, hinted: Boolean, uncertain: Boolean): Outcome = when {
        !correct -> Outcome.AGAIN
        hinted -> Outcome.ASSISTED
        uncertain -> Outcome.HARD
        else -> Outcome.GOOD
    }
    fun rating(outcome: Outcome): RecallRating? = when (outcome) {
        Outcome.GOOD -> RecallRating.GOOD
        Outcome.HARD -> RecallRating.HARD
        Outcome.AGAIN, Outcome.ASSISTED -> RecallRating.AGAIN
        else -> null
    }
}
