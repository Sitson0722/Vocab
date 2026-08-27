package com.sitson.vocab.data

import com.sitson.vocab.domain.AttemptGrade
import com.sitson.vocab.domain.MasteryDimension
import com.sitson.vocab.domain.MemoryModel
import com.sitson.vocab.domain.ReviewScheduler
import com.sitson.vocab.domain.ReviewState
import java.security.MessageDigest

class VocabRepository(private val dao: VocabDao, private val scheduler: ReviewScheduler = ReviewScheduler()) {
    suspend fun import(words: List<ImportedWord>, style: String = "general"): Pair<Int, Int> {
        var added = 0
        words.forEach { imported ->
            val word = WordSenseEntity(
                term = imported.term.trim().lowercase(), phonetic = imported.phonetic.trim(),
                definition = imported.definition.clean(), phrase = imported.phrase.clean(), example = imported.example.clean(),
            )
            val id = dao.addWordWithProgress(word)
            if (id != -1L) {
                added++
                addMaterial(id, "SENTENCE", word.example, word.definition, style, "IMPORT")
                addMaterial(id, "COLLOCATION", word.phrase, word.definition, style, "IMPORT")
            }
        }
        return added to (words.size - added)
    }

    suspend fun words() = dao.words()
    suspend fun styles(): List<String> = dao.styles().flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    suspend fun shouldRefreshMaterials(): Boolean {
        val materials = dao.materialCount()
        return dao.wordCount() > 0 && (materials < dao.wordCount() * 6 || dao.materialUsageCount() > materials * 3)
    }

    suspend fun queue(review: Boolean, limit: Int = 20, style: String = ""): List<StudyItem> {
        val now = System.currentTimeMillis()
        val candidates = if (review) dao.reviewCandidates().sortedWith(
            compareBy<ReviewCandidate> {
                val elapsed = (now - it.lastReviewedAt).coerceAtLeast(0) / DAY
                MemoryModel.retention(elapsed, it.stabilityDays)
            }.thenBy { it.mastery }.thenByDescending { it.difficulty }.thenBy { it.lastReviewedAt },
        ).take(limit) else dao.newCandidates(limit)

        val reserved = mutableSetOf<Long>()
        return candidates.mapNotNull { candidate ->
            val materials = dao.materialsFor(candidate.id, style).filter { material ->
                candidate.dimension != "CONTEXT_COMPREHENSION" ||
                    (material.type in setOf("SENTENCE", "PHRASE") && material.content.contains(candidate.term, ignoreCase = true))
            }
            val material = materials.firstOrNull { it.id !in reserved } ?: materials.firstOrNull() ?: return@mapNotNull null
            reserved += material.id
            val uses = dao.materialUseCount(material.id)
            val isolatedDelayed = candidate.dimension == "ISOLATED_MEANING" &&
                candidate.lastReviewedAt > 0 && now - candidate.lastReviewedAt > DAY / 2
            val novel = if (candidate.dimension == "ISOLATED_MEANING") isolatedDelayed else uses == 0
            candidate.toStudyItem(material, novel)
        }
    }

    suspend fun recordMaterialShown(item: StudyItem) {
        if (item.dimension != "ISOLATED_MEANING") {
            dao.insertUsage(MaterialUsageEntity(materialId = item.materialId, dimension = item.dimension))
        }
    }

