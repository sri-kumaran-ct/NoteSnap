package com.example.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.utils.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import kotlin.coroutines.resume

class OcrService(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractTextFromUri(uri: Uri): Result<String> {
        return try {
            // Read EXIF rotation and decode upright bitmap
            val uprightBitmap = loadUprightBitmapFromUri(context, uri)
            if (uprightBitmap != null) {
                val image = InputImage.fromBitmap(uprightBitmap, 0)
                processInputImage(image)
            } else {
                val image = InputImage.fromFilePath(context, uri)
                processInputImage(image)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractTextFromBitmap(bitmap: Bitmap): Result<String> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            processInputImage(image)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun processInputImage(image: InputImage): Result<String> =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Sort text blocks by vertical Y-coordinate (top) to maintain natural top-to-bottom reading order
                    val sortedBlocks = visionText.textBlocks.sortedWith(
                        compareBy<com.google.mlkit.vision.text.Text.TextBlock> { it.boundingBox?.top ?: 0 }
                            .thenBy { it.boundingBox?.left ?: 0 }
                    )

                    val orderedText = if (sortedBlocks.isNotEmpty()) {
                        sortedBlocks.mapNotNull { block ->
                            val sortedLines = block.lines.sortedWith(
                                compareBy<com.google.mlkit.vision.text.Text.Line> { it.boundingBox?.top ?: 0 }
                                    .thenBy { it.boundingBox?.left ?: 0 }
                            )
                            val blockText = sortedLines.joinToString("\n") { it.text }.trim()
                            blockText.ifBlank { null }
                        }.joinToString("\n\n")
                    } else {
                        visionText.text
                    }

                    if (orderedText.isNotBlank()) {
                        // Apply privacy filter to strip document drive links, page numbers, and headers
                        val cleanedText = ImageUtils.cleanOcrTextForPrivacy(orderedText)
                        continuation.resume(Result.success(cleanedText))
                    } else {
                        continuation.resume(Result.success("No text detected in image. You can manually enter or edit text here."))
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        }

    private fun loadUprightBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val rotationDegrees = if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                inputStream.close()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } else 0f

            val streamForBitmap = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(streamForBitmap)
            streamForBitmap.close()

            if (bitmap != null && rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}
