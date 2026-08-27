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
)

data class AppStatistics(
    val words: Int,
    val attempts: Int,
    val correct: Int,
    val due: Int,
    val comprehensionStrength: Double,
    val productionStrength: Double,
)

@Dao
interface VocabDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: WordSenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(progress: ProgressEntity)

    @Insert suspend fun insertAttempt(attempt: AttemptEntity)
    @Query("UPDATE ProgressEntity SET stabilityDays=:stability, difficulty=:difficulty, consecutiveSuccesses=:successes, lapses=:lapses, dueAt=:dueAt, lastReviewedAt=:reviewedAt, attempts=attempts+1 WHERE wordId=:wordId AND dimension=:dimension")
    suspend fun updateProgress(wordId: Long, dimension: String, stability: Double, difficulty: Double, successes: Int, lapses: Int, dueAt: Long, reviewedAt: Long)

    @Query("SELECT * FROM WordSenseEntity ORDER BY term, definition") suspend fun words(): List<WordSenseEntity>
    @Query("SELECT * FROM ProgressEntity WHERE wordId=:wordId AND dimension=:dimension") suspend fun progress(wordId: Long, dimension: String): ProgressEntity
    @Query("SELECT w.id,w.term,w.phonetic,w.definition,w.phrase,w.example,p.dimension,p.dueAt,p.lastReviewedAt,p.stabilityDays,p.difficulty,p.attempts FROM WordSenseEntity w JOIN ProgressEntity p ON p.wordId=w.id WHERE p.attempts>0")
    suspend fun reviewCandidates(): List<StudyItem>
    @Query("SELECT w.id,w.term,w.phonetic,w.definition,w.phrase,w.example,p.dimension,p.dueAt,p.lastReviewedAt,p.stabilityDays,p.difficulty,p.attempts FROM WordSenseEntity w JOIN ProgressEntity p ON p.wordId=w.id WHERE p.attempts=0 ORDER BY w.createdAt, CASE p.dimension WHEN 'COMPREHENSION' THEN 0 ELSE 1 END LIMIT :limit")
    suspend fun newItems(limit: Int): List<StudyItem>
    @Query("SELECT COUNT(*) FROM WordSenseEntity") suspend fun wordCount(): Int
    @Query("SELECT COUNT(*) FROM AttemptEntity") suspend fun attemptCount(): Int
    @Query("SELECT COUNT(*) FROM AttemptEntity WHERE correct=1") suspend fun correctCount(): Int
    @Query("SELECT COUNT(*) FROM ProgressEntity WHERE dueAt<=:now AND attempts>0") suspend fun dueCount(now: Long): Int
    @Query("SELECT COALESCE(AVG(stabilityDays),0) FROM ProgressEntity WHERE dimension=:dimension") suspend fun averageStrength(dimension: String): Double

    @Transaction
    suspend fun addWordWithProgress(word: WordSenseEntity): Boolean {
        val id = insertWord(word)
        if (id == -1L) return false
        insertProgress(ProgressEntity(id, "COMPREHENSION"))
        insertProgress(ProgressEntity(id, "PRODUCTION"))
        return true
    }
}

@Database(entities = [WordSenseEntity::class, ProgressEntity::class, AttemptEntity::class], version = 2, exportSchema = true)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun dao(): VocabDao

    companion object {
        @Volatile private var instance: VocabDatabase? = null
        fun get(context: Context): VocabDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, VocabDatabase::class.java, "vocab.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE WordSenseEntity ADD COLUMN phonetic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ProgressEntity ADD COLUMN lastReviewedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
