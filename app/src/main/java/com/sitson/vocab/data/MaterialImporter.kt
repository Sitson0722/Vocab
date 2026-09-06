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
        Return ONLY a JSON array. Each object has exactly: senseId, term, definition, type, content, explanation, style.
        Copy the exact stable senseId, term and definition from SENSES. Generate at most 2 materials per sense.
        type is SENTENCE, PHRASE, or COLLOCATION. Generate substantially different contexts per sense. SENTENCE/PHRASE/COLLOCATION content must contain the exact target term.
        For PHRASE and COLLOCATION prefer high-frequency 2-4 word expressions. explanation must be
        a concise Simplified Chinese translation or explanation of content.
        Do not generate diagnostic proper nouns or unrelated facts. Avoid mere paraphrases and ambiguous answers.

        SENSES:
        ${words.joinToString("\n") { "${it.id} | ${it.term} | ${it.definition} | ${it.phrase}" }}
    """.trimIndent()
}
