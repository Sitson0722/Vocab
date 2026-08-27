package com.sitson.vocab.data

import com.sitson.vocab.domain.AttemptGrade
import com.sitson.vocab.domain.MasteryDimension
import com.sitson.vocab.domain.MemoryModel
import com.sitson.vocab.domain.ReviewScheduler
import com.sitson.vocab.domain.ReviewState

class VocabRepository(private val dao: VocabDao, private val scheduler: ReviewScheduler = ReviewScheduler()) {
    suspend fun import(words: List<ImportedWord>): Pair<Int, Int> {
        var added = 0
        words.forEach {
            if (dao.addWordWithProgress(WordSenseEntity(
                term = it.term.trim().lowercase(),
                phonetic = it.phonetic.trim(),
                definition = it.definition.trim().replace(Regex("\\s+"), " "),
                phrase = it.phrase.trim().replace(Regex("\\s+"), " "),
                example = it.example.trim().replace(Regex("\\s+"), " "),
            ))) added++
        }
        return added to (words.size - added)
    }

    suspend fun words() = dao.words()
    suspend fun queue(review: Boolean, limit: Int = 20): List<StudyItem> = if (review) {
        val now = System.currentTimeMillis()
        dao.reviewCandidates().sortedWith(
            compareBy<StudyItem> {
                val elapsed = (now - it.lastReviewedAt).coerceAtLeast(0) / 86_400_000.0
                MemoryModel.retention(elapsed, it.stabilityDays)
            }.thenByDescending { it.difficulty }.thenBy { it.lastReviewedAt },
        ).take(limit)
    } else dao.newItems(limit)

    suspend fun grade(item: StudyItem, correct: Boolean, hints: Int, responseMillis: Long, answer: String) {
        val dimension = MasteryDimension.valueOf(item.dimension)
        val old = dao.progress(item.id, item.dimension)
        val now = System.currentTimeMillis()
        val elapsedDays = if (old.lastReviewedAt == 0L) old.stabilityDays else (now - old.lastReviewedAt).coerceAtLeast(0) / 86_400_000.0
        val decision = scheduler.review(
            ReviewState(dimension, old.stabilityDays, old.difficulty, old.consecutiveSuccesses, old.lapses),
            AttemptGrade(correct, hints, responseMillis, elapsedDays),
        )
        val dueAt = now + (decision.nextIntervalDays * 86_400_000L).toLong()
        dao.insertAttempt(AttemptEntity(wordId = item.id, dimension = item.dimension, correct = correct, hintsUsed = hints, responseMillis = responseMillis, answer = answer))
        dao.updateProgress(item.id, item.dimension, decision.state.stabilityDays, decision.state.difficulty, decision.state.consecutiveSuccesses, decision.state.lapses, dueAt, now)
    }

    suspend fun statistics() = AppStatistics(
        words = dao.wordCount(), attempts = dao.attemptCount(), correct = dao.correctCount(),
        due = dao.dueCount(System.currentTimeMillis()),
        comprehensionStrength = dao.averageStrength("COMPREHENSION"),
        productionStrength = dao.averageStrength("PRODUCTION"),
    )
}
