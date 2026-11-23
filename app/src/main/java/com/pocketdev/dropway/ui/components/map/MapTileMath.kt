package com.pocketdev.dropway.ui.components.map

import android.graphics.Rect
import kotlin.math.*

data class QuadtreeParent(
    val parentX: Int,
    val parentY: Int,
    val parentZ: Int,
    val srcRect: Rect
)

/**
 * Web Mercator projection math and slippy map tile calculations,
 * based on the proven native raster tile architecture in Velnox Mobile.
 *
 * Supports deep satellite zoom up to Zoom 23.0 with centimeter-level precision.
 */
object MapTileMath {
    const val TILE_SIZE = 256.0
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 23.0
    const val DEFAULT_ZOOM = 16.0

    /**
     * Converts WGS84 (latitude, longitude) to global Web Mercator pixel coordinates
     * at a given zoom level.
     */
    fun latLngToMercator(lat: Double, lng: Double, zoom: Double): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val x = ((lng + 180.0) / 360.0) * n * TILE_SIZE
        val latClamped = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(latClamped)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0) * n * TILE_SIZE
        return x to y
    }

    /**
     * Converts global Web Mercator pixel coordinates back to WGS84 (latitude, longitude)
     * at a given zoom level. Longitude wrapping and latitude limits are handled safely.
     */
    fun mercatorToLatLng(x: Double, y: Double, zoom: Double): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val totalPixels = n * TILE_SIZE
        val lng = (x / totalPixels) * 360.0 - 180.0
        val clampedLng = lng.coerceIn(-180.0, 180.0)
        val mercatorY = 0.5 - (y / totalPixels)
        val latRad = atan(sinh(mercatorY * 2.0 * Math.PI))
        val lat = Math.toDegrees(latRad).coerceIn(-85.05112878, 85.05112878)
        return lat to clampedLng
    }

    /**
     * Calculates the parent tile coordinates and the sub-rectangle within that parent tile
     * covering this child tile. Used for instant progressive tile display while high-res tiles load.
     */
    fun getQuadtreeParent(x: Int, y: Int, z: Int, levelsUp: Int = 1): QuadtreeParent? {
        if (z - levelsUp < 0) return null
        val parentZ = z - levelsUp
        val scale = 1 shl levelsUp
        val parentX = x shr levelsUp
        val parentY = y shr levelsUp

        val subX = x and (scale - 1)
        val subY = y and (scale - 1)
        val subSize = (TILE_SIZE / scale).toInt()

        val left = subX * subSize
        val top = subY * subSize
        val rect = Rect(left, top, left + subSize, top + subSize)
        return QuadtreeParent(parentX, parentY, parentZ, rect)
    }
}
