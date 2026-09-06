package com.sitson.vocab.domain

/** Evidence is based on an attempt, never on display counts or a cumulative percentage. */
data class RecallEvidence(
    val dimension: MasteryDimension,
    val day: String,
    val at: Long,
    val priorExposureAt: Long,
    val family: String,
    val scope: EvidenceScope,
    val source: EvidenceSource,
    val outcome: Outcome,
    val hinted: Boolean = false,
    val answerExposed: Boolean = false,
    val uncertain: Boolean = false,
    val valid: Boolean = true,
    val guided: Boolean = false,
    val corePattern: Boolean = false,
)
enum class LearningStatus(val label: String) {
    NEW("待学"), LEARNING("学习中 · 待验证"), STEADY("较稳"), DUE("待巩固"),
}
object GraduationPolicy {
    const val HOUR = 3_600_000L
    fun qualifies(e: RecallEvidence): Boolean = e.valid && e.source == EvidenceSource.OBSERVED &&
        e.outcome == Outcome.GOOD && !e.hinted && !e.answerExposed && !e.uncertain && !e.guided &&
        e.priorExposureAt > 0 && e.at - e.priorExposureAt >= 20 * HOUR && e.family.isNotBlank()

    fun verified(dimension: MasteryDimension, evidence: List<RecallEvidence>): Boolean {
        val good = evidence.filter { it.dimension == dimension && qualifies(it) }
        return good.map { it.day }.distinct().size >= 3 && good.map { it.family }.distinct().size >= 2 &&
            good.any { it.at - it.priorExposureAt >= 7 * 24 * HOUR } &&
            (dimension != MasteryDimension.PRODUCTION || (good.any { it.scope == EvidenceScope.FORM_RECALL } && good.any { it.corePattern }))
    }
    fun status(introduced: Boolean, evidence: List<RecallEvidence>, due: Boolean): LearningStatus {
        if (!introduced) return LearningStatus.NEW
        val verified = MasteryDimension.entries.all { verified(it, evidence) }
        val latest = MasteryDimension.entries.map { d -> evidence.filter { it.dimension == d && it.valid && GradingPolicy.rating(it.outcome) != null }.maxByOrNull { it.at } }
        if (due || (verified && latest.any { it?.outcome != Outcome.GOOD })) return LearningStatus.DUE
        return if (verified) LearningStatus.STEADY else LearningStatus.LEARNING
    }
}
