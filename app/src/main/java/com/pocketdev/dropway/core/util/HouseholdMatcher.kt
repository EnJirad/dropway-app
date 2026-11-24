package com.pocketdev.dropway.core.util

import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.model.Resident

data class MatchResult(
    val household: Household,
    val matchedResident: Resident? = null,
    val confidenceScore: Int, // 0 - 100
    val matchReason: String
)

object HouseholdMatcher {

    fun normalizeHouseNumber(raw: String): String {
        return raw.replace("(?i)(บ้านเลขที่|เลขที่|\\s)".toRegex(), "")
            .replace("\\s*/\\s*".toRegex(), "/")
            .trim()
    }

    fun normalizeVillage(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isNotBlank()) return digits
        return trimmed.replace("(?i)(หมู่ที่|หมู่|ม\\.?|\\s)".toRegex(), "").trim()
    }

    fun cleanResidentName(raw: String): String {
        return raw.replace("(?i)(คุณ|นาย|นางสาว|นาง|น\\.ส\\.|ด\\.ช\\.|ด\\.ญ\\.|ผู้รับ|ชื่อผู้รับ)\\s*[:=]?".toRegex(), "")
            .trim()
    }

    /**
     * Attempts to find the best matching household from existing households.
     * Considers house number, village (Moo), phone numbers, resident names, and address text.
     * Never automatically merges uncertain matches.
     */
    fun findBestMatch(
        households: List<Household>,
        residents: List<Resident>,
        targetHouseNumber: String,
        targetVillage: String,
        targetPhone: String,
        targetName: String,
        targetAddress: String = ""
    ): MatchResult? {
        val candidates = findTopMatches(
            households = households,
            residents = residents,
            targetHouseNumber = targetHouseNumber,
            targetVillage = targetVillage,
            targetPhone = targetPhone,
            targetName = targetName,
            targetAddress = targetAddress
        )
        return candidates.firstOrNull()
    }

    /**
     * Returns top ranked matching households sorted by confidence score descending.
     */
    fun findTopMatches(
        households: List<Household>,
        residents: List<Resident>,
        targetHouseNumber: String,
        targetVillage: String,
        targetPhone: String,
        targetName: String,
        targetAddress: String = ""
    ): List<MatchResult> {
        val cleanHouse = targetHouseNumber.trim()
        val normTargetHouse = normalizeHouseNumber(cleanHouse)
        val cleanMoo = targetVillage.trim()
        val normTargetMoo = normalizeVillage(cleanMoo)
        val cleanPhone = PhoneUtils.normalize(targetPhone)
        val cleanName = targetName.trim()
        val cleanNameNormalized = cleanResidentName(cleanName)

        if (cleanHouse.isBlank() && cleanPhone.isBlank() && cleanName.isBlank() && targetAddress.isBlank()) {
            return emptyList()
        }

        val candidates = mutableListOf<MatchResult>()

        for (h in households) {
            var score = 0
            val reasons = mutableListOf<String>()

            // 1. Phone match against residents (Very strong indicator)
            val houseResidents = residents.filter { it.householdId == h.id }
            val phoneMatch = if (cleanPhone.isNotBlank()) {
                houseResidents.firstOrNull { PhoneUtils.normalize(it.phoneNumber) == cleanPhone }
            } else null

            if (phoneMatch != null) {
                score += 50
                reasons.add("เบอร์โทรตรงกับผู้อยู่อาศัย (${phoneMatch.fullName})")
            }

            // 2. Name match against residents
            val nameMatch = if (cleanName.isNotBlank()) {
                houseResidents.firstOrNull { res ->
                    val cleanResName = cleanResidentName(res.fullName)
                    res.fullName.equals(cleanName, ignoreCase = true) ||
                    cleanName.contains(res.fullName, ignoreCase = true) ||
                    res.fullName.contains(cleanName, ignoreCase = true) ||
                    (cleanNameNormalized.isNotBlank() && (
                        cleanResName.equals(cleanNameNormalized, ignoreCase = true) ||
                        cleanNameNormalized.contains(cleanResName, ignoreCase = true) ||
                        cleanResName.contains(cleanNameNormalized, ignoreCase = true)
                    ))
                }
            } else null

            if (nameMatch != null) {
                score += 35
                reasons.add("ชื่อตรงกับผู้อยู่อาศัย (${nameMatch.fullName})")
            }

            // 3. House number match
            val normHHouse = normalizeHouseNumber(h.houseNumber)
            val houseMatches = cleanHouse.isNotBlank() && (
                h.houseNumber.equals(cleanHouse, ignoreCase = true) ||
                (normTargetHouse.isNotBlank() && normTargetHouse == normHHouse)
            )

            if (houseMatches) {
                score += 30
                reasons.add("บ้านเลขที่ตรงกัน (${h.houseNumber})")

                // Moo match together with house number
                val normHMoo = normalizeVillage(h.village)
                val mooMatches = cleanMoo.isNotBlank() && (
                    h.village.equals(cleanMoo, ignoreCase = true) ||
                    (normTargetMoo.isNotBlank() && normTargetMoo == normHMoo)
                )

                if (mooMatches) {
                    score += 25
                    reasons.add("หมู่ตรงกัน (หมู่ ${h.village})")
                } else if (cleanMoo.isNotBlank() && h.village.isNotBlank() && !mooMatches) {
                    // Conflicting Moo reduces confidence
                    score -= 30
                }
            }

            // 4. Address keywords / village name match
            if (targetAddress.isNotBlank()) {
                if (h.villageName.isNotBlank() && targetAddress.contains(h.villageName, ignoreCase = true)) {
                    score += 20
                    reasons.add("ชื่อหมู่บ้านตรงกัน (${h.villageName})")
                }
                if (h.addressDescription.isNotBlank() && targetAddress.contains(h.addressDescription, ignoreCase = true)) {
                    score += 15
                    reasons.add("รายละเอียดที่อยู่ตรงกัน (${h.addressDescription})")
                }
            }

            if (score >= 40) {
                candidates.add(
                    MatchResult(
                        household = h,
                        matchedResident = phoneMatch ?: nameMatch,
                        confidenceScore = score.coerceIn(0, 100),
                        matchReason = reasons.joinToString(", ")
                    )
                )
            }
        }

        return candidates.sortedByDescending { it.confidenceScore }
    }
}
