package com.pocketdev.dropway.core.model

import java.math.BigDecimal

enum class DeliveryStatus(val label: String) {
    PENDING("รอดำเนินการ"),
    DELIVERED("จัดส่งสำเร็จ"),
    FAILED("ส่งไม่สำเร็จ"),
    RETURNED("ส่งคืนต้นทาง"),
    CANCELLED("ยกเลิก")
}

enum class PaymentStatus(val label: String) {
    UNPAID("ยังไม่จ่าย"),
    AWAITING_TRANSFER("รอโอนเงิน"),
    PARTIALLY_PAID("จ่ายบางส่วน"),
    CASH_RECEIVED("รับเงินสดแล้ว"),
    TRANSFER_RECEIVED("โอนเงินแล้ว"),
    VERIFICATION_REQUIRED("ต้องตรวจสอบ"),
    CANCELLED("ยกเลิก")
}

data class Parcel(
    val id: Long = 0,
    val deliveryDayId: Long,
    val trackingNumber: String,
    val recipientName: String,
    val recipientPhone: String = "",
    val householdId: Long? = null,
    val destinationAddress: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val codAmountSatang: Long = 0L, // In satang minor units to prevent floating point errors
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    val deliveryNotes: String = "",
    val source: String = "MANUAL",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val codAmountBigDecimal: BigDecimal
        get() = BigDecimal(codAmountSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
}
