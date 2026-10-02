package com.example.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.models.ChatMessage
import com.example.data.models.DrawingPath
import com.example.data.models.Flashcard
import com.example.data.models.KeyPointItem
import com.example.data.models.NoteSummary
import com.example.data.models.QuizQuestion
import com.example.services.GeminiService
import com.example.services.OcrService
import com.example.services.WhiteboardStorageService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val ocrService: OcrService,
    private val geminiService: GeminiService,
    private val whiteboardStorageService: WhiteboardStorageService
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    val recentNotes: Flow<List<NoteEntity>> = noteDao.getRecentNotes()
    val favoriteNotes: Flow<List<NoteEntity>> = noteDao.getFavoriteNotes()
    val noteCount: Flow<Int> = noteDao.getNoteCount()

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    suspend fun getNoteById(id: Long): NoteEntity? = noteDao.getNoteById(id)

    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)

    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)

    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

    suspend fun deleteNoteById(id: Long) = noteDao.deleteNoteById(id)

    // OCR extraction
    suspend fun performOcrFromUri(uri: Uri): Result<String> {
        val savedPath = try {
            whiteboardStorageService.copyUriToAppStorage(uri)
        } catch (e: Exception) {
            null
        }
        val ocrResult = ocrService.extractTextFromUri(uri)
        return ocrResult
    }

    suspend fun performOcrFromBitmap(bitmap: Bitmap): Result<String> {
        return ocrService.extractTextFromBitmap(bitmap)
    }

    // AI Generation Helpers
    suspend fun generateSummary(text: String): Result<NoteSummary> = geminiService.generateSummary(text)
    suspend fun generateKeyPoints(text: String): Result<List<KeyPointItem>> = geminiService.generateKeyPoints(text)
    suspend fun generateQuiz(text: String): Result<List<QuizQuestion>> = geminiService.generateQuiz(text)
    suspend fun generateFlashcards(text: String): Result<List<Flashcard>> = geminiService.generateFlashcards(text)
    suspend fun askAiAboutNote(text: String, question: String, history: List<Pair<String, String>>): Result<String> =
        geminiService.askAiAboutNote(text, question, history)

    fun isApiKeyConfigured(): Boolean = geminiService.isApiKeyConfigured()

    // Whiteboard helpers
    fun serializePaths(paths: List<DrawingPath>): String = whiteboardStorageService.serializePaths(paths)
    fun deserializePaths(json: String?): List<DrawingPath> = whiteboardStorageService.deserializePaths(json)
    suspend fun saveBitmap(bitmap: Bitmap, prefix: String = "whiteboard"): String =
        whiteboardStorageService.saveBitmapToInternalStorage(bitmap, prefix)
    suspend fun renderPathsToBitmap(paths: List<DrawingPath>): Bitmap =
        whiteboardStorageService.renderPathsToBitmap(paths)

    // Moshi JSON Serialization helpers
    fun serializeSummary(summary: NoteSummary): String = moshi.adapter(NoteSummary::class.java).toJson(summary)
    fun deserializeSummary(json: String?): NoteSummary? = if (json.isNullOrBlank()) null else try { moshi.adapter(NoteSummary::class.java).fromJson(json) } catch (e: Exception) { null }

    fun serializeKeyPoints(list: List<KeyPointItem>): String = moshi.adapter<List<KeyPointItem>>(Types.newParameterizedType(List::class.java, KeyPointItem::class.java)).toJson(list)
    fun deserializeKeyPoints(json: String?): List<KeyPointItem> = if (json.isNullOrBlank()) emptyList() else try { moshi.adapter<List<KeyPointItem>>(Types.newParameterizedType(List::class.java, KeyPointItem::class.java)).fromJson(json) ?: emptyList() } catch (e: Exception) { emptyList() }

    fun serializeQuizzes(list: List<QuizQuestion>): String = moshi.adapter<List<QuizQuestion>>(Types.newParameterizedType(List::class.java, QuizQuestion::class.java)).toJson(list)
    fun deserializeQuizzes(json: String?): List<QuizQuestion> = if (json.isNullOrBlank()) emptyList() else try { moshi.adapter<List<QuizQuestion>>(Types.newParameterizedType(List::class.java, QuizQuestion::class.java)).fromJson(json) ?: emptyList() } catch (e: Exception) { emptyList() }

    fun serializeFlashcards(list: List<Flashcard>): String = moshi.adapter<List<Flashcard>>(Types.newParameterizedType(List::class.java, Flashcard::class.java)).toJson(list)
    fun deserializeFlashcards(json: String?): List<Flashcard> = if (json.isNullOrBlank()) emptyList() else try { moshi.adapter<List<Flashcard>>(Types.newParameterizedType(List::class.java, Flashcard::class.java)).fromJson(json) ?: emptyList() } catch (e: Exception) { emptyList() }

    fun serializeChatMessages(list: List<ChatMessage>): String = moshi.adapter<List<ChatMessage>>(Types.newParameterizedType(List::class.java, ChatMessage::class.java)).toJson(list)
    fun deserializeChatMessages(json: String?): List<ChatMessage> = if (json.isNullOrBlank()) emptyList() else try { moshi.adapter<List<ChatMessage>>(Types.newParameterizedType(List::class.java, ChatMessage::class.java)).fromJson(json) ?: emptyList() } catch (e: Exception) { emptyList() }
}
