package com.sitson.vocab.domain

data class DimensionEvidence(val mastery: Double, val stabilityDays: Double)

object GraduationPolicy {
    fun level(mastery: Double, stabilityDays: Double): Int = when {
        mastery >= 0.85 && stabilityDays >= 30 -> 5
        mastery >= 0.70 && stabilityDays >= 15 -> 4
        mastery >= 0.50 && stabilityDays >= 7 -> 3
        mastery >= 0.30 && stabilityDays >= 3 -> 2
        mastery >= 0.15 && stabilityDays >= 1 -> 1
        else -> 0
    }

    fun isMastered(ageDays: Double, dimensions: List<DimensionEvidence>, distinctMaterials: Int): Boolean =
        ageDays >= 90.0 && dimensions.size == 3 &&
            dimensions.all { level(it.mastery, it.stabilityDays) == 5 } && distinctMaterials >= 3
}
