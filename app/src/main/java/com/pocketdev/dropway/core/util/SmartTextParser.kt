package com.pocketdev.dropway.core.util

import com.pocketdev.dropway.core.scanner.BarcodeDecoder
import com.pocketdev.dropway.core.scanner.ThaiNormUtils

data class ParsedParcelRow(
    val trackingNumber: String,
    val recipientName: String,
    val recipientPhone: String,
    val address: String,
    val houseNumber: String = "",
    val village: String = "",
    val subdistrict: String = "",
    val district: String = "",
    val province: String = "",
    val postalCode: String = "",
    val carrier: String = "",
    val codAmountSatang: Long = 0L,
    val notes: String = "",
    val rawText: String = "",
    val isValid: Boolean = true,
    val validationError: String? = null
)

object SmartTextParser {

    private val phoneRegex = "(\\b0[689]\\d{8}\\b|\\b0[23457]\\d{7}\\b|0[689]\\d-\\d{3}-\\d{4}|0[689]\\d{2}-\\d{3}-\\d{3}|0[689]\\d\\s+\\d{3}\\s+\\d{4})".toRegex()
    private val codKeywordsRegex = "(?i)(?:COD|ยอด(?:เงิน)?(?:เก็บ)?(?:ปลายทาง)?|เก็บเงินปลายทาง|เก็บปลายทาง|ยอดเก็บ|ยอดสุทธิ|ปลายทาง)\\s*[:=]?\\s*(?:฿|THB)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:บาท|฿|\\.-)?".toRegex()
    private val codStandaloneAmountRegex = "(?:฿|THB)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:บาท|\\.-)?".toRegex()
    private val postalCodeRegex = "(?<![0-9/])\\b([1-9]\\d{4})\\b(?![0-9/])".toRegex()
    private val houseNumberRegex = "(?:บ้านเลขที่|เลขที่|ที่อยู่)?\\s*([0-9]+(?:\\/[0-9]+)?)".toRegex()
    private val villageRegex = "(?:หมู่ที่|หมู่|ม\\.?)\\s*([0-9]+)".toRegex()

    private val subdistrictRegex = "(?:ตำบล|ต\\.|แขวง)\\s*([ก-๙a-zA-Z]+)".toRegex()
    private val districtRegex = "(?:อำเภอ|อ\\.|เขต)\\s*([ก-๙a-zA-Z]+)".toRegex()
    private val provinceRegex = "(?:จังหวัด|จ\\.)\\s*([ก-๙a-zA-Z]+)|(?:กรุงเทพ(?:มหานคร)?|กทม\\.?)".toRegex()

    private val recipientHeaderRegex = "(?i)(?:ผู้รับ|ชื่อผู้รับ|ชื่อ|คุณ|ส่งถึง|To|Recipient|Consignee)\\s*[:=]?\\s*([^\\n,:]+?)(?=(?:\\s*(?:โทร|เบอร์|Tel|ที่อยู่|บ้านเลขที่|COD|\\b0[689]))|$)".toRegex()
    private val senderBlockRegex = "(?i)(?:ผู้ส่ง|จาก|Sender|From)\\s*[:=]?[\\s\\S]*?(?=(?:ผู้รับ|ส่งถึง|To|Recipient|$))".toRegex()

    private val carrierKeywords = listOf(
        "Flash Express" to "Flash Express",
        "Flash" to "Flash Express",
        "Kerry Express" to "Kerry Express (KEX)",
        "Kerry" to "Kerry Express (KEX)",
        "KEX" to "Kerry Express (KEX)",
        "SPX Express" to "SPX Express",
        "Shopee Express" to "SPX Express",
        "SPX" to "SPX Express",
        "J&T Express" to "J&T Express",
        "J&T" to "J&T Express",
        "ไปรษณีย์ไทย" to "ไปรษณีย์ไทย (Thailand Post)",
        "Thailand Post" to "ไปรษณีย์ไทย (Thailand Post)",
        "EMS" to "ไปรษณีย์ไทย (Thailand Post)",
        "Lazada Express" to "Lazada Express (LEX)",
        "LEX" to "Lazada Express (LEX)",
        "Best Express" to "Best Express",
        "Nim Express" to "Nim Express",
        "Ninja Van" to "Ninja Van"
    )

