package com.sitson.vocab.data

import android.content.Context
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
    indices = [Index("wordId"), Index("createdAt")],
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
)

data class StudyItem(
    val id: Long,
    val term: String,
    val phonetic: String,
    val definition: String,
    val phrase: String,
    val example: String,
    val dimension: String,
    val dueAt: Long,
    val lastReviewedAt: Long,
    val stabilityDays: Double,
    val difficulty: Double,
    val attempts: Int,
    val materialId: Long,
    val materialType: String,
    val materialContent: String,
    val materialExplanation: String,
    val styleTags: String,
    val isNovelMaterial: Boolean,
)

data class ReviewCandidate(
    val id: Long, val term: String, val phonetic: String, val definition: String,
    val phrase: String, val example: String, val dimension: String, val dueAt: Long,
    val lastReviewedAt: Long, val stabilityDays: Double, val difficulty: Double, val attempts: Int,
    val mastery: Double, val distinctMaterials: Int,
)

data class AppStatistics(
    val words: Int,
    val attempts: Int,
    val correct: Int,
    val due: Int,
    val contextualStrength: Double,
    val isolatedStrength: Double,
    val productionStrength: Double,
    val contextualMastery: Double,
    val isolatedMastery: Double,
    val productionMastery: Double,
    val contextualRetention: Double,
    val isolatedRetention: Double,
    val productionRetention: Double,
)

@Dao
interface VocabDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: WordSenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(progress: ProgressEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMaterial(material: MaterialEntity): Long
    @Insert suspend fun insertUsage(usage: MaterialUsageEntity)

    @Insert suspend fun insertAttempt(attempt: AttemptEntity)
    @Query("UPDATE ProgressEntity SET stabilityDays=:stability, difficulty=:difficulty, consecutiveSuccesses=:successes, lapses=:lapses, dueAt=:dueAt, lastReviewedAt=:reviewedAt, attempts=attempts+1, mastery=:mastery, distinctMaterials=:distinctMaterials WHERE wordId=:wordId AND dimension=:dimension")
    suspend fun updateProgress(wordId: Long, dimension: String, stability: Double, difficulty: Double, successes: Int, lapses: Int, dueAt: Long, reviewedAt: Long, mastery: Double, distinctMaterials: Int)

    @Query("SELECT * FROM WordSenseEntity ORDER BY term, definition") suspend fun words(): List<WordSenseEntity>
    @Query("SELECT * FROM ProgressEntity WHERE wordId=:wordId AND dimension=:dimension") suspend fun progress(wordId: Long, dimension: String): ProgressEntity
    @Query("SELECT w.id,w.term,w.phonetic,w.definition,w.phrase,w.example,p.dimension,p.dueAt,p.lastReviewedAt,p.stabilityDays,p.difficulty,p.attempts,p.mastery,p.distinctMaterials FROM WordSenseEntity w JOIN ProgressEntity p ON p.wordId=w.id WHERE p.attempts>0")
    suspend fun reviewCandidates(): List<ReviewCandidate>
    @Query("SELECT w.id,w.term,w.phonetic,w.definition,w.phrase,w.example,p.dimension,p.dueAt,p.lastReviewedAt,p.stabilityDays,p.difficulty,p.attempts,p.mastery,p.distinctMaterials FROM WordSenseEntity w JOIN ProgressEntity p ON p.wordId=w.id WHERE p.attempts=0 ORDER BY w.createdAt, CASE p.dimension WHEN 'CONTEXT_COMPREHENSION' THEN 0 WHEN 'ISOLATED_MEANING' THEN 1 ELSE 2 END LIMIT :limit")
    suspend fun newCandidates(limit: Int): List<ReviewCandidate>
    @Query("SELECT m.* FROM MaterialEntity m LEFT JOIN MaterialUsageEntity u ON u.materialId=m.id WHERE m.wordId=:wordId AND (:style='' OR m.styleTags LIKE '%' || :style || '%') GROUP BY m.id ORDER BY COUNT(u.id), COALESCE(MAX(u.shownAt),0), m.createdAt DESC")
    suspend fun materialsFor(wordId: Long, style: String): List<MaterialEntity>
    @Query("SELECT COUNT(*) FROM MaterialUsageEntity WHERE materialId=:materialId") suspend fun materialUseCount(materialId: Long): Int
    @Query("SELECT DISTINCT styleTags FROM MaterialEntity WHERE styleTags != '' ORDER BY styleTags") suspend fun styles(): List<String>
    @Query("SELECT COUNT(*) FROM MaterialEntity") suspend fun materialCount(): Int
    @Query("SELECT COUNT(*) FROM MaterialUsageEntity") suspend fun materialUsageCount(): Int
    @Query("SELECT COUNT(*) FROM WordSenseEntity") suspend fun wordCount(): Int
    @Query("SELECT COUNT(*) FROM AttemptEntity") suspend fun attemptCount(): Int
    @Query("SELECT COUNT(*) FROM AttemptEntity WHERE correct=1") suspend fun correctCount(): Int
    @Query("SELECT COUNT(*) FROM ProgressEntity WHERE dueAt<=:now AND attempts>0") suspend fun dueCount(now: Long): Int
    @Query("SELECT COALESCE(AVG(stabilityDays),0) FROM ProgressEntity WHERE dimension=:dimension") suspend fun averageStrength(dimension: String): Double
    @Query("SELECT COALESCE(AVG(mastery),0) FROM ProgressEntity WHERE dimension=:dimension") suspend fun averageMastery(dimension: String): Double

    @Transaction
    suspend fun addWordWithProgress(word: WordSenseEntity): Long {
        val id = insertWord(word)
        if (id == -1L) return -1
        insertProgress(ProgressEntity(id, "CONTEXT_COMPREHENSION"))
        insertProgress(ProgressEntity(id, "ISOLATED_MEANING"))
        insertProgress(ProgressEntity(id, "PRODUCTION"))
        return id
    }
}

@Database(entities = [WordSenseEntity::class, ProgressEntity::class, AttemptEntity::class, MaterialEntity::class, MaterialUsageEntity::class], version = 3, exportSchema = true)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun dao(): VocabDao

    companion object {
        @Volatile private var instance: VocabDatabase? = null
        fun get(context: Context): VocabDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, VocabDatabase::class.java, "vocab.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
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
    }
}
