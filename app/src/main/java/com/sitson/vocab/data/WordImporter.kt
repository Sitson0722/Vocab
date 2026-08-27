package com.sitson.vocab.data

import org.json.JSONArray

data class ImportedWord(val term: String, val phonetic: String, val definition: String, val phrase: String, val example: String)

object WordImporter {
    const val FORMAT_HELP = "word | phonetic | definition | phrase | example sentence"

    fun fixedFormat(text: String): List<ImportedWord> = text.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapIndexed { index, line ->
            val fields = line.split('|').map(String::trim)
            require(fields.size == 5 && fields.all(String::isNotBlank)) { "Line ${index + 1} must be: $FORMAT_HELP" }
            ImportedWord(fields[0], fields[1], fields[2], fields[3], fields[4])
        }.toList()

    fun aiJson(json: String): List<ImportedWord> {
        val clean = json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JSONArray(clean)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ImportedWord(item.getString("term"), item.getString("phonetic"), item.getString("definition"), item.getString("phrase"), item.getString("example"))
                .also { word -> require(listOf(word.term, word.phonetic, word.definition, word.phrase, word.example).all { it.isNotBlank() }) { "AI item ${index + 1} has an empty field." } }
        }
    }

    fun aiPrompt(source: String, style: String = "general") = """
        Extract useful English vocabulary from the source. Split distinct meanings into separate items.
        Return ONLY a JSON array. Each object must contain exactly: term, phonetic, definition, phrase, example.
        Phonetic must be an IPA transcription enclosed in slashes, for example /ˈsʌtəl/.
        Definition may use the source language. Phrase must be a natural collocation. Example must disambiguate the meaning.
        Avoid duplicates and inflected duplicates.
        Use a ${style.ifBlank { "general" }} style for the phrase and example.

        SOURCE:
        $source
    """.trimIndent()
}
