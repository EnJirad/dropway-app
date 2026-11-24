package com.pocketdev.dropway.core.scanner

import java.util.regex.Pattern

enum class CarrierProfileType {
    JNT_EXPRESS_TIKTOK,
    FLASH_EXPRESS,
    SPX_EXPRESS,
    THAILAND_POST,
    GENERIC
}

data class CarrierProfile(
    val type: CarrierProfileType,
    val displayName: String,
    val trackingRegex: Regex,
    val orderIdRegex: Regex?,
    val sortingCodeRegex: Regex?,
    val codPatternRegex: Regex,
    val expectedBranding: List<String>
)

object LabelTemplateProfiles {

    val JNT_TIKTOK = CarrierProfile(
        type = CarrierProfileType.JNT_EXPRESS_TIKTOK,
        displayName = "J&T Express / TikTok Shop",
        trackingRegex = "^(8\\d{11}|6\\d{11}|JNT\\w{9,15}|\\d{12})$".toRegex(),
        orderIdRegex = "(?i)(?:Order\\s*(?:ID|No|SN)?|คำสั่งซื้อ)\\s*[:=]?\\s*(TT\\w+|\\d{12,20})".toRegex(),
        sortingCodeRegex = "(?:\\b[1-9]\\s+[A-Z]{3}-\\d{2}\\b|[A-Z]{3}-\\d{2}|HUB-[A-Z0-9]+)".toRegex(),
        codPatternRegex = "(?i)(?:COD|ยอดเก็บปลายทาง|เก็บปลายทาง)\\s*[:=]?\\s*(?:฿|THB)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex(),
        expectedBranding = listOf("J&T", "J&T Express", "TikTok", "TikTok Shop", "JNT")
    )

    val FLASH_EXPRESS = CarrierProfile(
        type = CarrierProfileType.FLASH_EXPRESS,
        displayName = "Flash Express",
        trackingRegex = "^(TH\\w{10,18}|FL\\w{10,16})$".toRegex(),
        orderIdRegex = "(?i)(?:Order|SO|SN)\\s*[:=]?\\s*([A-Za-z0-9_-]{10,25})".toRegex(),
        sortingCodeRegex = "(?:\\b[A-Z0-9]{1,3}\\s+[A-Z0-9]{2,4}\\b|\\bBDC\\s+[A-Za-z0-9]+\\b)".toRegex(),
        codPatternRegex = "(?i)(?:COD|ยอดเก็บเงิน|ยอด COD)\\s*[:=]?\\s*(?:฿|THB)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex(),
        expectedBranding = listOf("Flash", "Flash Express", "แฟลช", "FL")
    )

    val SPX_EXPRESS = CarrierProfile(
        type = CarrierProfileType.SPX_EXPRESS,
        displayName = "SPX Express",
        trackingRegex = "^(SPXTH\\w{8,16}|TH\\d{10,14}|SHP\\w{8,14})$".toRegex(),
        orderIdRegex = "(?i)(?:Order\\s*SN|Order\\s*ID|เลขที่คำสั่งซื้อ)\\s*[:=]?\\s*([0-9A-Z]{12,25})".toRegex(),
        sortingCodeRegex = "(?:\\bSPX-[A-Z0-9-]+\\b|\\b[A-Z0-9]{2,4}-[A-Z0-9]{2,4}\\b)".toRegex(),
        codPatternRegex = "(?i)(?:COD|ยอดเก็บ)\\s*[:=]?\\s*(?:฿|THB)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex(),
        expectedBranding = listOf("SPX", "SPX Express", "Shopee", "Shopee Express")
    )

    val THAILAND_POST = CarrierProfile(
        type = CarrierProfileType.THAILAND_POST,
        displayName = "ไปรษณีย์ไทย (Thailand Post)",
        trackingRegex = "^([A-Z]{2}\\d{9}TH)$".toRegex(),
        orderIdRegex = null,
        sortingCodeRegex = "(?:\\b[1-9]\\d{4}\\b)".toRegex(),
        codPatternRegex = "(?i)(?:COD|ธนาณัติ|ยอดเก็บ)\\s*[:=]?\\s*(?:฿|THB)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex(),
        expectedBranding = listOf("ไปรษณีย์ไทย", "Thailand Post", "EMS", "ePacket")
    )

    val GENERIC = CarrierProfile(
        type = CarrierProfileType.GENERIC,
        displayName = "พัสดุทั่วไป",
        trackingRegex = "^([A-Z0-9]{8,24})$".toRegex(),
        orderIdRegex = "(?i)(?:Order|คำสั่งซื้อ)\\s*[:=]?\\s*([A-Za-z0-9_-]{8,24})".toRegex(),
        sortingCodeRegex = "(?:\\b[A-Z0-9]{2,4}-[A-Z0-9]{2,4}\\b)".toRegex(),
        codPatternRegex = "(?i)(?:COD|ยอดเก็บปลายทาง|เก็บปลายทาง)\\s*[:=]?\\s*(?:฿|THB)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex(),
        expectedBranding = emptyList()
    )

    val allProfiles = listOf(JNT_TIKTOK, FLASH_EXPRESS, SPX_EXPRESS, THAILAND_POST, GENERIC)

    /**
     * Identifies the best candidate profile based on carrier branding, tracking patterns, or OCR content.
     */
    fun matchProfile(carrierName: String, trackingNumber: String, rawText: String): CarrierProfile {
        val upperText = rawText.uppercase()
        val upperCarrier = carrierName.uppercase()
        val upperTrack = trackingNumber.uppercase()

        // 1. Check branding
        if (upperCarrier.contains("J&T") || upperText.contains("J&T") || upperText.contains("TIKTOK")) {
            return JNT_TIKTOK
        }
        if (upperCarrier.contains("FLASH") || upperText.contains("FLASH") || upperTrack.startsWith("TH")) {
            return FLASH_EXPRESS
        }
        if (upperCarrier.contains("SPX") || upperText.contains("SPX") || upperText.contains("SHOPEE") || upperTrack.startsWith("SPX")) {
            return SPX_EXPRESS
        }
        if (upperCarrier.contains("ไปรษณีย์") || upperText.contains("THAILAND POST") || upperTrack.endsWith("TH") && upperTrack.length == 13) {
            return THAILAND_POST
        }

        // 2. Check tracking regex
        for (p in allProfiles) {
            if (p.trackingRegex.matches(trackingNumber)) {
                return p
            }
        }

        return GENERIC
    }
}
