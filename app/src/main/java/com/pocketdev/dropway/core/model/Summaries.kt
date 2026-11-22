package com.pocketdev.dropway.core.model

import java.math.BigDecimal

data class DailyDeliverySummary(
    val totalParcels: Int = 0,
    val uniqueLocations: Int = 0,
    val deliveredCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val cancelledCount: Int = 0,
    val totalCodSatang: Long = 0L,
    val collectedCodSatang: Long = 0L,
    val outstandingCodSatang: Long = 0L,
    val awaitingTransferCount: Int = 0,
    val incompleteAddressCount: Int = 0,
    val noCoordinatesCount: Int = 0
) {
    val totalCodBigDecimal: BigDecimal
        get() = BigDecimal(totalCodSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
    val collectedCodBigDecimal: BigDecimal
        get() = BigDecimal(collectedCodSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
    val outstandingCodBigDecimal: BigDecimal
        get() = BigDecimal(outstandingCodSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
}

data class CodSummary(
    val totalCodSatang: Long = 0L,
    val cashCollectedSatang: Long = 0L,
    val transferCollectedSatang: Long = 0L,
    val totalCollectedSatang: Long = 0L,
    val outstandingSatang: Long = 0L,
    val awaitingTransferSatang: Long = 0L,
    val partialPaymentsSatang: Long = 0L,
    val previousDayOutstandingSatang: Long = 0L
) {
    val totalCod: BigDecimal get() = BigDecimal(totalCodSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
    val cashCollected: BigDecimal get() = BigDecimal(cashCollectedSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
    val transferCollected: BigDecimal get() = BigDecimal(transferCollectedSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
    val totalCollected: BigDecimal get() = BigDecimal(totalCollectedSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
    val outstanding: BigDecimal get() = BigDecimal(outstandingSatang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
}
