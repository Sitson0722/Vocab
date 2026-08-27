package com.sitson.vocab.data

import org.json.JSONArray

data class ImportedWord(val term: String, val definition: String, val phrase: String, val example: String)

object WordImporter {
    const val FORMAT_HELP = "word | definition | phrase | example sentence"

    fun fixedFormat(text: String): List<ImportedWord> = text.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapIndexed { index, line ->
            val fields = line.split('|').map(String::trim)
            require(fields.size == 4 && fields.all(String::isNotBlank)) { "Line ${index + 1} must be: $FORMAT_HELP" }
            ImportedWord(fields[0], fields[1], fields[2], fields[3])
        }.toList()

    fun aiJson(json: String): List<ImportedWord> {
        val clean = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JSONArray(clean)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ImportedWord(item.getString("term"), item.getString("definition"), item.getString("phrase"), item.getString("example"))
                .also { word -> require(listOf(word.term, word.definition, word.phrase, word.example).all { it.isNotBlank() }) { "AI item ${index + 1} has an empty field." } }
        }
    }

    fun aiPrompt(source: String) = """
        Extract useful English vocabulary from the source. Split distinct meanings into separate items.
        Return ONLY a JSON array. Each object must contain exactly: term, definition, phrase, example.
        Definition may use the source language. Phrase must be a natural collocation. Example must disambiguate the meaning.
        Avoid duplicates and inflected duplicates.

        SOURCE:
        $source
    """.trimIndent()
}
