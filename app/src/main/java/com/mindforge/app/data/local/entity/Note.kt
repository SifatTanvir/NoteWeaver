package com.mindforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val groupId: Long? = null, // For grouping similar notes
    /** When false, this note still needs to be considered in the next merge/group analysis pass. */
    val analyzedForSimilarity: Boolean = false
)
