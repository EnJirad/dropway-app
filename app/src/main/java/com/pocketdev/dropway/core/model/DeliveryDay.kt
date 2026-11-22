package com.pocketdev.dropway.core.model

data class DeliveryDay(
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val status: String = "ACTIVE", // ACTIVE, CLOSED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
)
