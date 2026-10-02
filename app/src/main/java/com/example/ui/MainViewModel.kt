package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.NoteEntity
import com.example.data.models.BreakdownItem
import com.example.data.models.ChatMessage
import com.example.data.models.DrawingPath
import com.example.data.models.Flashcard
import com.example.data.models.KeyPointItem
import com.example.data.models.KeyTakeaway
import com.example.data.models.NoteSummary
import com.example.data.models.QuizQuestion
import com.example.data.repository.NoteRepository
import com.example.services.GeminiService
import com.example.services.OcrService
import com.example.services.WhiteboardStorageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScreenDestination {
    object Home : ScreenDestination()
    object NotesList : ScreenDestination()
    object CameraScan : ScreenDestination()
    object GalleryImport : ScreenDestination()
    object OcrReview : ScreenDestination()
    data class Whiteboard(val noteId: Long? = null) : ScreenDestination()
    data class NoteDetail(val noteId: Long) : ScreenDestination()
    data class QuizPractice(val noteId: Long) : ScreenDestination()
    data class FlashcardStudy(val noteId: Long) : ScreenDestination()
    object Settings : ScreenDestination()
}

data class ScanDraftState(
    val title: String = "",
    val imageUri: Uri? = null,
    val localImagePath: String? = null,
    val rawOcrText: String = "",
    val editedText: String = "",
    val subjectTag: String = "General",
    val isOcrProcessing: Boolean = false,
    val ocrError: String? = null
)

