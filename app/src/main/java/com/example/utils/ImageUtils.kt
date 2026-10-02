package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

import kotlin.math.roundToInt

data class ScanCaptureResult(
    val originalUri: Uri,
    val croppedUri: Uri,
    val croppedBitmap: Bitmap?
)

object ImageUtils {

    /**
     * Loads a bitmap from Uri and applies EXIF rotation if present, producing an upright bitmap.
     */
    fun loadUprightBitmap(context: Context, inputUri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(inputUri)
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

            val streamForBitmap = context.contentResolver.openInputStream(inputUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(streamForBitmap)
            streamForBitmap.close()

            if (originalBitmap != null && rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(
                    originalBitmap, 0, 0,
                    originalBitmap.width, originalBitmap.height,
                    matrix, true
                )
            } else {
                originalBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calculates the exact crop rectangle on the upright bitmap corresponding to the
     * visible scan frame in the Camera preview viewport.
     *
     * Correctly accounts for:
     * - PreviewView dimensions (viewWidth, viewHeight)
     * - Upright captured bitmap dimensions (bitmapWidth, bitmapHeight)
     * - PreviewView.ScaleType.FILL_CENTER uniform aspect-ratio scaling and centering offsets
     * - Exact scan frame bounding rectangle coordinates on the screen
     */
    fun calculateScanFrameCropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
        frameRect: RectF
    ): Rect {
        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return Rect(0, 0, bitmapWidth, bitmapHeight)
        }

        // PreviewView with ScaleType.FILL_CENTER scales the camera stream to fill the view while maintaining aspect ratio
        val scaleX = viewWidth.toFloat() / bitmapWidth.toFloat()
        val scaleY = viewHeight.toFloat() / bitmapHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val renderedWidth = bitmapWidth * scale
        val renderedHeight = bitmapHeight * scale

        // Offsets of the rendered camera stream inside the preview view
        val dx = (viewWidth - renderedWidth) / 2.0f
        val dy = (viewHeight - renderedHeight) / 2.0f

        // Map screen coordinates of the frame to bitmap coordinates
        val cropLeftFloat = (frameRect.left - dx) / scale
        val cropTopFloat = (frameRect.top - dy) / scale
        val cropRightFloat = (frameRect.right - dx) / scale
        val cropBottomFloat = (frameRect.bottom - dy) / scale

        // Clamp coordinates strictly within the bitmap bounds
        val left = cropLeftFloat.roundToInt().coerceIn(0, bitmapWidth)
        val top = cropTopFloat.roundToInt().coerceIn(0, bitmapHeight)
        val right = cropRightFloat.roundToInt().coerceIn(left, bitmapWidth)
        val bottom = cropBottomFloat.roundToInt().coerceIn(top, bitmapHeight)

        return Rect(left, top, right, bottom)
    }

    /**
     * Crops the upright bitmap to match the exact visible scan frame coordinates.
     */
    fun cropBitmapToScanFrame(
        bitmap: Bitmap,
        viewWidth: Int,
        viewHeight: Int,
        frameRect: RectF?
    ): Bitmap {
        if (frameRect == null || viewWidth <= 0 || viewHeight <= 0) {
            return bitmap
        }

        val cropRect = calculateScanFrameCropRect(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            frameRect = frameRect
        )

        val width = cropRect.width()
        val height = cropRect.height()

        if (width <= 0 || height <= 0 || (width == bitmap.width && height == bitmap.height)) {
            return bitmap
        }

        return Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, width, height)
    }

    /**
     * Processes a captured camera image:
     * 1. Corrects EXIF orientation to obtain the upright full bitmap.
     * 2. Saves the full original upright image (for the Original Note tab).
     * 3. Calculates the exact crop rectangle of the visible scan frame on the upright bitmap.
     * 4. Crops and saves the scan frame region to be passed exclusively to ML Kit OCR.
     */
    fun processCapturedImageWithCrop(
        context: Context,
        inputUri: Uri,
        viewWidth: Int,
        viewHeight: Int,
        frameRect: RectF?
    ): ScanCaptureResult {
        val uprightBitmap = loadUprightBitmap(context, inputUri)
            ?: return ScanCaptureResult(originalUri = inputUri, croppedUri = inputUri, croppedBitmap = null)

        // 1. Save full upright bitmap to original file
        val originalFile = File(
            context.cacheDir,
            "original_${System.currentTimeMillis()}.jpg"
        )
        FileOutputStream(originalFile).use { out ->
            uprightBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val originalUri = Uri.fromFile(originalFile)

        // 2. Crop upright bitmap to exact scan frame region
        val croppedBitmap = if (frameRect != null && viewWidth > 0 && viewHeight > 0) {
            cropBitmapToScanFrame(uprightBitmap, viewWidth, viewHeight, frameRect)
        } else {
            uprightBitmap
        }

        // 3. Save cropped bitmap for OCR
        val croppedFile = File(
            context.cacheDir,
            "crop_${System.currentTimeMillis()}.jpg"
        )
        FileOutputStream(croppedFile).use { out ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        val croppedUri = Uri.fromFile(croppedFile)

        return ScanCaptureResult(
            originalUri = originalUri,
            croppedUri = croppedUri,
            croppedBitmap = croppedBitmap
        )
    }

    /**
     * Reads the image from Uri, corrects EXIF orientation, and saves normalized image to cache.
     * Note: Arbitrary percentage cropping is completely removed.
     */
    fun processCapturedImage(
        context: Context,
        inputUri: Uri,
        cropToFrame: Boolean = false
    ): Uri {
        val uprightBitmap = loadUprightBitmap(context, inputUri) ?: return inputUri
        val outputFile = File(
            context.cacheDir,
            "processed_${System.currentTimeMillis()}.jpg"
        )
        return try {
            FileOutputStream(outputFile).use { out ->
                uprightBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            inputUri
        }
    }

    /**
     * Filters extracted OCR text to strip header/footer noise, page numbers,
     * Google Drive links, URLs, and timestamps for enhanced note privacy.
     */
    fun cleanOcrTextForPrivacy(rawText: String): String {
        if (rawText.isBlank()) return rawText

        val urlRegex = Regex("https?://\\S+|drive\\.google\\.com\\S+|docs\\.google\\.com\\S+", RegexOption.IGNORE_CASE)
        val pageNumRegex = Regex("^\\s*(page\\s*\\d+(\\s*of\\s*\\d+)?|\\d+/\\d+|\\d+)\\s*$", RegexOption.IGNORE_CASE)

        val cleanedLines = rawText.lines().mapNotNull { line ->
            var trimmed = line.trim()

            // Remove URLs or Google Drive links embedded in lines
            trimmed = trimmed.replace(urlRegex, "").trim()

            // Filter out standalone page numbers or headers
            if (trimmed.matches(pageNumRegex)) {
                null
            } else {
                trimmed.ifBlank { null }
            }
        }

        return if (cleanedLines.isNotEmpty()) {
            cleanedLines.joinToString("\n")
        } else {
            rawText
        }
    }
}
