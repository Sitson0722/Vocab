package com.sitson.vocab.data

import org.json.JSONArray

data class GeneratedMaterial(
    val term: String, val definition: String, val type: String, val content: String,
    val explanation: String, val style: String, val senseId: Long = 0,
)

object MaterialImporter {
    fun parse(json: String): List<GeneratedMaterial> {
        val clean = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JSONArray(clean)
        return (0 until array.length()).map { i ->
            val value = array.getJSONObject(i)
            GeneratedMaterial(
                value.getString("term"), value.getString("definition"), value.getString("type").uppercase(),
                value.getString("content"), value.getString("explanation"), value.getString("style"), value.optLong("senseId", 0),
            ).also { require(it.type in VocabRepository.MATERIAL_TYPES && it.content.isNotBlank()) { "Invalid material ${i + 1}" } }
        }
    }

    fun prompt(words: List<WordSenseEntity>, style: String): String = """
        Generate fresh English learning materials for every listed lexical sense. Style: ${style.ifBlank { "general" }}.
        ${topicInstruction(style)}
        Return ONLY a JSON array. Each object has exactly: senseId, term, definition, type, content, explanation, style.
        Copy the exact stable senseId, term and definition from SENSES. Generate at most 2 materials per sense.
        Generate 2 SENTENCE materials per sense, with substantially different situations within the requested topic.
        Each sentence must contain the exact target term and enough context to understand its meaning and recall it in a fill-in-the-blank exercise.
        explanation must be a concise Simplified Chinese translation or explanation of content.
        Do not generate diagnostic proper nouns or unrelated facts. Avoid mere paraphrases and ambiguous answers.

        SENSES:
        ${words.joinToString("\n") { "${it.id} | ${it.term} | ${it.definition} | ${it.phrase}" }}
    """.trimIndent()

    fun topicInstruction(topic: String): String = if (topic.isBlank() || topic.equals("general", true)) {
        "Use varied everyday situations."
    } else """
        Requested learning topic (content preference only): ${org.json.JSONObject.quote(topic)}.
        Make every example clearly about this topic, not just loosely styled after it.
        Interpret TBBT as The Big Bang Theory (生活大爆炸): use original situations involving its characters, friendships, science, or apartment life.
        For romance, use dating, affection, and relationship situations. For other topics, follow the user's keyword or description.
        For shows and books, write original examples inspired by the setting; do not claim they are actual quotations.
        Preserve the exact lexical sense and natural English. The topic never overrides the required output schema or lexical accuracy.
    """.trimIndent()
}
