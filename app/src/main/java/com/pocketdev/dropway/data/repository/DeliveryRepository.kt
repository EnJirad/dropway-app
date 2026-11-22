package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.core.database.dao.DeliveryDayDao
import com.pocketdev.dropway.core.database.dao.DeliveryEventDao
import com.pocketdev.dropway.core.database.dao.ParcelDao
import com.pocketdev.dropway.core.model.DailyDeliverySummary
import com.pocketdev.dropway.core.model.DeliveryDay
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.TrackingUtils
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeliveryRepository(
    private val deliveryDayDao: DeliveryDayDao,
    private val parcelDao: ParcelDao,
    private val eventDao: DeliveryEventDao
) {
    suspend fun getOrCreateToday(): DeliveryDay {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        return deliveryDayDao.getOrCreateToday(todayStr)
    }

    fun observeActiveDay(): Flow<DeliveryDay?> = deliveryDayDao.observeActiveDay()

    suspend fun getAllDeliveryDays(): List<DeliveryDay> = deliveryDayDao.getAll()

    suspend fun closeDay(id: Long) {
        deliveryDayDao.setStatus(id, "CLOSED")
        eventDao.logEvent(eventType = "DAY_CLOSED", details = "ปิดรอบการจัดส่งวันที่ ID $id")
    }

    suspend fun reopenDay(id: Long) {
        deliveryDayDao.setStatus(id, "ACTIVE")
        eventDao.logEvent(eventType = "DAY_REOPENED", details = "เปิดรอบการจัดส่งวันที่ ID $id อีกครั้ง")
    }

    // Parcel operations
    fun observeParcelsForDay(dayId: Long): Flow<List<Parcel>> = parcelDao.observeByDayId(dayId)

    fun observeSummaryForDay(dayId: Long): Flow<DailyDeliverySummary> = parcelDao.observeSummary(dayId)

    suspend fun getParcelsForDay(dayId: Long): List<Parcel> = parcelDao.getByDayId(dayId)

    suspend fun getParcelById(id: Long): Parcel? = parcelDao.getById(id)

    suspend fun getParcelByTracking(tracking: String): Parcel? = parcelDao.getByTrackingNumber(tracking)

    suspend fun isTrackingDuplicate(tracking: String): Boolean {
        val norm = TrackingUtils.normalize(tracking)
        return parcelDao.getByTrackingNumber(norm) != null
    }

    suspend fun saveParcel(parcel: Parcel): Long {
        return if (parcel.id == 0L) {
            val id = parcelDao.insert(parcel)
            eventDao.logEvent(parcelId = id, eventType = "PARCEL_CREATED", details = "สร้างพัสดุ ${parcel.trackingNumber} (${parcel.source})")
            id
        } else {
            parcelDao.update(parcel)
            eventDao.logEvent(parcelId = parcel.id, eventType = "PARCEL_UPDATED", details = "แก้ไขข้อมูลพัสดุ ${parcel.trackingNumber}")
            parcel.id
        }
    }

    suspend fun saveParcelsBatch(parcels: List<Parcel>): List<Long> {
        val ids = parcelDao.insertBatch(parcels)
        eventDao.logEvent(eventType = "BATCH_IMPORTED", details = "นำเข้าพัสดุจำนวน ${parcels.size} รายการสำเร็จ")
        return ids
    }

    suspend fun updateDeliveryStatus(id: Long, status: DeliveryStatus, paymentStatus: PaymentStatus? = null) {
        parcelDao.updateStatus(id, status, paymentStatus)
        eventDao.logEvent(parcelId = id, eventType = "STATUS_CHANGED", details = "เปลี่ยนสถานะเป็น ${status.label}")
    }

    suspend fun updateParcelCoordinates(id: Long, lat: Double?, lng: Double?) {
        parcelDao.updateCoordinates(id, lat, lng)
        eventDao.logEvent(parcelId = id, eventType = "LOCATION_UPDATED", details = "อัปเดตพิกัด GPS: $lat, $lng")
    }

    suspend fun linkHousehold(parcelId: Long, householdId: Long?) {
        parcelDao.linkHousehold(parcelId, householdId)
        eventDao.logEvent(parcelId = parcelId, householdId = householdId, eventType = "HOUSEHOLD_LINKED", details = "เชื่อมโยงกับบ้าน ID $householdId")
    }

    suspend fun deleteParcel(id: Long) {
        eventDao.logEvent(parcelId = id, eventType = "PARCEL_DELETED", details = "ลบพัสดุ ID $id")
        parcelDao.delete(id)
    }

    suspend fun getOutstandingParcels(): List<Parcel> = parcelDao.getOutstandingParcels()
}
