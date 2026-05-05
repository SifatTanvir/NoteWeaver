package com.mindforge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mindforge.app.data.local.entity.MergeSuggestionCacheEntity
import com.mindforge.app.data.local.entity.SimilarGroupCacheEntity
import com.mindforge.app.data.local.entity.SimilarGroupMemberEntity

@Dao
interface AnalysisCacheDao {

    @Query("DELETE FROM merge_suggestion_cache")
    suspend fun clearMergeCache()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMergeRows(rows: List<MergeSuggestionCacheEntity>)

    @Query("SELECT * FROM merge_suggestion_cache")
    suspend fun getAllMergeRows(): List<MergeSuggestionCacheEntity>

    @Query("DELETE FROM similar_group_member")
    suspend fun clearGroupMembers()

    @Query("DELETE FROM similar_group_cache")
    suspend fun clearGroupCache()

    @Insert
    suspend fun insertGroup(group: SimilarGroupCacheEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<SimilarGroupMemberEntity>)

    @Query("SELECT id FROM similar_group_cache ORDER BY id ASC")
    suspend fun getAllGroupIds(): List<Long>

    @Query("SELECT noteId FROM similar_group_member WHERE groupId = :groupId ORDER BY noteId ASC")
    suspend fun getMemberNoteIds(groupId: Long): List<Long>

    @Query("DELETE FROM merge_suggestion_cache WHERE note1Id = :noteId OR note2Id = :noteId")
    suspend fun deleteMergeRowsForNote(noteId: Long)

    @Query("DELETE FROM similar_group_member WHERE noteId = :noteId")
    suspend fun deleteMembershipsForNote(noteId: Long)

    @Query("DELETE FROM similar_group_cache WHERE id NOT IN (SELECT DISTINCT groupId FROM similar_group_member)")
    suspend fun deleteEmptyGroups()

    @Transaction
    suspend fun pruneForDeletedNote(noteId: Long) {
        deleteMergeRowsForNote(noteId)
        deleteMembershipsForNote(noteId)
        deleteEmptyGroups()
    }

    /** Atomic replace — avoids wiping merge rows then failing on groups (or vice versa). */
    @Transaction
    suspend fun replaceMergeAndGroupCachesTransactional(
        mergeRows: List<MergeSuggestionCacheEntity>,
        groupsNoteIds: List<List<Long>>,
    ) {
        clearMergeCache()
        if (mergeRows.isNotEmpty()) {
            insertMergeRows(mergeRows)
        }
        clearGroupMembers()
        clearGroupCache()
        for (ids in groupsNoteIds) {
            if (ids.size < 2) continue
            val gid = insertGroup(SimilarGroupCacheEntity())
            val members = ids.map { SimilarGroupMemberEntity(groupId = gid, noteId = it) }
            insertMembers(members)
        }
    }
}
