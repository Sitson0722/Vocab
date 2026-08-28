package com.sitson.vocab.data

import com.sitson.vocab.provider.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class VocabBackup(
    val words: List<WordSenseEntity>,
    val progress: List<ProgressEntity>,
    val materials: List<MaterialEntity>,
    val usages: List<MaterialUsageEntity>,
    val attempts: List<AttemptEntity>,
    val provider: ProviderConfig,
)

class BackupManager(private val dao: VocabDao) {
    suspend fun export(provider: ProviderConfig): String = encode(
        VocabBackup(dao.words(), dao.allProgress(), dao.allMaterials(), dao.allUsages(), dao.allAttempts(), provider),
    )

    suspend fun restore(json: String): VocabBackup {
        val backup = decode(json)
        validate(backup)
        dao.restoreAll(backup.words, backup.progress, backup.materials, backup.usages, backup.attempts)
        return backup
    }

    private fun validate(b: VocabBackup) {
        require(b.words.map { it.id }.toSet().size == b.words.size) { "Backup contains duplicate word IDs." }
        require(b.materials.map { it.id }.toSet().size == b.materials.size) { "Backup contains duplicate material IDs." }
        require(b.usages.map { it.id }.toSet().size == b.usages.size) { "Backup contains duplicate usage IDs." }
        require(b.attempts.map { it.id }.toSet().size == b.attempts.size) { "Backup contains duplicate attempt IDs." }
        val wordIds = b.words.mapTo(hashSetOf()) { it.id }
        val materialIds = b.materials.mapTo(hashSetOf()) { it.id }
        require(b.progress.all { it.wordId in wordIds }) { "Progress refers to a missing word." }
        require(b.materials.all { it.wordId in wordIds }) { "Material refers to a missing word." }
        require(b.attempts.all { it.wordId in wordIds }) { "Attempt refers to a missing word." }
        require(b.usages.all { it.materialId in materialIds }) { "Usage history refers to missing material." }
        require(b.progress.all { it.dimension in DIMENSIONS }) { "Backup contains an unknown learning dimension." }
        require(b.words.all { it.status in setOf("LEARNING", "MASTERED") }) { "Backup contains an unknown word status." }
        require(b.progress.map { it.wordId to it.dimension }.toSet().size == b.progress.size) { "Backup contains duplicate progress records." }
        require(b.materials.map { it.fingerprint }.toSet().size == b.materials.size) { "Backup contains duplicate material fingerprints." }
        require(b.progress.all { it.stabilityDays.isFinite() && it.difficulty.isFinite() && it.mastery.isFinite() }) { "Backup contains invalid learning values." }
        val providerUri = runCatching { URI(b.provider.baseUrl) }.getOrNull()
        require(providerUri?.scheme == "https" && !providerUri.host.isNullOrBlank() && b.provider.model.isNotBlank()) { "Backup contains invalid provider settings." }
    }

    private fun encode(b: VocabBackup) = JSONObject().apply {
        put("format", "vocab-full-backup"); put("schemaVersion", 1); put("exportedAt", System.currentTimeMillis())
        put("provider", JSONObject().apply { put("baseUrl", b.provider.baseUrl); put("model", b.provider.model); put("apiKey", b.provider.apiKey) })
        put("words", array(b.words) { JSONObject().apply { put("id", it.id); put("term", it.term); put("phonetic", it.phonetic); put("definition", it.definition); put("phrase", it.phrase); put("example", it.example); put("createdAt", it.createdAt); put("status", it.status); put("masteredAt", it.masteredAt ?: JSONObject.NULL) } })
        put("progress", array(b.progress) { JSONObject().apply { put("wordId", it.wordId); put("dimension", it.dimension); put("stabilityDays", it.stabilityDays); put("difficulty", it.difficulty); put("consecutiveSuccesses", it.consecutiveSuccesses); put("lapses", it.lapses); put("dueAt", it.dueAt); put("lastReviewedAt", it.lastReviewedAt); put("attempts", it.attempts); put("mastery", it.mastery); put("distinctMaterials", it.distinctMaterials) } })
        put("materials", array(b.materials) { JSONObject().apply { put("id", it.id); put("wordId", it.wordId); put("type", it.type); put("content", it.content); put("explanation", it.explanation); put("styleTags", it.styleTags); put("fingerprint", it.fingerprint); put("source", it.source); put("createdAt", it.createdAt) } })
        put("usages", array(b.usages) { JSONObject().apply { put("id", it.id); put("materialId", it.materialId); put("dimension", it.dimension); put("shownAt", it.shownAt) } })
        put("attempts", array(b.attempts) { JSONObject().apply { put("id", it.id); put("wordId", it.wordId); put("dimension", it.dimension); put("correct", it.correct); put("hintsUsed", it.hintsUsed); put("responseMillis", it.responseMillis); put("answer", it.answer); put("createdAt", it.createdAt) } })
    }.toString(2)

    private fun decode(text: String): VocabBackup {
        val root = JSONObject(text)
        require(root.getString("format") == "vocab-full-backup") { "This is not a Vocab backup file." }
        require(root.getInt("schemaVersion") == 1) { "Unsupported backup version." }
        val p = root.getJSONObject("provider")
        return VocabBackup(
            words = root.getJSONArray("words").mapObjects { WordSenseEntity(it.getLong("id"), it.getString("term"), it.getString("phonetic"), it.getString("definition"), it.getString("phrase"), it.getString("example"), it.getLong("createdAt"), it.getString("status"), it.optLongOrNull("masteredAt")) },
            progress = root.getJSONArray("progress").mapObjects { ProgressEntity(it.getLong("wordId"), it.getString("dimension"), it.getDouble("stabilityDays"), it.getDouble("difficulty"), it.getInt("consecutiveSuccesses"), it.getInt("lapses"), it.getLong("dueAt"), it.getLong("lastReviewedAt"), it.getInt("attempts"), it.getDouble("mastery"), it.getInt("distinctMaterials")) },
            materials = root.getJSONArray("materials").mapObjects { MaterialEntity(it.getLong("id"), it.getLong("wordId"), it.getString("type"), it.getString("content"), it.getString("explanation"), it.getString("styleTags"), it.getString("fingerprint"), it.getString("source"), it.getLong("createdAt")) },
            usages = root.getJSONArray("usages").mapObjects { MaterialUsageEntity(it.getLong("id"), it.getLong("materialId"), it.getString("dimension"), it.getLong("shownAt")) },
            attempts = root.getJSONArray("attempts").mapObjects { AttemptEntity(it.getLong("id"), it.getLong("wordId"), it.getString("dimension"), it.getBoolean("correct"), it.getInt("hintsUsed"), it.getLong("responseMillis"), it.getString("answer"), it.getLong("createdAt")) },
            provider = ProviderConfig(p.getString("baseUrl"), p.getString("model"), p.getString("apiKey")),
        )
    }

    private fun <T> array(items: List<T>, block: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(block(it)) } }
    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T) = (0 until length()).map { block(getJSONObject(it)) }
    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private companion object { val DIMENSIONS = setOf("CONTEXT_COMPREHENSION", "ISOLATED_MEANING", "PRODUCTION") }
}
