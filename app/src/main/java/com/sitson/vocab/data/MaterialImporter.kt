package com.sitson.vocab.data

import org.json.JSONArray

data class GeneratedMaterial(
    val term: String, val definition: String, val type: String, val content: String,
    val explanation: String, val style: String,
)

object MaterialImporter {
    fun parse(json: String): List<GeneratedMaterial> {
        val clean = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JSONArray(clean)
        return (0 until array.length()).map { i ->
            val value = array.getJSONObject(i)
            GeneratedMaterial(
                value.getString("term"), value.getString("definition"), value.getString("type").uppercase(),
                value.getString("content"), value.getString("explanation"), value.getString("style"),
            ).also { require(it.type in VocabRepository.MATERIAL_TYPES && it.content.isNotBlank()) { "Invalid material ${i + 1}" } }
        }
    }

    fun prompt(words: List<WordSenseEntity>, style: String): String = """
        Generate fresh English learning materials for every listed lexical sense. Style: ${style.ifBlank { "general" }}.
        Return ONLY a JSON array. Each object has exactly: term, definition, type, content, explanation, style.
        type is SENTENCE, PHRASE, COLLOCATION, or PROPER_NOUN. Generate several substantially different
        contexts per sense. SENTENCE/PHRASE/COLLOCATION content must contain the exact target term.
        For PHRASE and COLLOCATION prefer high-frequency 2-4 word expressions. explanation must be
        a concise Simplified Chinese translation or explanation of content.
        PROPER_NOUN may instead use a highly diagnostic named entity (for example FBI for federal), but
        its explanation must make the target relation unambiguous. Avoid mere paraphrases and ambiguous answers.

        SENSES:
        ${words.joinToString("\n") { "${it.term} | ${it.definition} | ${it.phrase}" }}
    """.trimIndent()
}
