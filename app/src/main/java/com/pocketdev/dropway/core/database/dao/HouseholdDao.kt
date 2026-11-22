package com.pocketdev.dropway.core.database.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pocketdev.dropway.core.database.DatabaseChangeNotifier
import com.pocketdev.dropway.core.database.DropwayDatabaseHelper
import com.pocketdev.dropway.core.model.Household
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class HouseholdDao(private val dbHelper: DropwayDatabaseHelper) {

    private val writableDb: SQLiteDatabase get() = dbHelper.writableDatabase
    private val readableDb: SQLiteDatabase get() = dbHelper.readableDatabase

    suspend fun insert(household: Household): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("house_number", household.houseNumber.trim())
            put("village", household.village.trim())
            put("village_name", household.villageName.trim())
            put("subdistrict", household.subdistrict.trim())
            put("district", household.district.trim())
            put("province", household.province.trim())
            put("postal_code", household.postalCode.trim())
            put("latitude", household.latitude)
            put("longitude", household.longitude)
            put("address_description", household.addressDescription.trim())
            put("notes", household.notes.trim())
            put("created_at", household.createdAt)
            put("updated_at", household.updatedAt)
        }
        val id = writableDb.insert("households", null, cv)
        if (id != -1L) {
            DatabaseChangeNotifier.notifyChanged("households")
        }
        id
    }

    suspend fun update(household: Household): Boolean = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("house_number", household.houseNumber.trim())
            put("village", household.village.trim())
            put("village_name", household.villageName.trim())
            put("subdistrict", household.subdistrict.trim())
            put("district", household.district.trim())
            put("province", household.province.trim())
            put("postal_code", household.postalCode.trim())
            put("latitude", household.latitude)
            put("longitude", household.longitude)
            put("address_description", household.addressDescription.trim())
            put("notes", household.notes.trim())
            put("updated_at", System.currentTimeMillis())
        }
        val rows = writableDb.update("households", cv, "id = ?", arrayOf(household.id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("households")
        }
        rows > 0
    }

    suspend fun delete(householdId: Long): Boolean = withContext(Dispatchers.IO) {
        val rows = writableDb.delete("households", "id = ?", arrayOf(householdId.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("households", "residents", "parcels")
        }
        rows > 0
    }

    suspend fun getById(id: Long): Household? = withContext(Dispatchers.IO) {
        readableDb.rawQuery("SELECT * FROM households WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToHousehold(cursor) else null
        }
    }

    suspend fun getAll(): List<Household> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Household>()
        readableDb.rawQuery("SELECT * FROM households ORDER BY house_number ASC, village ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToHousehold(cursor))
            }
        }
        list
    }

    suspend fun search(query: String, villageFilter: String = ""): List<Household> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val list = mutableListOf<Household>()
        val sql: String
        val args: Array<String>

        if (cleanQuery.isBlank() && villageFilter.isBlank()) {
            return@withContext getAll()
        }

        val like = "%$cleanQuery%"
        if (villageFilter.isNotBlank()) {
            sql = """
                SELECT DISTINCT h.* FROM households h
                LEFT JOIN residents r ON r.household_id = h.id
                WHERE h.village = ? AND (
                    h.house_number LIKE ? OR
                    h.village_name LIKE ? OR
                    h.notes LIKE ? OR
                    h.address_description LIKE ? OR
                    r.full_name LIKE ? OR
                    r.phone_number LIKE ?
                )
                ORDER BY h.house_number ASC
            """.trimIndent()
            args = arrayOf(villageFilter, like, like, like, like, like, like)
        } else {
            sql = """
                SELECT DISTINCT h.* FROM households h
                LEFT JOIN residents r ON r.household_id = h.id
                WHERE
                    h.house_number LIKE ? OR
                    h.village LIKE ? OR
                    h.village_name LIKE ? OR
                    h.notes LIKE ? OR
                    h.address_description LIKE ? OR
                    r.full_name LIKE ? OR
                    r.phone_number LIKE ?
                ORDER BY h.house_number ASC, h.village ASC
            """.trimIndent()
            args = arrayOf(like, like, like, like, like, like, like)
        }

        readableDb.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToHousehold(cursor))
            }
        }
        list
    }

    fun observeAll(): Flow<List<Household>> = flow {
        emit(getAll())
        DatabaseChangeNotifier.tableChanges.collect { table ->
            if (table == "households") {
                emit(getAll())
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapCursorToHousehold(c: Cursor): Household {
        val id = c.getLong(c.getColumnIndexOrThrow("id"))
        val houseNumber = c.getString(c.getColumnIndexOrThrow("house_number"))
        val village = c.getString(c.getColumnIndexOrThrow("village")) ?: ""
        val villageName = c.getString(c.getColumnIndexOrThrow("village_name")) ?: ""
        val subdistrict = c.getString(c.getColumnIndexOrThrow("subdistrict")) ?: ""
        val district = c.getString(c.getColumnIndexOrThrow("district")) ?: ""
        val province = c.getString(c.getColumnIndexOrThrow("province")) ?: ""
        val postalCode = c.getString(c.getColumnIndexOrThrow("postal_code")) ?: ""
        val latIndex = c.getColumnIndexOrThrow("latitude")
        val latitude = if (c.isNull(latIndex)) null else c.getDouble(latIndex)
        val lngIndex = c.getColumnIndexOrThrow("longitude")
        val longitude = if (c.isNull(lngIndex)) null else c.getDouble(lngIndex)
        val addressDesc = c.getString(c.getColumnIndexOrThrow("address_description")) ?: ""
        val notes = c.getString(c.getColumnIndexOrThrow("notes")) ?: ""
        val createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        val updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))

        return Household(
            id = id,
            houseNumber = houseNumber,
            village = village,
            villageName = villageName,
            subdistrict = subdistrict,
            district = district,
            province = province,
            postalCode = postalCode,
            latitude = latitude,
            longitude = longitude,
            addressDescription = addressDesc,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
