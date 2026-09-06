package com.sitson.vocab.data

import androidx.room.withTransaction
import com.sitson.vocab.data.RuntimeCodec.objects
import com.sitson.vocab.domain.*
import com.sitson.vocab.provider.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class VocabBackup(
    val words: List<WordSenseEntity>, val progress: List<ProgressEntity>, val materials: List<MaterialEntity>,
    val usages: List<MaterialUsageEntity>, val attempts: List<AttemptEntity>, val provider: ProviderConfig,
    val exposures: List<ExposureEntity> = emptyList(), val runtime: RuntimeEntity? = null,
)

class BackupManager(private val db: VocabDatabase) {
    private val dao = db.dao()
    suspend fun export(provider: ProviderConfig): String = db.withTransaction {
        encode(VocabBackup(dao.words(), dao.allProgress(), dao.allMaterials(), dao.allUsages(), dao.allAttempts(), provider, dao.allExposures(), dao.runtime()))
    }
    suspend fun restore(json: String): VocabBackup {
        val backup = decode(json)
        validate(backup)
        dao.restoreAll(backup.words, backup.progress, backup.materials, backup.usages, backup.attempts, backup.exposures,
            backup.runtime ?: RuntimeEntity(json = RuntimeCodec.encode(LearningRuntime(initialized = true))))
        return backup
    }

