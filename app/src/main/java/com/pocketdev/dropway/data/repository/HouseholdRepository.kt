package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.core.database.dao.DeliveryEventDao
import com.pocketdev.dropway.core.database.dao.HouseholdDao
import com.pocketdev.dropway.core.database.dao.ResidentDao
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.model.Resident
import com.pocketdev.dropway.core.util.HouseholdMatcher
import com.pocketdev.dropway.core.util.MatchResult
import kotlinx.coroutines.flow.Flow

class HouseholdRepository(
    private val householdDao: HouseholdDao,
    private val residentDao: ResidentDao,
    private val eventDao: DeliveryEventDao
) {
    fun observeHouseholds(): Flow<List<Household>> = householdDao.observeAll()

    suspend fun getAllHouseholds(): List<Household> = householdDao.getAll()

    suspend fun searchHouseholds(query: String, villageFilter: String = ""): List<Household> {
        return householdDao.search(query, villageFilter)
    }

    suspend fun getHouseholdById(id: Long): Household? = householdDao.getById(id)

    suspend fun saveHousehold(household: Household): Long {
        return if (household.id == 0L) {
            val id = householdDao.insert(household)
            eventDao.logEvent(householdId = id, eventType = "HOUSEHOLD_CREATED", details = "สร้างข้อมูลบ้านเลขที่ ${household.houseNumber}")
            id
        } else {
            householdDao.update(household)
            eventDao.logEvent(householdId = household.id, eventType = "HOUSEHOLD_UPDATED", details = "แก้ไขข้อมูลบ้านเลขที่ ${household.houseNumber}")
            household.id
        }
    }

    suspend fun deleteHousehold(id: Long): Boolean {
        eventDao.logEvent(householdId = id, eventType = "HOUSEHOLD_DELETED", details = "ลบข้อมูลบ้าน ID $id")
        return householdDao.delete(id)
    }

    // Resident operations
    fun observeResidentsByHousehold(householdId: Long): Flow<List<Resident>> =
        residentDao.observeByHouseholdId(householdId)

    suspend fun getResidentsByHousehold(householdId: Long): List<Resident> =
        residentDao.getByHouseholdId(householdId)

    suspend fun saveResident(resident: Resident): Long {
        return if (resident.id == 0L) {
            val id = residentDao.insert(resident)
            eventDao.logEvent(householdId = resident.householdId, eventType = "RESIDENT_ADDED", details = "เพิ่มผู้อยู่อาศัย ${resident.fullName}")
            id
        } else {
            residentDao.update(resident)
            eventDao.logEvent(householdId = resident.householdId, eventType = "RESIDENT_UPDATED", details = "แก้ไขผู้อยู่อาศัย ${resident.fullName}")
            resident.id
        }
    }

    suspend fun deleteResident(id: Long, householdId: Long): Boolean {
        eventDao.logEvent(householdId = householdId, eventType = "RESIDENT_DELETED", details = "ลบผู้อยู่อาศัย ID $id")
        return residentDao.delete(id)
    }

    // Household matching
    suspend fun findMatchingHousehold(
        houseNumber: String = "",
        village: String = "",
        phone: String = "",
        name: String = "",
        address: String = ""
    ): MatchResult? {
        val allHouseholds = householdDao.getAll()
        val allResidents = residentDao.getAll()
        return HouseholdMatcher.findBestMatch(
            households = allHouseholds,
            residents = allResidents,
            targetHouseNumber = houseNumber,
            targetVillage = village,
            targetPhone = phone,
            targetName = name,
            targetAddress = address
        )
    }

    suspend fun findTopMatchingHouseholds(
        houseNumber: String = "",
        village: String = "",
        phone: String = "",
        name: String = "",
        address: String = ""
    ): List<MatchResult> {
        val allHouseholds = householdDao.getAll()
        val allResidents = residentDao.getAll()
        return HouseholdMatcher.findTopMatches(
            households = allHouseholds,
            residents = allResidents,
            targetHouseNumber = houseNumber,
            targetVillage = village,
            targetPhone = phone,
            targetName = name,
            targetAddress = address
        )
    }
}
