package com.pocketdev.dropway.core.model

data class Household(
    val id: Long = 0,
    val houseNumber: String,
    val village: String = "",
    val villageName: String = "",
    val subdistrict: String = "",
    val district: String = "",
    val province: String = "",
    val postalCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressDescription: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun fullAddress(): String {
        val parts = mutableListOf<String>()
        if (houseNumber.isNotBlank()) parts.add("บ้านเลขที่ $houseNumber")
        if (village.isNotBlank()) parts.add("หมู่ $village")
        if (villageName.isNotBlank()) parts.add(villageName)
        if (subdistrict.isNotBlank()) parts.add("ต.$subdistrict")
        if (district.isNotBlank()) parts.add("อ.$district")
        if (province.isNotBlank()) parts.add("จ.$province")
        if (postalCode.isNotBlank()) parts.add(postalCode)
        return parts.joinToString(" ")
    }
}
