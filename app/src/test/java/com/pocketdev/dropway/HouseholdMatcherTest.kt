package com.pocketdev.dropway

import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.model.Resident
import com.pocketdev.dropway.core.util.HouseholdMatcher
import org.junit.Assert.*
import org.junit.Test

class HouseholdMatcherTest {

    private val households = listOf(
        Household(id = 1, houseNumber = "55/4", village = "5", addressDescription = "เจริญเมือง", notes = "บ้านหลังสีฟ้า"),
        Household(id = 2, houseNumber = "88/2", village = "3", addressDescription = "เชียงใหม่-ลำพูน", notes = ""),
        Household(id = 3, houseNumber = "12/3", village = "2", addressDescription = "โชตนา", notes = "")
    )

    private val residents = listOf(
        Resident(id = 1, householdId = 1, fullName = "วิภาดา วงศ์ใหญ่", phoneNumber = "089-988-7766"),
        Resident(id = 2, householdId = 2, fullName = "สมเกียรติ สว่างศรี", phoneNumber = "086-123-4567"),
        Resident(id = 3, householdId = 3, fullName = "สมชาย เจริญผล", phoneNumber = "081-234-5678")
    )

    @Test
    fun testMatchByPhoneAndHouse() {
        val match = HouseholdMatcher.findBestMatch(
            households = households,
            residents = residents,
            targetHouseNumber = "55/4",
            targetVillage = "5",
            targetPhone = "0899887766",
            targetName = "วิภาดา วงศ์ใหญ่"
        )

        assertNotNull(match)
        assertEquals(1L, match!!.household.id)
        assertEquals("55/4", match.household.houseNumber)
        assertEquals("วิภาดา วงศ์ใหญ่", match.matchedResident?.fullName)
        assertTrue("Confidence score should be high (>= 90)", match.confidenceScore >= 90)
    }

    @Test
    fun testMatchByHouseNumberAndMooOnly() {
        val match = HouseholdMatcher.findBestMatch(
            households = households,
            residents = residents,
            targetHouseNumber = "88/2",
            targetVillage = "3",
            targetPhone = "",
            targetName = ""
        )

        assertNotNull(match)
        assertEquals(2L, match!!.household.id)
        assertEquals(55, match.confidenceScore) // 30 + 25 = 55
    }

    @Test
    fun testNoMatchForUnknownData() {
        val match = HouseholdMatcher.findBestMatch(
            households = households,
            residents = residents,
            targetHouseNumber = "999/99",
            targetVillage = "99",
            targetPhone = "0999999999",
            targetName = "คนแปลกหน้า"
        )

        assertNull(match)
    }
}
