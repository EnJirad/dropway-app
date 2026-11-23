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
 * - Comprehensive multi-level Quadtree fallback searching both Memory and Disk caches.
 * - Automatic rejection of provider dummy blank tiles (e.g. Esri 2521-byte or Carto 2049-byte placeholders).
 * - Transient failure cooldowns (15s) without permanent tile blacklisting.
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
    private const val COOLDOWN_MS = 15000L

    // Known dummy/empty placeholder tile MD5 hashes from map providers
    private val DUMMY_TILE_HASHES = setOf(
        "f27d9de7f80c13501f470595e327aa6d", // Esri 2521-byte blank tile
        "502fc5f6793fad87dcf8ba3fa646c38f"  // Carto 2049-byte dummy tile
    )

    fun getMemoryBitmap(url: String): Bitmap? {
        return memoryCache.get(url)
    }

    /**
     * Synchronously checks memory cache for exact tile or Quadtree ancestors (up to 4 levels).
     * Fast (<0.01ms), safe on the UI thread.
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

        for (lvl in 1..4) {
            val p = MapTileMath.getQuadtreeParent(x, y, z, lvl) ?: break
            val pUrl = tileUrlBuilder(p.parentX, p.parentY, p.parentZ)
            val memBmp = memoryCache.get(pUrl)
            if (memBmp != null) {
                return CachedTileBitmap(memBmp, p.srcRect)
            }
        }

        return null
    }

    /**
     * Asynchronously loads a tile with full Quadtree fallback from memory, disk, and network.
     * Guaranteed to run on Dispatchers.IO so it never blocks the main UI thread.
     */
    suspend fun loadTileWithFallback(
        context: Context,
        x: Int,
        y: Int,
        z: Int,
        tileUrlBuilder: (x: Int, y: Int, z: Int) -> String
    ): CachedTileBitmap? = withContext(Dispatchers.IO) {
        val exactUrl = tileUrlBuilder(x, y, z)

        // 1. Exact tile in memory cache
        memoryCache.get(exactUrl)?.let {
            return@withContext CachedTileBitmap(it, null)
        }

        // 2. Exact tile in disk cache
        val exactDiskFile = getDiskFile(context, exactUrl)
        if (exactDiskFile.exists() && exactDiskFile.length() > 0) {
            val diskBmp = BitmapFactory.decodeFile(exactDiskFile.absolutePath)
            if (diskBmp != null) {
                memoryCache.put(exactUrl, diskBmp)
                return@withContext CachedTileBitmap(diskBmp, null)
            } else {
                exactDiskFile.delete()
            }
        }

        // 3. Search Quadtree ancestors in memory and disk cache (up to 4 levels)
        var bestAncestor: CachedTileBitmap? = null
        for (lvl in 1..4) {
            val p = MapTileMath.getQuadtreeParent(x, y, z, lvl) ?: break
            val pUrl = tileUrlBuilder(p.parentX, p.parentY, p.parentZ)

            // Memory check
            val memBmp = memoryCache.get(pUrl)
            if (memBmp != null) {
                bestAncestor = CachedTileBitmap(memBmp, p.srcRect)
                break
            }

            // Disk check
            val pDiskFile = getDiskFile(context, pUrl)
            if (pDiskFile.exists() && pDiskFile.length() > 0) {
                val diskBmp = BitmapFactory.decodeFile(pDiskFile.absolutePath)
                if (diskBmp != null) {
                    memoryCache.put(pUrl, diskBmp)
                    bestAncestor = CachedTileBitmap(diskBmp, p.srcRect)
                    break
                }
            }
        }

        // 4. Try network download for exact tile if not on cooldown
        val now = System.currentTimeMillis()
        val lastFail = failureCooldowns[exactUrl]
        if (lastFail == null || now - lastFail >= COOLDOWN_MS) {
            val netBmp = loadTile(context, exactUrl)
            if (netBmp != null) {
                return@withContext CachedTileBitmap(netBmp, null)
            }
        }

        // 5. Return ancestor fallback if available, keeping map continuous and non-blank
        return@withContext bestAncestor
    }

    suspend fun loadTile(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(url)?.let { return@withContext it }

        val now = System.currentTimeMillis()
        val lastFail = failureCooldowns[url]
        if (lastFail != null && now - lastFail < COOLDOWN_MS) {
            return@withContext null
        }

        var isOriginator = false
        val deferred = inFlightRequests.computeIfAbsent(url) {
            isOriginator = true
            CompletableDeferred()
        }

        if (!isOriginator) {
            return@withContext deferred.await()
        }

        try {
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
                    // Check and reject dummy blank tiles (e.g. Esri 2521-byte or Carto 2049-byte dummy)
                    if (bytes.size in 2000..3000 || bytes.size < 800) {
                        val hash = computeMd5(bytes)
                        if (DUMMY_TILE_HASHES.contains(hash)) {
                            Log.i(TAG, "Rejected dummy placeholder tile for $urlStr (hash=$hash)")
                            return null
                        }
                    }

                    // Cache to disk
                    try {
                        diskFile.parentFile?.mkdirs()
                        diskFile.writeBytes(bytes)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing tile to disk: ${e.message}")
                    }

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

    private fun computeMd5(bytes: ByteArray): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
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
