package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.models.QuizQuestion
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    quizzes: List<QuizQuestion>,
    noteTitle: String,
    onBack: () -> Unit
) {
    if (quizzes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No quiz questions available.")
        }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOptionIndex by remember { mutableIntStateOf(-1) }
    var shortAnswerText by remember { mutableStateOf("") }
    var isAnswerSubmitted by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    val currentQuestion = quizzes.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz Practice: $noteTitle", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isQuizCompleted) {
            QuizResultsView(
                score = score,
                total = quizzes.size,
                onRestart = {
                    currentIndex = 0
                    score = 0
                    selectedOptionIndex = -1
                    shortAnswerText = ""
                    isAnswerSubmitted = false
                    isQuizCompleted = false
                },
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        if (currentQuestion == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question ${currentIndex + 1} of ${quizzes.size}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Score: $score",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = IndigoPrimary
                )
            }

            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / quizzes.size },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                color = IndigoPrimary
            )

            // Question Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentQuestion.isMultipleChoice) {
                        currentQuestion.options.forEachIndexed { optIndex, option ->
                            val isCorrect = optIndex == currentQuestion.correctAnswerIndex
                            val isSelected = optIndex == selectedOptionIndex

                            val backgroundColor = when {
                                isAnswerSubmitted && isCorrect -> EmeraldSuccess.copy(alpha = 0.2f)
                                isAnswerSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }

                            val borderColor = when {
                                isAnswerSubmitted && isCorrect -> EmeraldSuccess
                                isAnswerSubmitted && isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                isSelected -> IndigoPrimary
                                else -> Color.Transparent
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isAnswerSubmitted) {
                                        selectedOptionIndex = optIndex
                                    }
                                    .testTag("quiz_option_$optIndex"),
                                shape = RoundedCornerShape(12.dp),
                                color = backgroundColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${('A' + optIndex)}. ",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isAnswerSubmitted && isCorrect) {
                                        Icon(Icons.Default.Check, contentDescription = "Correct", tint = EmeraldSuccess)
                                    } else if (isAnswerSubmitted && isSelected && !isCorrect) {
                                        Icon(Icons.Default.Close, contentDescription = "Incorrect", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    } else {
                        // Short Answer Field
                        OutlinedTextField(
                            value = shortAnswerText,
                            onValueChange = { shortAnswerText = it },
                            label = { Text("Your Answer") },
                            modifier = Modifier.fillMaxWidth().testTag("quiz_short_answer_input"),
                            enabled = !isAnswerSubmitted,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (isAnswerSubmitted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Sample Solution:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text(currentQuestion.sampleAnswer, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // Explanation Card
            if (isAnswerSubmitted && currentQuestion.explanation.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Explanation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currentQuestion.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Submit / Next Question Action
            if (!isAnswerSubmitted) {
                Button(
                    onClick = {
                        isAnswerSubmitted = true
                        if (currentQuestion.isMultipleChoice && selectedOptionIndex == currentQuestion.correctAnswerIndex) {
                            score++
                        } else if (!currentQuestion.isMultipleChoice && shortAnswerText.isNotBlank()) {
                            score++
                        }
                    },
                    enabled = if (currentQuestion.isMultipleChoice) selectedOptionIndex >= 0 else shortAnswerText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("quiz_check_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Check Answer", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        if (currentIndex + 1 < quizzes.size) {
                            currentIndex++
                            selectedOptionIndex = -1
                            shortAnswerText = ""
                            isAnswerSubmitted = false
                        } else {
                            isQuizCompleted = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("quiz_next_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (currentIndex + 1 < quizzes.size) "Next Question" else "See Results",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun QuizResultsView(
    score: Int,
    total: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(IndigoPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Quiz Completed!", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You scored $score out of $total",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRestart,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Try Again")
            }

            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Done")
            }
        }
    }
}
