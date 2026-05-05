package com.mindforge.app.data.local.dao

import androidx.room.*
import com.mindforge.app.data.local.entity.NoteGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteGroupDao {
    @Query("SELECT * FROM note_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<NoteGroup>>
    
    @Query("SELECT * FROM note_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): NoteGroup?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: NoteGroup): Long
    
    @Update
    suspend fun updateGroup(group: NoteGroup)
    
    @Delete
    suspend fun deleteGroup(group: NoteGroup)
}
