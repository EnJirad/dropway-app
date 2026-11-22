package com.pocketdev.dropway.core.util

import com.pocketdev.dropway.core.model.Parcel

data class ParsedParcelRow(
    val trackingNumber: String,
    val recipientName: String,
    val recipientPhone: String,
    val address: String,
    val houseNumber: String = "",
    val village: String = "",
    val codAmountSatang: Long = 0L,
    val notes: String = "",
    val rawText: String = "",
    val isValid: Boolean = true,
    val validationError: String? = null
)

object SmartTextParser {

    private val phoneRegex = "(\\b0[689]\\d{8}\\b|\\b0[23457]\\d{7}\\b|0[689]\\d-\\d{3}-\\d{4}|0[689]\\d{2}-\\d{3}-\\d{3})".toRegex()
    private val codKeywordsRegex = "(?i)(?:COD|ยอด|เก็บปลายทาง|เก็บเงินปลายทาง|ปลายทาง|ยอดเก็บ|บาท|฿)\\s*[:=]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)".toRegex()
    private val trackingKeywordsRegex = "(?i)(?:Tracking|พัสดุ|เลขพัสดุ|TH|KER|KEX|SPX|FL|J&T|JT|COD)?\\s*([A-Za-z0-9]{9,30})".toRegex()
    private val postalCodeRegex = "\\b(\\d{5})\\b".toRegex()
    private val houseNumberRegex = "(?:บ้านเลขที่|เลขที่|ที่อยู่)?\\s*([0-9]+(?:\\/[0-9]+)?)".toRegex()
    private val villageRegex = "(?:หมู่|ม\\.?)\\s*([0-9]+)".toRegex()

    /**
     * Parses bulk input text (lines, TSV, CSV, or chat messages) into a list of parsed rows.
     */
    fun parseBulkText(input: String): List<ParsedParcelRow> {
        val lines = input.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<ParsedParcelRow>()

        // Check if input looks like TSV / CSV (multiple columns separated by \t or ,)
        val hasTabs = lines.any { it.contains("\t") }
        val hasCommas = lines.count { it.contains(",") } > lines.size / 2

        if (hasTabs || hasCommas) {
            val delimiter = if (hasTabs) "\t" else ","
            for (line in lines) {
                val row = parseDelimitedRow(line, delimiter)
                if (row != null) results.add(row)
            }
        } else {
            // Group multi-line messages or parse line-by-line
            var currentBlock = mutableListOf<String>()
            for (line in lines) {
                // If line contains a new tracking number or starts with typical item marker, it might be a new parcel
                if (TrackingUtils.isPossibleTrackingNumber(line) && currentBlock.isNotEmpty()) {
                    results.add(parseFreeText(currentBlock.joinToString("\n")))
                    currentBlock = mutableListOf(line)
                } else {
                    currentBlock.add(line)
                }
            }
            if (currentBlock.isNotEmpty()) {
                val joined = currentBlock.joinToString("\n")
                // Check if joined can be split into individual items if line count is high
                if (currentBlock.size > 1 && currentBlock.all { it.contains("0") && it.length > 15 }) {
                    for (l in currentBlock) {
                        results.add(parseFreeText(l))
                    }
                } else {
                    results.add(parseFreeText(joined))
                }
            }
        }

        return results
    }

    fun parseDelimitedRow(line: String, delimiter: String): ParsedParcelRow? {
        val parts = line.split(delimiter).map { it.trim().removeSurrounding("\"") }
        if (parts.size < 2) return null

        var tracking = ""
        var name = ""
        var phone = ""
        var address = ""
        var codSatang = 0L

        // Detect column roles dynamically
        for (part in parts) {
            if (part.isBlank()) continue
            if (tracking.isBlank() && TrackingUtils.isPossibleTrackingNumber(part)) {
                tracking = TrackingUtils.normalize(part)
            } else if (phone.isBlank() && phoneRegex.matches(part.replace("-", ""))) {
                phone = PhoneUtils.normalize(part)
            } else if (codSatang == 0L && part.all { it.isDigit() || it == '.' }) {
                codSatang = MoneyUtils.parseToSatang(part)
            } else if (name.isBlank() && part.length < 30 && !part.contains("ต.") && !part.contains("อ.")) {
                name = part
            } else {
                address = if (address.isBlank()) part else "$address $part"
            }
        }

        val houseNum = extractHouseNumber(address)
        val moo = extractVillage(address)

        val isValid = tracking.isNotBlank() || name.isNotBlank() || phone.isNotBlank()
        return ParsedParcelRow(
            trackingNumber = tracking,
            recipientName = name,
            recipientPhone = phone,
            address = address,
            houseNumber = houseNum,
            village = moo,
            codAmountSatang = codSatang,
            rawText = line,
            isValid = isValid,
            validationError = if (!isValid) "ไม่มีข้อมูลเพียงพอ" else null
        )
    }

    fun parseFreeText(text: String): ParsedParcelRow {
        // Extract phone
        val phoneMatch = phoneRegex.find(text)
        val rawPhone = phoneMatch?.value ?: ""
        val phone = PhoneUtils.normalize(rawPhone)

        // Extract COD
        var codSatang = 0L
        val codMatch = codKeywordsRegex.find(text)
        if (codMatch != null) {
            val amountStr = codMatch.groupValues[1]
            codSatang = MoneyUtils.parseToSatang(amountStr)
        }

        // Extract tracking
        var tracking = ""
        val words = text.split("\\s+".toRegex())
        for (w in words) {
            val clean = TrackingUtils.normalize(w)
            if (TrackingUtils.isPossibleTrackingNumber(clean) && !clean.all { it.isDigit() }) {
                tracking = clean
                break
            }
        }
        if (tracking.isBlank()) {
            for (w in words) {
                val clean = TrackingUtils.normalize(w)
                if (TrackingUtils.isPossibleTrackingNumber(clean) && clean != phone) {
                    tracking = clean
                    break
                }
            }
        }

        // Extract house number and village
        val houseNum = extractHouseNumber(text)
        val village = extractVillage(text)

        // Extract name and address
        var remaining = text
        if (rawPhone.isNotBlank()) remaining = remaining.replace(rawPhone, " ")
        if (tracking.isNotBlank()) remaining = remaining.replace(tracking, " ")
        if (codMatch != null) remaining = remaining.replace(codMatch.value, " ")

        val cleanTokens = remaining.lines().flatMap { it.split("\\s{2,}".toRegex()) }
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("COD", ignoreCase = true) }

        var recipientName = ""
        var address = ""

        if (cleanTokens.isNotEmpty()) {
            recipientName = cleanTokens.first()
            if (cleanTokens.size > 1) {
                address = cleanTokens.drop(1).joinToString(" ")
            }
        }

        if (address.isBlank() && cleanTokens.isNotEmpty()) {
            address = cleanTokens.joinToString(" ")
        }

        val isValid = tracking.isNotBlank() || recipientName.isNotBlank() || address.isNotBlank()

        return ParsedParcelRow(
            trackingNumber = tracking,
            recipientName = recipientName,
            recipientPhone = phone,
            address = address,
            houseNumber = houseNum,
            village = village,
            codAmountSatang = codSatang,
            rawText = text,
            isValid = isValid,
            validationError = if (!isValid) "ข้อมูลไม่สมบูรณ์" else null
        )
    }

    fun extractHouseNumber(text: String): String {
        val match = houseNumberRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractVillage(text: String): String {
        val match = villageRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }
}
