package com.pocketdev.dropway.core.database.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pocketdev.dropway.core.database.DatabaseChangeNotifier
import com.pocketdev.dropway.core.database.DropwayDatabaseHelper
import com.pocketdev.dropway.core.model.Resident
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ResidentDao(private val dbHelper: DropwayDatabaseHelper) {

    private val writableDb: SQLiteDatabase get() = dbHelper.writableDatabase
    private val readableDb: SQLiteDatabase get() = dbHelper.readableDatabase

    suspend fun insert(resident: Resident): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("household_id", resident.householdId)
            put("full_name", resident.fullName.trim())
            put("phone_number", resident.phoneNumber.trim())
            put("notes", resident.notes.trim())
            put("created_at", resident.createdAt)
            put("updated_at", resident.updatedAt)
        }
        val id = writableDb.insert("residents", null, cv)
        if (id != -1L) {
            DatabaseChangeNotifier.notifyChanged("residents", "households")
        }
        id
    }

    suspend fun update(resident: Resident): Boolean = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("full_name", resident.fullName.trim())
            put("phone_number", resident.phoneNumber.trim())
            put("notes", resident.notes.trim())
            put("updated_at", System.currentTimeMillis())
        }
        val rows = writableDb.update("residents", cv, "id = ?", arrayOf(resident.id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("residents", "households")
        }
        rows > 0
    }

    suspend fun delete(residentId: Long): Boolean = withContext(Dispatchers.IO) {
        val rows = writableDb.delete("residents", "id = ?", arrayOf(residentId.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("residents", "households")
        }
        rows > 0
    }

    suspend fun getByHouseholdId(householdId: Long): List<Resident> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Resident>()
        readableDb.rawQuery("SELECT * FROM residents WHERE household_id = ? ORDER BY full_name ASC", arrayOf(householdId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToResident(cursor))
            }
        }
        list
    }

    suspend fun getAll(): List<Resident> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Resident>()
        readableDb.rawQuery("SELECT * FROM residents", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToResident(cursor))
            }
        }
        list
    }

    fun observeByHouseholdId(householdId: Long): Flow<List<Resident>> = flow {
        emit(getByHouseholdId(householdId))
        DatabaseChangeNotifier.tableChanges.collect { table ->
            if (table == "residents") {
                emit(getByHouseholdId(householdId))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapCursorToResident(c: Cursor): Resident {
        return Resident(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            householdId = c.getLong(c.getColumnIndexOrThrow("household_id")),
            fullName = c.getString(c.getColumnIndexOrThrow("full_name")),
            phoneNumber = c.getString(c.getColumnIndexOrThrow("phone_number")) ?: "",
            notes = c.getString(c.getColumnIndexOrThrow("notes")) ?: "",
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        )
    }
}
