package com.pocketdev.dropway.core.database.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pocketdev.dropway.core.database.DatabaseChangeNotifier
import com.pocketdev.dropway.core.database.DropwayDatabaseHelper
import com.pocketdev.dropway.core.model.DeliveryEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeliveryEventDao(private val dbHelper: DropwayDatabaseHelper) {

    private val writableDb: SQLiteDatabase get() = dbHelper.writableDatabase
    private val readableDb: SQLiteDatabase get() = dbHelper.readableDatabase

    suspend fun logEvent(
        parcelId: Long? = null,
        householdId: Long? = null,
        eventType: String,
        details: String
    ): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            if (parcelId != null) put("parcel_id", parcelId) else putNull("parcel_id")
            if (householdId != null) put("household_id", householdId) else putNull("household_id")
            put("event_type", eventType)
            put("details", details.trim())
            put("timestamp", System.currentTimeMillis())
        }
        val id = writableDb.insert("delivery_events", null, cv)
        if (id != -1L) {
            DatabaseChangeNotifier.notifyChanged("delivery_events")
        }
        id
    }

    suspend fun getEventsForParcel(parcelId: Long): List<DeliveryEvent> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DeliveryEvent>()
        readableDb.rawQuery("SELECT * FROM delivery_events WHERE parcel_id = ? ORDER BY timestamp DESC", arrayOf(parcelId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToEvent(cursor))
            }
        }
        list
    }

    suspend fun getEventsForHousehold(householdId: Long): List<DeliveryEvent> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DeliveryEvent>()
        readableDb.rawQuery("SELECT * FROM delivery_events WHERE household_id = ? ORDER BY timestamp DESC", arrayOf(householdId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToEvent(cursor))
            }
        }
        list
    }

    private fun mapCursorToEvent(c: Cursor): DeliveryEvent {
        val pIndex = c.getColumnIndexOrThrow("parcel_id")
        val parcelId = if (c.isNull(pIndex)) null else c.getLong(pIndex)
        val hIndex = c.getColumnIndexOrThrow("household_id")
        val householdId = if (c.isNull(hIndex)) null else c.getLong(hIndex)

        return DeliveryEvent(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            parcelId = parcelId,
            householdId = householdId,
            eventType = c.getString(c.getColumnIndexOrThrow("event_type")),
            details = c.getString(c.getColumnIndexOrThrow("details")),
            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
        )
    }
}