    companion object {
        fun validate(b: VocabBackup) {
            fun <T> unique(values: List<T>) = values.distinct().size == values.size
            require(unique(b.words.map { it.id }) && b.words.all { it.id > 0 }) { "备份包含重复或无效的义项 ID。" }
            require(unique(b.materials.map { it.id }) && unique(b.usages.map { it.id }) && unique(b.attempts.map { it.id })) { "备份记录 ID 重复。" }
            require(unique(b.attempts.mapNotNull { it.attemptKey }) && unique(b.exposures.map { it.eventKey })) { "备份事件重复。" }
            val ids = b.words.map { it.id }.toSet(); val mats = b.materials.associateBy { it.id }
            require(b.progress.all { it.wordId in ids } && b.materials.all { it.wordId in ids } && b.attempts.all { it.wordId in ids } && b.exposures.all { it.wordId in ids }) { "备份引用了不存在的义项。" }
            require(b.usages.all { it.materialId in mats } && b.attempts.all { it.materialId == null || mats[it.materialId]?.wordId == it.wordId }) { "材料与义项关联无效。" }
            require(unique(b.progress.map { it.wordId to it.dimension }) && unique(b.materials.map { it.fingerprint })) { "备份包含重复进度或材料。" }
            require(b.progress.all { it.dimension in setOf("CONTEXT_COMPREHENSION", "ISOLATED_MEANING", "PRODUCTION") }) { "未知学习维度。" }
            require(b.progress.all { it.stabilityDays.isFinite() && it.stabilityDays > 0 && it.difficulty.isFinite() && it.mastery.isFinite() && it.attempts >= 0 &&
                it.engineVersion in setOf("LEGACY", ReviewScheduler.VERSION) && (it.engineVersion == "LEGACY" || it.difficulty in 1.0..10.0) }) { "无效的记忆状态。" }
            require(b.words.all { it.status in setOf("LEARNING", "MASTERED") }) { "未知历史状态。" }
            val uri = runCatching { URI(b.provider.baseUrl) }.getOrNull()
            require(uri?.scheme == "https" && !uri.host.isNullOrBlank() && b.provider.model.isNotBlank()) { "服务设置无效。" }
            b.attempts.filter { it.source != "LEGACY_UNKNOWN" }.forEach { a ->
                require(a.attemptKey != null && a.dimension in VocabRepository.ACTIVE_DIMENSIONS)
                EvidenceSource.valueOf(a.source); Outcome.valueOf(a.outcome); EvidenceScope.valueOf(a.scope); ExerciseKind.valueOf(a.kind)
                require(a.hintsUsed >= 0 && a.responseMillis >= 0 && a.presentedAt >= 0)
                if (a.priorProgress.isNotBlank()) require(RuntimeCodec.progress(JSONObject(a.priorProgress)).let { it.wordId == a.wordId && it.dimension == a.dimension })
                if (a.priorRuntime.isNotBlank()) RuntimeCodec.decode(a.priorRuntime)
            }
            b.runtime?.let { saved ->
                val r = RuntimeCodec.decode(saved.json)
                r.session?.card?.let { require(it.wordId in ids && mats[it.materialId]?.wordId == it.wordId) { "会话引用了不存在的材料。" } }
                require(r.session?.attempts.orEmpty().all { key -> b.attempts.any { it.attemptKey == key } }) { "会话缺少作答记录。" }
            }
        }
        fun encode(b: VocabBackup): String = JSONObject().apply {
            put("format", "vocab-full-backup"); put("schemaVersion", 2); put("exportedAt", System.currentTimeMillis())
            put("provider", JSONObject().put("baseUrl", b.provider.baseUrl).put("model", b.provider.model).put("apiKey", b.provider.apiKey))
            put("words", array(b.words) { JSONObject().apply { put("id", it.id); put("term", it.term); put("phonetic", it.phonetic); put("definition", it.definition); put("phrase", it.phrase); put("example", it.example); put("createdAt", it.createdAt); put("status", it.status); put("masteredAt", it.masteredAt ?: JSONObject.NULL) } })
            put("progress", array(b.progress, RuntimeCodec::progressJson))
            put("materials", array(b.materials) { JSONObject().apply { put("id", it.id); put("wordId", it.wordId); put("type", it.type); put("content", it.content); put("explanation", it.explanation); put("styleTags", it.styleTags); put("fingerprint", it.fingerprint); put("source", it.source); put("createdAt", it.createdAt); put("family", it.family); put("quality", it.quality); put("exerciseJson", it.exerciseJson) } })
            put("usages", array(b.usages) { JSONObject().put("id", it.id).put("materialId", it.materialId).put("dimension", it.dimension).put("shownAt", it.shownAt) })
            put("attempts", array(b.attempts) { a -> JSONObject().apply {
                put("id", a.id); put("wordId", a.wordId); put("dimension", a.dimension); put("correct", a.correct); put("hintsUsed", a.hintsUsed); put("responseMillis", a.responseMillis); put("answer", a.answer); put("createdAt", a.createdAt)
                put("attemptKey", a.attemptKey ?: JSONObject.NULL); put("materialId", a.materialId ?: JSONObject.NULL); put("source", a.source); put("outcome", a.outcome); put("family", a.family); put("scope", a.scope); put("kind", a.kind)
                put("answerExposed", a.answerExposed); put("uncertain", a.uncertain); put("guided", a.guided); put("corePattern", a.corePattern); put("valid", a.valid); put("priorExposureAt", a.priorExposureAt); put("presentedAt", a.presentedAt)
                put("day", a.day); put("timeZone", a.timeZone); put("sessionId", a.sessionId); put("priorProgress", a.priorProgress); put("priorRuntime", a.priorRuntime)
            } })
            put("exposures", array(b.exposures) { JSONObject().put("eventKey", it.eventKey).put("wordId", it.wordId).put("at", it.at).put("kind", it.kind) })
            put("runtime", b.runtime?.json ?: JSONObject.NULL)
        }.toString(2)

        fun decode(text: String): VocabBackup {
            val root = JSONObject(text)
            require(root.getString("format") == "vocab-full-backup" && root.getInt("schemaVersion") in 1..2) { "不是受支持的 Vocab 备份。" }
            val p = root.getJSONObject("provider")
            return VocabBackup(
                words = root.getJSONArray("words").objects { WordSenseEntity(it.getLong("id"), it.getString("term"), it.getString("phonetic"), it.getString("definition"), it.getString("phrase"), it.getString("example"), it.getLong("createdAt"), it.getString("status"), it.nullLong("masteredAt")) },
                progress = root.getJSONArray("progress").objects { RuntimeCodec.progress(it) },
                materials = root.getJSONArray("materials").objects { MaterialEntity(it.getLong("id"), it.getLong("wordId"), it.getString("type"), it.getString("content"), it.getString("explanation"), it.getString("styleTags"), it.getString("fingerprint"), it.getString("source"), it.getLong("createdAt"), it.optString("family", ""), it.optString("quality", "ACTIVE"), it.optString("exerciseJson", "")) },
                usages = root.getJSONArray("usages").objects { MaterialUsageEntity(it.getLong("id"), it.getLong("materialId"), it.getString("dimension"), it.getLong("shownAt")) },
                attempts = root.getJSONArray("attempts").objects { j -> AttemptEntity(j.getLong("id"), j.getLong("wordId"), j.getString("dimension"), j.getBoolean("correct"), j.getInt("hintsUsed"), j.getLong("responseMillis"), j.getString("answer"), j.getLong("createdAt"),
                    if (j.isNull("attemptKey")) null else j.getString("attemptKey"), j.nullLong("materialId"), j.optString("source", "LEGACY_UNKNOWN"), j.optString("outcome", ""), j.optString("family", ""), j.optString("scope", ""), j.optString("kind", ""),
                    j.optBoolean("answerExposed"), j.optBoolean("uncertain"), j.optBoolean("guided"), j.optBoolean("corePattern"), j.optBoolean("valid", true), j.optLong("priorExposureAt"), j.optLong("presentedAt"), j.optString("day", ""), j.optString("timeZone", ""), j.optString("sessionId", ""), j.optString("priorProgress", ""), j.optString("priorRuntime", "")) },
                provider = ProviderConfig(p.getString("baseUrl"), p.getString("model"), p.getString("apiKey")),
                exposures = root.optJSONArray("exposures")?.objects { ExposureEntity(it.getString("eventKey"), it.getLong("wordId"), it.getLong("at"), it.getString("kind")) }.orEmpty(),
                runtime = if (root.isNull("runtime")) null else RuntimeEntity(json = root.getString("runtime")),
            )
        }
        private fun <T> array(items: List<T>, block: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(block(it)) } }
        private fun JSONObject.nullLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    }
}
