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
    @Test fun `fresh offline launch teaches then performs one weak guided retrieval`() = runBlocking {
        val r = repo.start(); val c = requireNotNull(r.session?.card)
        assertEquals(ExerciseKind.TEACH, c.kind)
        val next = repo.grade(c.key, Outcome.TAUGHT)
        val guided = requireNotNull(next.session?.card)
        assertTrue(guided.guided); assertEquals(c.wordId, guided.wordId)
        repo.reveal(guided.key); repo.grade(guided.key, Outcome.GOOD)
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
    @Test fun `time budget persists across rounds and blocks new tasks until explicit extra time`() = runBlocking {
        val c = install(ExerciseKind.INPUT)
        repeat(10) { repo.checkpoint(c.key, 60_000) }
        repo.submit(c.key, c.answer)
        val finished = repo.next()
        assertEquals("TIME", finished.session!!.finishReason)
        assertNull(finished.session!!.card)
        assertEquals("TIME", repo.start().session!!.finishReason)
        assertNotEquals("TIME", repo.start(extra = true).session!!.finishReason)
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
