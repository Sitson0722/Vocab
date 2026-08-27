package com.sitson.vocab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WordImporterTest {
    @Test fun `fixed format imports separate senses and ignores comments`() {
        val result = WordImporter.fixedFormat(
            """
            # word | IPA | meaning | phrase | example
            charge | /tʃɑːrdʒ/ | 收费 | charge for service | They charge for delivery.
            charge | /tʃɑːrdʒ/ | 指控 | charge with a crime | Police charged him with theft.
            """.trimIndent(),
        )
        assertEquals(2, result.size)
        assertEquals("指控", result[1].definition)
        assertEquals("/tʃɑːrdʒ/", result[1].phonetic)
    }

    @Test fun `fixed format reports malformed line number`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WordImporter.fixedFormat("subtle | only two fields")
        }
        assertEquals(true, error.message!!.contains("Line 1"))
    }

    @Test fun `AI JSON accepts fenced arrays`() {
        val result = WordImporter.aiJson(
            """```json
            [{"term":"subtle","phonetic":"/ˈsʌtəl/","definition":"细微的","phrase":"subtle change","example":"A subtle change occurred."}]
            ```""",
        )
        assertEquals("subtle change", result.single().phrase)
    }
}
