package com.sitson.vocab.domain

import org.junit.Assert.*
import org.junit.Test

class GraduationPolicyTest {
    private val day = 24 * GraduationPolicy.HOUR
    private fun evidence(dimension: MasteryDimension) = (1..3).map { i ->
        RecallEvidence(dimension, "2026-09-$i", (i + 20) * day, (i + 10) * day, "family-${i % 2}",
            if (dimension == MasteryDimension.PRODUCTION) EvidenceScope.FORM_RECALL else EvidenceScope.CONTEXT_MEANING,
            EvidenceSource.OBSERVED, Outcome.GOOD, corePattern = true)
    }
    @Test fun `both dimensions need delayed observed evidence across days and families`() {
        val c = evidence(MasteryDimension.CONTEXT_COMPREHENSION); val p = evidence(MasteryDimension.PRODUCTION)
        assertEquals(LearningStatus.STEADY, GraduationPolicy.status(true, c + p, false))
        assertEquals(LearningStatus.LEARNING, GraduationPolicy.status(true, c, false))
        assertEquals(LearningStatus.DUE, GraduationPolicy.status(true, c + p, true))
    }
    @Test fun `self reports hints and answer exposure never verify mastery`() {
        val e = evidence(MasteryDimension.PRODUCTION).first()
        listOf(e.copy(source = EvidenceSource.SELF_REPORTED), e.copy(hinted = true), e.copy(answerExposed = true),
            e.copy(uncertain = true), e.copy(guided = true), e.copy(valid = false), e.copy(family = ""), e.copy(priorExposureAt = 0),
            e.copy(priorExposureAt = e.at - 19 * GraduationPolicy.HOUR)).forEach { assertFalse(GraduationPolicy.qualifies(it)) }
    }
    @Test fun `three displays or same day successes cannot verify a dimension`() {
        val p = evidence(MasteryDimension.PRODUCTION)
        assertFalse(GraduationPolicy.verified(MasteryDimension.PRODUCTION, p.map { it.copy(day = "same") }))
        assertFalse(GraduationPolicy.verified(MasteryDimension.PRODUCTION, p.map { it.copy(family = "same") }))
        assertFalse(GraduationPolicy.verified(MasteryDimension.PRODUCTION, p.map { it.copy(outcome = Outcome.AGAIN) }))
    }
    @Test fun `pattern completion alone cannot prove form recall`() {
        assertFalse(GraduationPolicy.verified(MasteryDimension.PRODUCTION, evidence(MasteryDimension.PRODUCTION).map { it.copy(scope = EvidenceScope.PATTERN_COMPLETION) }))
    }
    @Test fun `a later failure changes current status without erasing previous success`() {
        val all = evidence(MasteryDimension.CONTEXT_COMPREHENSION) + evidence(MasteryDimension.PRODUCTION)
        val failure = all.last().copy(at = 100 * day, outcome = Outcome.AGAIN)
        assertEquals(LearningStatus.DUE, GraduationPolicy.status(true, all + failure, false))
        assertEquals(LearningStatus.NEW, GraduationPolicy.status(false, emptyList(), false))
    }
}
