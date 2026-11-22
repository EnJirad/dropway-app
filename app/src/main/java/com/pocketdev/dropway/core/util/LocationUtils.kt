package com.pocketdev.dropway.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlin.math.*

object LocationUtils {

    /**
     * Computes the distance in meters between two lat/lng coordinates using the Haversine formula.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${meters.roundToInt()} ม."
        } else {
            String.format("%.1f กม.", meters / 1000.0)
        }
    }

    /**
     * Launches external navigation (Google Maps, Waze, etc.) to the target coordinates.
     */
    fun openNavigation(context: Context, latitude: Double, longitude: Double, label: String = "ปลายทางจัดส่ง") {
        val navUri = Uri.parse("google.navigation:q=$latitude,$longitude")
        val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (navIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(navIntent)
        } else {
            // Fallback to generic geo URI
            val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                // If no map app installed, open browser maps
                val webUri = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }
        }
    }
}
