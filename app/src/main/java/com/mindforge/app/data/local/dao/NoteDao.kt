package com.mindforge.app.data.local.dao

import androidx.room.*
import com.mindforge.app.data.local.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): Note?

    @Query("SELECT * FROM notes WHERE groupId = :groupId")
    suspend fun getNotesByGroupId(groupId: Long): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)

    @Query("SELECT * FROM notes WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%'")
    fun searchNotes(query: String): Flow<List<Note>>

    @Query("UPDATE notes SET analyzedForSimilarity = :analyzed WHERE id = :noteId")
    suspend fun setNoteAnalyzedForSimilarity(noteId: Long, analyzed: Boolean)

    @Query("UPDATE notes SET analyzedForSimilarity = 1")
    suspend fun markAllNotesAnalyzedForSimilarity()
}
