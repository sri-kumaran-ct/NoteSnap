package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.local.NoteEntity
import com.example.ui.components.EmptyState
import com.example.ui.components.NoteCard
import com.example.ui.theme.IndigoPrimary

enum class NoteSortOrder(val label: String) {
    RECENTLY_MODIFIED("Recently Modified"),
    DATE_CREATED("Date Created"),
    TITLE_AZ("Title (A-Z)"),
    SUBJECT("Subject")
}

@Composable
fun NotesListScreen(
    notes: List<NoteEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectNote: (Long) -> Unit,
    onToggleFavorite: (NoteEntity) -> Unit,
    onRenameNote: ((NoteEntity, String) -> Unit)? = null,
    onDuplicateNote: ((NoteEntity) -> Unit)? = null,
    onDeleteNote: ((NoteEntity) -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var currentSortOrder by remember { mutableStateOf(NoteSortOrder.RECENTLY_MODIFIED) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val categories = listOf("All", "Favorites", "Science", "Math", "History", "Literature", "Computer Science", "Economics", "General")

    val filteredNotes = notes.filter { note ->
        when (selectedCategory) {
            "All" -> true
            "Favorites" -> note.isFavorite
            else -> note.subjectTag.equals(selectedCategory, ignoreCase = true)
        }
    }.let { list ->
        when (currentSortOrder) {
            NoteSortOrder.RECENTLY_MODIFIED -> list.sortedByDescending { it.updatedAt }
            NoteSortOrder.DATE_CREATED -> list.sortedByDescending { it.createdAt }
            NoteSortOrder.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
            NoteSortOrder.SUBJECT -> list.sortedBy { it.subjectTag.lowercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Field with Sorting Options
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search notes by title or OCR text...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("notes_search_input"),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Sort Notes",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    NoteSortOrder.values().forEach { sort ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sort.label,
                                    fontWeight = if (currentSortOrder == sort) FontWeight.Bold else FontWeight.Normal,
                                    color = if (currentSortOrder == sort) IndigoPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                currentSortOrder = sort
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Category Pills
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clickable { selectedCategory = category }
                        .testTag("filter_$category")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (category == "Favorites") {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List View
        if (filteredNotes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyState(
                    title = if (searchQuery.isNotEmpty()) "No matching notes" else "No notes found",
                    description = if (searchQuery.isNotEmpty()) "We couldn't find any study notes matching \"$searchQuery\"." else "No notes in \"$selectedCategory\" category."
                )
                if (searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { onSearchQueryChange("") }) {
                        Text("Clear Search")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredNotes) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onSelectNote(note.id) },
                        onFavoriteToggle = { onToggleFavorite(note) },
                        onRename = onRenameNote?.let { cb -> { newTitle -> cb(note, newTitle) } },
                        onDuplicate = onDuplicateNote?.let { cb -> { cb(note) } },
                        onDelete = onDeleteNote?.let { cb -> { cb(note) } }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
