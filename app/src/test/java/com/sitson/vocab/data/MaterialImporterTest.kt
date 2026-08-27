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

    @Test fun `prompt carries user style and proper noun rule`() {
        val prompt = MaterialImporter.prompt(listOf(WordSenseEntity(term = "federal", definition = "联邦的", phrase = "federal law", example = "Federal law applies.")), "science")
        assertTrue(prompt.contains("Style: science"))
        assertTrue(prompt.contains("PROPER_NOUN"))
    }
}