    suspend fun grade(item: StudyItem, correct: Boolean, hints: Int, responseMillis: Long, answer: String) {
        val dimension = MasteryDimension.valueOf(item.dimension)
        val old = dao.progress(item.id, item.dimension)
        val now = System.currentTimeMillis()
        val elapsedDays = if (old.lastReviewedAt == 0L) old.stabilityDays else (now - old.lastReviewedAt).coerceAtLeast(0) / DAY
        val decision = scheduler.review(
            ReviewState(dimension, old.stabilityDays, old.difficulty, old.consecutiveSuccesses, old.lapses, old.mastery, old.distinctMaterials),
            AttemptGrade(correct, hints, responseMillis, elapsedDays, item.isNovelMaterial),
        )
        val dueAt = now + (decision.nextIntervalDays * DAY).toLong()
        dao.insertAttempt(AttemptEntity(wordId = item.id, dimension = item.dimension, correct = correct, hintsUsed = hints, responseMillis = responseMillis, answer = answer))
        dao.updateProgress(
            item.id, item.dimension, decision.state.stabilityDays, decision.state.difficulty,
            decision.state.consecutiveSuccesses, decision.state.lapses, dueAt, now,
            decision.state.mastery, decision.state.distinctMaterials,
        )
    }

    suspend fun addGeneratedMaterials(materials: List<GeneratedMaterial>): Pair<Int, Int> {
        val byTerm = dao.words().groupBy { it.term.lowercase() }
        var accepted = 0
        materials.forEach { item ->
            val word = byTerm[item.term.lowercase()]?.firstOrNull { it.definition == item.definition }
                ?: byTerm[item.term.lowercase()]?.singleOrNull()
            if (word != null && item.type in MATERIAL_TYPES && item.content.contains(word.term, ignoreCase = true).orProperNoun(item.type)) {
                if (addMaterial(word.id, item.type, item.content, item.explanation, item.style, "AI")) accepted++
            }
        }
        return accepted to (materials.size - accepted)
    }

    private suspend fun addMaterial(wordId: Long, type: String, content: String, explanation: String, style: String, source: String): Boolean {
        val normalized = content.lowercase().replace(Regex("\\s+"), " ").trim()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest("$wordId|$normalized".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return dao.insertMaterial(MaterialEntity(
            wordId = wordId, type = type, content = content.clean(), explanation = explanation.clean(),
            styleTags = style.clean().ifBlank { "general" }, fingerprint = fingerprint, source = source,
        )) != -1L
    }

    suspend fun statistics(): AppStatistics {
        val now = System.currentTimeMillis()
        val practiced = dao.reviewCandidates()
        fun retention(dimension: String): Double {
            val values = practiced.filter { it.dimension == dimension }.map {
                MemoryModel.retention((now - it.lastReviewedAt).coerceAtLeast(0) / DAY, it.stabilityDays)
            }
            return if (values.isEmpty()) 0.0 else values.average()
        }
        return AppStatistics(
            words = dao.wordCount(), attempts = dao.attemptCount(), correct = dao.correctCount(), due = dao.dueCount(now),
            contextualStrength = dao.averageStrength("CONTEXT_COMPREHENSION"),
            isolatedStrength = dao.averageStrength("ISOLATED_MEANING"), productionStrength = dao.averageStrength("PRODUCTION"),
            contextualMastery = dao.averageMastery("CONTEXT_COMPREHENSION"),
            isolatedMastery = dao.averageMastery("ISOLATED_MEANING"), productionMastery = dao.averageMastery("PRODUCTION"),
            contextualRetention = retention("CONTEXT_COMPREHENSION"),
            isolatedRetention = retention("ISOLATED_MEANING"), productionRetention = retention("PRODUCTION"),
        )
    }

    private fun ReviewCandidate.toStudyItem(material: MaterialEntity, novel: Boolean) = StudyItem(
        id, term, phonetic, definition, phrase, example, dimension, dueAt, lastReviewedAt,
        stabilityDays, difficulty, attempts, material.id, material.type, material.content,
        material.explanation, material.styleTags, novel,
    )

    private fun String.clean() = trim().replace(Regex("\\s+"), " ")
    private fun Boolean.orProperNoun(type: String) = this || type == "PROPER_NOUN"

    companion object {
        private const val DAY = 86_400_000.0
        val MATERIAL_TYPES = setOf("SENTENCE", "PHRASE", "COLLOCATION", "PROPER_NOUN")
    }
}
