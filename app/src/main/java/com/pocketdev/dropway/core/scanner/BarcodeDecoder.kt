package com.pocketdev.dropway.core.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.pocketdev.dropway.core.util.ParsedParcelRow
import com.pocketdev.dropway.core.util.SmartTextParser
import com.pocketdev.dropway.core.util.TrackingUtils
import java.util.EnumMap
import java.util.EnumSet

data class BarcodeResult(
    val rawText: String,
    val normalizedTracking: String,
    val format: BarcodeFormat,
    val carrierName: String,
    val confidence: Int = 100,
    val recipientName: String = "",
    val recipientPhone: String = "",
    val address: String = "",
    val houseNumber: String = "",
    val village: String = "",
    val codAmountSatang: Long = 0L,
    val parsedParcel: ParsedParcelRow? = null
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

    private val fastReader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, supportedFormats)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        setHints(hints)
    }

    /**
     * Fast decoder optimized for live camera preview frames.
     * Uses normal orientation (and 90 deg rotation fallback) without heavy TRY_HARDER.
     */
    fun decodeBitmapFast(bitmap: Bitmap): BarcodeResult? {
        synchronized(fastReader) {
            val result = decodeWithReader(bitmap, fastReader, tryInvert = false)
            if (result != null) return result

            val rot90 = rotateBitmap(bitmap, 90f)
            val result90 = decodeWithReader(rot90, fastReader, tryInvert = false)
            rot90.recycle()
            return result90
        }
    }

    /**
     * Decodes 1D barcode or 2D QR code from an Android Bitmap.
     * Attempts multi-orientation rotation (0, 90, 180, 270 degrees) and inverted luminance.
     */
    fun decodeBitmap(bitmap: Bitmap): BarcodeResult? {
        synchronized(reader) {
            // Try original orientation
            val result = decodeWithReader(bitmap, reader, tryInvert = true)
            if (result != null) return result

            // Try 90 degree clockwise rotation
            val rot90 = rotateBitmap(bitmap, 90f)
            val result90 = decodeWithReader(rot90, reader, tryInvert = true)
            rot90.recycle()
            if (result90 != null) return result90

            // Try 180 degree rotation
            val rot180 = rotateBitmap(bitmap, 180f)
            val result180 = decodeWithReader(rot180, reader, tryInvert = true)
            rot180.recycle()
            if (result180 != null) return result180

            // Try 270 degree rotation
            val rot270 = rotateBitmap(bitmap, 270f)
            val result270 = decodeWithReader(rot270, reader, tryInvert = true)
            rot270.recycle()
            if (result270 != null) return result270

            return null
        }
    }

    private fun decodeWithReader(bitmap: Bitmap, r: MultiFormatReader, tryInvert: Boolean): BarcodeResult? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binarizer = HybridBinarizer(source)
        val binaryBitmap = BinaryBitmap(binarizer)

        try {
            val zxingResult = r.decodeWithState(binaryBitmap)
            r.reset()
            return mapZxingResult(zxingResult)
        } catch (_: Exception) {
            r.reset()
        }

        if (tryInvert) {
            try {
                val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
                val zxingResult = r.decodeWithState(invertedBitmap)
                r.reset()
                return mapZxingResult(zxingResult)
            } catch (_: Exception) {
                r.reset()
            }
        }

        return null
    }

    private fun mapZxingResult(result: Result): BarcodeResult {
        val text = result.text.trim()
        val parsed = SmartTextParser.parseFreeText(text)

        val tracking = if (parsed.trackingNumber.isNotBlank()) {
            parsed.trackingNumber
        } else {
            TrackingUtils.normalize(text)
        }

        val carrier = if (parsed.carrier.isNotBlank()) {
            parsed.carrier
        } else {
            detectCarrier(tracking)
        }

        return BarcodeResult(
            rawText = text,
            normalizedTracking = tracking,
            format = result.barcodeFormat,
            carrierName = carrier,
            confidence = 100,
            recipientName = parsed.recipientName,
            recipientPhone = parsed.recipientPhone,
            address = parsed.address,
            houseNumber = parsed.houseNumber,
            village = parsed.village,
            codAmountSatang = parsed.codAmountSatang,
            parsedParcel = if (parsed.isValid) parsed else null
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
