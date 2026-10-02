package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NoteEntity
import com.example.ui.ScreenDestination
import com.example.ui.components.AiSparkBadge
import com.example.ui.components.NoteCard
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoSecondary
import com.example.ui.theme.VioletAccent

@Composable
fun HomeScreen(
    recentNotes: List<NoteEntity>,
    totalNoteCount: Int,
    onNavigate: (ScreenDestination) -> Unit,
    onSelectNote: (Long) -> Unit,
    onToggleFavorite: (NoteEntity) -> Unit,
    onRenameNote: ((NoteEntity, String) -> Unit)? = null,
    onDuplicateNote: ((NoteEntity) -> Unit)? = null,
    onDeleteNote: ((NoteEntity) -> Unit)? = null,
    onStartCameraScan: () -> Unit,
    onImportGallery: () -> Unit,
    onOpenWhiteboard: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Hero Capture -> Understand -> Study Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("hero_banner"),
                colors = CardDefaults.cardColors(containerColor = Color.Unspecified)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(IndigoPrimary, VioletAccent, IndigoSecondary)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        AiSparkBadge(text = "NoteSnap AI Companion")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Turn class notes into instant study packs",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "CAPTURE → UNDERSTAND → STUDY",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = CyanSpark
                        )
                    }
                }
            }
        }

        // Quick Actions Grid (Scan, Gallery, Whiteboard)
        item {
            Column {
                Text(
                    text = "Start Creating",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionTile(
                        title = "Scan Notes",
                        subtitle = "Camera OCR",
                        icon = Icons.Default.CameraAlt,
                        badgeColor = IndigoPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_scan"),
                        onClick = onStartCameraScan
                    )

                    ActionTile(
                        title = "Import",
                        subtitle = "Gallery",
                        icon = Icons.Default.Image,
                        badgeColor = VioletAccent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_import"),
                        onClick = onImportGallery
                    )

                    ActionTile(
                        title = "Whiteboard",
                        subtitle = "Draw & Solve",
                        icon = Icons.Default.Draw,
                        badgeColor = CyanSpark,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_whiteboard"),
                        onClick = onOpenWhiteboard
                    )
                }
            }
        }

        // Study Practice Shortcut Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudyShortcutCard(
                    title = "Practice Quizzes",
                    subtitle = "Test your knowledge",
                    icon = Icons.Default.Quiz,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(ScreenDestination.NotesList) }
                )
                StudyShortcutCard(
                    title = "Flashcard Decks",
                    subtitle = "Master concepts",
                    icon = Icons.Default.Style,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(ScreenDestination.NotesList) }
                )
            }
        }

        // Recent Notes Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Notes ($totalNoteCount)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (recentNotes.isNotEmpty()) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = IndigoPrimary,
                        modifier = Modifier
                            .clickable { onNavigate(ScreenDestination.NotesList) }
                            .padding(4.dp)
                    )
                }
            }
        }

        if (recentNotes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No study notes yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan a handwritten page or open the Whiteboard to begin!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentNotes) { note ->
                NoteCard(
                    note = note,
                    onClick = { onSelectNote(note.id) },
                    onFavoriteToggle = { onToggleFavorite(note) },
                    onRename = onRenameNote?.let { cb -> { newTitle -> cb(note, newTitle) } },
                    onDuplicate = onDuplicateNote?.let { cb -> { cb(note) } },
                    onDelete = onDeleteNote?.let { cb -> { cb(note) } }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = badgeColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StudyShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IndigoPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
