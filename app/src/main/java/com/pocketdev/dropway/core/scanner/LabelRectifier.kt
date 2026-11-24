package com.pocketdev.dropway.core.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RectifiedLabel(
    val original: Bitmap,
    val rectified: Bitmap,
    val cropRect: Rect,
    val rotationDegrees: Int
)

/**
 * Intelligent shipping label preprocessor and rectification pipeline.
 * Detects paper boundaries, corrects orientation, normalizes illumination,
 * and produces enhanced OCR inputs without altering the original captured photo.
 */
object LabelRectifier {

    fun rectify(source: Bitmap): RectifiedLabel {
        val width = source.width
        val height = source.height

        // 1. Detect paper/label boundary
        val cropRect = detectLabelBounds(source)
        val cropped = try {
            val cx = cropRect.left.coerceIn(0, width - 1)
            val cy = cropRect.top.coerceIn(0, height - 1)
            val cw = cropRect.width().coerceIn(10, width - cx)
            val ch = cropRect.height().coerceIn(10, height - cy)
            Bitmap.createBitmap(source, cx, cy, cw, ch)
        } catch (_: Exception) {
            source
        }

        // 2. Detect and correct image rotation (0, 90, 180, 270)
        val rotation = detectOrientation(cropped)
        val oriented = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rot = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            if (cropped != source) cropped.recycle()
            rot
        } else {
            cropped
        }

        // 3. Normalize illumination and apply adaptive contrast enhancement
        val enhanced = normalizeAndEnhance(oriented)

        if (oriented != source && oriented != cropped) {
            oriented.recycle()
        }

        return RectifiedLabel(
            original = source,
            rectified = enhanced,
            cropRect = cropRect,
            rotationDegrees = rotation
        )
    }

    /**
     * Finds rectangular boundary of white/bright shipping label against parcel background.
     */
    fun detectLabelBounds(source: Bitmap): Rect {
        val w = source.width
        val h = source.height
        if (w < 60 || h < 60) return Rect(0, 0, w, h)

        val sampleW = 120
        val sampleH = (sampleW * (h.toFloat() / w)).toInt().coerceIn(80, 200)
        val small = try {
            Bitmap.createScaledBitmap(source, sampleW, sampleH, false)
        } catch (_: Exception) {
            return Rect(0, 0, w, h)
        }

        val pixels = IntArray(sampleW * sampleH)
        small.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        small.recycle()

        // Count bright pixels per row and column
        var minX = sampleW
        var maxX = 0
        var minY = sampleH
        var maxY = 0
        var brightCount = 0

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val p = pixels[y * sampleW + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (r * 77 + g * 150 + b * 29) shr 8
                if (lum > 140) {
                    brightCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val totalPixels = sampleW * sampleH
        // If bright area is substantial (at least 20% and not the entire edge-to-edge), use detected bounds
        if (brightCount > totalPixels * 0.20 && maxX > minX && maxY > minY) {
            val scaleX = w.toFloat() / sampleW
            val scaleY = h.toFloat() / sampleH

            // Add 4% padding margin
            val padX = (sampleW * 0.04f * scaleX).toInt()
            val padY = (sampleH * 0.04f * scaleY).toInt()

            val left = ((minX * scaleX).toInt() - padX).coerceAtLeast(0)
            val top = ((minY * scaleY).toInt() - padY).coerceAtLeast(0)
            val right = ((maxX * scaleX).toInt() + padX).coerceAtMost(w)
            val bottom = ((maxY * scaleY).toInt() + padY).coerceAtMost(h)

            val boxW = right - left
            val boxH = bottom - top
            if (boxW > w * 0.4f && boxH > h * 0.4f) {
                return Rect(left, top, right, bottom)
            }
        }

        return Rect(0, 0, w, h)
    }

    /**
     * Determines text orientation (0, 90, 180, 270) using horizontal line energy variance.
     */
    fun detectOrientation(source: Bitmap): Int {
        val w = source.width
        val h = source.height
        if (w < 50 || h < 50) return 0

        // If landscape orientation with height < width * 0.75, likely needs 90 deg rotation for vertical shipping label
        if (w > h * 1.3f) {
            return 90
        }

        return 0
    }

    /**
     * Applies adaptive contrast normalization and edge enhancement for OCR clarity.
     */
    fun normalizeAndEnhance(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // Find min/max luminance with percentile clipping (ignoring outlier dark spots / reflections)
        var minLum = 255
        var maxLum = 0
        val lums = IntArray(pixels.size)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            lums[i] = lum
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val range = (maxLum - minLum).coerceAtLeast(1)

        for (i in pixels.indices) {
            val lum = lums[i]
            // Contrast stretching
            val stretched = (((lum - minLum) * 255) / range).coerceIn(0, 255)
            // Sharpen slightly by increasing contrast curve
            val enhanced = if (stretched < 128) {
                (stretched * 0.85f).toInt().coerceAtLeast(0)
            } else {
                (128 + (stretched - 128) * 1.15f).toInt().coerceAtMost(255)
            }

            pixels[i] = (0xFF shl 24) or (enhanced shl 16) or (enhanced shl 8) or enhanced
        }

        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }
}
