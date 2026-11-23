package com.pocketdev.dropway.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/**
 * Native tile caching and image loading pipeline.
 * Features:
 * - 48MB Memory LruCache for instant 0-latency bitmap recall.
 * - Persistent disk caching under context.cacheDir/dropway_map_tiles.
 * - In-flight asynchronous request deduplication via CompletableDeferred.
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
    private const val COOLDOWN_MS = 8000L

    fun getMemoryBitmap(url: String): Bitmap? {
        return memoryCache.get(url)
    }

    suspend fun loadTile(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Check memory cache
        memoryCache.get(url)?.let { return@withContext it }

        // 2. Check failure cooldown
        val now = System.currentTimeMillis()
        val lastFail = failureCooldowns[url]
        if (lastFail != null && now - lastFail < COOLDOWN_MS) {
            return@withContext null
        }

        // 3. Deduplicate concurrent in-flight requests for the exact same tile
        var isOriginator = false
        val deferred = inFlightRequests.computeIfAbsent(url) {
            isOriginator = true
            CompletableDeferred()
        }

        if (!isOriginator) {
            return@withContext deferred.await()
        }

        try {
            // 4. Check persistent disk cache
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

            // 5. Fetch from network
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
