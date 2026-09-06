package com.sitson.vocab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialImporterTest {
    @Test fun `parses all supported material types and style`() {
        val parsed = MaterialImporter.parse(
            """[
              {"term":"federal","definition":"联邦的","type":"proper_noun","content":"FBI","explanation":"The F stands for Federal.","style":"crime"},
              {"term":"subtle","definition":"细微的","type":"sentence","content":"A subtle signal altered the experiment.","explanation":"The signal was hard to notice.","style":"science"}
            ]""",
        )
        assertEquals("PROPER_NOUN", parsed.first().type)
        assertEquals("science", parsed.last().style)
    }

    @Test fun `rejects unsupported material type`() {
        assertThrows(IllegalArgumentException::class.java) {
            MaterialImporter.parse("""[{"term":"x","definition":"x","type":"IMAGE","content":"x","explanation":"x","style":"x"}]""")
        }
    }

    @Test fun `prompt carries user style and stable sense identity`() {
        val prompt = MaterialImporter.prompt(listOf(WordSenseEntity(id = 42, term = "federal", definition = "联邦的", phrase = "federal law", example = "Federal law applies.")), "science")
        assertTrue(prompt.contains("Style: science"))
        assertTrue(prompt.contains("senseId"))
        assertTrue(prompt.contains("42 | federal"))
    }
    @Test fun `custom topics apply to material generation and imported examples`() {
        val word = WordSenseEntity(id = 7, term = "subtle", definition = "细微的", phrase = "a subtle hint", example = "She gave him a subtle hint.")
        val prompt = MaterialImporter.prompt(listOf(word), "TBBT")
        assertTrue(prompt.contains("Style: TBBT"))
        assertTrue(prompt.contains("The Big Bang Theory"))
        assertTrue(prompt.contains("7 | subtle | 细微的"))
        assertTrue(WordImporter.aiPrompt("subtle", "romance").contains("Requested learning topic (content preference only): \"romance\""))
    }
}
