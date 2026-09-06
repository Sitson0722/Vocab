package com.sitson.vocab.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseMigrationTest {
    @Test fun `version four migrates without losing archived progress or claiming new evidence`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-v4-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE WordSenseEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, term TEXT NOT NULL, phonetic TEXT NOT NULL, definition TEXT NOT NULL, phrase TEXT NOT NULL, example TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL, masteredAt INTEGER)")
            old.execSQL("CREATE UNIQUE INDEX index_WordSenseEntity_term_definition ON WordSenseEntity(term,definition)")
            old.execSQL("CREATE TABLE ProgressEntity (wordId INTEGER NOT NULL, dimension TEXT NOT NULL, stabilityDays REAL NOT NULL, difficulty REAL NOT NULL, consecutiveSuccesses INTEGER NOT NULL, lapses INTEGER NOT NULL, dueAt INTEGER NOT NULL, lastReviewedAt INTEGER NOT NULL, attempts INTEGER NOT NULL, mastery REAL NOT NULL, distinctMaterials INTEGER NOT NULL, PRIMARY KEY(wordId,dimension), FOREIGN KEY(wordId) REFERENCES WordSenseEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_ProgressEntity_wordId ON ProgressEntity(wordId)")
            old.execSQL("CREATE INDEX index_ProgressEntity_dueAt ON ProgressEntity(dueAt)")
            old.execSQL("CREATE TABLE MaterialEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, wordId INTEGER NOT NULL, type TEXT NOT NULL, content TEXT NOT NULL, explanation TEXT NOT NULL, styleTags TEXT NOT NULL, fingerprint TEXT NOT NULL, source TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(wordId) REFERENCES WordSenseEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_MaterialEntity_wordId ON MaterialEntity(wordId)")
            old.execSQL("CREATE UNIQUE INDEX index_MaterialEntity_fingerprint ON MaterialEntity(fingerprint)")
            old.execSQL("CREATE TABLE MaterialUsageEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, materialId INTEGER NOT NULL, dimension TEXT NOT NULL, shownAt INTEGER NOT NULL, FOREIGN KEY(materialId) REFERENCES MaterialEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_MaterialUsageEntity_materialId ON MaterialUsageEntity(materialId)")
            old.execSQL("CREATE INDEX index_MaterialUsageEntity_shownAt ON MaterialUsageEntity(shownAt)")
            old.execSQL("CREATE TABLE AttemptEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, wordId INTEGER NOT NULL, dimension TEXT NOT NULL, correct INTEGER NOT NULL, hintsUsed INTEGER NOT NULL, responseMillis INTEGER NOT NULL, answer TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(wordId) REFERENCES WordSenseEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_AttemptEntity_wordId ON AttemptEntity(wordId)")
            old.execSQL("CREATE INDEX index_AttemptEntity_createdAt ON AttemptEntity(createdAt)")
            old.execSQL("INSERT INTO WordSenseEntity VALUES(1,'charge','/tʃɑːrdʒ/','收费','charge extra','They charge extra.',100,'MASTERED',900)")
            listOf("CONTEXT_COMPREHENSION", "ISOLATED_MEANING", "PRODUCTION").forEach { dim -> old.execSQL("INSERT INTO ProgressEntity VALUES(1,'$dim',30.0,0.5,5,1,2000,1000,10,0.9,3)") }
            old.execSQL("INSERT INTO MaterialEntity VALUES(1,1,'SENTENCE','They charge extra.','额外收费。','general','old-fingerprint','IMPORT',100)")
            old.execSQL("INSERT INTO MaterialUsageEntity VALUES(1,1,'PRODUCTION',1000)")
            old.execSQL("INSERT INTO AttemptEntity VALUES(1,1,'PRODUCTION',1,0,3000,'SELF_KNOW',1000)")
            old.version = 4
        }
        val migrated = Room.databaseBuilder(context, VocabDatabase::class.java, name).addMigrations(VocabDatabase.MIGRATION_4_5).allowMainThreadQueries().build()
        try {
            // Opening through Room also validates the complete migrated schema against generated entities.
            assertEquals(3, migrated.dao().allProgress().size)
            assertEquals("MASTERED", migrated.dao().words().single().status)
            assertTrue(migrated.dao().allProgress().all { it.engineVersion == "LEGACY" && it.dueAt == 2000L })
            val attempt = migrated.dao().allAttempts().single()
            assertEquals("LEGACY_UNKNOWN", attempt.source)
            assertNull(attempt.materialId); assertNull(attempt.attemptKey)
            assertEquals("", migrated.dao().allMaterials().single().family)
            assertTrue(migrated.dao().allExposures().isEmpty())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
