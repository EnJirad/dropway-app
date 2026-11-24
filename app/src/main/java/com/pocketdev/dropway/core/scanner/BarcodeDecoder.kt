package com.pocketdev.dropway.core.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.pocketdev.dropway.core.util.TrackingUtils
import java.util.EnumMap
import java.util.EnumSet

data class BarcodeResult(
    val rawText: String,
    val normalizedTracking: String,
    val format: BarcodeFormat,
    val carrierName: String,
    val confidence: Int = 100
)

object BarcodeDecoder {

    private val supportedFormats = EnumSet.of(
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.ITF,
        BarcodeFormat.CODABAR,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417
    )

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, supportedFormats)
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        setHints(hints)
    }

    /**
     * Decodes 1D barcode or 2D QR code from an Android Bitmap.
     * Attempts multi-orientation rotation (0, 90, 180, 270 degrees) and inverted luminance.
     */
    fun decodeBitmap(bitmap: Bitmap): BarcodeResult? {
        // Try original orientation
        val result = decodeSingleBitmap(bitmap)
        if (result != null) return result

        // Try 90 degree clockwise rotation
        val rot90 = rotateBitmap(bitmap, 90f)
        val result90 = decodeSingleBitmap(rot90)
        if (result90 != null) return result90

        // Try 180 degree rotation
        val rot180 = rotateBitmap(bitmap, 180f)
        val result180 = decodeSingleBitmap(rot180)
        if (result180 != null) return result180

        // Try 270 degree rotation
        val rot270 = rotateBitmap(bitmap, 270f)
        val result270 = decodeSingleBitmap(rot270)
        if (result270 != null) return result270

        return null
    }

    private fun decodeSingleBitmap(bitmap: Bitmap): BarcodeResult? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binarizer = HybridBinarizer(source)
        val binaryBitmap = BinaryBitmap(binarizer)

        try {
            val zxingResult = reader.decodeWithState(binaryBitmap)
            reader.reset()
            return mapZxingResult(zxingResult)
        } catch (_: Exception) {
            reader.reset()
        }

        // Try inverted luminance (white barcode on dark background)
        try {
            val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
            val zxingResult = reader.decodeWithState(invertedBitmap)
            reader.reset()
            return mapZxingResult(zxingResult)
        } catch (_: Exception) {
            reader.reset()
        }

        return null
    }

    private fun mapZxingResult(result: Result): BarcodeResult {
        val text = result.text.trim()
        val normalized = TrackingUtils.normalize(text)
        val carrier = detectCarrier(normalized)
        return BarcodeResult(
            rawText = text,
            normalizedTracking = normalized,
            format = result.barcodeFormat,
            carrierName = carrier,
            confidence = 100
        )
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Identifies the Thai logistics carrier from the tracking number pattern.
     */
    fun detectCarrier(tracking: String): String {
        val clean = TrackingUtils.normalize(tracking)
        return when {
            clean.startsWith("TH") && clean.length in 12..18 -> "Flash Express"
            clean.startsWith("FL") -> "Flash Express"
            clean.startsWith("SPX") -> "SPX Express"
            clean.startsWith("KER") || clean.startsWith("KEX") || clean.startsWith("SHP") || clean.startsWith("SDOF") -> "Kerry Express (KEX)"
            clean.startsWith("JT") || clean.startsWith("J&T") -> "J&T Express"
            (clean.startsWith("8") || clean.startsWith("6")) && clean.length == 12 && clean.all { it.isDigit() } -> "J&T Express"
            clean.endsWith("TH") && clean.length == 13 && clean.substring(2, 11).all { it.isDigit() } -> "ไปรษณีย์ไทย (Thailand Post)"
            clean.startsWith("ED") || clean.startsWith("EF") || clean.startsWith("EG") || clean.startsWith("RC") || clean.startsWith("OB") -> "ไปรษณีย์ไทย (Thailand Post)"
            clean.startsWith("LEX") -> "Lazada Express (LEX)"
            clean.startsWith("BEST") || clean.startsWith("22") && clean.length == 14 -> "Best Express"
            clean.startsWith("NIM") -> "Nim Express"
            clean.startsWith("NJV") -> "Ninja Van"
            else -> "พัสดุทั่วไป"
        }
    }
}
