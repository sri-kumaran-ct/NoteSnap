package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.ScreenDestination
import com.example.ui.components.NoteSnapBottomBar
import com.example.ui.components.NoteSnapTopBar
import com.example.ui.screens.CameraScanScreen
import com.example.ui.screens.FlashcardsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NoteDetailScreen
import com.example.ui.screens.NotesListScreen
import com.example.ui.screens.OcrReviewScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WhiteboardScreen
import com.example.ui.theme.NoteSnapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoteSnapTheme {
                NoteSnapApp()
            }
        }
    }
}

@Composable
fun NoteSnapApp(
    viewModel: MainViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val noteCount by viewModel.noteCount.collectAsState()
    val selectedNote by viewModel.selectedNote.collectAsState()
    val scanDraft by viewModel.scanDraft.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher for importing images from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.startNewScan()
            viewModel.processImageUriForOcr(uri)
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // System Back Button Handling
    BackHandler(enabled = currentScreen !is ScreenDestination.Home) {
        viewModel.popBackStack()
    }

    // Determine when to show top & bottom bars
    val showBottomBar = when (currentScreen) {
        is ScreenDestination.Home,
        is ScreenDestination.NotesList,
        is ScreenDestination.Settings -> true
        else -> false
    }

    Scaffold(
        topBar = {
            if (showBottomBar) {
                NoteSnapTopBar(
                    title = when (currentScreen) {
                        is ScreenDestination.Home -> "NoteSnap"
                        is ScreenDestination.NotesList -> "Saved Notes"
                        is ScreenDestination.Settings -> "Settings"
                        else -> "NoteSnap"
                    },
                    onSearchClick = { viewModel.navigateTo(ScreenDestination.NotesList) },
                    onSettingsClick = { viewModel.navigateTo(ScreenDestination.Settings) }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NoteSnapBottomBar(
                    currentDestination = currentScreen,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is ScreenDestination.Home -> {
                    HomeScreen(
                        recentNotes = recentNotes,
                        totalNoteCount = noteCount,
                        onNavigate = { viewModel.navigateTo(it) },
                        onSelectNote = { id ->
                            viewModel.selectNote(id)
                            viewModel.navigateTo(ScreenDestination.NoteDetail(id))
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRenameNote = { note, newTitle -> viewModel.renameNote(note, newTitle) },
                        onDuplicateNote = { note -> viewModel.duplicateNote(note) },
                        onDeleteNote = { note -> viewModel.deleteNote(note) },
                        onStartCameraScan = {
                            viewModel.startNewScan()
                            viewModel.navigateTo(ScreenDestination.CameraScan)
                        },
                        onImportGallery = { galleryLauncher.launch("image/*") },
                        onOpenWhiteboard = { viewModel.navigateTo(ScreenDestination.Whiteboard()) }
                    )
                }

                is ScreenDestination.NotesList -> {
                    NotesListScreen(
                        notes = searchResults,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onSelectNote = { id ->
                            viewModel.selectNote(id)
                            viewModel.navigateTo(ScreenDestination.NoteDetail(id))
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onRenameNote = { note, newTitle -> viewModel.renameNote(note, newTitle) },
                        onDuplicateNote = { note -> viewModel.duplicateNote(note) },
                        onDeleteNote = { note -> viewModel.deleteNote(note) }
                    )
                }

                is ScreenDestination.CameraScan -> {
                    CameraScanScreen(
                        onImageCaptured = { origUri, cropUri -> viewModel.processCapturedImage(origUri, cropUri) },
                        onGallerySelected = { uri -> viewModel.processImageUriForOcr(uri) },
                        onBack = { viewModel.popBackStack() }
                    )
                }

                is ScreenDestination.GalleryImport -> {
                    LaunchedEffect(Unit) {
                        galleryLauncher.launch("image/*")
                        viewModel.navigateTo(ScreenDestination.Home)
                    }
                }

                is ScreenDestination.OcrReview -> {
                    OcrReviewScreen(
                        draftState = scanDraft,
                        onTitleChange = { viewModel.updateDraftTitle(it) },
                        onTextChange = { viewModel.updateDraftText(it) },
                        onTagChange = { viewModel.updateDraftTag(it) },
                        onSaveAndGenerateAi = { viewModel.saveDraftAsNote(autoGenerateAi = true) },
                        onBack = { viewModel.popBackStack() }
                    )
                }

                is ScreenDestination.Whiteboard -> {
                    WhiteboardScreen(
                        onSaveWhiteboard = { title, tag, paths, bitmap ->
                            viewModel.saveWhiteboardNote(title, tag, paths, bitmap)
                        },
                        onBack = { viewModel.popBackStack() }
                    )
                }

                is ScreenDestination.NoteDetail -> {
                    NoteDetailScreen(
                        note = selectedNote,
                        repository = viewModel.repository,
                        aiState = aiState,
                        chatMessages = chatMessages,
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        onRenameNote = { note, newTitle -> viewModel.renameNote(note, newTitle) },
                        onUpdateNoteText = { note, newText -> viewModel.updateNoteText(note, newText) },
                        onRegenerateAi = { viewModel.generateAllStudyMaterialsForNote(it) },
                        onAskAiQuestion = { viewModel.sendChatMessage(it) },
                        onStartQuizPractice = { noteId ->
                            viewModel.navigateTo(ScreenDestination.QuizPractice(noteId))
                        },
                        onStartFlashcardStudy = { noteId ->
                            viewModel.navigateTo(ScreenDestination.FlashcardStudy(noteId))
                        },
                        onBack = { viewModel.popBackStack() }
                    )
                }

                is ScreenDestination.QuizPractice -> {
                    val note = selectedNote
                    val quizzes = viewModel.repository.deserializeQuizzes(note?.quizzesJson)
                    QuizScreen(
                        quizzes = quizzes,
                        noteTitle = note?.title ?: "Note",
                        onBack = { viewModel.popBackStack() }
                    )
                }

                is ScreenDestination.FlashcardStudy -> {
                    val note = selectedNote
                    val flashcards = viewModel.repository.deserializeFlashcards(note?.flashcardsJson)
                    FlashcardsScreen(
                        flashcards = flashcards,
                        noteTitle = note?.title ?: "Note",
                        onBack = { viewModel.popBackStack() }
                    )
                }

                is ScreenDestination.Settings -> {
                    SettingsScreen(
                        isApiKeyConfigured = viewModel.repository.isApiKeyConfigured(),
                        noteCount = noteCount
                    )
                }
            }
        }
    }
}
