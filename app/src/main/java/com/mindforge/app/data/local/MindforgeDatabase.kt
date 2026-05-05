package com.mindforge.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mindforge.app.data.local.dao.AnalysisCacheDao
import com.mindforge.app.data.local.dao.NoteDao
import com.mindforge.app.data.local.dao.NoteGroupDao
import com.mindforge.app.data.local.entity.MergeSuggestionCacheEntity
import com.mindforge.app.data.local.entity.Note
import com.mindforge.app.data.local.entity.NoteGroup
import com.mindforge.app.data.local.entity.SimilarGroupCacheEntity
import com.mindforge.app.data.local.entity.SimilarGroupMemberEntity

@Database(
    entities = [
        Note::class,
        NoteGroup::class,
        MergeSuggestionCacheEntity::class,
        SimilarGroupCacheEntity::class,
        SimilarGroupMemberEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MindforgeDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteGroupDao(): NoteGroupDao
    abstract fun analysisCacheDao(): AnalysisCacheDao

    companion object {
        @Volatile
        private var INSTANCE: MindforgeDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN entitiesJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        groupId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO notes_new (id, title, content, createdAt, updatedAt, groupId)
                    SELECT id, title, content, createdAt, updatedAt, groupId FROM notes
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN analyzedForSimilarity INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merge_suggestion_cache (
                        note1Id INTEGER NOT NULL,
                        note2Id INTEGER NOT NULL,
                        score REAL NOT NULL,
                        PRIMARY KEY(note1Id, note2Id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS similar_group_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS similar_group_member (
                        groupId INTEGER NOT NULL,
                        noteId INTEGER NOT NULL,
                        PRIMARY KEY(groupId, noteId)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS note_tag_cross_ref")
                db.execSQL("DROP TABLE IF EXISTS tags")
            }
        }

        fun getDatabase(context: Context): MindforgeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MindforgeDatabase::class.java,
                    "mindforge_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
