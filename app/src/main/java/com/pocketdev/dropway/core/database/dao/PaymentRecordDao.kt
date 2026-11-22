package com.pocketdev.dropway.core.database.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.pocketdev.dropway.core.database.DatabaseChangeNotifier
import com.pocketdev.dropway.core.database.DropwayDatabaseHelper
import com.pocketdev.dropway.core.model.CodSummary
import com.pocketdev.dropway.core.model.PaymentMethod
import com.pocketdev.dropway.core.model.PaymentRecord
import com.pocketdev.dropway.core.model.PaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class PaymentRecordDao(private val dbHelper: DropwayDatabaseHelper) {

    private val writableDb: SQLiteDatabase get() = dbHelper.writableDatabase
    private val readableDb: SQLiteDatabase get() = dbHelper.readableDatabase

    /**
     * Records a payment safely in an append-oriented audit ledger.
     * Updates the parcel's payment status automatically based on settled amount.
     */
    suspend fun recordPayment(
        parcelId: Long,
        amountSatang: Long,
        method: PaymentMethod,
        note: String = "",
        reference: String? = null
    ): Long = withContext(Dispatchers.IO) {
        writableDb.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("parcel_id", parcelId)
                put("amount_satang", amountSatang)
                put("payment_method", method.name)
                put("status", "COMPLETED")
                put("note", note.trim())
                put("reference", reference?.trim())
                put("recorded_at", System.currentTimeMillis())
                put("created_at", System.currentTimeMillis())
            }
            val paymentId = writableDb.insert("payment_records", null, cv)

            // Recalculate parcel payment status
            val totalPaid = getTotalPaidForParcelInternal(writableDb, parcelId)
            val codAmount = getParcelCodInternal(writableDb, parcelId)

            val newStatus = when {
                totalPaid >= codAmount && codAmount > 0 -> {
                    if (method == PaymentMethod.CASH) PaymentStatus.CASH_RECEIVED else PaymentStatus.TRANSFER_RECEIVED
                }
                totalPaid > 0 -> PaymentStatus.PARTIALLY_PAID
                else -> PaymentStatus.UNPAID
            }

            val pCv = ContentValues().apply {
                put("payment_status", newStatus.name)
                put("updated_at", System.currentTimeMillis())
            }
            writableDb.update("parcels", pCv, "id = ?", arrayOf(parcelId.toString()))

            writableDb.setTransactionSuccessful()
            DatabaseChangeNotifier.notifyChanged("payment_records", "parcels")
            paymentId
        } finally {
            writableDb.endTransaction()
        }
    }

    suspend fun getByParcelId(parcelId: Long): List<PaymentRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PaymentRecord>()
        readableDb.rawQuery("SELECT * FROM payment_records WHERE parcel_id = ? ORDER BY id ASC", arrayOf(parcelId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(mapCursorToPaymentRecord(cursor))
            }
        }
        list
    }

    suspend fun getTotalPaidForParcel(parcelId: Long): Long = withContext(Dispatchers.IO) {
        getTotalPaidForParcelInternal(readableDb, parcelId)
    }

    private fun getTotalPaidForParcelInternal(db: SQLiteDatabase, parcelId: Long): Long {
        val sql = "SELECT SUM(amount_satang) FROM payment_records WHERE parcel_id = ? AND status = 'COMPLETED'"
        db.rawQuery(sql, arrayOf(parcelId.toString())).use { c ->
            return if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        }
    }

    private fun getParcelCodInternal(db: SQLiteDatabase, parcelId: Long): Long {
        db.rawQuery("SELECT cod_amount_satang FROM parcels WHERE id = ?", arrayOf(parcelId.toString())).use { c ->
            return if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        }
    }

    suspend fun getCodSummary(dayId: Long): CodSummary = withContext(Dispatchers.IO) {
        // Total COD for today
        val totalCodSql = "SELECT SUM(cod_amount_satang) FROM parcels WHERE delivery_day_id = ?"
        var totalCodSatang = 0L
        readableDb.rawQuery(totalCodSql, arrayOf(dayId.toString())).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) totalCodSatang = c.getLong(0)
        }

        // Payments collected for today's parcels
        val paymentsSql = """
            SELECT 
                SUM(CASE WHEN pr.payment_method = 'CASH' THEN pr.amount_satang ELSE 0 END) as cash_collected,
                SUM(CASE WHEN pr.payment_method IN ('TRANSFER', 'QR') THEN pr.amount_satang ELSE 0 END) as transfer_collected,
                SUM(pr.amount_satang) as total_collected
            FROM payment_records pr
            JOIN parcels p ON p.id = pr.parcel_id
            WHERE p.delivery_day_id = ? AND pr.status = 'COMPLETED'
        """.trimIndent()

        var cashCollected = 0L
        var transferCollected = 0L
        var totalCollected = 0L

        readableDb.rawQuery(paymentsSql, arrayOf(dayId.toString())).use { c ->
            if (c.moveToFirst()) {
                cashCollected = if (c.isNull(0)) 0L else c.getLong(0)
                transferCollected = if (c.isNull(1)) 0L else c.getLong(1)
                totalCollected = if (c.isNull(2)) 0L else c.getLong(2)
            }
        }

        // Awaiting transfer
        val awaitingSql = """
            SELECT SUM(cod_amount_satang) FROM parcels 
            WHERE delivery_day_id = ? AND payment_status = 'AWAITING_TRANSFER'
        """.trimIndent()
        var awaitingSatang = 0L
        readableDb.rawQuery(awaitingSql, arrayOf(dayId.toString())).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) awaitingSatang = c.getLong(0)
        }

        // Previous day outstanding carryover (parcels from prior days with unpaid COD)
        val carryoverSql = """
            SELECT SUM(p.cod_amount_satang - COALESCE(paid.total_paid, 0))
            FROM parcels p
            LEFT JOIN (
                SELECT parcel_id, SUM(amount_satang) as total_paid 
                FROM payment_records 
                WHERE status = 'COMPLETED' 
                GROUP BY parcel_id
            ) paid ON paid.parcel_id = p.id
            WHERE p.delivery_day_id < ? 
              AND p.cod_amount_satang > 0 
              AND p.payment_status NOT IN ('CASH_RECEIVED', 'TRANSFER_RECEIVED', 'CANCELLED')
        """.trimIndent()

        var previousDayOutstanding = 0L
        readableDb.rawQuery(carryoverSql, arrayOf(dayId.toString())).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) previousDayOutstanding = c.getLong(0).coerceAtLeast(0L)
        }

        val outstanding = (totalCodSatang - totalCollected).coerceAtLeast(0L)

        CodSummary(
            totalCodSatang = totalCodSatang,
            cashCollectedSatang = cashCollected,
            transferCollectedSatang = transferCollected,
            totalCollectedSatang = totalCollected,
            outstandingSatang = outstanding,
            awaitingTransferSatang = awaitingSatang,
            previousDayOutstandingSatang = previousDayOutstanding
        )
    }

    fun observeByParcelId(parcelId: Long): Flow<List<PaymentRecord>> = flow {
        emit(getByParcelId(parcelId))
        DatabaseChangeNotifier.tableChanges.collect { table ->
            if (table == "payment_records") {
                emit(getByParcelId(parcelId))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun observeCodSummary(dayId: Long): Flow<CodSummary> = flow {
        emit(getCodSummary(dayId))
        DatabaseChangeNotifier.tableChanges.collect { table ->
            if (table == "payment_records" || table == "parcels") {
                emit(getCodSummary(dayId))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapCursorToPaymentRecord(c: Cursor): PaymentRecord {
        val methodStr = c.getString(c.getColumnIndexOrThrow("payment_method"))
        val method = try { PaymentMethod.valueOf(methodStr) } catch (_: Exception) { PaymentMethod.CASH }

        val refIndex = c.getColumnIndexOrThrow("reference")
        val reference = if (c.isNull(refIndex)) null else c.getString(refIndex)

        return PaymentRecord(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            parcelId = c.getLong(c.getColumnIndexOrThrow("parcel_id")),
            amountSatang = c.getLong(c.getColumnIndexOrThrow("amount_satang")),
            paymentMethod = method,
            status = c.getString(c.getColumnIndexOrThrow("status")),
            note = c.getString(c.getColumnIndexOrThrow("note")) ?: "",
            reference = reference,
            recordedAt = c.getLong(c.getColumnIndexOrThrow("recorded_at")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        )
    }
}
