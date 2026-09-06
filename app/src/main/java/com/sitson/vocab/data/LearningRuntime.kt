package com.sitson.vocab.data

import com.sitson.vocab.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** A frozen question is persisted before display; a restart never generates a different front/back. */
data class LearningCard(
    val key: String, val wordId: Long, val materialId: Long, val dimension: MasteryDimension,
    val kind: ExerciseKind, val scope: EvidenceScope,
    val term: String, val phonetic: String, val definition: String, val phrase: String,
    val prompt: String, val answer: String, val explanation: String, val family: String,
    val choices: List<String> = emptyList(), val alternatives: List<String> = emptyList(),
    val corePattern: Boolean = false, val guided: Boolean = false,
    val reason: String = "到了复习时间，换个语境再想一下。",
    val presentedAt: Long = 0, val priorExposureAt: Long = 0,
    val phase: String = "QUESTION", val hints: Int = 0, val revealed: Boolean = false,
    val uncertain: Boolean = false, val draft: String = "", val activeMillis: Long = 0,
    val feedback: String = "", val repair: Boolean = false,
)
data class DayWork(
    val day: String, val activeMillis: Long = 0, val newSenses: List<Long> = emptyList(),
    val aiCalls: Int = 0, val repairMillis: Long = 0,
)
data class StudySession(
    val id: String, val startedAt: Long, val day: String, val extraUntilMillis: Long = 0,
    val steps: Int = 0, val card: LearningCard? = null, val attempts: List<String> = emptyList(),
    val finishReason: String = "", val guidedWordId: Long? = null,
    val repairs: List<String> = emptyList(), val retried: List<String> = emptyList(),
)
data class LearningRuntime(
    val dailyMinutes: Int = 10, val autoAi: Boolean = true, val maxDailyCalls: Int = 3,
    val initialized: Boolean = false, val selfGradeStreak: Int = 0,
    val session: StudySession? = null, val days: List<DayWork> = emptyList(),
    val lastUndoKey: String? = null, val pendingWords: List<String> = emptyList(),
) {
    fun day(day: String) = days.firstOrNull { it.day == day } ?: DayWork(day)
    fun withDay(value: DayWork) = copy(days = days.filterNot { it.day == value.day } + value)
}

/** Explicit, versioned serialization also used by backups and transactional undo snapshots. */
object RuntimeCodec {
    fun encode(r: LearningRuntime): String = JSONObject().apply {
        put("version", 1); put("dailyMinutes", r.dailyMinutes); put("autoAi", r.autoAi); put("maxDailyCalls", r.maxDailyCalls)
        put("initialized", r.initialized); put("selfGradeStreak", r.selfGradeStreak)
        put("session", r.session?.let(::sessionJson) ?: JSONObject.NULL)
        put("days", JSONArray(r.days.map { d -> JSONObject().put("day", d.day).put("activeMillis", d.activeMillis).put("newSenses", JSONArray(d.newSenses)).put("aiCalls", d.aiCalls).put("repairMillis", d.repairMillis) }))
        put("lastUndoKey", r.lastUndoKey ?: JSONObject.NULL); put("pendingWords", JSONArray(r.pendingWords))
    }.toString()

