package com.sitson.vocab.domain

object SessionPolicy {
    const val DAY = 86_400_000L
    const val ROUND_SIZE = 5
    const val COOLDOWN_MILLIS = 120_000L
    fun newLimit(minutes: Int) = when (minutes) { 5 -> 2; 15 -> 5; else -> 3 }
    fun overdue(now: Long, due: Long, reviewed: Long): Double =
        (now - due).coerceAtLeast(0).toDouble() / (due - reviewed).coerceAtLeast(DAY)
    fun canRepeat(now: Long, exposed: Long, distinctOtherWords: Int): Boolean =
        exposed == 0L || (now - exposed >= COOLDOWN_MILLIS && distinctOtherWords >= 3)
    fun canIntroduce(remainingMillis: Long, dueMillis: Long, newToday: Int, minutes: Int): Boolean =
        newToday < newLimit(minutes) && remainingMillis - dueMillis >= 100_000L
    fun estimate(kind: ExerciseKind): Long = when (kind) {
        ExerciseKind.TEACH -> 40_000L
        ExerciseKind.INPUT -> 35_000L
        ExerciseKind.CHOICE -> 25_000L
        ExerciseKind.SELF -> 20_000L
    }
}
