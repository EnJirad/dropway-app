package com.pocketdev.dropway.core.scanner

import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.SmartTextParser
import com.pocketdev.dropway.core.util.TrackingUtils

object StructuredFieldExtractor {

    private val phoneRegex = "(\\b0[689]\\d{8}\\b|\\b0[23457]\\d{7}\\b|0[689]\\d-\\d{3}-\\d{4}|0[689]\\d{2}-\\d{3}-\\d{3}|0[689]\\d\\s+\\d{3}\\s+\\d{4})".toRegex()
    private val codAmountStrictRegex = "(?i)(?:COD|ยอด(?:เงิน)?(?:เก็บ)?(?:ปลายทาง)?|เก็บเงินปลายทาง|เก็บปลายทาง)\\s*[:=]?\\s*(?:฿|THB)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:บาท|฿|\\.-)?".toRegex()
    private val sortingCodeRegex = "(?:\\b[1-9]\\s+[A-Z]{3}-\\d{2}\\b|\\b[A-Z]{2,4}-[A-Z0-9]{2,4}\\b|\\bHUB-[A-Z0-9-]+\\b|\\bSPX-[A-Z0-9-]+\\b|\\bBDC\\s+[A-Za-z0-9]+\\b)".toRegex()
    private val orderIdRegex = "(?i)(?:Order\\s*(?:ID|No|SN)?|คำสั่งซื้อ|เลขที่คำสั่งซื้อ)\\s*[:=]?\\s*([A-Za-z0-9_-]{10,28})".toRegex()
    private val platformRegex = "(?i)(TikTok(?:\\s*Shop)?|Shopee(?:\\s*Express)?|Lazada(?:\\s*Express)?|Line\\s*Shopping|Facebook)".toRegex()

