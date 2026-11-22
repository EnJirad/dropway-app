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

    /**
     * Attempts to find the best matching household from existing households.
     * Considers house number, village (Moo), phone numbers, and resident names.
     * Never automatically merges uncertain matches.
     */
    fun findBestMatch(
        households: List<Household>,
        residents: List<Resident>,
        targetHouseNumber: String,
        targetVillage: String,
        targetPhone: String,
        targetName: String
    ): MatchResult? {
        val cleanHouse = targetHouseNumber.trim()
        val cleanMoo = targetVillage.trim()
        val cleanPhone = PhoneUtils.normalize(targetPhone)
        val cleanName = targetName.trim()

        if (cleanHouse.isBlank() && cleanPhone.isBlank() && cleanName.isBlank()) {
            return null
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
                houseResidents.firstOrNull {
                    it.fullName.equals(cleanName, ignoreCase = true) ||
                    cleanName.contains(it.fullName) ||
                    it.fullName.contains(cleanName)
                }
            } else null

            if (nameMatch != null) {
                score += 35
                reasons.add("ชื่อตรงกับผู้อยู่อาศัย (${nameMatch.fullName})")
            }

            // 3. House number match
            if (cleanHouse.isNotBlank() && h.houseNumber.equals(cleanHouse, ignoreCase = true)) {
                score += 30
                reasons.add("บ้านเลขที่ตรงกัน ($cleanHouse)")

                // Moo match together with house number
                if (cleanMoo.isNotBlank() && h.village.equals(cleanMoo, ignoreCase = true)) {
                    score += 25
                    reasons.add("หมู่ตรงกัน (หมู่ $cleanMoo)")
                } else if (cleanMoo.isNotBlank() && h.village.isNotBlank() && !h.village.equals(cleanMoo, ignoreCase = true)) {
                    // Conflicting Moo reduces confidence
                    score -= 30
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

        return candidates.maxByOrNull { it.confidenceScore }
    }
}
