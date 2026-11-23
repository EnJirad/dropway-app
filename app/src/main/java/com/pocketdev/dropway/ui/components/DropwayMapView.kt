package com.pocketdev.dropway.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.MapView
import com.pocketdev.dropway.BuildConfig
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.NetworkUtils
import com.pocketdev.dropway.ui.components.map.MapTileLoader
import com.pocketdev.dropway.ui.components.map.MapTileMath
import com.pocketdev.dropway.ui.theme.*
import java.util.Locale
import kotlin.math.*

/**
 * Standard 2D map providers in Dropway:
 * 1. OpenStreetMap (Standard road map, streets, labels, place details, keyless)
 * 2. Satellite (Real aerial satellite imagery via Esri World Imagery, keyless)
 * 3. Carto Voyager (Clean modern street map tiles, keyless)
 */
enum class DropwayMapType(
    val label: String,
    val layerKey: String,
    val iconRes: Int,
    val attribution: String
) {
    OPEN_STREET_MAP(
        label = "OpenStreetMap",
        layerKey = "osm",
        iconRes = R.drawable.ic_map,
        attribution = "© OpenStreetMap contributors"
    ),
    SATELLITE(
        label = "ดาวเทียม",
        layerKey = "satellite",
        iconRes = R.drawable.ic_satellite,
        attribution = "© Esri, Maxar, Earthstar Geographics"
    ),
    CARTO_VOYAGER(
        label = "Carto",
        layerKey = "carto",
        iconRes = R.drawable.ic_layers,
        attribution = "© CARTO © OpenStreetMap contributors"
    );

    fun getTileUrl(x: Int, y: Int, z: Int): String {
        return when (this) {
            OPEN_STREET_MAP -> "https://tile.openstreetmap.org/$z/$x/$y.png"
            SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
            CARTO_VOYAGER -> "https://a.basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png"
        }
    }
}

data class MapMarkerItem(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val status: String, // "DELIVERED", "PENDING", "COD"
    val subtitle: String = ""
)

/**
 * Helper to determine whether the official Google Maps Android SDK is configured
 * with a valid API key and Google Play Services is available on the device.
 * Strictly preserved for the Google Street View 360° panorama feature.
 */
