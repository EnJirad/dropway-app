package com.pocketdev.dropway.core.model

import java.math.BigDecimal

enum class PaymentMethod(val label: String) {
    CASH("เงินสด"),
    TRANSFER("โอนเงิน"),
    QR("สแกน QR"),
    OTHER("อื่นๆ")
}

data class PaymentRecord(
    val id: Long = 0,
    val parcelId: Long,
    val amountSatang: Long, // safe integer minor units
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val status: String = "COMPLETED", // COMPLETED, PENDING_VERIFY, CANCELLED, REFUNDED
    val note: String = "",
    val reference: String? = null,
    val recordedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val amountBigDecimal: BigDecimal
        get() = BigDecimal(amountSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
}
