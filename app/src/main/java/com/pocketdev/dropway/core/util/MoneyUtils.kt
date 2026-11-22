package com.pocketdev.dropway.core.util

import java.math.BigDecimal
import java.text.DecimalFormat

object MoneyUtils {
    private val formatter = DecimalFormat("#,##0.00")

    fun satangToBigDecimal(satang: Long): BigDecimal {
        return BigDecimal(satang).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
    }

    fun bigDecimalToSatang(amount: BigDecimal): Long {
        return amount.multiply(BigDecimal(100)).toLong()
    }

    fun parseToSatang(input: String): Long {
        val clean = input.replace("฿", "").replace(",", "").trim()
        if (clean.isBlank()) return 0L
        val bd = try {
            BigDecimal(clean)
        } catch (_: Exception) {
            BigDecimal.ZERO
        }
        return bigDecimalToSatang(bd)
    }

    fun formatBaht(satang: Long): String {
        return "฿" + formatter.format(satangToBigDecimal(satang))
    }

    fun calculateOutstandingSatang(codAmountSatang: Long, totalPaidSatang: Long): Long {
        val remaining = codAmountSatang - totalPaidSatang
        return if (remaining < 0) 0L else remaining
    }
}
