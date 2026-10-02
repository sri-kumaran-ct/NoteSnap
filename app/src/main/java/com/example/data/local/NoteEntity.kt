package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val rawOcrText: String = "",
    val editedText: String = "",
    val imageUri: String? = null,
    val whiteboardImagePath: String? = null,
    val whiteboardStrokesJson: String? = null,
    val summaryJson: String? = null,
    val keyPointsJson: String? = null,
    val quizzesJson: String? = null,
    val flashcardsJson: String? = null,
    val chatHistoryJson: String? = null,
    val subjectTag: String = "General",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
