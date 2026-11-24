package com.pocketdev.dropway.core.scanner

import android.graphics.Bitmap

/**
 * Technical specification and contract for the OpenThai 2.1 OCR Text Model
 * (iapp/OpenThai2.1-OCR-Text-20261118).
 * Retained for model contract specification and offline runtime auditing.
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
 * Production-grade On-Device Shipping Label Recognition Pipeline.
 * Orchestrates:
 * 1. LabelRectifier: Paper boundary detection, orientation deskew, illumination normalization.
 * 2. BarcodeDecoder: Multi-region 1D barcode & 2D QR extraction with deduplication.
 * 3. LabelOcrEngine: Offline character recognition, line grouping, and gazetteer scoring.
 * 4. StructuredFieldExtractor: Multi-source evidence fusion with per-field confidence & conflict detection.
 * 100% offline-first with zero external network dependencies.
 */
object ParcelOcrEngine {

    fun processLabel(bitmap: Bitmap): LabelRecognitionResult {
        // Step 1: Label boundary detection & image rectification
        val rectified = LabelRectifier.rectify(bitmap)

        // Step 2: Multi-source Barcode & QR decoding across rectified image
        val barcodes = BarcodeDecoder.decodeAllBarcodes(rectified.rectified).ifEmpty {
            if (rectified.rectified != bitmap) BarcodeDecoder.decodeAllBarcodes(bitmap) else emptyList()
        }

        // Step 3: Full on-device text OCR recognition
        val ocrResult = LabelOcrEngine.recognizeSync(rectified.rectified)

        // Clean up temporary rectified bitmap if distinct from original
        if (rectified.rectified != bitmap) {
            rectified.rectified.recycle()
        }

        // Step 4: Multi-source data fusion & structured field extraction
        return StructuredFieldExtractor.extract(
            barcodes = barcodes,
            ocrResult = ocrResult
        )
    }
}
