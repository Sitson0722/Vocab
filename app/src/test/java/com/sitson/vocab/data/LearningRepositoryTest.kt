package com.sitson.vocab.data

import androidx.room.Room
import com.sitson.vocab.domain.*
import com.sitson.vocab.provider.ProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningRepositoryTest {
    private lateinit var db: VocabDatabase
    private lateinit var repo: VocabRepository
    private var time = 1_800_000_000_000L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), VocabDatabase::class.java).allowMainThreadQueries().build()
        repo = VocabRepository(db, now = { time }, zone = { ZoneId.of("Asia/Shanghai") })
        repo.initialize()
        Unit
    }
    @After fun close() { db.close() }
    private suspend fun install(kind: ExerciseKind, dimension: MasteryDimension = MasteryDimension.PRODUCTION): LearningCard {
        val word = db.dao().words().first { it.term == "abandon" }
        val material = db.dao().allMaterials().first { it.wordId == word.id }
        val card = requireNotNull(CardFactory.create(word, material, dimension, false, true, false, time, time - 10 * SessionPolicy.DAY, "test")).copy(kind = kind)
        val r = repo.runtime().copy(session = StudySession("test", time, repo.today(), 600_000, card = card))
        db.dao().saveRuntime(RuntimeEntity(json = RuntimeCodec.encode(r)))
        return card
    }
    @Test fun `fresh offline launch interleaves teaching instead of immediately testing the same word`() = runBlocking {
        val r = repo.start(); val c = requireNotNull(r.session?.card)
        assertEquals(ExerciseKind.TEACH, c.kind)
        val next = repo.grade(c.key, Outcome.TAUGHT)
        val following = requireNotNull(next.session?.card)
        assertFalse(following.guided)
        assertNotEquals(c.term, following.term)
        assertEquals(ExerciseKind.TEACH, following.kind)
        assertEquals(0, db.dao().progress(c.wordId, "CONTEXT_COMPREHENSION")!!.attempts)
        assertEquals(1, repo.runtime().day(repo.today()).newSenses.size)
    }
    @Test fun `answer and progress commit once while feedback survives reload`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        repo.submit(c.key, c.answer); repo.submit(c.key, c.answer)
        assertEquals(1, db.dao().allAttempts().size)
        assertEquals(1, db.dao().progress(c.wordId, c.dimension.name)!!.attempts)
        assertEquals(0, db.dao().progress(c.wordId, "CONTEXT_COMPREHENSION")!!.attempts)
        val restored = VocabRepository(db).runtime()
        assertEquals("FEEDBACK", restored.session?.card?.phase)
        assertEquals(c.key, restored.session?.card?.key)
        assertFalse(db.dao().allAttempts().single().answerExposed)
    }
    @Test fun `hinted success is assisted and cannot qualify as independent evidence`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        repo.hint(c.key); repo.submit(c.key, c.answer)
        val a = db.dao().allAttempts().single()
        assertEquals("ASSISTED", a.outcome)
        assertFalse(GraduationPolicy.qualifies(requireNotNull(repo.evidence(a))))
        assertEquals(1, db.dao().progress(c.wordId, c.dimension.name)!!.lapses)
    }
    @Test fun `reveal survives repository recreation and self grading never becomes observed`() = runBlocking {
        val c = install(ExerciseKind.SELF)
        repo.reveal(c.key)
        val restored = VocabRepository(db).runtime().session!!.card!!
        assertTrue(restored.revealed); assertEquals("REVEALED", restored.phase)
        repo.grade(c.key, Outcome.GOOD)
        assertEquals("SELF_REPORTED", db.dao().allAttempts().single().source)
    }
    @Test fun `unrecognized free answer waits for self assessment without a failure`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        repo.submit(c.key, "some other expression")
        assertEquals("UNJUDGED", repo.runtime().session!!.card!!.phase)
        assertTrue(db.dao().allAttempts().isEmpty())
        assertEquals(0, db.dao().progress(c.wordId, c.dimension.name)!!.attempts)
    }
    @Test fun `skip changes session position but not memory`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        val before = db.dao().progress(c.wordId, c.dimension.name)
        repo.grade(c.key, Outcome.SKIP)
        assertEquals(before, db.dao().progress(c.wordId, c.dimension.name))
        assertEquals(1, repo.runtime().session!!.steps)
    }
    @Test fun `reporting a material replays progress excluding its evidence`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        val before = db.dao().progress(c.wordId, c.dimension.name)
        repo.submit(c.key, c.answer); repo.report(c.key)
        assertFalse(db.dao().allAttempts().single().valid)
        assertEquals(before, db.dao().progress(c.wordId, c.dimension.name))
        assertEquals("REPORTED", db.dao().allMaterials().first { it.id == c.materialId }.quality)
    }
    @Test fun `undo restores schedule while retaining exposure and forbidding observed resubmission`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        val before = db.dao().progress(c.wordId, c.dimension.name)
        repo.submit(c.key, c.answer); repo.undo()
        assertEquals(before, db.dao().progress(c.wordId, c.dimension.name))
        assertTrue(db.dao().allAttempts().isEmpty())
        assertTrue(db.dao().allExposures().isNotEmpty())
        assertEquals("REVEALED", repo.runtime().session!!.card!!.phase)
        assertEquals(ExerciseKind.SELF, repo.runtime().session!!.card!!.kind)
        repo.submit(c.key, c.answer)
        assertTrue(db.dao().allAttempts().isEmpty())
    }
    @Test fun `legacy schedule stays intact until first new protocol review`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        val old = ProgressEntity(c.wordId, c.dimension.name, stabilityDays = 100.0, difficulty = 0.2, dueAt = time + SessionPolicy.DAY, attempts = 50, mastery = 0.8)
        db.dao().saveProgress(old)
        assertEquals("LEGACY", db.dao().progress(c.wordId, c.dimension.name)!!.engineVersion)
        repo.submit(c.key, c.answer)
        val migrated = db.dao().progress(c.wordId, c.dimension.name)!!
        assertEquals(ReviewScheduler.VERSION, migrated.engineVersion)
        assertEquals(2.3065, migrated.stabilityDays, 1e-9)
        assertEquals(0.8, migrated.mastery, 0.0)
    }
    @Test fun `daily time goal never stops the learning queue`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        repeat(10) { repo.checkpoint(c.key, 60_000) }
        repo.submit(c.key, c.answer)
        val continued = repo.next()
        assertNotNull(continued.session!!.card)
        assertEquals(600_000L, continued.day(repo.today()).activeMillis)
        assertEquals(continued.session.card!!.key, repo.start().session!!.card!!.key)
    }
    @Test fun `more than five new senses continue in one session and survive reload`() = runBlocking {
        repo.import((1..20).map { ImportedWord("sample$it", "", "示例$it", "sample$it", "Use sample$it here.") })
        repo.settings(5, false, 0)
        var r = repo.start()
        val sessionId = r.session!!.id
        repeat(20) {
            val c = requireNotNull(r.session!!.card)
            assertEquals(ExerciseKind.TEACH, c.kind)
            time += 1_000
            r = repo.grade(c.key, Outcome.TAUGHT)
        }
        val restored = VocabRepository(db, now = { time }).runtime()
        assertEquals(sessionId, restored.session!!.id)
        assertEquals(20, restored.session.steps)
        assertEquals(20, restored.day(repo.today()).newSenses.size)
        assertNotNull(restored.session.card)
    }
    @Test fun `normal learning reaches both dimensions with spaced hidden expression tasks`() = runBlocking {
        var r = repo.start()
        val seen = mutableSetOf<MasteryDimension>()
        val taughtMaterial = mutableMapOf<Long, Long>()
        repeat(40) {
            val c = r.session!!.card
            if (c == null) {
                time += SessionPolicy.SMALL_POOL_COOLDOWN_MILLIS
                r = repo.next()
            } else {
                assertFalse(c.guided)
                if (c.priorExposureAt > 0) assertTrue(time - c.priorExposureAt >= SessionPolicy.COOLDOWN_MILLIS)
                time += 40_000
                if (c.kind == ExerciseKind.TEACH) {
                    taughtMaterial[c.wordId] = c.materialId
                    r = repo.grade(c.key, Outcome.TAUGHT)
                } else {
                    seen += c.dimension
                    if (c.dimension == MasteryDimension.PRODUCTION) {
                        assertEquals(ExerciseKind.INPUT, c.kind)
                        assertFalse(CardFactory.termRegex(c.term).containsMatchIn(c.prompt))
                    }
                    if (db.dao().progress(c.wordId, c.dimension.name)!!.attempts == 0 && c.dimension == MasteryDimension.CONTEXT_COMPREHENSION) {
                        assertNotEquals(taughtMaterial[c.wordId], c.materialId)
                    }
                    repo.submit(c.key, c.answer)
                    r = repo.next()
                }
            }
        }
        assertEquals(MasteryDimension.entries.toSet(), seen)
        assertTrue(db.dao().allProgress().filter { it.dimension == "PRODUCTION" }.all { it.attempts > 0 })
        assertTrue(db.dao().allAttempts().none { repo.evidence(it)?.let(GraduationPolicy::qualifies) == true })
    }
    @Test fun `production is scheduled even when comprehension has never succeeded`() = runBlocking {
        val c = install(ExerciseKind.CHOICE, MasteryDimension.CONTEXT_COMPREHENSION)
        repo.grade(c.key, Outcome.AGAIN)
        // End this session's repair; its independent production schedule remains untested.
        val r = repo.runtime()
        db.dao().saveRuntime(RuntimeEntity(json = RuntimeCodec.encode(r.copy(session = null))))
        time += SessionPolicy.SMALL_POOL_COOLDOWN_MILLIS
        val next = repo.start().session!!.card!!
        assertEquals(c.wordId, next.wordId)
        assertEquals(MasteryDimension.PRODUCTION, next.dimension)
        assertEquals(ExerciseKind.INPUT, next.kind)
    }
    @Test fun `one word waits across restart and then practices both dimensions`() = runBlocking {
        val w = db.dao().words().first { it.term == "abandon" }
        db.dao().restoreAll(listOf(w), emptyList(), db.dao().allMaterials().filter { it.wordId == w.id }, emptyList(), emptyList(),
            runtime = RuntimeEntity(json = RuntimeCodec.encode(LearningRuntime(initialized = true))))
        val first = repo.start().session!!.card!!
        val waiting = repo.grade(first.key, Outcome.TAUGHT)
        assertEquals("WAIT", waiting.session!!.finishReason)
        assertNull(repo.start().session!!.card)
        time += SessionPolicy.SMALL_POOL_COOLDOWN_MILLIS - 1
        assertNull(repo.next().session!!.card)
        time += 1
        val comprehension = repo.next().session!!.card!!
        assertEquals(MasteryDimension.CONTEXT_COMPREHENSION, comprehension.dimension)
        repo.submit(comprehension.key, comprehension.answer)
        assertEquals("WAIT", repo.next().session!!.finishReason)
        time += SessionPolicy.SMALL_POOL_COOLDOWN_MILLIS
        assertEquals(MasteryDimension.PRODUCTION, repo.next().session!!.card!!.dimension)
    }
    @Test fun `full backup restores frozen card exposures preferences and old archived dimension`() = runBlocking {
        val c = install(ExerciseKind.SELF)
        db.dao().insertProgress(ProgressEntity(c.wordId, "ISOLATED_MEANING", attempts = 8))
        repo.settings(5, false, 1); repo.hint(c.key); repo.reveal(c.key)
        val manager = BackupManager(db)
        val saved = manager.export(ProviderConfig(apiKey = "test-only-key"))
        repo.settings(15, true, 3)
        manager.restore(saved)
        assertEquals(5, repo.runtime().dailyMinutes)
        assertTrue(repo.runtime().session!!.card!!.revealed)
        assertEquals(1, repo.runtime().session!!.card!!.hints)
        assertEquals(8, db.dao().progress(c.wordId, "ISOLATED_MEANING")!!.attempts)
    }
    @Test fun `upgrade replaces an unanswered guided card but keeps exposure and history`() = runBlocking {
        val c = install(ExerciseKind.SELF, MasteryDimension.CONTEXT_COMPREHENSION)
        val r = repo.runtime()
        db.dao().insertExposure(ExposureEntity("${c.key}:front", c.wordId, time, "FRONT"))
        db.dao().saveRuntime(RuntimeEntity(json = RuntimeCodec.encode(r.copy(session = r.session!!.copy(card = c.copy(guided = true))))))
        val upgraded = repo.initialize()
        assertNotEquals(c.wordId, upgraded.session!!.card!!.wordId)
        assertFalse(upgraded.session.card!!.guided)
        assertTrue(db.dao().allExposures().any { it.eventKey == "${c.key}:front" })
        assertTrue(db.dao().allAttempts().isEmpty())
    }
    @Test fun `upgrade preserves the answer of a revealed guided card`() = runBlocking {
        val c = install(ExerciseKind.SELF)
        val r = repo.runtime()
        val revealed = c.copy(guided = true, revealed = true, phase = "REVEALED", draft = "saved answer")
        db.dao().saveRuntime(RuntimeEntity(json = RuntimeCodec.encode(r.copy(session = r.session!!.copy(card = revealed)))))
        assertEquals(revealed, repo.initialize().session!!.card)
    }
    @Test fun `generation reservation remains bounded across repeated starts`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        // Mark bundled materials seen so this sense actually needs replenishing.
        db.dao().allMaterials().forEach { db.dao().insertUsage(MaterialUsageEntity(materialId = it.id, dimension = c.dimension.name)) }
        repo.settings(10, true, 1)
        assertTrue(repo.reserveGeneration().isNotEmpty())
        assertTrue(repo.reserveGeneration().isEmpty())
        assertEquals(1, repo.runtime().day(repo.today()).aiCalls)
    }
    @Test fun `material admission never falls back to another sense of the same term`() = runBlocking {
        val w = db.dao().words().first { it.term == "charge" }
        val wrong = GeneratedMaterial("charge", "an unrelated sense", "SENTENCE", "They charge extra.", "wrong", "general", w.id)
        assertEquals(0, repo.addGeneratedMaterials(listOf(wrong)).first)
    }
    @Test fun `topic persists through backup and old runtime defaults to general`() = runBlocking {
        repo.setMaterialTopic("  TBBT  ")
        val manager = BackupManager(db)
        val saved = manager.export(ProviderConfig())
        repo.setMaterialTopic("romance")
        manager.restore(saved)
        assertEquals("TBBT", repo.runtime().materialTopic)
        val old = org.json.JSONObject(RuntimeCodec.encode(repo.runtime())).apply { remove("materialTopic") }
        assertEquals("", RuntimeCodec.decode(old.toString()).materialTopic)
    }
    @Test fun `selected topic is generated despite unused bundled content and preferred in learning`() = runBlocking {
        repo.setMaterialTopic("romance")
        val selected = repo.reserveGeneration()
        assertEquals(5, selected.size)
        val word = selected.first { it.term == "abandon" }
        val item = GeneratedMaterial(word.term, word.definition, "SENTENCE",
            "They decided to abandon the plan and have a romantic dinner instead.", "他们放弃原计划，改为共进浪漫晚餐。", "model-chosen-tag", word.id)
        assertEquals(1, repo.addGeneratedMaterials(listOf(item), requestedTopic = "romance").first)
        val material = db.dao().allMaterials().single { it.source == "AI" }
        assertEquals("romance", material.styleTags)
        assertEquals("", material.family)
        assertEquals(material.id, repo.start().session!!.card!!.materialId)
        repo.setMaterialTopic("TBBT")
        assertTrue(repo.reserveGeneration().any { it.id == word.id })
        // A new preference never replaces a question already shown to the learner.
        assertEquals(material.id, repo.start().session!!.card!!.materialId)
    }
    @Test fun `manual topic generation works with automatic generation disabled`() = runBlocking {
        repo.settings(10, false, 0)
        repo.setMaterialTopic("TBBT")
        assertTrue(repo.reserveGeneration().isEmpty())
        assertEquals(5, repo.reserveGeneration(manual = true).size)
        assertEquals(0, repo.runtime().day(repo.today()).aiCalls)
        assertNotNull(repo.start().session!!.card) // Offline fallback remains usable.
    }
    @Test fun `undo keeps the newly selected topic`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        repo.submit(c.key, c.answer)
        repo.setMaterialTopic("romance")
        assertEquals("romance", repo.undo().materialTopic)
    }
    @Test fun `invalidating an earlier material preserves and replays later valid attempts`() = runBlocking {
        val first = install(ExerciseKind.INPUT)
        repo.submit(first.key, first.answer)
        time += 3 * SessionPolicy.DAY
        val word = db.dao().words().first { it.id == first.wordId }
        val material = db.dao().allMaterials().first { it.wordId == word.id && it.id != first.materialId }
        val second = CardFactory.create(word, material, first.dimension, false, true, false, time, time - 3 * SessionPolicy.DAY, "test")!!
        val r = repo.runtime().copy(session = StudySession("second", time, repo.today(), 600_000, card = second))
        db.dao().saveRuntime(RuntimeEntity(json = RuntimeCodec.encode(r)))
        repo.submit(second.key, second.answer)
        assertTrue(db.dao().progress(word.id, first.dimension.name)!!.stabilityDays > 2.3065)
        val reportView = repo.runtime().copy(session = StudySession("first", time, repo.today(), 600_000, steps = 1, card = first.copy(phase = "FEEDBACK"), attempts = listOf(first.key)))
        db.dao().saveRuntime(RuntimeEntity(json = RuntimeCodec.encode(reportView)))
        repo.report(first.key)
        assertEquals(1, db.dao().allAttempts().count { it.valid })
        assertEquals(1, db.dao().progress(word.id, first.dimension.name)!!.attempts)
        assertEquals(2.3065, db.dao().progress(word.id, first.dimension.name)!!.stabilityDays, 1e-9)
    }

}
