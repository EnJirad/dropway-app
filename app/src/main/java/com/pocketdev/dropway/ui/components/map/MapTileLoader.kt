package com.pocketdev.dropway.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class CachedTileBitmap(
    val bitmap: Bitmap,
    val srcRect: Rect? = null
)

/**
 * Native tile caching and image loading pipeline.
 * Features:
 * - 48MB Memory LruCache for instant 0-latency bitmap recall.
 * - Persistent disk caching under context.cacheDir/dropway_map_tiles.
 * - In-flight asynchronous request deduplication via CompletableDeferred.
 * - Quadtree parent tile recall for smooth progressive tile display without blank flashes.
 * - Permanent 404/400 failure caching to prevent repeated network waste at unsupported zoom levels.
 * - HTTP User-Agent and Referer headers strictly compliant with OSM and Esri policies.
 * - Requires zero third-party dependencies, keeping builds 100% offline-compatible.
 */
object MapTileLoader {
    private const val TAG = "MapTileLoader"
    private const val MAX_MEMORY_BYTES = 48 * 1024 * 1024 // 48 MB

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()
    private val failureCooldowns = ConcurrentHashMap<String, Long>()
    private val permanentlyUnavailable = ConcurrentHashMap.newKeySet<String>()
    private const val COOLDOWN_MS = 15000L

    fun getMemoryBitmap(url: String): Bitmap? {
        return memoryCache.get(url)
    }

    /**
     * Synchronously retrieves the exact tile bitmap from memory, or finds an ancestor
     * parent/grandparent tile from the Quadtree and returns the corresponding cropped sub-rectangle.
     * Guarantees 0-latency display while high-resolution child tiles are loaded in background.
     */
    fun getMemoryBitmapOrParent(
        x: Int,
        y: Int,
        z: Int,
        tileUrlBuilder: (x: Int, y: Int, z: Int) -> String
    ): CachedTileBitmap? {
        val exactUrl = tileUrlBuilder(x, y, z)
        memoryCache.get(exactUrl)?.let {
            return CachedTileBitmap(it, null)
        }

        // Try parent tile (1 level up: 128x128 sub-rect)
        val p1 = MapTileMath.getQuadtreeParent(x, y, z, 1)
        if (p1 != null) {
            val pUrl = tileUrlBuilder(p1.parentX, p1.parentY, p1.parentZ)
            memoryCache.get(pUrl)?.let {
                return CachedTileBitmap(it, p1.srcRect)
            }
        }

        // Try grandparent tile (2 levels up: 64x64 sub-rect)
        val p2 = MapTileMath.getQuadtreeParent(x, y, z, 2)
        if (p2 != null) {
            val gpUrl = tileUrlBuilder(p2.parentX, p2.parentY, p2.parentZ)
            memoryCache.get(gpUrl)?.let {
                return CachedTileBitmap(it, p2.srcRect)
            }
        }

        return null
    }

    suspend fun loadTile(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Check memory cache
        memoryCache.get(url)?.let { return@withContext it }

        // 2. Check permanently unavailable tiles (e.g. 404/400 at unsupported zoom)
        if (permanentlyUnavailable.contains(url)) {
            return@withContext null
        }

        // 3. Check failure cooldown
        val now = System.currentTimeMillis()
        val lastFail = failureCooldowns[url]
        if (lastFail != null && now - lastFail < COOLDOWN_MS) {
            return@withContext null
        }

        // 4. Deduplicate concurrent in-flight requests for the exact same tile
        var isOriginator = false
        val deferred = inFlightRequests.computeIfAbsent(url) {
            isOriginator = true
            CompletableDeferred()
        }

        if (!isOriginator) {
            return@withContext deferred.await()
        }

        try {
            // 5. Check persistent disk cache
            val diskFile = getDiskFile(context, url)
            if (diskFile.exists() && diskFile.length() > 0) {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(url, bitmap)
                    deferred.complete(bitmap)
                    return@withContext bitmap
                } else {
                    diskFile.delete()
                }
            }

            // 6. Fetch from network
            val bitmap = downloadTile(url, diskFile)
            if (bitmap != null) {
                memoryCache.put(url, bitmap)
                deferred.complete(bitmap)
                failureCooldowns.remove(url)
                return@withContext bitmap
            } else {
                failureCooldowns[url] = System.currentTimeMillis()
                deferred.complete(null)
                return@withContext null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tile load error for $url: ${e.message}")
            failureCooldowns[url] = System.currentTimeMillis()
            deferred.complete(null)
            return@withContext null
        } finally {
            inFlightRequests.remove(url)
        }
    }

    private fun downloadTile(urlStr: String, diskFile: File): Bitmap? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "DropwayDeliveryApp/1.0 (contact: support@dropway.app)")
                setRequestProperty("Referer", "https://dropway.app/")
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }

            val statusCode = conn.responseCode
            if (statusCode == HttpURLConnection.HTTP_OK) {
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    // Cache to disk
                    try {
                        diskFile.parentFile?.mkdirs()
                        diskFile.writeBytes(bytes)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing tile to disk: ${e.message}")
                    }

                    // Decode to bitmap
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } else {
                Log.w(TAG, "Tile HTTP $statusCode for $urlStr")
                if (statusCode == HttpURLConnection.HTTP_NOT_FOUND || statusCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                    permanentlyUnavailable.add(urlStr)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for $urlStr: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun getDiskFile(context: Context, url: String): File {
        val cacheDir = File(context.cacheDir, "dropway_map_tiles").apply {
            if (!exists()) mkdirs()
        }
        val hash = try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.hashCode().toString()
        }
        return File(cacheDir, "$hash.tile")
    }

    fun clearCache(context: Context) {
        memoryCache.evictAll()
        try {
            val dir = File(context.cacheDir, "dropway_map_tiles")
            dir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
