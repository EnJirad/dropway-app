package com.pocketdev.dropway.core.scanner

import android.graphics.Bitmap
import com.pocketdev.dropway.core.util.SmartTextParser
import com.pocketdev.dropway.core.util.ParsedParcelRow
import com.pocketdev.dropway.core.util.TrackingUtils

/**
 * Result from the on-device parcel label recognition pipeline.
 */
data class LabelRecognitionResult(
    val trackingNumber: String,
    val carrierName: String,
    val barcodeFormat: String?,
    val recipientName: String,
    val recipientPhone: String,
    val fullAddress: String,
    val houseNumber: String,
    val villageMoo: String,
    val subdistrict: String,
    val district: String,
    val province: String,
    val postalCode: String,
    val codAmountSatang: Long,
    val rawOcrText: String,
    val detectionSource: String, // "BARCODE_PRIMARY", "QR_STRUCTURED", "OCR_FALLBACK", "HYBRID"
    val confidenceScore: Int
)

/**
 * Technical specification and contract for the OpenThai 2.1 OCR Text Model
 * (iapp/OpenThai2.1-OCR-Text-20261118).
 */
object OpenThaiModelSpec {
    const val MODEL_ID = "iapp/OpenThai2.1-OCR-Text-20261118"
    const val DETECTOR_NAME = "PP-OCRv6_tiny_det (DBNet)"
    const val DETECTOR_INPUT_SHAPE = "[1, 3, 736, 736] (dynamic: min 32x32, max 4000x4000)"
    const val DETECTOR_SIZE_BYTES = 1780590L // 1.78 MB
    const val RECOGNIZER_NAME = "Thai CTC Line Recognizer (v2.1)"
    const val RECOGNIZER_INPUT_SHAPE = "[1, 3, 48, W] (dynamic width)"
    const val RECOGNIZER_SIZE_BYTES = 2392837L // 2.39 MB
    const val RECOGNIZER_LOOPLESS_SIZE_BYTES = 2392837L // 2.39 MB
    const val TOTAL_CHARSET_SIZE = 528
    const val ONNX_RUNTIME_ANDROID_AAR_SIZE_BYTES = 23970257L // 23.97 MB
    const val ESTIMATED_MOBILE_INFERENCE_MS = "450ms - 900ms per label on modern ARM64"
    const val ESTIMATED_MEMORY_FOOTPRINT_MB = "120MB - 180MB"
}

/**
 * Modular on-device label recognition pipeline:
 * Camera Capture -> Preprocessing -> Multi-Region Barcode/QR -> Normalization -> Field Extraction -> Address Parsing.
 * Strictly 100% offline with zero external network calls.
 */
object ParcelOcrEngine {

