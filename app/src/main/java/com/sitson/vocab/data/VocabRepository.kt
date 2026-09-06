package com.sitson.vocab.data

import androidx.room.withTransaction
import com.sitson.vocab.domain.*
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class VocabRepository(private val db: VocabDatabase, private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault, private val scheduler: ReviewScheduler = ReviewScheduler()) {
    private val dao = db.dao()
    fun today(): String = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate().toString()
    suspend fun runtime() = dao.runtime()?.let { RuntimeCodec.decode(it.json) } ?: LearningRuntime()
    private suspend fun save(r: LearningRuntime) { dao.saveRuntime(RuntimeEntity(json = RuntimeCodec.encode(r))) }
    suspend fun initialize(): LearningRuntime = db.withTransaction {
        var r = runtime()
        if (!r.initialized) {
            if (dao.wordCount() == 0) {
                for (seed in SeedContent.senses) {
                    val w = seed.word
                    val id = dao.addWordWithProgress(WordSenseEntity(term = w.term, phonetic = w.phonetic, definition = w.definition, phrase = w.phrase, example = w.example))
                    for (c in seed.contexts) addMaterial(id, "SENTENCE", c.sentence, c.translation, "general", "BUNDLED", c.family, SeedContent.exercise(c))
                }
            }
            r = r.copy(initialized = true); save(r)
        }
        r
    }
    suspend fun words() = dao.words()
    suspend fun attempts() = dao.allAttempts()
    suspend fun schedules() = dao.allProgress().filter { it.dimension in ACTIVE_DIMENSIONS }
    suspend fun settings(minutes: Int, autoAi: Boolean, maxCalls: Int): LearningRuntime = db.withTransaction {
        require(minutes in listOf(5, 10, 15) && maxCalls in 0..10)
        runtime().copy(dailyMinutes = minutes, autoAi = autoAi, maxDailyCalls = maxCalls).also { save(it) }
    }

    suspend fun import(words: List<ImportedWord>, style: String = "general"): Pair<Int, Int> = db.withTransaction {
        var added = 0
        for (imported in words) {
            val word = WordSenseEntity(term = imported.term.trim().lowercase(java.util.Locale.ROOT), phonetic = imported.phonetic.trim(),
                definition = imported.definition.clean(), phrase = imported.phrase.clean(), example = imported.example.clean())
            val id = dao.addWordWithProgress(word)
            if (id != -1L) {
                added++
                addMaterial(id, "SENTENCE", word.example, word.definition, style, "IMPORT")
                addMaterial(id, "COLLOCATION", word.phrase, word.definition, style, "IMPORT")
            }
        }
        val r = runtime()
        save(r.copy(pendingWords = r.pendingWords.filterNot { pending -> words.any { it.term.equals(pending, true) } }))
        added to (words.size - added)
    }
    suspend fun addPending(text: String): Int = db.withTransaction {
        val incoming = text.split(Regex("[,，;；\\n]+" )).map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.distinct()
        require(incoming.size <= 500 && incoming.all { it.length <= 80 }) { "请每行放一个单词或短语，最多 500 条。" }
        val r = runtime(); val pending = (r.pendingWords + incoming).distinct()
        save(r.copy(pendingWords = pending)); pending.size - r.pendingWords.size
    }

    suspend fun start(extra: Boolean = false): LearningRuntime = db.withTransaction {
        var r = runtime()
        val old = r.session
        if (old?.card != null) return@withTransaction r // resume exact question/back, including committed feedback
        val day = today()
        val budget = r.dailyMinutes * 60_000L
        val spent = r.day(day).activeMillis
        val sameDay = old?.day == day
        val allowance = if (extra) spent + 5 * 60_000L else budget
        r = r.copy(session = StudySession(UUID.randomUUID().toString(), now(), day, allowance,
            guidedWordId = old?.guidedWordId,
            repairs = if (sameDay) old?.repairs.orEmpty() else emptyList(), retried = if (sameDay) old?.retried.orEmpty() else emptyList()))
        r = selectNext(r); save(r); r
    }

    suspend fun next(): LearningRuntime = db.withTransaction {
        val r = runtime(); val s = r.session ?: return@withTransaction r
        if (s.card != null && s.card.phase != "FEEDBACK") return@withTransaction r
        selectNext(r.copy(session = s.copy(card = null))).also { save(it) }
    }

    private suspend fun selectNext(input: LearningRuntime): LearningRuntime {
        var r = input
        var s = r.session ?: return r
        val time = now(); val day = today()
        if (s.day != day) { s = s.copy(day = day, extraUntilMillis = r.dailyMinutes * 60_000L, repairs = emptyList(), retried = emptyList()); r = r.copy(session = s) }
        fun finish(reason: String) = r.copy(session = s.copy(card = null, finishReason = reason))
        if (s.steps >= SessionPolicy.ROUND_SIZE) return finish("ROUND")
        val remaining = s.extraUntilMillis - r.day(day).activeMillis
        if (remaining <= 0) return finish("TIME")
        val words = dao.words(); val byId = words.associateBy { it.id }
        val progress = dao.allProgress().filter { it.dimension in ACTIVE_DIMENSIONS }
        val attempts = dao.allAttempts(); val exposures = dao.allExposures()
        val materials = dao.allMaterials().filter { it.quality == "ACTIVE" && it.type in setOf("SENTENCE", "PHRASE", "COLLOCATION") }
        val usages = dao.allUsages().groupBy { it.materialId }
        val introduced = attempts.filter { it.valid && it.outcome !in listOf("VOID", "SKIP") }.mapTo(hashSetOf()) { it.wordId }
        introduced += progress.filter { it.attempts > 0 }.map { it.wordId }
        fun key(p: ProgressEntity) = "${p.wordId}:${p.dimension}"
        fun lastExposure(word: WordSenseEntity) = maxOf(
            exposures.filter { byId[it.wordId]?.term == word.term }.maxOfOrNull { it.at } ?: 0,
            // Old reviews are a conservative proxy for exposure until reliable new events exist.
            progress.filter { byId[it.wordId]?.term == word.term }.maxOfOrNull { it.lastReviewedAt } ?: 0)
        fun cooled(word: WordSenseEntity): Boolean {
            val last = lastExposure(word)
            val other = attempts.count { it.createdAt > last && byId[it.wordId]?.term != word.term }
            return time - last >= 20 * GraduationPolicy.HOUR || SessionPolicy.canRepeat(time, last, other)
        }
        val skipped = attempts.filter { it.sessionId == s.id && it.outcome in listOf("SKIP", "VOID") }.map { "${it.wordId}:${it.dimension}" }.toSet()
        val due = progress.filter { it.attempts > 0 && it.dueAt <= time }
        val dueMillis = due.sumOf { if (it.dimension == "PRODUCTION") 35_000L else 25_000L }
        // Initial teaching may have ONE guided retrieval immediately. It is explicitly weak evidence,
        // doesn't update FSRS, and avoids a cold-start deadlock when only one new word is available.
        val guided = s.guidedWordId?.let { id -> progress.firstOrNull { it.wordId == id && it.dimension == "CONTEXT_COMPREHENSION" } }
        val retry = progress.filter { key(it) in s.repairs && s.retried.none { k -> k.substringBefore(':') == it.wordId.toString() } }
        val initial = progress.filter { p -> p.wordId in introduced && p.attempts == 0 &&
            (p.dimension == "CONTEXT_COMPREHENSION" || attempts.any { it.wordId == p.wordId && it.dimension == "CONTEXT_COMPREHENSION" && it.valid && it.outcome == "GOOD" }) }
        val dueSorted = due.sortedWith(compareByDescending<ProgressEntity> { SessionPolicy.overdue(time, it.dueAt, it.lastReviewedAt) }.thenBy { it.lastReviewedAt }.thenBy { it.wordId })
        val extraPractice = if (s.extraUntilMillis > r.dailyMinutes * 60_000L) progress.filter { it.attempts > 0 }.sortedBy { it.lastReviewedAt } else emptyList()
        val reviewCandidates = (listOfNotNull(guided) + retry + dueSorted + initial + extraPractice).distinctBy { key(it) }
        val newAllowed = SessionPolicy.canIntroduce(remaining, dueMillis, r.day(day).newSenses.size, adjustedNewMinutes(r))
        val fresh = if (newAllowed) progress.filter { it.wordId !in introduced && it.dimension == "CONTEXT_COMPREHENSION" } else emptyList()
        for (p in reviewCandidates + fresh) {
            val word = byId[p.wordId] ?: continue
            val isGuided = guided?.wordId == p.wordId && p.dimension == "CONTEXT_COMPREHENSION"
            if (key(p) in skipped || (!isGuided && !cooled(word))) continue
            val teaching = p.wordId !in introduced
            val isRepair = key(p) in s.repairs
            // A retried lapse waits for its real next due date; restarting a short round cannot farm retries.
            if (isRepair && (s.retried.any { it.substringBefore(':') == p.wordId.toString() } || r.day(day).repairMillis >= r.dailyMinutes * 12_000L)) continue
            val observed = r.selfGradeStreak >= 4 || (p.dimension == "PRODUCTION" && p.attempts == 0) ||
                attempts.count { it.wordId == p.wordId && it.dimension == p.dimension && it.source == "OBSERVED" && it.valid } < 3
            val dimEvidence = attempts.filter { it.wordId == word.id && it.dimension == p.dimension }.mapNotNull(::evidence)
            val needsDiversity = !GraduationPolicy.verified(MasteryDimension.valueOf(p.dimension), dimEvidence)
            val provenFamilies = dimEvidence.filter { GraduationPolicy.qualifies(it) }.map { it.family }.toSet()
            val pool = materials.filter { it.wordId == word.id }.sortedWith(compareBy<MaterialEntity> {
                when { !needsDiversity -> 0; it.family.isBlank() -> 2; it.family !in provenFamilies -> 0; else -> 1 }
            }.thenBy { usages[it.id].orEmpty().count { use -> use.dimension == p.dimension } }
                .thenBy { usages[it.id].orEmpty().filter { use -> use.dimension == p.dimension }.maxOfOrNull { use -> use.shownAt } ?: 0 }
                .thenBy { usages[it.id].orEmpty().size }.thenBy { it.id })
            for (m in pool) {
                val card = CardFactory.create(word, m, MasteryDimension.valueOf(p.dimension), teaching, observed && !isGuided, isGuided,
                    time, lastExposure(word), when {
                        teaching -> "先认识这个义项和一个核心搭配。"
                        isGuided -> "刚学过，轻轻回忆一次。这次只是引导练习。"
                        isRepair -> "隔开了一些内容，再试一次刚才没想起的表达。"
                        p.attempts == 0 -> "这项能力还没单独检查过，试着回忆一下。"
                        observed -> "换一个任务，检查是否真的想起来了。"
                        else -> "这个义项到了复习时间。"
                    })?.copy(repair = isRepair) ?: continue
                if (SessionPolicy.estimate(card.kind) > remaining && s.steps > 0) continue
                dao.insertUsage(MaterialUsageEntity(materialId = m.id, dimension = p.dimension, shownAt = time))
                // Conservative: a front is an exposure even if it only contains a meaning cue.
                dao.insertExposure(ExposureEntity("${card.key}:front", word.id, time, "FRONT"))
                return r.copy(session = s.copy(card = card, finishReason = "", retried = if (isRepair) (s.retried + key(p)).distinct() else s.retried))
            }
        }
        return finish("EMPTY")
    }

    private fun adjustedNewMinutes(r: LearningRuntime): Int {
        val recent = r.days.filter { it.day != today() }.sortedByDescending { it.day }.take(3)
        return if (recent.size == 3 && recent.all { it.activeMillis > r.dailyMinutes * 72_000L }) when (r.dailyMinutes) { 15 -> 10; else -> 5 } else r.dailyMinutes
    }

    /** Active time comes from a foreground monotonic ticker, never wall time since app launch. */
    suspend fun checkpoint(key: String, activeMillis: Long, draft: String? = null): LearningRuntime = db.withTransaction {
        val r = runtime(); val s = r.session ?: return@withTransaction r
        val c = s.card ?: return@withTransaction r
        if (c.key != key) return@withTransaction r
        val delta = activeMillis.coerceIn(0, 60_000)
        val d = r.day(today())
        r.withDay(d.copy(activeMillis = d.activeMillis + delta, repairMillis = d.repairMillis + if (c.repair) delta else 0L)).copy(session = s.copy(card = c.copy(activeMillis = c.activeMillis + delta, draft = draft ?: c.draft))).also { save(it) }
    }
    suspend fun reveal(key: String): LearningRuntime = changeCard(key) { c ->
        if (c.phase != "QUESTION") c else c.copy(revealed = true, phase = "REVEALED")
    }
    suspend fun hint(key: String): LearningRuntime = changeCard(key) { c -> if (c.phase == "QUESTION") c.copy(hints = 1) else c }
    suspend fun uncertain(key: String): LearningRuntime = changeCard(key) { c -> if (c.phase == "QUESTION") c.copy(uncertain = !c.uncertain) else c }
    private suspend fun changeCard(key: String, transform: (LearningCard) -> LearningCard): LearningRuntime = db.withTransaction {
        val r = runtime(); val s = r.session ?: return@withTransaction r
        val c = s.card ?: return@withTransaction r
        if (c.key != key) return@withTransaction r
        val next = transform(c)
        if ((!c.revealed && next.revealed) || next.hints > c.hints) dao.insertExposure(ExposureEntity("$key:help", c.wordId, now(), "HELP"))
        r.copy(session = s.copy(card = next)).also { save(it) }
    }

    suspend fun submit(key: String, answer: String): LearningRuntime = db.withTransaction {
        val r = runtime(); val c = r.session?.card ?: return@withTransaction r
        if (c.key != key || c.phase != "QUESTION") return@withTransaction r
        val normalized = CardFactory.normalize(answer)
        if (normalized.isBlank()) return@withTransaction r
        when {
            normalized == CardFactory.normalize(c.answer) -> gradeInternal(r, GradingPolicy.outcome(true, c.hints > 0, c.uncertain), EvidenceSource.OBSERVED, answer)
            c.alternatives.any { CardFactory.normalize(it) == normalized } -> gradeInternal(r, Outcome.ALTERNATIVE, EvidenceSource.OBSERVED, answer)
            c.kind == ExerciseKind.CHOICE -> gradeInternal(r, Outcome.AGAIN, EvidenceSource.OBSERVED, answer)
            else -> {
                // Exact mismatch cannot establish a semantic error. Let the learner compare, never invent a wrong verdict.
                dao.insertExposure(ExposureEntity("$key:unjudged", c.wordId, now(), "ANSWER"))
                r.copy(session = requireNotNull(r.session).copy(card = c.copy(phase = "UNJUDGED", revealed = true, draft = answer))).also { save(it) }
            }
        }
    }
    suspend fun grade(key: String, outcome: Outcome): LearningRuntime = db.withTransaction {
        val r = runtime(); val c = r.session?.card ?: return@withTransaction r
        if (c.key != key || c.phase == "FEEDBACK") return@withTransaction r
        if (outcome == Outcome.TAUGHT && c.kind != ExerciseKind.TEACH) return@withTransaction r
        if (outcome in listOf(Outcome.GOOD, Outcome.HARD) && c.phase !in listOf("REVEALED", "UNJUDGED")) return@withTransaction r
        val safeOutcome = if (c.hints > 0 && outcome in listOf(Outcome.GOOD, Outcome.HARD)) Outcome.ASSISTED else outcome
        gradeInternal(r, safeOutcome, if (GradingPolicy.rating(safeOutcome) == null) EvidenceSource.NONE else EvidenceSource.SELF_REPORTED, c.draft)
    }

    private suspend fun gradeInternal(r: LearningRuntime, outcome: Outcome, source: EvidenceSource, answer: String): LearningRuntime {
        val s = requireNotNull(r.session); val c = requireNotNull(s.card)
        if (dao.attempt(c.key) != null) return runtime()
        val time = now(); val day = today()
        val previous = dao.progress(c.wordId, c.dimension.name) ?: ProgressEntity(c.wordId, c.dimension.name)
        val a = AttemptEntity(wordId = c.wordId, dimension = c.dimension.name, correct = outcome in listOf(Outcome.GOOD, Outcome.HARD), hintsUsed = c.hints,
            responseMillis = c.activeMillis, answer = answer, createdAt = time, attemptKey = c.key, materialId = c.materialId, source = source.name,
            outcome = outcome.name, family = c.family, scope = c.scope.name, kind = c.kind.name, answerExposed = c.revealed, uncertain = c.uncertain,
            guided = c.guided, corePattern = c.corePattern, priorExposureAt = c.priorExposureAt, presentedAt = c.presentedAt, day = day, timeZone = zone().id, sessionId = s.id,
            priorProgress = RuntimeCodec.progressJson(previous).toString(), priorRuntime = RuntimeCodec.encode(r.copy(lastUndoKey = null)))
        if (dao.insertAttempt(a) == -1L) return runtime()
        applyAttempt(previous, a)?.let { dao.saveProgress(it) }
        if (outcome !in listOf(Outcome.SKIP, Outcome.VOID)) dao.insertExposure(ExposureEntity("${c.key}:answer", c.wordId, time, "ANSWER"))
        val repairKey = "${c.wordId}:${c.dimension.name}"
        val repair = outcome in listOf(Outcome.AGAIN, Outcome.HARD, Outcome.ASSISTED, Outcome.ALTERNATIVE)
        val feedback = when (outcome) {
            Outcome.GOOD -> "这次想起来了。之后隔一段时间，再换个语境检查。"
            Outcome.HARD -> "费劲想起，也有进展。后面再巩固一下。"
            Outcome.AGAIN -> "先看一眼用法就好，隔开后再试。"
            Outcome.ASSISTED -> "这次借助提示完成了，之后再试独立回忆。"
            Outcome.ALTERNATIVE -> "你的表达也可以成立；目标表达还需要另一次检查。"
            Outcome.TAUGHT -> "先认识这个意思。接下来轻轻回忆一次。"
            Outcome.SKIP -> "已跳过，本题不改变记忆进度。"
            Outcome.VOID -> "已标记题目问题，本题不影响记忆进度。"
        }
        val work = r.day(day)
        var next = r.withDay(if (outcome == Outcome.TAUGHT) work.copy(newSenses = (work.newSenses + c.wordId).distinct()) else work)
            .copy(lastUndoKey = if (GradingPolicy.rating(outcome) != null && !c.guided) c.key else null,
                selfGradeStreak = when { source == EvidenceSource.OBSERVED -> 0; source == EvidenceSource.SELF_REPORTED && !c.guided -> r.selfGradeStreak + 1; else -> r.selfGradeStreak },
                session = s.copy(steps = s.steps + 1, attempts = s.attempts + c.key,
                    guidedWordId = if (outcome == Outcome.TAUGHT) c.wordId else if (c.guided) null else s.guidedWordId,
                    repairs = if (repair && repairKey !in s.retried) (s.repairs + repairKey).distinct() else s.repairs.filterNot { it == repairKey },
                    card = c.copy(phase = "FEEDBACK", revealed = true, feedback = feedback)))
        if (outcome == Outcome.VOID) { invalidateMaterial(c.materialId); next = next.copy(lastUndoKey = null) }
        if (outcome in listOf(Outcome.GOOD, Outcome.HARD, Outcome.TAUGHT, Outcome.SKIP) && c.kind in listOf(ExerciseKind.SELF, ExerciseKind.TEACH)) {
            next = selectNext(next.copy(session = next.session?.copy(card = null)))
        }
        save(next); return next
    }

    private fun applyAttempt(previous: ProgressEntity, a: AttemptEntity): ProgressEntity? {
        if (!a.valid || a.guided) return null
        val outcome = runCatching { Outcome.valueOf(a.outcome) }.getOrNull() ?: return null
        val rating = GradingPolicy.rating(outcome) ?: return null
        val decision = scheduler.review(ReviewState(MasteryDimension.valueOf(a.dimension), previous.stabilityDays, previous.difficulty,
            previous.engineVersion == ReviewScheduler.VERSION), rating, (a.createdAt - previous.lastReviewedAt).coerceAtLeast(0) / SessionPolicy.DAY.toDouble())
        return previous.copy(stabilityDays = decision.state.stabilityDays, difficulty = decision.state.difficulty,
            consecutiveSuccesses = if (rating == RecallRating.AGAIN) 0 else previous.consecutiveSuccesses + 1,
            lapses = previous.lapses + if (rating == RecallRating.AGAIN) 1 else 0, attempts = previous.attempts + 1,
            dueAt = a.createdAt + decision.nextIntervalDays * SessionPolicy.DAY, lastReviewedAt = a.createdAt,
            engineVersion = ReviewScheduler.VERSION) // Legacy mastery fields are preserved as history, never read by the new UI.
    }

    suspend fun report(key: String): LearningRuntime = db.withTransaction {
        val r = runtime(); val s = r.session ?: return@withTransaction r
        val c = s.card ?: return@withTransaction r
        if (c.key != key) return@withTransaction r
        if (c.phase != "FEEDBACK") return@withTransaction gradeInternal(r, Outcome.VOID, EvidenceSource.NONE, c.draft)
        invalidateMaterial(c.materialId)
        r.copy(lastUndoKey = null, session = s.copy(card = c.copy(feedback = "已标记问题，并撤销这份材料可追溯的评分影响。"))).also { save(it) }
    }
    private suspend fun invalidateMaterial(id: Long) {
        dao.reportMaterial(id)
        val all = dao.allAttempts()
        val affected = all.filter { it.materialId == id && it.attemptKey != null }
        affected.forEach { dao.invalidateAttempt(requireNotNull(it.attemptKey)) }
        for ((pair, _) in affected.groupBy { it.wordId to it.dimension }) {
            val logs = all.filter { it.wordId == pair.first && it.dimension == pair.second && it.priorProgress.isNotBlank() }
            var state = logs.firstOrNull()?.let { RuntimeCodec.progress(JSONObject(it.priorProgress)) } ?: continue
            for (log in logs) if (log.valid && log.materialId != id) state = applyAttempt(state, log) ?: state
            dao.saveProgress(state)
        }
    }
    suspend fun undo(): LearningRuntime = db.withTransaction {
        val r = runtime(); val a = r.lastUndoKey?.let { dao.attempt(it) } ?: return@withTransaction r
        if (a.priorRuntime.isBlank() || !a.valid) return@withTransaction r
        val prior = RuntimeCodec.decode(a.priorRuntime)
        dao.deleteAttempt(requireNotNull(a.attemptKey)); dao.saveProgress(RuntimeCodec.progress(JSONObject(a.priorProgress)))
        // Keep all exposure events: undo cannot make the learner unsee an answer or the next card.
        val card = prior.session?.card
        val restored = prior.copy(dailyMinutes = r.dailyMinutes, autoAi = r.autoAi, maxDailyCalls = r.maxDailyCalls,
            days = r.days.map { d -> d.copy(newSenses = if (d.day == a.day && a.outcome == "TAUGHT") d.newSenses.filterNot { it == a.wordId } else d.newSenses) },
            lastUndoKey = null, session = prior.session?.copy(card = card?.copy(kind = ExerciseKind.SELF, revealed = true, phase = "REVEALED", feedback = "")))
        save(restored); restored
    }

    fun evidence(a: AttemptEntity): RecallEvidence? {
        if (a.dimension !in ACTIVE_DIMENSIONS || a.source == "LEGACY_UNKNOWN") return null
        return RecallEvidence(MasteryDimension.valueOf(a.dimension), a.day, a.presentedAt, a.priorExposureAt, a.family,
            EvidenceScope.valueOf(a.scope), EvidenceSource.valueOf(a.source), Outcome.valueOf(a.outcome), a.hintsUsed > 0, a.answerExposed,
            a.uncertain, a.valid, a.guided, a.corePattern)
    }
    suspend fun statuses(): Map<Long, LearningStatus> {
        val progress = dao.allProgress().filter { it.dimension in ACTIVE_DIMENSIONS }
        val attempts = dao.allAttempts(); val now = now()
        return dao.words().associate { w -> w.id to GraduationPolicy.status(
            attempts.any { it.wordId == w.id && it.valid && it.outcome !in listOf("SKIP", "VOID") } || progress.any { it.wordId == w.id && it.attempts > 0 },
            attempts.filter { it.wordId == w.id }.mapNotNull(::evidence), progress.any { it.wordId == w.id && it.attempts > 0 && it.dueAt <= now }) }
    }
    suspend fun statistics(): AppStatistics {
        val status = statuses(); val raw = dao.allAttempts()
        val bySense = raw.mapNotNull { a -> evidence(a)?.let { a.wordId to it } }.groupBy({ it.first }, { it.second })
        val work = runtime().day(today())
        return AppStatistics(status.size, status.values.count { it == LearningStatus.STEADY }, status.values.count { it == LearningStatus.DUE },
            bySense.values.count { GraduationPolicy.verified(MasteryDimension.CONTEXT_COMPREHENSION, it) },
            bySense.values.count { GraduationPolicy.verified(MasteryDimension.PRODUCTION, it) }, (work.activeMillis / 60_000).toInt(), work.newSenses.size)
    }

    /** Reserves a bounded batch before a network request; failure/restart never refunds the call budget. */
    suspend fun reserveGeneration(): List<WordSenseEntity> = db.withTransaction {
        val r = runtime(); val day = r.day(today())
        if (!r.autoAi || day.aiCalls >= r.maxDailyCalls) return@withTransaction emptyList()
        val mats = dao.allMaterials().filter { it.quality == "ACTIVE" }
        val usage = dao.allUsages().map { it.materialId }.toSet()
        val progresses = dao.allProgress().filter { it.dimension in ACTIVE_DIMENSIONS }
        val eligible = dao.words().filter { w -> progresses.any { it.wordId == w.id && it.dueAt <= now() + 3 * SessionPolicy.DAY } }
            .filter { w -> mats.count { it.wordId == w.id && it.id !in usage } < 2 }.take(5)
        if (eligible.isNotEmpty()) save(r.withDay(day.copy(aiCalls = day.aiCalls + 1)))
        eligible
    }
    suspend fun addGeneratedMaterials(items: List<GeneratedMaterial>): Pair<Int, Int> = db.withTransaction {
        val byId = dao.words().associateBy { it.id }; var accepted = 0
        for (item in items) {
            val word = byId[item.senseId] ?: continue
            if (word.term != item.term || word.definition != item.definition || item.type !in setOf("SENTENCE", "PHRASE", "COLLOCATION")) continue
            if (!CardFactory.termRegex(word.term).containsMatchIn(item.content)) continue
            // AI family/semantic claims are not trusted as verified transfer evidence.
            if (addMaterial(word.id, item.type, item.content, item.explanation, item.style, "AI")) accepted++
        }
        accepted to (items.size - accepted)
    }
    private suspend fun addMaterial(wordId: Long, type: String, content: String, explanation: String, style: String, source: String, family: String = "", exercise: String = ""): Boolean {
        val normalized = content.lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest("$wordId|$normalized".toByteArray()).joinToString("") { "%02x".format(it) }
        return dao.insertMaterial(MaterialEntity(wordId = wordId, type = type, content = content.clean(), explanation = explanation.clean(), styleTags = style.ifBlank { "general" },
            fingerprint = fingerprint, source = source, family = family, exerciseJson = exercise)) != -1L
    }
    private fun String.clean() = trim().replace(Regex("\\s+"), " ")
    companion object {
        val ACTIVE_DIMENSIONS = MasteryDimension.entries.map { it.name }.toSet()
        val MATERIAL_TYPES = setOf("SENTENCE", "PHRASE", "COLLOCATION", "PROPER_NOUN")
    }
}
