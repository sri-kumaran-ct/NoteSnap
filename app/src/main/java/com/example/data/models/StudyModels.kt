package com.example.data.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KeyTakeaway(
    val title: String = "",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class BreakdownItem(
    val title: String = "",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class NoteSummary(
    val conciseSummary: String = "",
    val keyTakeaways: List<KeyTakeaway> = emptyList(),
    val detailedBreakdown: List<BreakdownItem> = emptyList(),
    val detailedSummary: String = ""
)

@JsonClass(generateAdapter = true)
data class KeyPointItem(
    val title: String = "",
    val concept: String = "",
    val explanation: String = "",
    val category: String = "Concept" // Concept, Definition, Formula, Fact
) {
    val displayTitle: String
        get() = title.ifBlank { concept }.ifBlank { "Key Concept" }
}

@JsonClass(generateAdapter = true)
data class QuizQuestion(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(), // Empty for short-answer
    val correctAnswerIndex: Int = -1, // -1 for short-answer
    val sampleAnswer: String = "", // For short-answer questions
    val explanation: String = "",
    val isMultipleChoice: Boolean = true
)

@JsonClass(generateAdapter = true)
data class Flashcard(
    val id: String = "",
    val front: String = "",
    val back: String = "",
    val topic: String = "General",
    var isMastered: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val id: String = "",
    val sender: String = "user", // "user" or "ai"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// Whiteboard stroke drawing data
data class DrawingPoint(
    val x: Float,
    val y: Float
)

data class DrawingPath(
    val points: List<DrawingPoint>,
    val colorHex: Long,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val isHighlighter: Boolean = false
)
