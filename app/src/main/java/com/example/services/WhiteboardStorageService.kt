package com.example.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.models.DrawingPath
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WhiteboardStorageService(private val context: Context) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, DrawingPath::class.java)
    private val jsonAdapter = moshi.adapter<List<DrawingPath>>(listType)

    fun serializePaths(paths: List<DrawingPath>): String {
        return jsonAdapter.toJson(paths)
    }

    fun deserializePaths(json: String?): List<DrawingPath> {
        if (json.isNull_or_blank()) return emptyList()
        return try {
            jsonAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    suspend fun saveBitmapToInternalStorage(bitmap: Bitmap, prefix: String = "whiteboard"): String = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_$timeStamp.png"
        val storageDir = File(context.filesDir, "note_images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageFile = File(storageDir, fileName)
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        imageFile.absolutePath
    }

    suspend fun copyUriToAppStorage(uri: Uri): String = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "scan_$timeStamp.jpg"
        val storageDir = File(context.filesDir, "note_images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val destinationFile = File(storageDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }
        destinationFile.absolutePath
    }

    suspend fun renderPathsToBitmap(paths: List<DrawingPath>, width: Int = 1080, height: Int = 1440): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        for (pathData in paths) {
            if (pathData.points.size < 2) continue

            paint.strokeWidth = pathData.strokeWidth
            if (pathData.isEraser) {
                paint.color = Color.WHITE
            } else if (pathData.isHighlighter) {
                // semi transparent color
                val color = pathData.colorHex.toInt()
                paint.color = Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))
            } else {
                paint.color = pathData.colorHex.toInt()
            }

            for (i in 0 until pathData.points.size - 1) {
                val p1 = pathData.points[i]
                val p2 = pathData.points[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }
        }

        bitmap
    }
}
