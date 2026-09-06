package com.sitson.vocab.data

import com.sitson.vocab.domain.*
import com.sitson.vocab.data.RuntimeCodec.strings
import org.json.JSONObject
import java.util.UUID

object CardFactory {
    fun termRegex(term: String) = Regex("(?i)(?<![a-z])${Regex.escape(term)}(?![a-z])")
    fun normalize(text: String) = text.trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("[.!?。！？，,]+$"), "").replace(Regex("\\s+"), " ")

    fun create(word: WordSenseEntity, material: MaterialEntity, dimension: MasteryDimension, teach: Boolean,
        observed: Boolean, guided: Boolean, now: Long, priorExposureAt: Long, reason: String): LearningCard? {
        val regex = termRegex(word.term)
        val metadata = if (material.exerciseJson.isBlank()) JSONObject() else JSONObject(material.exerciseJson)
        val isProduction = dimension == MasteryDimension.PRODUCTION
        val patternPrompt = metadata.optString("productionPrompt")
        val pattern = isProduction && patternPrompt.isNotBlank() && !regex.containsMatchIn(patternPrompt)
        val phrase = word.phrase.takeIf { regex.containsMatchIn(it) && termRegex(it).containsMatchIn(material.content) }
        val target = if (pattern) metadata.getString("productionAnswer") else if (isProduction) phrase ?: word.term else word.term
        val englishPrompt = if (pattern) patternPrompt else termRegex(target).replace(material.content, "____")
        if (!teach && isProduction && (!englishPrompt.contains("____") || (!pattern && regex.containsMatchIn(englishPrompt)))) return null
        val choices = metadata.optJSONArray("choices")?.strings().orEmpty()
        val kind = when {
            teach -> ExerciseKind.TEACH
            guided -> ExerciseKind.SELF
            observed && isProduction -> ExerciseKind.INPUT
            observed && choices.size >= 2 -> ExerciseKind.CHOICE
            else -> ExerciseKind.SELF
        }
        // Never show English embedded in a definition that could expose the target form.
        val targetRegex = termRegex(target)
        if (!teach && isProduction && targetRegex.containsMatchIn(englishPrompt)) return null
        val meaning = if (isProduction) targetRegex.replace(regex.replace(word.definition, "（目标表达）"), "（目标表达）") else word.definition
        val prompt = when {
            teach -> material.content
            isProduction -> "$meaning\n\n$englishPrompt"
            else -> material.content
        }
        return LearningCard(UUID.randomUUID().toString(), word.id, material.id, dimension, kind,
            if (teach) EvidenceScope.ENCOUNTER else if (pattern) EvidenceScope.PATTERN_COMPLETION else if (isProduction) EvidenceScope.FORM_RECALL else EvidenceScope.CONTEXT_MEANING,
            word.term, word.phonetic, word.definition, word.phrase, prompt,
            when { isProduction -> target; kind == ExerciseKind.CHOICE -> metadata.getString("choiceAnswer"); else -> word.definition },
            material.explanation, material.family,
            // Stable shuffle, frozen in the saved card. Correct choice is not always first.
            choices.shuffled(kotlin.random.Random((word.id xor now).toInt())),
            if (target == word.term || pattern) metadata.optJSONArray("alternatives")?.strings().orEmpty() else emptyList(),
            corePattern = pattern || metadata.optBoolean("corePattern") || material.content.contains(word.phrase, ignoreCase = true), guided = guided,
            reason = reason, presentedAt = now, priorExposureAt = priorExposureAt, revealed = teach)
    }
}
