package com.mindforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merge_suggestion_cache", primaryKeys = ["note1Id", "note2Id"])
data class MergeSuggestionCacheEntity(
    val note1Id: Long,
    val note2Id: Long,
    val score: Float
)

@Entity(tableName = "similar_group_cache")
data class SimilarGroupCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)

@Entity(tableName = "similar_group_member", primaryKeys = ["groupId", "noteId"])
data class SimilarGroupMemberEntity(
    val groupId: Long,
    val noteId: Long
)
