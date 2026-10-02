package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.models.DrawingPath
import com.example.data.models.DrawingPoint
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.VioletAccent

enum class WhiteboardTool { PEN, HIGHLIGHTER, ERASER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    initialTitle: String = "Whiteboard Math & Diagrams",
    onSaveWhiteboard: (title: String, tag: String, paths: List<DrawingPath>, bitmap: Bitmap) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var subjectTag by remember { mutableStateOf("Math") }

    val paths = remember { mutableStateListOf<DrawingPath>() }
    val undonePaths = remember { mutableStateListOf<DrawingPath>() }

    var currentTool by remember { mutableStateOf(WhiteboardTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFF0F172A)) } // Dark navy
    var strokeWidth by remember { mutableFloatStateOf(6f) }

    val palette = listOf(
        Color(0xFF0F172A), // Charcoal Navy
        Color(0xFF4F46E5), // Indigo
        Color(0xFF8B5CF6), // Violet
        Color(0xFF06B6D4), // Cyan
        Color(0xFFEF4444), // Coral Red
        Color(0xFF10B981), // Emerald Green
        Color(0xFFF59E0B)  // Amber
    )

    // Active path being drawn
    var activePoints by remember { mutableStateOf<List<DrawingPoint>>(emptyList()) }

    var canvasWidth by remember { mutableStateOf(1080) }
    var canvasHeight by remember { mutableStateOf(1440) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interactive Whiteboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undo Button
                    IconButton(
                        onClick = {
                            if (paths.isNotEmpty()) {
                                undonePaths.add(paths.removeLast())
                            }
                        },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }

                    // Redo Button
                    IconButton(
                        onClick = {
                            if (undonePaths.isNotEmpty()) {
                                paths.add(undonePaths.removeLast())
                            }
                        },
                        enabled = undonePaths.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }

                    // Clear Canvas
                    IconButton(
                        onClick = {
                            paths.clear()
                            undonePaths.clear()
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Info Input
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Whiteboard Title") },
                        modifier = Modifier.weight(1f).testTag("whiteboard_title"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            // Render to bitmap
                            val bitmap = renderToBitmap(paths, canvasWidth, canvasHeight)
                            onSaveWhiteboard(title, subjectTag, paths.toList(), bitmap)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_whiteboard_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Canvas Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .pointerInput(currentTool, selectedColor, strokeWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                canvasWidth = size.width
                                canvasHeight = size.height
                                activePoints = listOf(DrawingPoint(offset.x, offset.y))
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newPoint = DrawingPoint(change.position.x, change.position.y)
                                activePoints = activePoints + newPoint
                            },
                            onDragEnd = {
                                if (activePoints.size >= 2) {
                                    val newPath = DrawingPath(
                                        points = activePoints,
                                        colorHex = selectedColor.toArgb().toLong(),
                                        strokeWidth = strokeWidth * density,
                                        isEraser = currentTool == WhiteboardTool.ERASER,
                                        isHighlighter = currentTool == WhiteboardTool.HIGHLIGHTER
                                    )
                                    paths.add(newPath)
                                    undonePaths.clear()
                                }
                                activePoints = emptyList()
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw saved paths
                    for (drawingPath in paths) {
                        drawPathPoints(drawingPath)
                    }

                    // Draw current active path
                    if (activePoints.size >= 2) {
                        val activePathData = DrawingPath(
                            points = activePoints,
                            colorHex = selectedColor.toArgb().toLong(),
                            strokeWidth = strokeWidth * density,
                            isEraser = currentTool == WhiteboardTool.ERASER,
                            isHighlighter = currentTool == WhiteboardTool.HIGHLIGHTER
                        )
                        drawPathPoints(activePathData)
                    }
                }
            }

            // Compact Floating Toolbar at Bottom
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tool Picker Row (Pen, Highlighter, Eraser)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolIconButton(
                            icon = Icons.Default.Create,
                            label = "Pen",
                            isSelected = currentTool == WhiteboardTool.PEN,
                            onClick = { currentTool = WhiteboardTool.PEN; strokeWidth = 6f }
                        )

                        ToolIconButton(
                            icon = Icons.Default.Highlight,
                            label = "Highlight",
                            isSelected = currentTool == WhiteboardTool.HIGHLIGHTER,
                            onClick = { currentTool = WhiteboardTool.HIGHLIGHTER; strokeWidth = 24f }
                        )

                        ToolIconButton(
                            icon = Icons.Default.FormatPaint,
                            label = "Eraser",
                            isSelected = currentTool == WhiteboardTool.ERASER,
                            onClick = { currentTool = WhiteboardTool.ERASER; strokeWidth = 32f }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Color Palette (Only when not using eraser)
                    if (currentTool != WhiteboardTool.ERASER) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (color in palette) {
                                val isSelected = selectedColor == color
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPathPoints(drawingPath: DrawingPath) {
    if (drawingPath.points.size < 2) return

    val composePath = Path().apply {
        moveTo(drawingPath.points.first().x, drawingPath.points.first().y)
        for (i in 1 until drawingPath.points.size) {
            lineTo(drawingPath.points[i].x, drawingPath.points[i].y)
        }
    }

    val drawColor = if (drawingPath.isEraser) {
        Color.White
    } else if (drawingPath.isHighlighter) {
        Color(drawingPath.colorHex.toInt()).copy(alpha = 0.4f)
    } else {
        Color(drawingPath.colorHex.toInt())
    }

    drawPath(
        path = composePath,
        color = drawColor,
        style = Stroke(
            width = drawingPath.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun renderToBitmap(paths: List<DrawingPath>, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(
        if (width <= 0) 1080 else width,
        if (height <= 0) 1440 else height,
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    for (drawingPath in paths) {
        if (drawingPath.points.size < 2) continue
        paint.strokeWidth = drawingPath.strokeWidth

        if (drawingPath.isEraser) {
            paint.color = android.graphics.Color.WHITE
        } else if (drawingPath.isHighlighter) {
            val argb = drawingPath.colorHex.toInt()
            paint.color = android.graphics.Color.argb(100, android.graphics.Color.red(argb), android.graphics.Color.green(argb), android.graphics.Color.blue(argb))
        } else {
            paint.color = drawingPath.colorHex.toInt()
        }

        for (i in 0 until drawingPath.points.size - 1) {
            val p1 = drawingPath.points[i]
            val p2 = drawingPath.points[i + 1]
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
    }
    return bitmap
}
