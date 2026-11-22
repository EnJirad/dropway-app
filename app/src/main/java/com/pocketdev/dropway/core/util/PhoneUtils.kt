package com.pocketdev.dropway.core.util

object PhoneUtils {
    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.startsWith("66") && digits.length >= 11) {
            return "0" + digits.substring(2)
        }
        return digits
    }

    fun format(phone: String): String {
        val norm = normalize(phone)
        return when (norm.length) {
            10 -> "${norm.substring(0, 3)}-${norm.substring(3, 6)}-${norm.substring(6)}"
            9 -> "${norm.substring(0, 2)}-${norm.substring(2, 5)}-${norm.substring(5)}"
            else -> phone
        }
    }
}
