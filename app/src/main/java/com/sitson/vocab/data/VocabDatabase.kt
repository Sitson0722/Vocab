package com.sitson.vocab.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(indices = [Index(value = ["term", "definition"], unique = true)])
data class WordSenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val phonetic: String = "",
    val definition: String,
    val phrase: String,
    val example: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "LEARNING",
    val masteredAt: Long? = null,
)

@Entity(
    primaryKeys = ["wordId", "dimension"],
    foreignKeys = [ForeignKey(
        entity = WordSenseEntity::class,
        parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("wordId"), Index("dueAt")],
)
data class ProgressEntity(
    val wordId: Long,
    val dimension: String,
    val stabilityDays: Double = 0.5,
    val difficulty: Double = 0.5,
    val consecutiveSuccesses: Int = 0,
    val lapses: Int = 0,
    val dueAt: Long = 0,
    val lastReviewedAt: Long = 0,
    val attempts: Int = 0,
    val mastery: Double = 0.0,
    val distinctMaterials: Int = 0,
    @ColumnInfo(defaultValue = "'LEGACY'") val engineVersion: String = "LEGACY",
)

@Entity(
    foreignKeys = [ForeignKey(entity = WordSenseEntity::class, parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("wordId"), Index(value = ["fingerprint"], unique = true)],
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val type: String,
    val content: String,
    val explanation: String,
    val styleTags: String,
    val fingerprint: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val family: String = "",
    @ColumnInfo(defaultValue = "'ACTIVE'") val quality: String = "ACTIVE",
    @ColumnInfo(defaultValue = "''") val exerciseJson: String = "",
)

@Entity(
    foreignKeys = [ForeignKey(entity = MaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("materialId"), Index("shownAt")],
)
data class MaterialUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val dimension: String,
    val shownAt: Long = System.currentTimeMillis(),
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = WordSenseEntity::class,
        parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("wordId"), Index("createdAt"), Index(value = ["attemptKey"], unique = true)],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val dimension: String,
    val correct: Boolean,
    val hintsUsed: Int,
    val responseMillis: Long,
    val answer: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attemptKey: String? = null,
    val materialId: Long? = null,
    @ColumnInfo(defaultValue = "'LEGACY_UNKNOWN'") val source: String = "LEGACY_UNKNOWN",
    @ColumnInfo(defaultValue = "''") val outcome: String = "",
    @ColumnInfo(defaultValue = "''") val family: String = "",
    @ColumnInfo(defaultValue = "''") val scope: String = "",
    @ColumnInfo(defaultValue = "''") val kind: String = "",
    @ColumnInfo(defaultValue = "0") val answerExposed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val uncertain: Boolean = false,
    @ColumnInfo(defaultValue = "0") val guided: Boolean = false,
    @ColumnInfo(defaultValue = "0") val corePattern: Boolean = false,
    @ColumnInfo(defaultValue = "1") val valid: Boolean = true,
    @ColumnInfo(defaultValue = "0") val priorExposureAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val presentedAt: Long = 0,
    @ColumnInfo(defaultValue = "''") val day: String = "",
    @ColumnInfo(defaultValue = "''") val timeZone: String = "",
    @ColumnInfo(defaultValue = "''") val sessionId: String = "",
    @ColumnInfo(defaultValue = "''") val priorProgress: String = "",
    @ColumnInfo(defaultValue = "''") val priorRuntime: String = "",
)

@Entity(indices = [Index("wordId"), Index("at")])
data class ExposureEntity(@PrimaryKey val eventKey: String, val wordId: Long, val at: Long, val kind: String)

@Entity
data class RuntimeEntity(@PrimaryKey val id: Int = 1, val json: String)

data class AppStatistics(
    val senses: Int = 0, val steady: Int = 0, val due: Int = 0,
    val comprehensionVerified: Int = 0, val productionVerified: Int = 0,
    val activeMinutes: Int = 0, val newToday: Int = 0,
)

@Dao
interface VocabDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWord(word: WordSenseEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertProgress(progress: ProgressEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(progress: ProgressEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMaterial(material: MaterialEntity): Long
    @Insert suspend fun insertUsage(usage: MaterialUsageEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAttempt(attempt: AttemptEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertExposure(exposure: ExposureEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRuntime(runtime: RuntimeEntity)
    @Query("SELECT * FROM RuntimeEntity WHERE id=1") suspend fun runtime(): RuntimeEntity?
    @Query("SELECT * FROM WordSenseEntity ORDER BY term, definition") suspend fun words(): List<WordSenseEntity>
    @Query("SELECT * FROM ProgressEntity") suspend fun allProgress(): List<ProgressEntity>
    @Query("SELECT * FROM MaterialEntity") suspend fun allMaterials(): List<MaterialEntity>
    @Query("SELECT * FROM MaterialUsageEntity") suspend fun allUsages(): List<MaterialUsageEntity>
    @Query("SELECT * FROM AttemptEntity ORDER BY createdAt, id") suspend fun allAttempts(): List<AttemptEntity>
    @Query("SELECT * FROM ExposureEntity ORDER BY at") suspend fun allExposures(): List<ExposureEntity>
    @Query("SELECT * FROM ProgressEntity WHERE wordId=:wordId AND dimension=:dimension") suspend fun progress(wordId: Long, dimension: String): ProgressEntity?
    @Query("SELECT * FROM AttemptEntity WHERE attemptKey=:key") suspend fun attempt(key: String): AttemptEntity?
    @Query("UPDATE AttemptEntity SET valid=0 WHERE attemptKey=:key") suspend fun invalidateAttempt(key: String)
    @Query("DELETE FROM AttemptEntity WHERE attemptKey=:key") suspend fun deleteAttempt(key: String)
    @Query("UPDATE AttemptEntity SET priorRuntime='' WHERE priorRuntime != ''") suspend fun clearUndoSnapshots()
    @Query("UPDATE MaterialEntity SET quality='REPORTED' WHERE id=:id") suspend fun reportMaterial(id: Long)
    @Query("SELECT COUNT(*) FROM WordSenseEntity") suspend fun wordCount(): Int
    @Query("DELETE FROM ExposureEntity") suspend fun clearExposures()
    @Query("DELETE FROM RuntimeEntity") suspend fun clearRuntime()
    @Query("DELETE FROM MaterialUsageEntity") suspend fun clearUsages()
    @Query("DELETE FROM AttemptEntity") suspend fun clearAttempts()
    @Query("DELETE FROM MaterialEntity") suspend fun clearMaterials()
    @Query("DELETE FROM ProgressEntity") suspend fun clearProgress()
    @Query("DELETE FROM WordSenseEntity") suspend fun clearWords()
    @Insert suspend fun insertWordsForRestore(words: List<WordSenseEntity>)
    @Insert suspend fun insertProgressForRestore(progress: List<ProgressEntity>)
    @Insert suspend fun insertMaterialsForRestore(materials: List<MaterialEntity>)
    @Insert suspend fun insertUsagesForRestore(usages: List<MaterialUsageEntity>)
    @Insert suspend fun insertAttemptsForRestore(attempts: List<AttemptEntity>)
    @Insert suspend fun insertExposuresForRestore(exposures: List<ExposureEntity>)

    @Transaction
    suspend fun restoreAll(words: List<WordSenseEntity>, progress: List<ProgressEntity>, materials: List<MaterialEntity>,
        usages: List<MaterialUsageEntity>, attempts: List<AttemptEntity>, exposures: List<ExposureEntity> = emptyList(), runtime: RuntimeEntity? = null) {
        clearRuntime(); clearExposures(); clearUsages(); clearAttempts(); clearMaterials(); clearProgress(); clearWords()
        if (words.isNotEmpty()) insertWordsForRestore(words)
        if (progress.isNotEmpty()) insertProgressForRestore(progress)
        if (materials.isNotEmpty()) insertMaterialsForRestore(materials)
        if (usages.isNotEmpty()) insertUsagesForRestore(usages)
        if (attempts.isNotEmpty()) insertAttemptsForRestore(attempts)
        if (exposures.isNotEmpty()) insertExposuresForRestore(exposures)
        runtime?.let { saveRuntime(it) }
        words.forEach { word ->
            insertProgress(ProgressEntity(word.id, "CONTEXT_COMPREHENSION"))
            insertProgress(ProgressEntity(word.id, "PRODUCTION"))
        }
    }

    @Transaction
    suspend fun addWordWithProgress(word: WordSenseEntity): Long {
        val id = insertWord(word)
        if (id == -1L) return -1
        insertProgress(ProgressEntity(id, "CONTEXT_COMPREHENSION"))
        insertProgress(ProgressEntity(id, "PRODUCTION"))
        return id
    }
}

@Database(entities = [WordSenseEntity::class, ProgressEntity::class, AttemptEntity::class, MaterialEntity::class, MaterialUsageEntity::class, ExposureEntity::class, RuntimeEntity::class], version = 5, exportSchema = true)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun dao(): VocabDao

    companion object {
        @Volatile private var instance: VocabDatabase? = null
        fun get(context: Context): VocabDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, VocabDatabase::class.java, "vocab.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE WordSenseEntity ADD COLUMN phonetic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ProgressEntity ADD COLUMN lastReviewedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ProgressEntity ADD COLUMN mastery REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ProgressEntity ADD COLUMN distinctMaterials INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE ProgressEntity SET dimension='CONTEXT_COMPREHENSION' WHERE dimension='COMPREHENSION'")
                db.execSQL("INSERT OR IGNORE INTO ProgressEntity (wordId,dimension,stabilityDays,difficulty,consecutiveSuccesses,lapses,dueAt,lastReviewedAt,attempts,mastery,distinctMaterials) SELECT id,'ISOLATED_MEANING',0.5,0.5,0,0,0,0,0,0,0 FROM WordSenseEntity")
                db.execSQL("CREATE TABLE IF NOT EXISTS MaterialEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, wordId INTEGER NOT NULL, type TEXT NOT NULL, content TEXT NOT NULL, explanation TEXT NOT NULL, styleTags TEXT NOT NULL, fingerprint TEXT NOT NULL, source TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(wordId) REFERENCES WordSenseEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_MaterialEntity_wordId ON MaterialEntity(wordId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_MaterialEntity_fingerprint ON MaterialEntity(fingerprint)")
                db.execSQL("CREATE TABLE IF NOT EXISTS MaterialUsageEntity (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, materialId INTEGER NOT NULL, dimension TEXT NOT NULL, shownAt INTEGER NOT NULL, FOREIGN KEY(materialId) REFERENCES MaterialEntity(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_MaterialUsageEntity_materialId ON MaterialUsageEntity(materialId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_MaterialUsageEntity_shownAt ON MaterialUsageEntity(shownAt)")
                db.execSQL("INSERT OR IGNORE INTO MaterialEntity (wordId,type,content,explanation,styleTags,fingerprint,source,createdAt) SELECT id,'SENTENCE',example,definition,'general','legacy-sentence-' || id,'MIGRATED',createdAt FROM WordSenseEntity")
                db.execSQL("INSERT OR IGNORE INTO MaterialEntity (wordId,type,content,explanation,styleTags,fingerprint,source,createdAt) SELECT id,'COLLOCATION',phrase,definition,'general','legacy-phrase-' || id,'MIGRATED',createdAt FROM WordSenseEntity")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE WordSenseEntity ADD COLUMN status TEXT NOT NULL DEFAULT 'LEARNING'")
                db.execSQL("ALTER TABLE WordSenseEntity ADD COLUMN masteredAt INTEGER")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ProgressEntity ADD COLUMN engineVersion TEXT NOT NULL DEFAULT 'LEGACY'")
                db.execSQL("ALTER TABLE MaterialEntity ADD COLUMN family TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE MaterialEntity ADD COLUMN quality TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE MaterialEntity ADD COLUMN exerciseJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE AttemptEntity ADD COLUMN attemptKey TEXT")
                db.execSQL("ALTER TABLE AttemptEntity ADD COLUMN materialId INTEGER")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_AttemptEntity_attemptKey ON AttemptEntity(attemptKey)")
                listOf("source" to "LEGACY_UNKNOWN", "outcome" to "", "family" to "", "scope" to "", "kind" to "",
                    "day" to "", "timeZone" to "", "sessionId" to "", "priorProgress" to "", "priorRuntime" to "").forEach { (name, value) ->
                    db.execSQL("ALTER TABLE AttemptEntity ADD COLUMN $name TEXT NOT NULL DEFAULT '$value'")
                }
                listOf("answerExposed", "uncertain", "guided", "corePattern", "priorExposureAt", "presentedAt").forEach { name ->
                    db.execSQL("ALTER TABLE AttemptEntity ADD COLUMN $name INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("ALTER TABLE AttemptEntity ADD COLUMN valid INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE TABLE IF NOT EXISTS ExposureEntity (eventKey TEXT NOT NULL PRIMARY KEY, wordId INTEGER NOT NULL, at INTEGER NOT NULL, kind TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ExposureEntity_wordId ON ExposureEntity(wordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ExposureEntity_at ON ExposureEntity(at)")
                db.execSQL("CREATE TABLE IF NOT EXISTS RuntimeEntity (id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
                // Existing third-dimension records and original MASTERED tags are archived, never erased.
            }
        }

    }
}
