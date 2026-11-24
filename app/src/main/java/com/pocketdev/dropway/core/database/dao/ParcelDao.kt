package com.pocketdev.dropway.core.database.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pocketdev.dropway.core.database.DatabaseChangeNotifier
import com.pocketdev.dropway.core.database.DropwayDatabaseHelper
import com.pocketdev.dropway.core.model.DailyDeliverySummary
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.TrackingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ParcelDao(private val dbHelper: DropwayDatabaseHelper) {

    private val writableDb: SQLiteDatabase get() = dbHelper.writableDatabase
    private val readableDb: SQLiteDatabase get() = dbHelper.readableDatabase

    suspend fun insert(parcel: Parcel): Long = withContext(Dispatchers.IO) {
        val cv = createContentValues(parcel)
        val id = writableDb.insert("parcels", null, cv)
        if (id != -1L) {
            DatabaseChangeNotifier.notifyChanged("parcels")
        }
        id
    }

    suspend fun insertBatch(parcels: List<Parcel>): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        writableDb.beginTransaction()
        try {
            for (p in parcels) {
                val cv = createContentValues(p)
                val id = writableDb.insert("parcels", null, cv)
                ids.add(id)
            }
            writableDb.setTransactionSuccessful()
            DatabaseChangeNotifier.notifyChanged("parcels")
        } finally {
            writableDb.endTransaction()
        }
        ids
    }

    suspend fun update(parcel: Parcel): Boolean = withContext(Dispatchers.IO) {
        val cv = createContentValues(parcel).apply {
            put("updated_at", System.currentTimeMillis())
        }
        val rows = writableDb.update("parcels", cv, "id = ?", arrayOf(parcel.id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("parcels")
        }
        rows > 0
    }

    suspend fun updateStatus(id: Long, deliveryStatus: DeliveryStatus, paymentStatus: PaymentStatus? = null): Boolean = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("delivery_status", deliveryStatus.name)
            if (paymentStatus != null) {
                put("payment_status", paymentStatus.name)
            }
            put("updated_at", System.currentTimeMillis())
        }
        val rows = writableDb.update("parcels", cv, "id = ?", arrayOf(id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("parcels")
        }
        rows > 0
    }

    suspend fun updatePaymentStatus(id: Long, paymentStatus: PaymentStatus): Boolean = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("payment_status", paymentStatus.name)
            put("updated_at", System.currentTimeMillis())
        }
        val rows = writableDb.update("parcels", cv, "id = ?", arrayOf(id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("parcels")
        }
        rows > 0
    }

    suspend fun updateCoordinates(id: Long, lat: Double?, lng: Double?): Boolean = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            if (lat != null && lng != null) {
                put("latitude", lat)
                put("longitude", lng)
            } else {
                putNull("latitude")
                putNull("longitude")
            }
            put("updated_at", System.currentTimeMillis())
        }
        val rows = writableDb.update("parcels", cv, "id = ?", arrayOf(id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("parcels")
        }
        rows > 0
    }

    suspend fun linkHousehold(id: Long, householdId: Long?): Boolean = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            if (householdId != null) {
                put("household_id", householdId)
            } else {
                putNull("household_id")
            }
            put("updated_at", System.currentTimeMillis())
        }
        val rows = writableDb.update("parcels", cv, "id = ?", arrayOf(id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("parcels")
        }
        rows > 0
    }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val rows = writableDb.delete("parcels", "id = ?", arrayOf(id.toString()))
        if (rows > 0) {
            DatabaseChangeNotifier.notifyChanged("parcels", "payment_records")
        }
        rows > 0
    }

    suspend fun getById(id: Long): Parcel? = withContext(Dispatchers.IO) {
        readableDb.rawQuery("SELECT * FROM parcels WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToParcel(cursor) else null
        }
    }

    suspend fun getByTrackingNumber(trackingNumber: String): Parcel? = withContext(Dispatchers.IO) {
        val norm = TrackingUtils.normalize(trackingNumber)
        readableDb.rawQuery("SELECT * FROM parcels WHERE tracking_number = ? LIMIT 1", arrayOf(norm)).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToParcel(cursor) else null
        }
    }

    suspend fun getRecentParcelByPhone(phone: String): Parcel? = withContext(Dispatchers.IO) {
        val normPhone = com.pocketdev.dropway.core.util.PhoneUtils.normalize(phone)
        if (normPhone.isBlank()) return@withContext null
        readableDb.rawQuery(
            "SELECT * FROM parcels WHERE recipient_phone = ? AND household_id IS NOT NULL ORDER BY id DESC LIMIT 1",
            arrayOf(normPhone)
        ).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToParcel(cursor) else null
        }
    }

    suspend fun getRecentParcelByName(name: String): Parcel? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext null
        readableDb.rawQuery(
            "SELECT * FROM parcels WHERE recipient_name LIKE ? AND household_id IS NOT NULL ORDER BY id DESC LIMIT 1",
            arrayOf("%$clean%")
        ).use { cursor ->
            if (cursor.moveToFirst()) mapCursorToParcel(cursor) else null
        }
    }

    suspend fun getByDayId(dayId: Long): List<Parcel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Parcel>()
        readableDb.rawQuery("SELECT * FROM parcels WHERE delivery_day_id = ? ORDER BY id DESC", arrayOf(dayId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToParcel(cursor))
            }
        }
        list
    }

    suspend fun getOutstandingParcels(): List<Parcel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Parcel>()
        val sql = """
            SELECT * FROM parcels 
            WHERE cod_amount_satang > 0 
              AND payment_status NOT IN ('CASH_RECEIVED', 'TRANSFER_RECEIVED', 'CANCELLED')
            ORDER BY id DESC
        """.trimIndent()
        readableDb.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToParcel(cursor))
            }
        }
        list
    }

    suspend fun getSummaryForDay(dayId: Long): DailyDeliverySummary = withContext(Dispatchers.IO) {
        val sql = """
            SELECT 
                COUNT(*) as total_count,
                COUNT(DISTINCT CASE WHEN household_id IS NOT NULL THEN household_id ELSE destination_address END) as unique_locs,
                SUM(CASE WHEN delivery_status = 'DELIVERED' THEN 1 ELSE 0 END) as delivered_count,
                SUM(CASE WHEN delivery_status = 'PENDING' THEN 1 ELSE 0 END) as pending_count,
                SUM(CASE WHEN delivery_status = 'FAILED' THEN 1 ELSE 0 END) as failed_count,
                SUM(CASE WHEN delivery_status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_count,
                SUM(cod_amount_satang) as total_cod,
                SUM(CASE WHEN payment_status IN ('CASH_RECEIVED', 'TRANSFER_RECEIVED') THEN cod_amount_satang ELSE 0 END) as collected_cod,
                SUM(CASE WHEN payment_status = 'AWAITING_TRANSFER' THEN 1 ELSE 0 END) as awaiting_transfer_count,
                SUM(CASE WHEN destination_address = '' OR destination_address IS NULL THEN 1 ELSE 0 END) as incomplete_addr_count,
                SUM(CASE WHEN latitude IS NULL OR longitude IS NULL THEN 1 ELSE 0 END) as no_coords_count
            FROM parcels 
            WHERE delivery_day_id = ?
        """.trimIndent()

        readableDb.rawQuery(sql, arrayOf(dayId.toString())).use { c ->
            if (c.moveToFirst()) {
                val total = c.getInt(0)
                val uniqueLocs = c.getInt(1)
                val delivered = c.getInt(2)
                val pending = c.getInt(3)
                val failed = c.getInt(4)
                val cancelled = c.getInt(5)
                val totalCod = if (c.isNull(6)) 0L else c.getLong(6)
                val collectedCod = if (c.isNull(7)) 0L else c.getLong(7)
                val awaitingTransfer = c.getInt(8)
                val incompleteAddr = c.getInt(9)
                val noCoords = c.getInt(10)
                val outstandingCod = (totalCod - collectedCod).coerceAtLeast(0L)

                DailyDeliverySummary(
                    totalParcels = total,
                    uniqueLocations = uniqueLocs,
                    deliveredCount = delivered,
                    pendingCount = pending,
                    failedCount = failed,
                    cancelledCount = cancelled,
                    totalCodSatang = totalCod,
                    collectedCodSatang = collectedCod,
                    outstandingCodSatang = outstandingCod,
                    awaitingTransferCount = awaitingTransfer,
                    incompleteAddressCount = incompleteAddr,
                    noCoordinatesCount = noCoords
                )
            } else {
                DailyDeliverySummary()
            }
        }
    }

    fun observeByDayId(dayId: Long): Flow<List<Parcel>> = flow {
        emit(getByDayId(dayId))
        DatabaseChangeNotifier.tableChanges.collect { table ->
            if (table == "parcels") {
                emit(getByDayId(dayId))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun observeSummary(dayId: Long): Flow<DailyDeliverySummary> = flow {
        emit(getSummaryForDay(dayId))
        DatabaseChangeNotifier.tableChanges.collect { table ->
            if (table == "parcels" || table == "payment_records") {
                emit(getSummaryForDay(dayId))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun createContentValues(p: Parcel): ContentValues {
        return ContentValues().apply {
            put("delivery_day_id", p.deliveryDayId)
            put("tracking_number", TrackingUtils.normalize(p.trackingNumber))
            put("recipient_name", p.recipientName.trim())
            put("recipient_phone", p.recipientPhone.trim())
            if (p.householdId != null) {
                put("household_id", p.householdId)
            } else {
                putNull("household_id")
            }
            put("destination_address", p.destinationAddress.trim())
            if (p.latitude != null && p.longitude != null) {
                put("latitude", p.latitude)
                put("longitude", p.longitude)
            } else {
                putNull("latitude")
                putNull("longitude")
            }
            put("cod_amount_satang", p.codAmountSatang)
            put("delivery_status", p.deliveryStatus.name)
            put("payment_status", p.paymentStatus.name)
            put("delivery_notes", p.deliveryNotes.trim())
            put("source", p.source)
            put("created_at", p.createdAt)
            put("updated_at", p.updatedAt)
        }
    }

    private fun mapCursorToParcel(c: Cursor): Parcel {
        val hIndex = c.getColumnIndexOrThrow("household_id")
        val householdId = if (c.isNull(hIndex)) null else c.getLong(hIndex)

        val latIndex = c.getColumnIndexOrThrow("latitude")
        val latitude = if (c.isNull(latIndex)) null else c.getDouble(latIndex)

        val lngIndex = c.getColumnIndexOrThrow("longitude")
        val longitude = if (c.isNull(lngIndex)) null else c.getDouble(lngIndex)

        val deliveryStatusStr = c.getString(c.getColumnIndexOrThrow("delivery_status"))
        val deliveryStatus = try {
            DeliveryStatus.valueOf(deliveryStatusStr)
        } catch (_: Exception) {
            DeliveryStatus.PENDING
        }

        val paymentStatusStr = c.getString(c.getColumnIndexOrThrow("payment_status"))
        val paymentStatus = try {
            PaymentStatus.valueOf(paymentStatusStr)
        } catch (_: Exception) {
            PaymentStatus.UNPAID
        }

        return Parcel(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            deliveryDayId = c.getLong(c.getColumnIndexOrThrow("delivery_day_id")),
            trackingNumber = c.getString(c.getColumnIndexOrThrow("tracking_number")),
            recipientName = c.getString(c.getColumnIndexOrThrow("recipient_name")),
            recipientPhone = c.getString(c.getColumnIndexOrThrow("recipient_phone")) ?: "",
            householdId = householdId,
            destinationAddress = c.getString(c.getColumnIndexOrThrow("destination_address")) ?: "",
            latitude = latitude,
            longitude = longitude,
            codAmountSatang = c.getLong(c.getColumnIndexOrThrow("cod_amount_satang")),
            deliveryStatus = deliveryStatus,
            paymentStatus = paymentStatus,
            deliveryNotes = c.getString(c.getColumnIndexOrThrow("delivery_notes")) ?: "",
            source = c.getString(c.getColumnIndexOrThrow("source")) ?: "MANUAL",
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        )
    }
}