    /**
     * Executes the complete recognition pipeline on a captured or previewed Bitmap.
     */
    fun processLabel(bitmap: Bitmap): LabelRecognitionResult {
        // Step 1: Preprocessing & Contrast Evaluation
        val preprocessed = preprocessImage(bitmap)

        // Step 2: Multi-region Barcode & QR code decoding
        // Shipping labels often have both a 1D tracking barcode (center/bottom) and a 2D QR (top/side)
        val fullResult = BarcodeDecoder.decodeBitmap(bitmap) ?: BarcodeDecoder.decodeBitmap(preprocessed)

        // Crop regions if full decoding didn't find both tracking and text
        val topRegion = tryCrop(bitmap, 0f, 0f, 1f, 0.6f)
        val bottomRegion = tryCrop(bitmap, 0f, 0.4f, 1f, 0.6f)

        val topResult = topRegion?.let { BarcodeDecoder.decodeBitmap(it) }
        val bottomResult = bottomRegion?.let { BarcodeDecoder.decodeBitmap(it) }

        // Step 3: Combine decoded results from regions
        var tracking = ""
        var carrier = ""
        var recipientName = ""
        var recipientPhone = ""
        var address = ""
        var houseNum = ""
        var village = ""
        var subdist = ""
        var dist = ""
        var prov = ""
        var zip = ""
        var codSatang = 0L
        var rawText = ""
        var format: String? = null

        val resultsList = listOfNotNull(fullResult, topResult, bottomResult)

        for (res in resultsList) {
            if (tracking.isBlank() && res.normalizedTracking.isNotBlank()) {
                tracking = res.normalizedTracking
                carrier = res.carrierName
                format = res.format.name
            }
            if (recipientName.isBlank() && res.recipientName.isNotBlank()) {
                recipientName = res.recipientName
            }
            if (recipientPhone.isBlank() && res.recipientPhone.isNotBlank()) {
                recipientPhone = res.recipientPhone
            }
            if (address.isBlank() && res.address.isNotBlank()) {
                address = res.address
                houseNum = res.houseNumber
                village = res.village
            }
            if (codSatang == 0L && res.codAmountSatang > 0L) {
                codSatang = res.codAmountSatang
            }
            if (rawText.isBlank() && res.rawText.isNotBlank()) {
                rawText = res.rawText
            }
        }

        // Step 4: If address or name is still empty, parse raw text again with Thai normalization
        if (rawText.isNotBlank()) {
            val normalized = ThaiNormUtils.canonical(rawText)
            val parsed = SmartTextParser.parseFreeText(normalized)
            if (tracking.isBlank() && parsed.trackingNumber.isNotBlank()) tracking = parsed.trackingNumber
            if (carrier.isBlank() && parsed.carrier.isNotBlank()) carrier = parsed.carrier
            if (recipientName.isBlank() && parsed.recipientName.isNotBlank()) recipientName = parsed.recipientName
            if (recipientPhone.isBlank() && parsed.recipientPhone.isNotBlank()) recipientPhone = parsed.recipientPhone
            if (address.isBlank() && parsed.address.isNotBlank()) {
                address = parsed.address
                houseNum = parsed.houseNumber
                village = parsed.village
                subdist = parsed.subdistrict
                dist = parsed.district
                prov = parsed.province
                zip = parsed.postalCode
            }
            if (codSatang == 0L && parsed.codAmountSatang > 0L) codSatang = parsed.codAmountSatang
        }

        if (carrier.isBlank() && tracking.isNotBlank()) {
            carrier = BarcodeDecoder.detectCarrier(tracking)
        }

        val source = when {
            recipientName.isNotBlank() && tracking.isNotBlank() -> "HYBRID"
            recipientName.isNotBlank() -> "QR_STRUCTURED"
            tracking.isNotBlank() -> "BARCODE_PRIMARY"
            else -> "OCR_FALLBACK"
        }

        val confidence = when {
            tracking.isNotBlank() && recipientName.isNotBlank() -> 95
            tracking.isNotBlank() -> 90
            recipientName.isNotBlank() -> 80
            else -> 50
        }

        return LabelRecognitionResult(
            trackingNumber = tracking,
            carrierName = carrier.ifBlank { "พัสดุทั่วไป" },
            barcodeFormat = format,
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            fullAddress = address,
            houseNumber = houseNum,
            villageMoo = village,
            subdistrict = subdist,
            district = dist,
            province = prov,
            postalCode = zip,
            codAmountSatang = codSatang,
            rawOcrText = rawText,
            detectionSource = source,
            confidenceScore = confidence
        )
    }

    private fun tryCrop(source: Bitmap, relX: Float, relY: Float, relW: Float, relH: Float): Bitmap? {
        return try {
            val x = (source.width * relX).toInt().coerceIn(0, source.width - 1)
            val y = (source.height * relY).toInt().coerceIn(0, source.height - 1)
            val w = (source.width * relW).toInt().coerceIn(1, source.width - x)
            val h = (source.height * relH).toInt().coerceIn(1, source.height - y)
            Bitmap.createBitmap(source, x, y, w, h)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Preprocesses the bitmap with grayscale luminance conversion and contrast stretching.
     */
    fun preprocessImage(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var minLum = 255
        var maxLum = 0

        // Pass 1: compute luminance and bounds
        val lums = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            lums[i] = lum
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val range = (maxLum - minLum).coerceAtLeast(1)

        // Pass 2: stretch contrast and binarize
        for (i in pixels.indices) {
            val stretched = (((lums[i] - minLum) * 255) / range).coerceIn(0, 255)
            val argb = (0xFF shl 24) or (stretched shl 16) or (stretched shl 8) or stretched
            pixels[i] = argb
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
