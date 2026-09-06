package com.sitson.vocab.data

import com.sitson.vocab.provider.ProviderConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupManagerTest {
    private fun backup() = VocabBackup(
        listOf(WordSenseEntity(1, "subtle", definition = "细微的", phrase = "subtle difference", example = "A subtle difference.")),
        listOf(ProgressEntity(1, "CONTEXT_COMPREHENSION"), ProgressEntity(1, "ISOLATED_MEANING", attempts = 2), ProgressEntity(1, "PRODUCTION")),
        emptyList(), emptyList(), listOf(AttemptEntity(1, 1, "ISOLATED_MEANING", true, 0, 3000, "SELF_KNOW", 100)), ProviderConfig(apiKey = "test-only"))
    @Test fun `version one backup remains readable without fabricated evidence`() {
        val json = JSONObject(BackupManager.encode(backup())).put("schemaVersion", 1)
        json.remove("runtime"); json.remove("exposures")
        val attempts = json.getJSONArray("attempts")
        val old = attempts.getJSONObject(0)
        listOf("attemptKey", "materialId", "source", "outcome", "family", "scope", "kind", "answerExposed", "uncertain", "guided", "corePattern", "valid", "priorExposureAt", "presentedAt", "day", "timeZone", "sessionId", "priorProgress", "priorRuntime").forEach { old.remove(it) }
        val progress = json.getJSONArray("progress")
        for (i in 0 until progress.length()) progress.getJSONObject(i).remove("engineVersion")
        val decoded = BackupManager.decode(json.toString())
        BackupManager.validate(decoded)
        assertEquals("LEGACY_UNKNOWN", decoded.attempts.single().source)
        assertTrue(decoded.progress.all { it.engineVersion == "LEGACY" })
        assertEquals("test-only", decoded.provider.apiKey)
    }
    @Test fun `invalid references fail before restore can mutate data`() {
        val invalid = backup().copy(progress = listOf(ProgressEntity(99, "PRODUCTION")))
        assertThrows(IllegalArgumentException::class.java) { BackupManager.validate(invalid) }
    }
    @Test fun `version two preserves runtime preferences`() {
        val b = backup().copy(runtime = RuntimeEntity(json = RuntimeCodec.encode(LearningRuntime(dailyMinutes = 5, autoAi = false))))
        val decoded = BackupManager.decode(BackupManager.encode(b)); BackupManager.validate(decoded)
        assertEquals(5, RuntimeCodec.decode(decoded.runtime!!.json).dailyMinutes)
    }
}
