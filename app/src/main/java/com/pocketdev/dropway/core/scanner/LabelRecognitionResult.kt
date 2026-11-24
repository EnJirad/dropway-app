package com.pocketdev.dropway.core.scanner

import android.graphics.Rect
import com.google.zxing.BarcodeFormat

data class DecodedBarcodeItem(
    val rawText: String,
    val format: BarcodeFormat,
    val normalizedTracking: String,
    val carrierName: String,
    val boundingBox: Rect? = null,
    val is2D: Boolean = (format == BarcodeFormat.QR_CODE || format == BarcodeFormat.DATA_MATRIX || format == BarcodeFormat.AZTEC || format == BarcodeFormat.PDF_417),
    val is1D: Boolean = !is2D
)

data class OcrChar(
    val char: Char,
    val boundingBox: Rect,
    val confidence: Float
)

data class OcrWord(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float,
    val characters: List<OcrChar> = emptyList()
)

data class OcrLine(
    val text: String,
    val boundingBox: Rect,
    val words: List<OcrWord> = emptyList(),
    val orientationDegrees: Int = 0
)

data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float,
    val lines: List<OcrLine> = emptyList(),
    val blockType: String = "BODY" // "HEADER", "CARRIER", "TRACKING", "RECIPIENT", "ADDRESS", "COD", "SORTING"
)

data class FieldConflict(
    val fieldName: String,
    val fieldLabel: String,
    val valueA: String,
    val sourceA: String,
    val valueB: String,
    val sourceB: String,
    val resolvedValue: String = valueA
)

/**
 * Complete, production-grade shipping label recognition result.
 * Aggregates 1D barcodes, 2D QR codes, on-device OCR, and structured fields.
 */
data class LabelRecognitionResult(
    // Shipment Information
    val carrierName: String = "พัสดุทั่วไป",
    val carrierConfidence: Int = 50,
    val trackingNumber: String = "",
    val trackingConfidence: Int = 0,
    val trackingSource: String = "UNKNOWN", // "1D_BARCODE", "QR_CODE", "OCR_TEXT", "HYBRID"
    val barcodeValue: String = "",
    val barcodeFormat: String? = null,
    val qrPayload: String = "",
    val allBarcodes: List<DecodedBarcodeItem> = emptyList(),
    val orderId: String = "",
    val sellerPlatform: String = "",
    val sortingCode: String = "",
    val deliveryType: String = "Standard Delivery",

    // Recipient Information
    val recipientName: String = "",
    val recipientNameConfidence: Int = 0,
    val recipientPhone: String = "",
    val recipientPhoneConfidence: Int = 0,
    val fullAddress: String = "",
    val addressConfidence: Int = 0,
    val houseNumber: String = "",
    val villageMoo: String = "",
    val subdistrict: String = "",
    val district: String = "",
    val province: String = "",
    val postalCode: String = "",

    // Payment Information
    val codAmountSatang: Long = 0L,
    val codConfidence: Int = 0,
    val currency: String = "THB",
    val isCod: Boolean = (codAmountSatang > 0L),

    // Other Fields
    val productDescription: String = "",
    val quantity: Int = 1,
    val shippingDate: String = "",
    val pickupNotes: String = "",

    // OCR & Audit Metadata
    val rawOcrText: String = "",
    val textBlocks: List<OcrTextBlock> = emptyList(),
    val fieldSources: Map<String, String> = emptyMap(),
    val fieldConfidences: Map<String, Int> = emptyMap(),
    val conflicts: List<FieldConflict> = emptyList(),
    val warnings: List<String> = emptyList(),
    val missingFields: List<String> = emptyList(),
    val detectionSource: String = "HYBRID",
    val confidenceScore: Int = 0
) {
    fun codAmountBaht(): Double = codAmountSatang / 100.0

    fun hasCriticalConflicts(): Boolean = conflicts.isNotEmpty()

    fun isReadyForSaving(): Boolean = trackingNumber.isNotBlank() && (recipientName.isNotBlank() || fullAddress.isNotBlank())
}