    /**
     * Parses bulk input text (lines, TSV, CSV, or chat messages) into a list of parsed rows.
     */
    fun parseBulkText(input: String): List<ParsedParcelRow> {
        val canonicalInput = ThaiNormUtils.canonical(input)
        val lines = canonicalInput.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<ParsedParcelRow>()

        val hasTabs = lines.any { it.contains("\t") }
        val hasCommas = lines.count { it.contains(",") } > lines.size / 2

        if (hasTabs || hasCommas) {
            val delimiter = if (hasTabs) "\t" else ","
            for (line in lines) {
                val row = parseDelimitedRow(line, delimiter)
                if (row != null) results.add(row)
            }
        } else {
            var currentBlock = mutableListOf<String>()
            for (line in lines) {
                if (TrackingUtils.isPossibleTrackingNumber(line) && currentBlock.isNotEmpty()) {
                    results.add(parseFreeText(currentBlock.joinToString("\n")))
                    currentBlock = mutableListOf(line)
                } else {
                    currentBlock.add(line)
                }
            }
            if (currentBlock.isNotEmpty()) {
                val joined = currentBlock.joinToString("\n")
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

        for (part in parts) {
            if (part.isBlank()) continue
            if (tracking.isBlank() && TrackingUtils.isPossibleTrackingNumber(part)) {
                tracking = TrackingUtils.normalize(part)
            } else if (phone.isBlank() && phoneRegex.matches(part.replace("-", "").replace(" ", ""))) {
                phone = PhoneUtils.normalize(part)
            } else if (codSatang == 0L && (part.all { it.isDigit() || it == '.' || it == ',' })) {
                codSatang = MoneyUtils.parseToSatang(part.replace(",", ""))
            } else if (name.isBlank() && part.length < 35 && !part.contains("ต.") && !part.contains("อ.")) {
                name = cleanRecipientName(part)
            } else {
                address = if (address.isBlank()) part else "$address $part"
            }
        }

        val houseNum = extractHouseNumber(address)
        val moo = extractVillage(address)
        val subdist = extractSubdistrict(address)
        val dist = extractDistrict(address)
        val prov = extractProvince(address)
        val zip = extractPostalCode(address)
        val carrier = if (tracking.isNotBlank()) BarcodeDecoder.detectCarrier(tracking) else ""

        val isValid = tracking.isNotBlank() || name.isNotBlank() || phone.isNotBlank()
        return ParsedParcelRow(
            trackingNumber = tracking,
            recipientName = name,
            recipientPhone = phone,
            address = address,
            houseNumber = houseNum,
            village = moo,
            subdistrict = subdist,
            district = dist,
            province = prov,
            postalCode = zip,
            carrier = carrier,
            codAmountSatang = codSatang,
            rawText = line,
            isValid = isValid,
            validationError = if (!isValid) "ไม่มีข้อมูลเพียงพอ" else null
        )
    }

    fun parseFreeText(text: String): ParsedParcelRow {
        val normalizedText = ThaiNormUtils.canonical(text)

        // 1. Detect Carrier
        var detectedCarrier = ""
        for ((keyword, carrierName) in carrierKeywords) {
            if (normalizedText.contains(keyword, ignoreCase = true)) {
                detectedCarrier = carrierName
                break
            }
        }

        // 2. Isolate Recipient section if Sender block exists
        var workingText = normalizedText
        val senderMatch = senderBlockRegex.find(workingText)
        if (senderMatch != null && workingText.contains("ผู้รับ")) {
            val splitIdx = workingText.indexOf("ผู้รับ")
            if (splitIdx >= 0) {
                workingText = workingText.substring(splitIdx)
            }
        }

        // 3. Extract Phone
        val phoneMatch = phoneRegex.find(workingText)
        val rawPhone = phoneMatch?.value ?: ""
        val phone = PhoneUtils.normalize(rawPhone)

        // 4. Extract COD Amount
        var codSatang = 0L
        val codMatch = codKeywordsRegex.find(workingText) ?: codStandaloneAmountRegex.find(workingText)
        if (codMatch != null) {
            val amountStr = codMatch.groupValues[1].replace(",", "").trim()
            codSatang = MoneyUtils.parseToSatang(amountStr)
        }

        // 5. Extract Tracking Number
        var tracking = ""
        val trackingLabelRegex = "(?i)(?:Tracking|พัสดุ|เลขพัสดุ|เลขแทรค|Waybill|Tracking No)\\s*[:=]?\\s*([A-Za-z0-9]{8,35})".toRegex()
        val explicitTrackMatch = trackingLabelRegex.find(workingText)
        if (explicitTrackMatch != null) {
            val candidate = TrackingUtils.normalize(explicitTrackMatch.groupValues[1])
            if (candidate != phone && candidate.length >= 8) {
                tracking = candidate
            }
        }

        if (tracking.isBlank()) {
            val words = workingText.split("\\s+".toRegex())
            for (w in words) {
                val clean = TrackingUtils.normalize(w)
                if (TrackingUtils.isPossibleTrackingNumber(clean) && !clean.all { it.isDigit() } && clean != phone) {
                    tracking = clean
                    break
                }
            }
        }

        if (tracking.isBlank()) {
            val words = workingText.split("\\s+".toRegex())
            for (w in words) {
                val clean = TrackingUtils.normalize(w)
                if (TrackingUtils.isPossibleTrackingNumber(clean) && clean != phone && clean.length >= 9) {
                    tracking = clean
                    break
                }
            }
        }

        if (detectedCarrier.isBlank() && tracking.isNotBlank()) {
            detectedCarrier = BarcodeDecoder.detectCarrier(tracking)
        }

        // 6. Clean tracking, phone, and COD from working text to prevent them interfering with address and name
        var addressAndNameText = workingText
        if (tracking.isNotBlank()) addressAndNameText = addressAndNameText.replace(tracking, " ")
        if (rawPhone.isNotBlank()) addressAndNameText = addressAndNameText.replace(rawPhone, " ")
        if (codMatch != null) addressAndNameText = addressAndNameText.replace(codMatch.value, " ")

        // Remove carrier keywords from text
        for ((keyword, _) in carrierKeywords) {
            addressAndNameText = addressAndNameText.replace(keyword, " ")
        }
        addressAndNameText = addressAndNameText.replace("(?i)(พัสดุ|เลขพัสดุ|Tracking No|Waybill|เก็บเงินปลายทาง|ยอดเก็บ)\\s*[:=]?".toRegex(), " ")

        // 7. Extract Address Elements from cleaned text
        val houseNum = extractHouseNumber(addressAndNameText)
        val village = extractVillage(addressAndNameText)
        val subdist = extractSubdistrict(addressAndNameText)
        val dist = extractDistrict(addressAndNameText)
        val prov = extractProvince(addressAndNameText)
        val zip = extractPostalCode(addressAndNameText)

        // 8. Extract Recipient Name
        var recipientName = ""
        val recipientHeaderMatch = recipientHeaderRegex.find(workingText)
        if (recipientHeaderMatch != null) {
            val rawName = recipientHeaderMatch.groupValues[1].lines().firstOrNull()?.trim() ?: ""
            recipientName = cleanRecipientName(rawName)
        }

        // 9. Build Clean Address and Fallback Name
        var finalAddress = addressAndNameText
        if (recipientName.isNotBlank()) {
            finalAddress = finalAddress.replace(recipientName, " ")
        }

        val cleanedTokens = addressAndNameText.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
        if (recipientName.isBlank() && cleanedTokens.isNotEmpty()) {
            val nameTokens = mutableListOf<String>()
            val addrTokens = mutableListOf<String>()
            var isAddress = false

            for (token in cleanedTokens) {
                if (!isAddress && (token.firstOrNull()?.isDigit() == true ||
                            token.startsWith("หมู่") || token.startsWith("ม.") ||
                            token.startsWith("ต.") || token.startsWith("ตำบล") ||
                            token.startsWith("อ.") || token.startsWith("อำเภอ") ||
                            token.startsWith("จ.") || token.startsWith("จังหวัด") ||
                            token.startsWith("บ้าน") || token.startsWith("แขวง") ||
                            token.startsWith("เขต"))) {
                    isAddress = true
                }
                if (isAddress) {
                    addrTokens.add(token)
                } else {
                    nameTokens.add(token)
                }
            }

            if (nameTokens.isNotEmpty()) {
                recipientName = cleanRecipientName(nameTokens.joinToString(" "))
            }
            if (addrTokens.isNotEmpty()) {
                finalAddress = addrTokens.joinToString(" ")
            }
        }

        finalAddress = finalAddress.replace("(?i)(ที่อยู่|Address|ผู้รับ|ชื่อผู้รับ|ชื่อ|โทร|เบอร์โทร|Tel)\\s*[:=]?".toRegex(), " ")
            .replace("\\s{2,}".toRegex(), " ")
            .trim()

        val isValid = tracking.isNotBlank() || recipientName.isNotBlank() || finalAddress.isNotBlank() || phone.isNotBlank()

        return ParsedParcelRow(
            trackingNumber = tracking,
            recipientName = recipientName,
            recipientPhone = phone,
            address = finalAddress,
            houseNumber = houseNum,
            village = village,
            subdistrict = subdist,
            district = dist,
            province = prov,
            postalCode = zip,
            carrier = detectedCarrier,
            codAmountSatang = codSatang,
            rawText = text,
            isValid = isValid,
            validationError = if (!isValid) "ข้อมูลไม่สมบูรณ์" else null
        )
    }

    fun cleanRecipientName(raw: String): String {
        return raw.replace("(?i)(ผู้รับ|ชื่อผู้รับ|ชื่อ|To|Recipient|Consignee)\\s*[:=]?".toRegex(), "")
            .replace(phoneRegex, "")
            .replace("\\d{5,}".toRegex(), "")
            .trim()
            .lines()
            .firstOrNull() ?: ""
    }

    fun extractHouseNumber(text: String): String {
        val match = houseNumberRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractVillage(text: String): String {
        val match = villageRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractSubdistrict(text: String): String {
        val match = subdistrictRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractDistrict(text: String): String {
        val match = districtRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractProvince(text: String): String {
        val match = provinceRegex.find(text)
        return match?.value?.trim() ?: ""
    }

    fun extractPostalCode(text: String): String {
        val match = postalCodeRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }
}
