package com.sitson.vocab.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigValidatorTest {
    @Test fun `accepts an HTTPS OpenAI compatible endpoint`() {
        assertNull(ProviderConfigValidator.validate(ProviderConfig("https://example.com/v1", "model-a", "secret")))
    }

    @Test fun `rejects cleartext endpoints to protect credentials`() {
        val error = ProviderConfigValidator.validate(ProviderConfig("http://example.com/v1", "model-a", "secret"))
        assertTrue(error!!.contains("HTTPS"))
    }

    @Test fun `normalizes chat completions path`() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            ProviderConfigValidator.chatCompletionsUrl("https://example.com/v1/"),
        )
    }
}
