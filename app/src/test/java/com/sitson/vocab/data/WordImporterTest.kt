package com.sitson.vocab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WordImporterTest {
    @Test fun `fixed format imports separate senses and ignores comments`() {
        val result = WordImporter.fixedFormat(
            """
            # word | meaning | phrase | example
            charge | 收费 | charge for service | They charge for delivery.
            charge | 指控 | charge with a crime | Police charged him with theft.
            """.trimIndent(),
        )
        assertEquals(2, result.size)
        assertEquals("指控", result[1].definition)
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
            [{"term":"subtle","definition":"细微的","phrase":"subtle change","example":"A subtle change occurred."}]
            ```""",
        )
        assertEquals("subtle change", result.single().phrase)
    }
}
