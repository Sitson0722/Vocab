package com.sitson.vocab.domain

object SessionPolicy {
    const val DAY = 86_400_000L
    const val COOLDOWN_MILLIS = 120_000L
    const val SMALL_POOL_COOLDOWN_MILLIS = 600_000L
    fun overdue(now: Long, due: Long, reviewed: Long): Double =
        (now - due).coerceAtLeast(0).toDouble() / (due - reviewed).coerceAtLeast(DAY)
    fun canRepeat(now: Long, exposed: Long, distinctOtherWords: Int): Boolean =
        exposed == 0L || now - exposed >= SMALL_POOL_COOLDOWN_MILLIS ||
            (now - exposed >= COOLDOWN_MILLIS && distinctOtherWords >= 3)
    fun estimate(kind: ExerciseKind): Long = when (kind) {
        ExerciseKind.TEACH -> 40_000L
        ExerciseKind.INPUT -> 35_000L
        ExerciseKind.CHOICE -> 25_000L
        ExerciseKind.SELF -> 20_000L
    }
}
