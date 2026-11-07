package com.edgecue.app.data.history.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.edgecue.app.data.rag.db.RagChunkEntity
import com.edgecue.app.data.rag.db.RagDao
import com.edgecue.app.data.rag.db.RagDocumentEntity

/**
 * Android Jetpack Room Database for EdgeCue.
 */
@Database(
    entities = [
        InterviewSessionEntity::class,
        AiContentReportEntity::class,
        RagDocumentEntity::class,
        RagChunkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun interviewSessionDao(): InterviewSessionDao
    abstract fun aiContentReportDao(): AiContentReportDao
    abstract fun ragDao(): RagDao

    companion object {
        private const val DATABASE_NAME = "interview_assistant.db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rag_documents` (
                        `sourceType` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `rawText` TEXT NOT NULL,
                        `charCount` INTEGER NOT NULL,
                        `chunkCount` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceType`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rag_chunks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `sectionTitle` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `tokensCsv` TEXT NOT NULL,
                        FOREIGN KEY(`sourceType`) REFERENCES `rag_documents`(`sourceType`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_rag_chunks_sourceType` ON `rag_chunks` (`sourceType`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_rag_chunks_sourceType_chunkIndex` ON `rag_chunks` (`sourceType`, `chunkIndex`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(): AppDatabase? = INSTANCE

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