object GoogleMapsHelper {
    fun isApiKeyConfigured(): Boolean {
        val key = try {
            BuildConfig.GOOGLE_MAPS_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
        return key.isNotEmpty() &&
                key != "YOUR_API_KEY_HERE" &&
                !key.contains("API_KEY") &&
                key.length > 10
    }

    fun isPlayServicesAvailable(context: Context): Boolean {
        return try {
            val availability = GoogleApiAvailability.getInstance()
            val result = availability.isGooglePlayServicesAvailable(context)
            result == ConnectionResult.SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    fun isGoogleMapsSdkUsable(context: Context): Boolean {
        return isApiKeyConfigured() && isPlayServicesAvailable(context)
    }
}

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

/**
 * Native Android Raster Tile Map Viewer:
 * Rebuilt using Velnox Mobile's proven native raster tile architecture in Jetpack Compose.
 * Eliminates all WebView, WebGL, MapLibre, and Leaflet dependencies for 2D mapping.
 *
 * Provides:
 * - Real raster map tiles: OpenStreetMap, real Satellite imagery (Esri), and Carto Voyager.
 * - Multi-touch pan & pinch-to-zoom with Web Mercator camera positioning.
 * - Native bitmap tile loader with in-memory LruCache and persistent disk caching.
 * - Single fixed center pin with rooftop crosshairs in picker mode.
 * - Numbered status-colored delivery markers in viewer mode.
 * - Preserved Google Street View 360° panorama launcher.
 * - Strictly zero emojis across all UI, drawables, and strings.
 */
@Composable
fun DropwayMapView(
    modifier: Modifier = Modifier,
    initialLat: Double = 18.788300,
    initialLng: Double = 98.985300,
    initialZoom: Float = 16f,
    isPickerMode: Boolean = true,
    markers: List<MapMarkerItem> = emptyList(),
    onCoordinatesChanged: ((Double, Double) -> Unit)? = null,
    onMarkerClick: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val isOnline = remember { NetworkUtils.isNetworkAvailable(context) }

    var currentMapType by remember { mutableStateOf(DropwayMapType.OPEN_STREET_MAP) }
    var currentLat by remember { mutableStateOf(initialLat) }
    var currentLng by remember { mutableStateOf(initialLng) }
    var zoom by remember { mutableStateOf(initialZoom.toDouble()) }
    var isDragging by remember { mutableStateOf(false) }

    var showStreetViewDialog by remember { mutableStateOf(false) }

    // Sync external coordinate changes (e.g. city presets, GPS updates)
    LaunchedEffect(initialLat, initialLng) {
        if (abs(currentLat - initialLat) > 0.00001 || abs(currentLng - initialLng) > 0.00001) {
            currentLat = initialLat
            currentLng = initialLng
        }
    }

    val pinLift by animateDpAsState(
        targetValue = if (isDragging) (-10).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            .background(Color(0xFFF1F5F9))
    ) {
        // Native Tile Map Viewport
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val density = LocalDensity.current.density
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()

            // Multi-touch gestures: Drag pan + Pinch zoom + Double tap zoom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = (zoom + 1.0).coerceAtMost(MapTileMath.MAX_ZOOM)
                                onCoordinatesChanged?.invoke(currentLat, currentLng)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomFactor, _ ->
                            if (zoomFactor != 1f) {
                                val zoomChange = ln(zoomFactor.toDouble()) / ln(2.0)
                                zoom = (zoom + zoomChange).coerceIn(MapTileMath.MIN_ZOOM, MapTileMath.MAX_ZOOM)
                            }
                            if (pan.x != 0f || pan.y != 0f) {
                                isDragging = true
                                val n = 2.0.pow(zoom)
                                val worldTotalPx = n * MapTileMath.TILE_SIZE
                                val currWorldX = ((currentLng + 180.0) / 360.0) * worldTotalPx
                                val latClamped = currentLat.coerceIn(-85.05112878, 85.05112878)
                                val latRad = Math.toRadians(latClamped)
                                val currWorldY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0) * worldTotalPx

                                val newWorldX = currWorldX - pan.x.toDouble()
                                val newWorldY = currWorldY - pan.y.toDouble()

                                val newLng = ((newWorldX / worldTotalPx) * 360.0 - 180.0).coerceIn(-180.0, 180.0)
                                val mercY = 0.5 - (newWorldY / worldTotalPx)
                                val newLat = Math.toDegrees(atan(sinh(mercY * 2.0 * Math.PI))).coerceIn(-85.05112878, 85.05112878)

                                currentLat = newLat
                                currentLng = newLng
                                onCoordinatesChanged?.invoke(newLat, newLng)
                            }
                        }
                    }
            ) {
                // Layer 1: Stylized vector backdrop canvas (smooth background while tiles load)
                MapBackdropCanvas(
                    isSatellite = (currentMapType == DropwayMapType.SATELLITE),
                    modifier = Modifier.fillMaxSize()
                )

                // Layer 2: Native Slippy Raster Map Tiles
                val intZoom = zoom.toInt().coerceIn(MapTileMath.MIN_ZOOM.toInt(), MapTileMath.MAX_ZOOM.toInt())
                val scale = 2.0.pow(zoom - intZoom).toFloat()
                val scaledTilePx = (MapTileMath.TILE_SIZE * scale).toFloat()
                val numTiles = 1 shl intZoom

                val (centerMercX, centerMercY) = MapTileMath.latLngToMercator(currentLat, currentLng, intZoom.toDouble())
                val halfW = wPx / 2f
                val halfH = hPx / 2f

                val minTileX = floor((centerMercX - halfW / scale) / MapTileMath.TILE_SIZE).toInt()
                val maxTileX = ceil((centerMercX + halfW / scale) / MapTileMath.TILE_SIZE).toInt()
                val minTileY = floor((centerMercY - halfH / scale) / MapTileMath.TILE_SIZE).toInt().coerceIn(0, numTiles - 1)
                val maxTileY = ceil((centerMercY + halfH / scale) / MapTileMath.TILE_SIZE).toInt().coerceIn(0, numTiles - 1)

                for (tx in minTileX..maxTileX) {
                    for (ty in minTileY..maxTileY) {
                        val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
                        val tileScreenX = halfW + ((tx * MapTileMath.TILE_SIZE - centerMercX) * scale).toFloat()
                        val tileScreenY = halfH + ((ty * MapTileMath.TILE_SIZE - centerMercY) * scale).toFloat()
                        val tileUrl = currentMapType.getTileUrl(wrappedTx, ty, intZoom)

                        key("$intZoom-$wrappedTx-$ty-$currentMapType") {
                            NativeTileItem(
                                tileUrl = tileUrl,
                                modifier = Modifier
                                    .offset { IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt()) }
                                    .size((scaledTilePx / density).dp)
                            )
                        }
                    }
                }

