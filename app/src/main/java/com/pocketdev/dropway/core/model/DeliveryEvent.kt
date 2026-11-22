package com.pocketdev.dropway.core.model

data class DeliveryEvent(
    val id: Long = 0,
    val parcelId: Long? = null,
    val householdId: Long? = null,
    val eventType: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
