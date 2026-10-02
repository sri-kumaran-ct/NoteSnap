package com.example

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.utils.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanCoordinateMappingTest {

    @Test
    fun testTallScreenPreviewToBitmapCoordinateMapping() {
        // Preview View: 1080x2400 (aspect ratio = 0.45)
        // Captured Upright Bitmap: 3000x4000 (aspect ratio = 0.75)
        // Scale in FILL_CENTER: max(1080/3000, 2400/4000) = max(0.36, 0.60) = 0.60
        // Rendered size: 1800x2400. Offsets: dx = (1080 - 1800)/2 = -360, dy = 0
        val viewWidth = 1080
        val viewHeight = 2400
        val bitmapWidth = 3000
        val bitmapHeight = 4000

        // Visible Scan frame on screen with padding: left=100, top=300, right=980, bottom=2100
        val frameRect = RectF(100f, 300f, 980f, 2100f)

        val cropRect = ImageUtils.calculateScanFrameCropRect(
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            frameRect = frameRect
        )

        // Expected left = (100 - (-360)) / 0.60 = 460 / 0.60 = 766.67 -> 767
        // Expected right = (980 - (-360)) / 0.60 = 1340 / 0.60 = 2233.33 -> 2233
        // Expected top = (300 - 0) / 0.60 = 500
        // Expected bottom = (2100 - 0) / 0.60 = 3500
        assertEquals(767, cropRect.left)
        assertEquals(500, cropRect.top)
        assertEquals(2233, cropRect.right)
        assertEquals(3500, cropRect.bottom)

        // Content outside the frame:
        // Top browser tabs at screen Y=100 (in bitmap Y = 100/0.60 = 167 < 500) -> OUTSIDE cropRect
        assertTrue(167 < cropRect.top)
        // Bottom controls at screen Y=2250 (in bitmap Y = 2250/0.60 = 3750 > 3500) -> OUTSIDE cropRect
        assertTrue(3750 > cropRect.bottom)
    }

    @Test
    fun testLandscapePreviewToBitmapCoordinateMapping() {
        // Preview View: 2400x1080 (aspect ratio = 2.22)
        // Captured Upright Bitmap: 4000x3000 (aspect ratio = 1.33)
        // Scale in FILL_CENTER: max(2400/4000, 1080/3000) = max(0.60, 0.36) = 0.60
        // Rendered size: 2400x1800. Offsets: dx = 0, dy = (1080 - 1800)/2 = -360
        val viewWidth = 2400
        val viewHeight = 1080
        val bitmapWidth = 4000
        val bitmapHeight = 3000

        val frameRect = RectF(200f, 100f, 2200f, 980f)

        val cropRect = ImageUtils.calculateScanFrameCropRect(
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            frameRect = frameRect
        )

        // Expected left = (200 - 0) / 0.60 = 333.33 -> 333
        // Expected right = (2200 - 0) / 0.60 = 3666.67 -> 3667
        // Expected top = (100 - (-360)) / 0.60 = 460 / 0.60 = 766.67 -> 767
        // Expected bottom = (980 - (-360)) / 0.60 = 1340 / 0.60 = 2233.33 -> 2233
        assertEquals(333, cropRect.left)
        assertEquals(767, cropRect.top)
        assertEquals(3667, cropRect.right)
        assertEquals(2233, cropRect.bottom)
    }

    @Test
    fun testEdgePreservationNoArbitraryCrop() {
        val viewWidth = 1000
        val viewHeight = 2000
        val bitmapWidth = 1000
        val bitmapHeight = 2000

        // Frame placed with 50px margin
        val frameRect = RectF(50f, 100f, 950f, 1900f)

        val cropRect = ImageUtils.calculateScanFrameCropRect(
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            frameRect = frameRect
        )

        // Exact match with no artificial 8% or 10% insets
        assertEquals(50, cropRect.left)
        assertEquals(100, cropRect.top)
        assertEquals(950, cropRect.right)
        assertEquals(1900, cropRect.bottom)

        val width = cropRect.width()
        val height = cropRect.height()
        assertEquals(900, width)
        assertEquals(1800, height)
    }

    @Test
    fun testCropBitmapProducesCorrectBitmap() {
        val bitmap = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888)
        val frameRect = RectF(100f, 200f, 900f, 1800f)

        val cropped = ImageUtils.cropBitmapToScanFrame(
            bitmap = bitmap,
            viewWidth = 1000,
            viewHeight = 2000,
            frameRect = frameRect
        )

        assertNotNull(cropped)
        assertEquals(800, cropped.width)
        assertEquals(1600, cropped.height)
    }
}