    /**
     * Merges all evidence sources (1D barcodes, 2D QR payloads, OCR text blocks, layout profiles)
     * into a unified, deterministic LabelRecognitionResult.
     */
    fun extract(
        barcodes: List<DecodedBarcodeItem>,
        ocrResult: LabelOcrEngine.OcrEngineResult,
        capturedRawText: String = ""
    ): LabelRecognitionResult {
        val ocrText = ocrResult.fullText.ifBlank { capturedRawText }
        val canonicalOcr = ThaiNormUtils.canonical(ocrText)

        val sources = mutableMapOf<String, String>()
        val confidences = mutableMapOf<String, Int>()
        val conflicts = mutableListOf<FieldConflict>()
        val warnings = mutableListOf<String>()
        val missing = mutableListOf<String>()

        // 1. Barcode & QR code separation
        val oneDBarcodes = barcodes.filter { it.is1D }
        val qrCodes = barcodes.filter { it.is2D }

        val primary1D = oneDBarcodes.firstOrNull { TrackingUtils.isPossibleTrackingNumber(it.normalizedTracking) }
            ?: oneDBarcodes.firstOrNull()

        val primaryQr = qrCodes.firstOrNull()

        // 2. Carrier Identification
        var carrier = ""
        var carrierConf = 50

        if (primary1D != null && primary1D.carrierName != "พัสดุทั่วไป") {
            carrier = primary1D.carrierName
            carrierConf = 95
            sources["carrier"] = "1D_BARCODE"
        } else if (primaryQr != null && primaryQr.carrierName != "พัสดุทั่วไป") {
            carrier = primaryQr.carrierName
            carrierConf = 90
            sources["carrier"] = "QR_CODE"
        }

        if (carrier.isBlank()) {
            val detectedFromOcr = BarcodeDecoder.detectCarrier(canonicalOcr)
            if (detectedFromOcr != "พัสดุทั่วไป") {
                carrier = detectedFromOcr
                carrierConf = 85
                sources["carrier"] = "OCR_TEXT"
            }
        }

        if (carrier.isBlank()) {
            carrier = "พัสดุทั่วไป"
            carrierConf = 40
            sources["carrier"] = "DEFAULT"
        }
        confidences["carrier"] = carrierConf

        // Carrier Profile hint
        val profile = LabelTemplateProfiles.matchProfile(carrier, primary1D?.normalizedTracking ?: "", canonicalOcr)

        // 3. Tracking Number Extraction & Conflict Detection
        var tracking = ""
        var trackingConf = 0
        var trackingSource = "UNKNOWN"

        val trackingFrom1D = primary1D?.normalizedTracking?.trim() ?: ""
        val trackingFromQr = primaryQr?.normalizedTracking?.trim() ?: ""
        val parsedFromOcr = SmartTextParser.parseFreeText(canonicalOcr)
        val trackingFromOcr = parsedFromOcr.trackingNumber.trim()

        if (trackingFrom1D.isNotBlank()) {
            tracking = trackingFrom1D
            trackingConf = 98
            trackingSource = "1D_BARCODE"
            sources["tracking"] = "1D_BARCODE"

            // Check conflict with OCR if OCR has a strong different tracking candidate
            if (trackingFromOcr.isNotBlank() && trackingFromOcr != trackingFrom1D && trackingFromOcr.length >= 8) {
                conflicts.add(
                    FieldConflict(
                        fieldName = "trackingNumber",
                        fieldLabel = "เลขพัสดุ",
                        valueA = trackingFrom1D,
                        sourceA = "บาร์โค้ด 1D",
                        valueB = trackingFromOcr,
                        sourceB = "ข้อความ OCR",
                        resolvedValue = trackingFrom1D
                    )
                )
                warnings.add("เลขพัสดุจากบาร์โค้ด ($trackingFrom1D) ไม่ตรงกับตัวหนังสือ OCR ($trackingFromOcr)")
            }
        } else if (trackingFromQr.isNotBlank()) {
            tracking = trackingFromQr
            trackingConf = 90
            trackingSource = "QR_CODE"
            sources["tracking"] = "QR_CODE"
        } else if (trackingFromOcr.isNotBlank()) {
            tracking = trackingFromOcr
            trackingConf = 80
            trackingSource = "OCR_TEXT"
            sources["tracking"] = "OCR_TEXT"
        } else {
            missing.add("trackingNumber")
        }
        confidences["tracking"] = trackingConf

        // 4. Order ID & Platform
        var orderId = ""
        var orderConf = 0
        var platform = ""

        profile.orderIdRegex?.find(canonicalOcr)?.let { match ->
            val id = match.groupValues.getOrNull(1)?.trim() ?: ""
            if (id.isNotBlank()) {
                orderId = id
                orderConf = 90
                sources["orderId"] = "OCR_TEXT"
            }
        }
        if (orderId.isBlank()) {
            orderIdRegex.find(canonicalOcr)?.let { match ->
                orderId = match.groupValues.getOrNull(1)?.trim() ?: ""
                orderConf = 85
                sources["orderId"] = "OCR_TEXT"
            }
        }
        confidences["orderId"] = orderConf

        platformRegex.find(canonicalOcr)?.let { match ->
            platform = match.value.trim()
            sources["sellerPlatform"] = "OCR_TEXT"
        } ?: run {
            if (profile == LabelTemplateProfiles.JNT_TIKTOK) platform = "TikTok Shop"
            else if (profile == LabelTemplateProfiles.SPX_EXPRESS) platform = "Shopee"
        }

        // 5. Sorting Code
        var sortingCode = ""
        var sortConf = 0
        profile.sortingCodeRegex?.find(canonicalOcr)?.let { match ->
            sortingCode = match.value.trim()
            sortConf = 90
            sources["sortingCode"] = "OCR_TEXT"
        } ?: run {
            sortingCodeRegex.find(canonicalOcr)?.let { match ->
                sortingCode = match.value.trim()
                sortConf = 80
                sources["sortingCode"] = "OCR_TEXT"
            }
        }
        confidences["sortingCode"] = sortConf

        // 6. Recipient Details Extraction
        var recipientName = ""
        var nameConf = 0
        var phone = ""
        var phoneConf = 0
        var address = ""
        var addrConf = 0

        // Tier A: Check QR payload (some carriers encode structured name/phone/address in QR)
        val qrParsed = if (primaryQr != null) SmartTextParser.parseFreeText(primaryQr.rawText) else null
        if (qrParsed != null && qrParsed.recipientPhone.isNotBlank()) {
            phone = qrParsed.recipientPhone
            phoneConf = 92
            sources["phone"] = "QR_CODE"
        }
        if (qrParsed != null && qrParsed.recipientName.isNotBlank()) {
            recipientName = qrParsed.recipientName
            nameConf = 90
            sources["recipientName"] = "QR_CODE"
        }
        if (qrParsed != null && qrParsed.address.isNotBlank()) {
            address = qrParsed.address
            addrConf = 90
            sources["address"] = "QR_CODE"
        }

        // Tier B: Extract from OCR text
        if (phone.isBlank()) {
            phoneRegex.find(canonicalOcr)?.let { match ->
                phone = PhoneUtils.normalize(match.value)
                phoneConf = 94
                sources["phone"] = "OCR_TEXT"
            }
        }

        if (recipientName.isBlank()) {
            if (parsedFromOcr.recipientName.isNotBlank()) {
                recipientName = parsedFromOcr.recipientName
                nameConf = 88
                sources["recipientName"] = "OCR_TEXT"
            } else {
                // Heuristic: line after "ผู้รับ:" or "To:"
                val lines = canonicalOcr.lines().map { it.trim() }.filter { it.isNotBlank() }
                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.contains("ผู้รับ") || line.contains("To:") || line.contains("Recipient")) {
                        val inline = line.replace("ผู้รับ", "").replace("To:", "").replace("[:=]".toRegex(), "").trim()
                        if (inline.length in 2..35 && !inline.contains("โทร") && !inline.contains("0")) {
                            recipientName = inline
                            nameConf = 85
                            sources["recipientName"] = "OCR_TEXT"
                            break
                        } else if (i + 1 < lines.size) {
                            val next = lines[i + 1]
                            if (next.length in 2..35 && !next.contains("โทร") && !next.contains("0")) {
                                recipientName = next
                                nameConf = 80
                                sources["recipientName"] = "OCR_TEXT"
                                break
                            }
                        }
                    }
                }
            }
        }

        if (address.isBlank()) {
            if (parsedFromOcr.address.isNotBlank()) {
                address = parsedFromOcr.address
                addrConf = 86
                sources["address"] = "OCR_TEXT"
            }
        }

        if (recipientName.isBlank()) missing.add("recipientName")
        if (phone.isBlank()) missing.add("recipientPhone")
        if (address.isBlank()) missing.add("fullAddress")

        confidences["recipientName"] = nameConf
        confidences["phone"] = phoneConf
        confidences["address"] = addrConf

        val houseNum = SmartTextParser.extractHouseNumber(address).ifBlank { parsedFromOcr.houseNumber }
        val moo = SmartTextParser.extractVillage(address).ifBlank { parsedFromOcr.village }
        val subdist = SmartTextParser.extractSubdistrict(address).ifBlank { parsedFromOcr.subdistrict }
        val dist = SmartTextParser.extractDistrict(address).ifBlank { parsedFromOcr.district }
        val prov = SmartTextParser.extractProvince(address).ifBlank { parsedFromOcr.province }
        val zip = SmartTextParser.extractPostalCode(address).ifBlank { parsedFromOcr.postalCode }

        // 7. Payment & COD Extraction (Strict verification)
        var codSatang = 0L
        var codConf = 0
        var isCod = false

        // Check QR code first
        if (qrParsed != null && qrParsed.codAmountSatang > 0L) {
            codSatang = qrParsed.codAmountSatang
            codConf = 95
            isCod = true
            sources["codAmount"] = "QR_CODE"
        }

        if (codSatang == 0L) {
            // Strict regex search with explicit COD keyword
            codAmountStrictRegex.find(canonicalOcr)?.let { match ->
                val amtStr = match.groupValues.getOrNull(1)?.replace(",", "") ?: ""
                val parsedSatang = MoneyUtils.parseToSatang(amtStr)
                if (parsedSatang > 0L) {
                    codSatang = parsedSatang
                    codConf = 92
                    isCod = true
                    sources["codAmount"] = "OCR_TEXT"
                }
            }
        }

        confidences["codAmount"] = codConf

        // 8. Overall Confidence Score calculation
        val score = (
            (trackingConf * 0.35f) +
            (nameConf * 0.20f) +
            (phoneConf * 0.15f) +
            (addrConf * 0.15f) +
            (carrierConf * 0.15f)
        ).toInt().coerceIn(10, 100)

        val finalSource = when {
            trackingSource == "1D_BARCODE" && (nameConf > 70 || addrConf > 70) -> "HYBRID"
            trackingSource == "1D_BARCODE" -> "BARCODE_PRIMARY"
            trackingSource == "QR_CODE" -> "QR_STRUCTURED"
            else -> "OCR_FALLBACK"
        }

        return LabelRecognitionResult(
            carrierName = carrier,
            carrierConfidence = carrierConf,
            trackingNumber = tracking,
            trackingConfidence = trackingConf,
            trackingSource = trackingSource,
            barcodeValue = primary1D?.rawText ?: "",
            barcodeFormat = primary1D?.format?.name,
            qrPayload = primaryQr?.rawText ?: "",
            allBarcodes = barcodes,
            orderId = orderId,
            sellerPlatform = platform,
            sortingCode = sortingCode,
            deliveryType = if (isCod) "COD" else "Standard Delivery",
            recipientName = recipientName,
            recipientNameConfidence = nameConf,
            recipientPhone = phone,
            recipientPhoneConfidence = phoneConf,
            fullAddress = address,
            addressConfidence = addrConf,
            houseNumber = houseNum,
            villageMoo = moo,
            subdistrict = subdist,
            district = dist,
            province = prov,
            postalCode = zip,
            codAmountSatang = codSatang,
            codConfidence = codConf,
            currency = "THB",
            isCod = isCod,
            rawOcrText = canonicalOcr,
            textBlocks = ocrResult.textBlocks,
            fieldSources = sources,
            fieldConfidences = confidences,
            conflicts = conflicts,
            warnings = warnings,
            missingFields = missing,
            detectionSource = finalSource,
            confidenceScore = score
        )
    }
}
