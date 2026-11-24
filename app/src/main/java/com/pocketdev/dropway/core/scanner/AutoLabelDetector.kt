package com.pocketdev.dropway.core.scanner

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

enum class DetectionStatus(val message: String) {
    IDLE("เตรียมพร้อม"),
    SEARCHING("วางใบปะหน้าให้อยู่ในกรอบ"),
    HOLDING("ถือค้างไว้อีกนิด..."),
    CAPTURING("กำลังถ่ายและอ่านข้อมูล..."),
    STEP_BACK("ถอยกล้องออกเล็กน้อย (จ่อใกล้เกินไป)")
}

data class FrameAnalysis(
    val gray: ByteArray,
    val mean: Double,
    val std: Double,
    val whiteRatio: Double,
    val edgeRatio: Double,
    val sharpness: Double
)

/**
 * Intelligent on-device label detector matching the Dropway web scanner.
 * Evaluates contrast, white paper ratio, edge density, and stability to auto-trigger capture.
 */
object AutoLabelDetector {

    const val AUTO_HOLD_MS = 850L
    const val AUTO_STILL_MS = 450L

    /**
     * High-speed raw Y-plane frame analyzer without any Bitmap allocations.
     * Evaluates contrast, white paper ratio, edge density, and sharpness.
     */
    fun analyzeYPlane(
        yData: ByteArray,
        width: Int,
        height: Int,
        cropRatio: Float = 0.70f
    ): FrameAnalysis? {
        if (width < 8 || height < 8) return null

        val cropW = (width * cropRatio).toInt().coerceAtLeast(8)
        val cropH = (height * cropRatio).toInt().coerceAtLeast(8)
        val cropX = ((width - cropW) / 2).coerceAtLeast(0)
        val cropY = ((height - cropH) / 2).coerceAtLeast(0)

        val targetSamples = 120
        val stepX = (cropW / targetSamples).coerceAtLeast(1)
        val stepY = (cropH / targetSamples).coerceAtLeast(1)

        val sampleW = (cropW + stepX - 1) / stepX
        val sampleH = (cropH + stepY - 1) / stepY
        val total = sampleW * sampleH
        if (total == 0) return null

        val gray = ByteArray(total)
        var sum = 0L
        var white = 0
        var sampleIdx = 0

        for (sy in 0 until sampleH) {
            val y = cropY + sy * stepY
            val rowOffset = y * width
            for (sx in 0 until sampleW) {
                val x = cropX + sx * stepX
                val v = yData[rowOffset + x].toInt() and 0xFF
                gray[sampleIdx++] = v.toByte()
                sum += v
                if (v > 170) white++
            }
        }

        val mean = sum.toDouble() / total

        var variance = 0.0
        for (i in 0 until total) {
            val v = gray[i].toInt() and 0xFF
            val d = v - mean
            variance += d * d
        }
        val std = sqrt(variance / total)

        var edges = 0
        var compared = 0
        var gradientSum = 0L

        for (sy in 1 until sampleH - 1) {
            for (sx in 1 until sampleW - 1) {
                val i = sy * sampleW + sx
                val v = gray[i].toInt() and 0xFF
                val vRight = gray[i + 1].toInt() and 0xFF
                val vDown = gray[i + sampleW].toInt() and 0xFF
                val gx = abs(v - vRight)
                val gy = abs(v - vDown)
                val gTotal = gx + gy
                gradientSum += gTotal
                if (gTotal > 40) edges++
                compared++
            }
        }

        return FrameAnalysis(
            gray = gray,
            mean = mean,
            std = std,
            whiteRatio = white.toDouble() / total,
            edgeRatio = if (compared > 0) edges.toDouble() / compared else 0.0,
            sharpness = if (compared > 0) gradientSum.toDouble() / compared else 0.0
        )
    }

    fun analyzeBitmap(bitmap: Bitmap): FrameAnalysis? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 8 || height < 8) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = ByteArray(width * height)
        var sum = 0L
        var white = 0

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val v = ((r * 306 + g * 601 + b * 117) shr 10).coerceIn(0, 255)
            gray[i] = v.toByte()
            sum += v
            if (v > 170) white++
        }

        val total = width * height
        val mean = sum.toDouble() / total

        var variance = 0.0
        for (i in gray.indices) {
            val v = gray[i].toInt() and 0xFF
            val d = v - mean
            variance += d * d
        }
        val std = sqrt(variance / total)

        var edges = 0
        var compared = 0
        var gradientSum = 0L

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val v = gray[i].toInt() and 0xFF
                val vRight = gray[i + 1].toInt() and 0xFF
                val vDown = gray[i + width].toInt() and 0xFF
                val gx = abs(v - vRight)
                val gy = abs(v - vDown)
                val gTotal = gx + gy
                gradientSum += gTotal
                if (gTotal > 40) edges++
                compared++
            }
        }

        return FrameAnalysis(
            gray = gray,
            mean = mean,
            std = std,
            whiteRatio = white.toDouble() / total,
            edgeRatio = if (compared > 0) edges.toDouble() / compared else 0.0,
            sharpness = if (compared > 0) gradientSum.toDouble() / compared else 0.0
        )
    }

    fun looksLikeLabel(a: FrameAnalysis): Boolean {
        return a.mean > 95 &&
                a.std > 20 &&
                a.whiteRatio > 0.14 &&
                a.whiteRatio < 0.92 &&
                a.edgeRatio > 0.02 &&
                a.edgeRatio < 0.42 &&
                a.sharpness > 6.0
    }

    fun looksTooClose(a: FrameAnalysis): Boolean {
        return a.mean > 168 && a.whiteRatio > 0.9
    }

    fun frameDifference(a: ByteArray, b: ByteArray): Double {
        val len = kotlin.math.min(a.size, b.size)
        if (len == 0) return 255.0
        var sum = 0L
        var count = 0
        var i = 0
        while (i < len) {
            val v1 = a[i].toInt() and 0xFF
            val v2 = b[i].toInt() and 0xFF
            sum += kotlin.math.abs(v1 - v2)
            count++
            i += 3
        }
        return if (count > 0) sum.toDouble() / count else 255.0
    }
}
