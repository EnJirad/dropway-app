package com.pocketdev.dropway.core.model

data class Resident(
    val id: Long = 0,
    val householdId: Long,
    val fullName: String,
    val phoneNumber: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
