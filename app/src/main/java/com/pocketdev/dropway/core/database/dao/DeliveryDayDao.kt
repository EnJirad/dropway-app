package com.pocketdev.dropway.core.database.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pocketdev.dropway.core.database.DatabaseChangeNotifier
import com.pocketdev.dropway.core.database.DropwayDatabaseHelper
import com.pocketdev.dropway.core.model.DeliveryDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class DeliveryDayDao(private val dbHelper: DropwayDatabaseHelper) {

    private val writableDb: SQLiteDatabase get() = dbHelper.writableDatabase
    private val readableDb: SQLiteDatabase get() = dbHelper.readableDatabase

    suspend fun getOrCreateToday(dateStr: String): DeliveryDay = withContext(Dispatchers.IO) {
        val existing = getByDate(dateStr)
        if (existing != null) return@withContext existing

        val cv = ContentValues().apply {
            put("date", dateStr)
            put("status", "ACTIVE")
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        }
        val id = writableDb.insert("delivery_days", null, cv)
        DatabaseChangeNotifier.notifyChanged("delivery_days")
        DeliveryDay(
            id = id,
            date = dateStr,
            status = "ACTIVE"
        )
    }

    suspend fun getByDate(dateStr: String): DeliveryDay? = withContext(Dispatchers.IO) {
        readableDb.rawQuery("SELECT * FROM delivery_days WHERE date = ?", arrayOf(dateStr)).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToDeliveryDay(cursor) else null
        }
    }

    suspend fun getActiveDay(): DeliveryDay? = withContext(Dispatchers.IO) {
        readableDb.rawQuery("SELECT * FROM delivery_days WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToDeliveryDay(cursor) else null
        }
    }

    suspend fun getById(id: Long): DeliveryDay? = withContext(Dispatchers.IO) {
        readableDb.rawQuery("SELECT * FROM delivery_days WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToDeliveryDay(cursor) else null
        }
    }

    suspend fun getAll(): List<DeliveryDay> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DeliveryDay>()
        readableDb.rawQuery("SELECT * FROM delivery_days ORDER BY id DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToDeliveryDay(cursor))
            }
        }
        list
    }

    suspend fun setStatus(id: Long, status: String): Boolean = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("status", status)
            put("updated_at", System.currentTimeMillis())
            if (status == "CLOSED") {
                put("closed_at", System.currentTimeMillis())
            } else {
                putNull("closed_at")
            }
        }
        val rows = writableDb.update("delivery_days", cv, "id = ?", arrayOf(id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("delivery_days")
        }
        rows > 0
    }

    fun observeActiveDay(): Flow<DeliveryDay?> = flow {
        emit(getActiveDay())
        DatabaseChangeNotifier.tableChanges.collect { table ->
            if (table == "delivery_days") {
                emit(getActiveDay())
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapCursorToDeliveryDay(c: Cursor): DeliveryDay {
        val closedAtIndex = c.getColumnIndexOrThrow("closed_at")
        val closedAt = if (c.isNull(closedAtIndex)) null else c.getLong(closedAtIndex)

        return DeliveryDay(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            date = c.getString(c.getColumnIndexOrThrow("date")),
            status = c.getString(c.getColumnIndexOrThrow("status")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            closedAt = closedAt
        )
    }
}