    fun decode(text: String): LearningRuntime {
        val j = JSONObject(text)
        require(j.getInt("version") == 1) { "不支持的学习会话版本" }
        return LearningRuntime(j.getInt("dailyMinutes"), j.getBoolean("autoAi"), j.getInt("maxDailyCalls"), j.getBoolean("initialized"), j.getInt("selfGradeStreak"),
            if (j.isNull("session")) null else session(j.getJSONObject("session")),
            j.getJSONArray("days").objects { DayWork(it.getString("day"), it.getLong("activeMillis"), it.getJSONArray("newSenses").longs(), it.getInt("aiCalls"), it.optLong("repairMillis")) },
            if (j.isNull("lastUndoKey")) null else j.getString("lastUndoKey"), j.optJSONArray("pendingWords")?.strings().orEmpty()).also { r ->
                require(r.dailyMinutes in listOf(5, 10, 15) && r.maxDailyCalls in 0..10 && r.selfGradeStreak >= 0)
                require(r.days.map { it.day }.distinct().size == r.days.size && r.days.all { it.activeMillis >= 0 && it.aiCalls >= 0 })
                r.session?.let { s -> require(s.steps in 0..5 && s.attempts.size == s.steps && s.card?.phase in listOf(null, "QUESTION", "REVEALED", "FEEDBACK", "UNJUDGED")) }
            }
    }
    private fun sessionJson(s: StudySession) = JSONObject().apply {
        put("id", s.id); put("startedAt", s.startedAt); put("day", s.day); put("extraUntilMillis", s.extraUntilMillis)
        put("steps", s.steps); put("card", s.card?.let(::cardJson) ?: JSONObject.NULL)
        put("attempts", JSONArray(s.attempts)); put("finishReason", s.finishReason)
        put("guidedWordId", s.guidedWordId ?: JSONObject.NULL); put("repairs", JSONArray(s.repairs)); put("retried", JSONArray(s.retried))
    }
    private fun session(j: JSONObject) = StudySession(j.getString("id"), j.getLong("startedAt"), j.getString("day"), j.getLong("extraUntilMillis"), j.getInt("steps"),
        if (j.isNull("card")) null else card(j.getJSONObject("card")), j.getJSONArray("attempts").strings(), j.getString("finishReason"),
        if (j.isNull("guidedWordId")) null else j.getLong("guidedWordId"), j.getJSONArray("repairs").strings(), j.getJSONArray("retried").strings())
    private fun cardJson(c: LearningCard) = JSONObject().apply {
        put("key", c.key); put("wordId", c.wordId); put("materialId", c.materialId); put("dimension", c.dimension.name); put("kind", c.kind.name); put("scope", c.scope.name)
        put("term", c.term); put("phonetic", c.phonetic); put("definition", c.definition); put("phrase", c.phrase); put("prompt", c.prompt); put("answer", c.answer)
        put("explanation", c.explanation); put("family", c.family); put("choices", JSONArray(c.choices)); put("alternatives", JSONArray(c.alternatives))
        put("corePattern", c.corePattern); put("guided", c.guided); put("reason", c.reason); put("presentedAt", c.presentedAt); put("priorExposureAt", c.priorExposureAt)
        put("phase", c.phase); put("hints", c.hints); put("revealed", c.revealed); put("uncertain", c.uncertain); put("draft", c.draft); put("activeMillis", c.activeMillis); put("feedback", c.feedback); put("repair", c.repair)
    }
    private fun card(j: JSONObject) = LearningCard(j.getString("key"), j.getLong("wordId"), j.getLong("materialId"), MasteryDimension.valueOf(j.getString("dimension")),
        ExerciseKind.valueOf(j.getString("kind")), EvidenceScope.valueOf(j.getString("scope")), j.getString("term"), j.getString("phonetic"), j.getString("definition"), j.getString("phrase"),
        j.getString("prompt"), j.getString("answer"), j.getString("explanation"), j.getString("family"), j.getJSONArray("choices").strings(), j.getJSONArray("alternatives").strings(),
        j.getBoolean("corePattern"), j.getBoolean("guided"), j.getString("reason"), j.getLong("presentedAt"), j.getLong("priorExposureAt"), j.getString("phase"), j.getInt("hints"),
        j.getBoolean("revealed"), j.getBoolean("uncertain"), j.getString("draft"), j.getLong("activeMillis"), j.getString("feedback"), j.optBoolean("repair"))

    fun progressJson(p: ProgressEntity) = JSONObject().apply {
        put("wordId", p.wordId); put("dimension", p.dimension); put("stabilityDays", p.stabilityDays); put("difficulty", p.difficulty)
        put("consecutiveSuccesses", p.consecutiveSuccesses); put("lapses", p.lapses); put("dueAt", p.dueAt); put("lastReviewedAt", p.lastReviewedAt)
        put("attempts", p.attempts); put("mastery", p.mastery); put("distinctMaterials", p.distinctMaterials); put("engineVersion", p.engineVersion)
    }
    fun progress(j: JSONObject) = ProgressEntity(j.getLong("wordId"), j.getString("dimension"), j.getDouble("stabilityDays"), j.getDouble("difficulty"), j.getInt("consecutiveSuccesses"), j.getInt("lapses"),
        j.getLong("dueAt"), j.getLong("lastReviewedAt"), j.getInt("attempts"), j.getDouble("mastery"), j.getInt("distinctMaterials"), j.optString("engineVersion", "LEGACY"))
    fun JSONArray.strings() = (0 until length()).map { getString(it) }
    fun JSONArray.longs() = (0 until length()).map { getLong(it) }
    fun <T> JSONArray.objects(block: (JSONObject) -> T) = (0 until length()).map { block(getJSONObject(it)) }
}