                // Layer 3: Delivery Markers (Viewer Mode only)
                if (!isPickerMode) {
                    markers.forEachIndexed { index, marker ->
                        val (mX, mY) = MapTileMath.latLngToMercator(marker.latitude, marker.longitude, intZoom.toDouble())
                        val screenX = halfW + ((mX - centerMercX) * scale).toFloat()
                        val screenY = halfH + ((mY - centerMercY) * scale).toFloat()

                        if (screenX >= -40f && screenX <= wPx + 40f && screenY >= -40f && screenY <= hPx + 40f) {
                            val pinBg = when (marker.status) {
                                "DELIVERED" -> Color(0xFF10B981)
                                "COD" -> Color(0xFFF59E0B)
                                else -> Color(0xFF0284C7)
                            }

                            key("marker-${marker.id}") {
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset((screenX - 14.dp.toPx()).roundToInt(), (screenY - 14.dp.toPx()).roundToInt()) }
                                        .clickable { onMarkerClick?.invoke(marker.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = pinBg,
                                        shadowElevation = 5.dp,
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Layer 4: Rooftop crosshairs on map center (Picker Mode only)
                if (isPickerMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val crossLen = 14.dp.toPx()
                        val strokeColor = if (currentMapType == DropwayMapType.SATELLITE)
                            Color.White.copy(alpha = 0.60f)
                        else
                            Color.Black.copy(alpha = 0.35f)

                        drawLine(color = strokeColor, start = Offset(cx - crossLen, cy), end = Offset(cx + crossLen, cy), strokeWidth = 1.5f)
                        drawLine(color = strokeColor, start = Offset(cx, cy - crossLen), end = Offset(cx, cy + crossLen), strokeWidth = 1.5f)
                    }
                }
            }

            // FIXED CENTER PIN (Picker Mode Only - Single Fixed Center Pin)
            if (isPickerMode) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Ground target shadow dot
                    Canvas(
                        modifier = Modifier
                            .size(16.dp, 8.dp)
                            .offset(y = 1.dp)
                    ) {
                        drawOval(Color(0x55000000))
                    }

                    // Teardrop Pin tip sits exactly on the target
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = (-20).dp + pinLift)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_map_pin),
                            contentDescription = "หมุดตำแหน่ง",
                            tint = DropwayPrimary,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            // Top Controls: Segmented Map Switcher + Live Coordinates HUD
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Segmented Switcher [ OpenStreetMap | ดาวเทียม | Carto ]
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DropwayMapType.values().forEach { type ->
                            val isSelected = currentMapType == type
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) DropwayPrimary else Color.Transparent,
                                modifier = Modifier.clickable {
                                    currentMapType = type
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = type.iconRes),
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color(0xFF0F172A),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = type.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF0F172A)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Live Coordinates HUD Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DropwayNavy.copy(alpha = 0.92f),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = String.format(Locale.US, "พิกัด: %.5f, %.5f", currentLat, currentLng),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Offline Warning Banner
            if (!isOnline) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFEF3C7),
                    shadowElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ออฟไลน์: แผนที่ออนไลน์และ Street View ต้องใช้เน็ต • ข้อมูลพัสดุทำงานได้ปกติ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // Visible Required Tile Attribution Pill (Bottom Left)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = if (isPickerMode) 38.dp else 8.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = currentMapType.attribution,
                    fontSize = 9.sp,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Floating Action Buttons (Right Side)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Zoom In
                FloatingRoundButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = "ซูมเข้า",
                    onClick = {
                        zoom = (zoom + 1.0).coerceAtMost(MapTileMath.MAX_ZOOM)
                        onCoordinatesChanged?.invoke(currentLat, currentLng)
                    }
                )

                // Zoom Out
                FloatingRoundButton(
                    iconRes = R.drawable.ic_minus,
                    contentDescription = "ซูมออก",
                    onClick = {
                        zoom = (zoom - 1.0).coerceAtLeast(MapTileMath.MIN_ZOOM)
                        onCoordinatesChanged?.invoke(currentLat, currentLng)
                    }
                )

                // GPS Current / Initial Position Reset
                FloatingRoundButton(
                    iconRes = R.drawable.ic_my_location,
                    contentDescription = "ตำแหน่งเริ่มต้น",
                    onClick = {
                        currentLat = initialLat
                        currentLng = initialLng
                        zoom = MapTileMath.DEFAULT_ZOOM
                        if (isPickerMode) {
                            onCoordinatesChanged?.invoke(initialLat, initialLng)
                        }
                    }
                )

                // Open in External Google Maps App
                FloatingRoundButton(
                    iconRes = R.drawable.ic_navigation,
                    contentDescription = "เปิดใน Google Maps",
                    tint = DropwayPrimary,
                    onClick = {
                        LocationUtils.openNavigation(context, currentLat, currentLng, "พิกัดที่เลือก")
                    }
                )

                // Real Google Street View 360° Panorama Launcher (Preserved exactly as requested)
                FloatingRoundButton(
                    iconRes = R.drawable.ic_street_view,
                    contentDescription = "ดูภาพถนนจริง Street View",
                    tint = DropwayPrimaryDark,
                    onClick = {
                        if (GoogleMapsHelper.isGoogleMapsSdkUsable(context)) {
                            showStreetViewDialog = true
                        } else {
                            LocationUtils.openStreetView(context, currentLat, currentLng)
                        }
                    }
                )
            }

            // Hint bar at the bottom in Picker Mode
            if (isPickerMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_street_view),
                            contentDescription = null,
                            tint = DropwayPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "เลื่อนแผนที่ให้หมุดชี้ที่ตำแหน่งบ้านหรือจุดส่งพัสดุ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }

    // In-App Native Google Street View 360° Panorama Dialog
    if (showStreetViewDialog) {
        DropwayStreetViewDialog(
            latitude = currentLat,
            longitude = currentLng,
            title = "ภาพถนนจริง Street View",
            onDismiss = { showStreetViewDialog = false }
        )
    }
}

/**
 * Individual native raster tile composable.
 * Synchronously checks in-memory LruCache to eliminate any draw delay,
 * and asynchronously loads from disk or network via MapTileLoader.
 */
@Composable
private fun NativeTileItem(
    tileUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(tileUrl) { mutableStateOf<Bitmap?>(MapTileLoader.getMemoryBitmap(tileUrl)) }

    LaunchedEffect(tileUrl) {
        if (bitmap == null) {
            val loaded = MapTileLoader.loadTile(context, tileUrl)
            if (loaded != null) {
                bitmap = loaded
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier
        )
    }
}

/**
 * Vector grid backdrop canvas, based on Velnox MapBackdropCanvas.
 * Ensures a clean, pleasant grid appears while tiles are loading or when offline,
 * rather than a jarring black or empty canvas.
 */
@Composable
private fun MapBackdropCanvas(
    isSatellite: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSatellite) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val gridColor = if (isSatellite) Color(0xFF1E293B).copy(alpha = 0.6f) else Color(0xFFCBD5E1).copy(alpha = 0.6f)

    Canvas(modifier = modifier) {
        drawRect(color = bgColor)
        val step = 32.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

@Composable
private fun FloatingRoundButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color = DropwayNavy,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.size(38.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
