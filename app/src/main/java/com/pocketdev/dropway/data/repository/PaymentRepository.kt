package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.core.database.dao.DeliveryEventDao
import com.pocketdev.dropway.core.database.dao.ParcelDao
import com.pocketdev.dropway.core.database.dao.PaymentRecordDao
import com.pocketdev.dropway.core.model.CodSummary
import com.pocketdev.dropway.core.model.PaymentMethod
import com.pocketdev.dropway.core.model.PaymentRecord
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.MoneyUtils
import kotlinx.coroutines.flow.Flow

class PaymentRepository(
    private val paymentRecordDao: PaymentRecordDao,
    private val parcelDao: ParcelDao,
    private val eventDao: DeliveryEventDao
) {
    suspend fun recordPayment(
        parcelId: Long,
        amountSatang: Long,
        method: PaymentMethod,
        note: String = "",
        reference: String? = null
    ): Long {
        val paymentId = paymentRecordDao.recordPayment(parcelId, amountSatang, method, note, reference)
        val formatted = MoneyUtils.formatBaht(amountSatang)
        eventDao.logEvent(
            parcelId = parcelId,
            eventType = "PAYMENT_RECORDED",
            details = "บันทึกการชำระเงิน $formatted (${method.label}) โน้ต: $note"
        )
        return paymentId
    }

    suspend fun updatePaymentStatusOnly(parcelId: Long, status: PaymentStatus) {
        parcelDao.updatePaymentStatus(parcelId, status)
        eventDao.logEvent(
            parcelId = parcelId,
            eventType = "PAYMENT_STATUS_UPDATED",
            details = "ปรับสถานะการชำระเงินเป็น: ${status.label}"
        )
    }

    fun observePaymentsForParcel(parcelId: Long): Flow<List<PaymentRecord>> =
        paymentRecordDao.observeByParcelId(parcelId)

    fun observeCodSummary(dayId: Long): Flow<CodSummary> =
        paymentRecordDao.observeCodSummary(dayId)

    suspend fun getCodSummary(dayId: Long): CodSummary =
        paymentRecordDao.getCodSummary(dayId)

    suspend fun getPaymentsForParcel(parcelId: Long): List<PaymentRecord> =
        paymentRecordDao.getByParcelId(parcelId)

    suspend fun getTotalPaidForParcel(parcelId: Long): Long =
        paymentRecordDao.getTotalPaidForParcel(parcelId)
}
