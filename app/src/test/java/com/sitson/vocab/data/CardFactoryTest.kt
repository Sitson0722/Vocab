package com.sitson.vocab.data

import com.sitson.vocab.domain.*
import org.junit.Assert.*
import org.junit.Test

class CardFactoryTest {
    private val word = WordSenseEntity(1, "charge", definition = "收费 charge", phrase = "charge extra", example = "They charge extra.")
    private fun material(content: String) = MaterialEntity(1, 1, "SENTENCE", content, "收取额外费用。", "general", "test", "IMPORT")
    @Test fun `production front hides the target even inside the Chinese definition`() {
        val card = CardFactory.create(word, material("They charge extra."), MasteryDimension.PRODUCTION, false, true, false, 100, 0, "test")!!
        assertFalse(CardFactory.termRegex(word.term).containsMatchIn(card.prompt))
        assertTrue(card.prompt.contains("____"))
    }
    @Test fun `substring matches do not create unsolvable inflection blanks`() {
        assertNull(CardFactory.create(word, material("They charged extra."), MasteryDimension.PRODUCTION, false, true, false, 100, 0, "test"))
    }
    @Test fun `pattern completion has a different evidence scope from form recall`() {
        val seed = SeedContent.senses.first { it.word.term == "depend" }; val context = seed.contexts.first()
        val w = WordSenseEntity(1, "depend", definition = seed.word.definition, phrase = seed.word.phrase, example = seed.word.example)
        val card = CardFactory.create(w, material(context.sentence).copy(exerciseJson = SeedContent.exercise(context)), MasteryDimension.PRODUCTION, false, true, false, 100, 0, "test")!!
        assertEquals(EvidenceScope.PATTERN_COMPLETION, card.scope)
        assertEquals("on", card.answer)
    }
}