data class AiGenerationState(
    val isGeneratingSummary: Boolean = false,
    val isGeneratingKeyPoints: Boolean = false,
    val isGeneratingQuiz: Boolean = false,
    val isGeneratingFlashcards: Boolean = false,
    val isChatLoading: Boolean = false,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val ocrService = OcrService(application)
    private val geminiService = GeminiService()
    private val whiteboardStorageService = WhiteboardStorageService(application)

    val repository = NoteRepository(
        noteDao = db.noteDao(),
        ocrService = ocrService,
        geminiService = geminiService,
        whiteboardStorageService = whiteboardStorageService
    )

    // Navigation state
    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    // Notes Flows
    val allNotes = repository.allNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentNotes = repository.recentNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteNotes = repository.favoriteNotes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val noteCount = repository.noteCount.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Search query flow
    val searchQuery = MutableStateFlow("")
    val searchResults = searchQuery.flatMapLatest { query ->
        if (query.isBlank()) repository.allNotes else repository.searchNotes(query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Active Note Detail
    private val _selectedNote = MutableStateFlow<NoteEntity?>(null)
    val selectedNote: StateFlow<NoteEntity?> = _selectedNote.asStateFlow()

    // Draft Note State (for scan / edit / OCR flow)
    private val _scanDraft = MutableStateFlow(ScanDraftState())
    val scanDraft: StateFlow<ScanDraftState> = _scanDraft.asStateFlow()

    // AI Generation Progress State
    private val _aiState = MutableStateFlow(AiGenerationState())
    val aiState: StateFlow<AiGenerationState> = _aiState.asStateFlow()

    // Active Note Chat Messages
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Toast/Snackbar Message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Navigation stack for back button support
    private val backStack = mutableListOf<ScreenDestination>()

    fun navigateTo(destination: ScreenDestination) {
        val current = _currentScreen.value
        if (current != destination) {
            if (destination is ScreenDestination.Home) {
                backStack.clear()
            } else {
                backStack.add(current)
            }
            _currentScreen.value = destination
        }
    }

    fun popBackStack(): Boolean {
        if (backStack.isNotEmpty()) {
            val previous = backStack.removeAt(backStack.lastIndex)
            _currentScreen.value = previous
            return true
        } else if (_currentScreen.value !is ScreenDestination.Home) {
            _currentScreen.value = ScreenDestination.Home
            return true
        }
        return false // Return false when on Home to let system exit app
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun startNewScan(title: String = "Class Note", subjectTag: String = "General") {
        _scanDraft.value = ScanDraftState(title = title, subjectTag = subjectTag)
    }

    fun updateDraftText(text: String) {
        _scanDraft.value = _scanDraft.value.copy(editedText = text)
    }

    fun updateDraftTitle(title: String) {
        _scanDraft.value = _scanDraft.value.copy(title = title)
    }

    fun updateDraftTag(tag: String) {
        _scanDraft.value = _scanDraft.value.copy(subjectTag = tag)
    }

    // Process captured camera image: stores full original image for viewing, runs OCR strictly on cropped scan region
    fun processCapturedImage(originalUri: Uri, croppedUri: Uri) {
        viewModelScope.launch {
            _scanDraft.value = _scanDraft.value.copy(
                imageUri = originalUri,
                isOcrProcessing = true,
                ocrError = null
            )
            val imagePath = try {
                whiteboardStorageService.copyUriToAppStorage(originalUri)
            } catch (e: Exception) {
                null
            }
            _scanDraft.value = _scanDraft.value.copy(localImagePath = imagePath)

            val result = repository.performOcrFromUri(croppedUri)
            result.onSuccess { extracted ->
                _scanDraft.value = _scanDraft.value.copy(
                    rawOcrText = extracted,
                    editedText = extracted,
                    isOcrProcessing = false
                )
                _currentScreen.value = ScreenDestination.OcrReview
            }.onFailure { err ->
                _scanDraft.value = _scanDraft.value.copy(
                    isOcrProcessing = false,
                    ocrError = err.localizedMessage ?: "Failed to perform OCR"
                )
                _userMessage.value = "OCR Failed: ${err.localizedMessage}"
            }
        }
    }

    // Process OCR from URI (e.g. Gallery import)
    fun processImageUriForOcr(uri: Uri) {
        viewModelScope.launch {
            _scanDraft.value = _scanDraft.value.copy(
                imageUri = uri,
                isOcrProcessing = true,
                ocrError = null
            )
            val result = repository.performOcrFromUri(uri)
            result.onSuccess { extracted ->
                _scanDraft.value = _scanDraft.value.copy(
                    rawOcrText = extracted,
                    editedText = extracted,
                    isOcrProcessing = false
                )
                val imagePath = try {
                    whiteboardStorageService.copyUriToAppStorage(uri)
                } catch (e: Exception) {
                    null
                }
                _scanDraft.value = _scanDraft.value.copy(localImagePath = imagePath)
                _currentScreen.value = ScreenDestination.OcrReview
            }.onFailure { err ->
                _scanDraft.value = _scanDraft.value.copy(
                    isOcrProcessing = false,
                    ocrError = err.localizedMessage ?: "Failed to perform OCR"
                )
                _userMessage.value = "OCR Failed: ${err.localizedMessage}"
            }
        }
    }

    // Save Scan Draft as Note and Optionally Trigger AI Analysis
    fun saveDraftAsNote(autoGenerateAi: Boolean = true) {
        viewModelScope.launch {
            val draft = _scanDraft.value
            val title = if (draft.title.isBlank()) "Study Note ${System.currentTimeMillis() % 1000}" else draft.title
            
            var note = NoteEntity(
                title = title,
                rawOcrText = draft.rawOcrText,
                editedText = draft.editedText,
                imageUri = draft.localImagePath ?: draft.imageUri?.toString(),
                subjectTag = draft.subjectTag,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val newId = repository.insertNote(note)
            note = note.copy(id = newId)
            _selectedNote.value = note

            _userMessage.value = "Note saved successfully!"
            _currentScreen.value = ScreenDestination.NoteDetail(newId)

            if (autoGenerateAi && draft.editedText.isNotBlank()) {
                generateAllStudyMaterialsForNote(note)
            }
        }
    }

    fun selectNote(noteId: Long) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId)
            _selectedNote.value = note
            if (note != null) {
                val chatList = repository.deserializeChatMessages(note.chatHistoryJson)
                _chatMessages.value = chatList
            }
        }
    }

    fun toggleFavorite(note: NoteEntity) {
        viewModelScope.launch {
            val updated = note.copy(isFavorite = !note.isFavorite, updatedAt = System.currentTimeMillis())
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
        }
    }

    fun renameNote(note: NoteEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val updated = note.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis())
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
            _userMessage.value = "Note renamed to \"${newTitle.trim()}\""
        }
    }

    fun updateNoteText(note: NoteEntity, newText: String) {
        viewModelScope.launch {
            val updated = note.copy(editedText = newText, updatedAt = System.currentTimeMillis())
            repository.updateNote(updated)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = updated
            }
            _userMessage.value = "Note text updated and saved."
        }
    }

    fun duplicateNote(note: NoteEntity) {
        viewModelScope.launch {
            val copy = note.copy(
                id = 0,
                title = "${note.title} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val newId = repository.insertNote(copy)
            _userMessage.value = "Created duplicate \"${copy.title}\""
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
            if (_selectedNote.value?.id == note.id) {
                _selectedNote.value = null
            }
            _userMessage.value = "Note deleted"
            _currentScreen.value = ScreenDestination.Home
        }
    }

    // Generate All AI Materials for current Note
    fun generateAllStudyMaterialsForNote(note: NoteEntity) {
        viewModelScope.launch {
            val text = note.editedText.ifBlank { note.rawOcrText }
            if (text.isBlank()) {
                _userMessage.value = "No text content available to generate study material."
                return@launch
            }

            _aiState.value = AiGenerationState(
                isGeneratingSummary = true,
                isGeneratingKeyPoints = true,
                isGeneratingQuiz = true,
                isGeneratingFlashcards = true
            )

            var currentNote = repository.getNoteById(note.id) ?: note

            // 1. Summary
            val summaryRes = repository.generateSummary(text)
            summaryRes.onSuccess { summary ->
                if (summary.conciseSummary.isNotBlank()) {
                    currentNote = currentNote.copy(summaryJson = repository.serializeSummary(summary))
                } else {
                    val fallback = generateLocalFallbackSummary(text)
                    currentNote = currentNote.copy(summaryJson = repository.serializeSummary(fallback))
                }
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }.onFailure {
                val fallback = generateLocalFallbackSummary(text)
                currentNote = currentNote.copy(summaryJson = repository.serializeSummary(fallback))
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }
            _aiState.value = _aiState.value.copy(isGeneratingSummary = false)

            // 2. Key Points
            val keyPointsRes = repository.generateKeyPoints(text)
            keyPointsRes.onSuccess { keyPoints ->
                val finalPoints = if (keyPoints.isNotEmpty()) keyPoints else generateLocalFallbackKeyPoints(text)
                currentNote = currentNote.copy(keyPointsJson = repository.serializeKeyPoints(finalPoints))
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }.onFailure {
                val fallback = generateLocalFallbackKeyPoints(text)
                currentNote = currentNote.copy(keyPointsJson = repository.serializeKeyPoints(fallback))
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }
            _aiState.value = _aiState.value.copy(isGeneratingKeyPoints = false)

            // 3. Quizzes
            val quizRes = repository.generateQuiz(text)
            quizRes.onSuccess { quiz ->
                val finalQuiz = if (quiz.isNotEmpty()) quiz else generateLocalFallbackQuiz(text)
                currentNote = currentNote.copy(quizzesJson = repository.serializeQuizzes(finalQuiz))
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }.onFailure {
                val fallback = generateLocalFallbackQuiz(text)
                currentNote = currentNote.copy(quizzesJson = repository.serializeQuizzes(fallback))
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }
            _aiState.value = _aiState.value.copy(isGeneratingQuiz = false)

            // 4. Flashcards
            val flashcardRes = repository.generateFlashcards(text)
            flashcardRes.onSuccess { cards ->
                val finalCards = if (cards.isNotEmpty()) cards else generateLocalFallbackFlashcards(text)
                currentNote = currentNote.copy(flashcardsJson = repository.serializeFlashcards(finalCards))
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }.onFailure {
                val fallback = generateLocalFallbackFlashcards(text)
                currentNote = currentNote.copy(flashcardsJson = repository.serializeFlashcards(fallback))
                repository.updateNote(currentNote)
                _selectedNote.value = currentNote
            }
            _aiState.value = _aiState.value.copy(isGeneratingFlashcards = false)

            _userMessage.value = "AI Study Materials generated successfully!"
        }
    }

    private fun generateLocalFallbackSummary(text: String): NoteSummary {
        val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        val takeaways = mutableListOf<KeyTakeaway>()
        val breakdown = mutableListOf<BreakdownItem>()

        for (paragraph in paragraphs) {
            val lines = paragraph.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val firstLine = lines.first()

            if (firstLine.endsWith(":") || lines.any { it.startsWith("•") || it.startsWith("-") }) {
                val header = firstLine.removeSuffix(":").removePrefix("•").removePrefix("-").trim()
                val listDetails = lines.drop(if (firstLine.endsWith(":")) 1 else 0)
                    .joinToString(", ") { it.removePrefix("•").removePrefix("-").removePrefix("*").trim() }
                val title = if (header.length > 35) header.take(35) + "..." else header
                takeaways.add(KeyTakeaway(title = title, explanation = listDetails.ifBlank { paragraph }))
                breakdown.add(BreakdownItem(title = title, explanation = listDetails.ifBlank { paragraph }))
            } else if (firstLine.endsWith("?") || firstLine.length <= 40) {
                val concept = firstLine.removeSuffix("?").trim()
                val explanation = lines.drop(1).joinToString(" ").ifBlank { paragraph }
                takeaways.add(KeyTakeaway(title = concept, explanation = explanation))
                breakdown.add(BreakdownItem(title = concept, explanation = explanation))
            } else {
                val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
                val firstSentence = sentences.firstOrNull() ?: paragraph
                val title = firstSentence.take(35).let { if (it.length == 35) "$it..." else it }
                takeaways.add(KeyTakeaway(title = title, explanation = paragraph))
                breakdown.add(BreakdownItem(title = title, explanation = paragraph))
            }
        }

        val concise = paragraphs.firstOrNull { it.length > 30 } ?: text.take(150)
        return NoteSummary(
            conciseSummary = concise,
            keyTakeaways = takeaways.distinctBy { it.title }.take(4),
            detailedBreakdown = breakdown.distinctBy { it.title }.take(4),
            detailedSummary = paragraphs.joinToString("\n\n")
        )
    }

    private fun generateLocalFallbackKeyPoints(text: String): List<KeyPointItem> {
        val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        val items = mutableListOf<KeyPointItem>()
        
        for (paragraph in paragraphs) {
            val lines = paragraph.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            
            val firstLine = lines.first()
            if (firstLine.endsWith(":") || lines.any { it.startsWith("•") || it.startsWith("-") || it.startsWith("*") }) {
                // List or application block
                val header = firstLine.removeSuffix(":").removePrefix("•").removePrefix("-").removePrefix("*").trim()
                val listDetails = lines.drop(if (firstLine.endsWith(":")) 1 else 0)
                    .joinToString(", ") { it.removePrefix("•").removePrefix("-").removePrefix("*").trim() }
                items.add(
                    KeyPointItem(
                        title = if (header.length > 40) header.take(40) + "..." else header,
                        explanation = listDetails.ifBlank { paragraph },
                        category = "Application"
                    )
                )
            } else if (firstLine.endsWith("?") || firstLine.length <= 40) {
                // Heading or Question
                val conceptName = firstLine.removeSuffix("?").trim()
                val explanation = lines.drop(1).joinToString(" ").ifBlank { paragraph }
                items.add(
                    KeyPointItem(
                        title = conceptName,
                        explanation = explanation,
                        category = if (firstLine.contains("What", ignoreCase = true) || firstLine.contains("Defin", ignoreCase = true)) "Definition" else "Concept"
                    )
                )
            } else {
                // Standard statement / definition paragraph
                val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
                val firstSentence = sentences.firstOrNull() ?: paragraph
                val conceptCandidate = firstSentence.split(Regex(" (enables|is|provides|refers to|eliminates|consists of) "), 2).firstOrNull()?.trim()
                val title = if (!conceptCandidate.isNullOrBlank() && conceptCandidate.length in 3..40) conceptCandidate else "Key Concept"
                items.add(
                    KeyPointItem(
                        title = title,
                        explanation = paragraph,
                        category = "Concept"
                    )
                )
            }
        }
        
        if (items.isEmpty() && text.isNotBlank()) {
            items.add(
                KeyPointItem(
                    title = "Core Concept",
                    explanation = text,
                    category = "Concept"
                )
            )
        }
        return items.distinctBy { it.displayTitle }.take(5)
    }

    private fun generateLocalFallbackQuiz(text: String): List<QuizQuestion> {
        val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        val questions = mutableListOf<QuizQuestion>()
        var questionCounter = 1

        // Collect all distinct bullet/list items across the note to serve as high-quality intra-text distractors
        val allListItems = text.lines()
            .map { it.trim() }
            .filter { it.startsWith("•") || it.startsWith("-") || it.startsWith("*") }
            .map { it.removePrefix("•").removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotBlank() }
            .distinct()

        for (paragraph in paragraphs) {
            val lines = paragraph.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val firstLine = lines.first()

            if (firstLine.endsWith(":") || lines.any { it.startsWith("•") || it.startsWith("-") || it.startsWith("*") }) {
                // List or applications group
                val topic = firstLine.removeSuffix(":").removePrefix("•").removePrefix("-").removePrefix("*").trim()
                val validItems = lines.drop(if (firstLine.endsWith(":")) 1 else 0)
                    .map { it.removePrefix("•").removePrefix("-").removePrefix("*").trim() }
                    .filter { it.isNotBlank() }

                for (item in validItems.take(2)) {
                    val otherItems = allListItems.filter { it != item }
                    val distractors = if (otherItems.size >= 3) {
                        otherItems.shuffled().take(3)
                    } else {
                        val genericDistractors = listOf(
                            "An unrelated process not listed in the study note",
                            "A mechanism explicitly excluded from this system",
                            "A secondary method not applicable to this context"
                        )
                        (otherItems + genericDistractors).take(3)
                    }
                    val options = (listOf(item) + distractors).shuffled()
                    val correctIdx = options.indexOf(item).coerceAtLeast(0)

                    questions.add(
                        QuizQuestion(
                            id = "q_${questionCounter++}",
                            question = "According to the study note, which of the following is associated with $topic?",
                            options = options,
                            correctAnswerIndex = correctIdx,
                            sampleAnswer = "",
                            explanation = "'$item' is explicitly documented in the note under $topic.",
                            isMultipleChoice = true
                        )
                    )
                }
            } else if (firstLine.endsWith("?") || firstLine.length <= 60) {
                // Concept / Definition / Key statement
                val concept = firstLine.removeSuffix("?").trim()
                val explanationLines = lines.drop(if (firstLine.endsWith("?") || lines.size > 1) 1 else 0)
                val rawExplanation = explanationLines.joinToString(" ").ifBlank { paragraph }
                
                // Keep option text clean and concise (avoid huge blocks)
                val conciseAnswer = if (rawExplanation.length > 140) {
                    rawExplanation.split(". ").firstOrNull()?.plus(".") ?: rawExplanation.take(130).plus("...")
                } else {
                    rawExplanation
                }

                val distractors = listOf(
                    "A process functioning contrary to the described principle",
                    "An obsolete method not supported by the provided text",
                    "A theoretical concept outside the scope of this topic"
                )
                val options = (listOf(conciseAnswer) + distractors).shuffled()
                val correctIdx = options.indexOf(conciseAnswer).coerceAtLeast(0)

                questions.add(
                    QuizQuestion(
                        id = "q_${questionCounter++}",
                        question = if (firstLine.endsWith("?")) firstLine else "What is the primary function or description of $concept?",
                        options = options,
                        correctAnswerIndex = correctIdx,
                        sampleAnswer = "",
                        explanation = "Directly supported by the note text: $conciseAnswer",
                        isMultipleChoice = true
                    )
                )
            }
        }

        return questions.distinctBy { it.question }.ifEmpty {
            val firstMeaningfulLine = text.lines().firstOrNull { it.isNotBlank() } ?: "the subject material"
            val options = listOf(
                firstMeaningfulLine.take(120),
                "An unrelated subject not covered in the note",
                "A contradicting premise with no basis in the text",
                "An external topic outside this domain"
            ).shuffled()
            val correctIdx = options.indexOf(firstMeaningfulLine.take(120)).coerceAtLeast(0)
            listOf(
                QuizQuestion(
                    id = "q1",
                    question = "Which statement is directly supported by the study notes?",
                    options = options,
                    correctAnswerIndex = correctIdx,
                    sampleAnswer = "",
                    explanation = "Directly stated in the source note.",
                    isMultipleChoice = true
                )
            )
        }
    }

    private fun generateLocalFallbackFlashcards(text: String): List<Flashcard> {
        val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        val cards = mutableListOf<Flashcard>()
        var cardCounter = 1

        for (paragraph in paragraphs) {
            val lines = paragraph.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val firstLine = lines.first()

            if (firstLine.endsWith(":") || lines.any { it.startsWith("•") || it.startsWith("-") || it.startsWith("*") }) {
                // List or applications group
                val header = firstLine.removeSuffix(":").removePrefix("•").removePrefix("-").removePrefix("*").trim()
                val listDetails = lines.drop(if (firstLine.endsWith(":")) 1 else 0)
                    .map { it.removePrefix("•").removePrefix("-").removePrefix("*").trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")

                if (listDetails.isNotBlank()) {
                    cards.add(
                        Flashcard(
                            id = "f_${cardCounter++}",
                            front = if (header.isNotBlank()) "What are the key items/uses listed under '$header'?" else "What key applications/items are listed in this note?",
                            back = listDetails,
                            topic = "Applications"
                        )
                    )
                }
            } else if (firstLine.endsWith("?") || firstLine.length <= 60) {
                // Question / Concept heading
                val concept = firstLine.removeSuffix("?").trim()
                val explanationLines = lines.drop(if (firstLine.endsWith("?") || lines.size > 1) 1 else 0)
                val rawExplanation = explanationLines.joinToString(" ").ifBlank { paragraph }
                val conciseBack = if (rawExplanation.length > 200) {
                    rawExplanation.split(". ").firstOrNull()?.plus(".") ?: rawExplanation.take(190).plus("...")
                } else {
                    rawExplanation
                }

                cards.add(
                    Flashcard(
                        id = "f_${cardCounter++}",
                        front = if (firstLine.endsWith("?")) firstLine else "What is $concept?",
                        back = conciseBack,
                        topic = "Definition"
                    )
                )
            } else {
                // Sentence / statement paragraph
                val sentences = paragraph.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                val firstSentence = sentences.firstOrNull() ?: paragraph
                val match = Regex("(.*) (enables|is|provides|eliminates the need for|refers to|consists of|functions as|serves as) (.*)", RegexOption.IGNORE_CASE).find(firstSentence)
                if (match != null) {
                    val subject = match.groupValues[1].trim()
                    val verb = match.groupValues[2].trim()
                    val rest = match.groupValues[3].trim()
                    cards.add(
                        Flashcard(
                            id = "f_${cardCounter++}",
                            front = "What does $subject $verb?",
                            back = rest.replaceFirstChar { it.uppercase() } + (if (!rest.endsWith(".")) "." else ""),
                            topic = "Key Concept"
                        )
                    )
                } else {
                    val conciseSentence = firstSentence.take(180)
                    cards.add(
                        Flashcard(
                            id = "f_${cardCounter++}",
                            front = "Key takeaway from this section:",
                            back = conciseSentence,
                            topic = "Concept"
                        )
                    )
                }
            }
        }

        return cards.distinctBy { it.front }.ifEmpty {
            val firstMeaningfulLine = text.lines().firstOrNull { it.isNotBlank() }?.take(150) ?: "Core study note concepts."
            listOf(
                Flashcard(
                    id = "f1",
                    front = "Core takeaway from this note:",
                    back = firstMeaningfulLine,
                    topic = "General"
                )
            )
        }
    }

    // Ask AI Chat about active note
    fun sendChatMessage(question: String) {
        val note = _selectedNote.value ?: return
        if (question.isBlank()) return

        viewModelScope.launch {
            val userMsg = ChatMessage(sender = "user", text = question)
            val updatedMessages = _chatMessages.value + userMsg
            _chatMessages.value = updatedMessages

            _aiState.value = _aiState.value.copy(isChatLoading = true)

            val textContext = note.editedText.ifBlank { note.rawOcrText }
            val history = updatedMessages.takeLast(6).map { it.sender to it.text }

            val responseRes = repository.askAiAboutNote(textContext, question, history)
            _aiState.value = _aiState.value.copy(isChatLoading = false)

            responseRes.onSuccess { answer ->
                val aiMsg = ChatMessage(sender = "ai", text = answer)
                val finalMessages = updatedMessages + aiMsg
                _chatMessages.value = finalMessages

                val updatedNote = note.copy(
                    chatHistoryJson = repository.serializeChatMessages(finalMessages),
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateNote(updatedNote)
                _selectedNote.value = updatedNote
            }.onFailure { err ->
                _userMessage.value = "AI Chat Error: ${err.localizedMessage}"
            }
        }
    }

    // Save Whiteboard Note
    fun saveWhiteboardNote(
        title: String,
        subjectTag: String,
        drawingPaths: List<DrawingPath>,
        renderedBitmap: Bitmap
    ) {
        viewModelScope.launch {
            val strokeJson = repository.serializePaths(drawingPaths)
            val imagePath = repository.saveBitmap(renderedBitmap, "whiteboard")

            // Perform OCR on whiteboard drawing!
            val ocrRes = repository.performOcrFromBitmap(renderedBitmap)
            val ocrText = ocrRes.getOrDefault("")

            var note = NoteEntity(
                title = title.ifBlank { "Whiteboard ${System.currentTimeMillis() % 1000}" },
                rawOcrText = ocrText,
                editedText = ocrText,
                whiteboardImagePath = imagePath,
                whiteboardStrokesJson = strokeJson,
                subjectTag = subjectTag,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val newId = repository.insertNote(note)
            note = note.copy(id = newId)
            _selectedNote.value = note

            _userMessage.value = "Whiteboard saved!"
            _currentScreen.value = ScreenDestination.NoteDetail(newId)

            if (ocrText.isNotBlank()) {
                generateAllStudyMaterialsForNote(note)
            }
        }
    }
}
