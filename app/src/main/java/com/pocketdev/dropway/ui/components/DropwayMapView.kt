package com.pocketdev.dropway.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pocketdev.dropway.ui.components.map.CachedTileBitmap
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
    val attribution: String,
    val maxNativeZoom: Int
) {
    OPEN_STREET_MAP(
        label = "OpenStreetMap",
        layerKey = "osm",
        iconRes = R.drawable.ic_map,
        attribution = "© OpenStreetMap contributors",
        maxNativeZoom = 19
    ),
    SATELLITE(
        label = "ดาวเทียม",
        layerKey = "satellite",
        iconRes = R.drawable.ic_satellite,
        attribution = "© Esri, Maxar, Earthstar Geographics",
        maxNativeZoom = 18 // Esri guaranteed universal high-res imagery coverage level
    ),
    CARTO_VOYAGER(
        label = "Carto",
        layerKey = "carto",
        iconRes = R.drawable.ic_layers,
        attribution = "© CARTO © OpenStreetMap contributors",
        maxNativeZoom = 19
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
    val subtitle: String = "",
    val sequenceNumber: Int? = null,
    val isSelected: Boolean = false
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
 *
 * Performance & Rendering Highlights:
 * - Ultra-smooth 60/120 FPS continuous pan and pinch-to-zoom using stable pointerInput lifecycle.
 * - Deep Satellite Zoom up to Zoom 23.0 with smooth adaptive overzoom.
 * - Multi-level Quadtree fallback searching both Memory and Disk caches to guarantee zero white/blank frames.
 * - Automatic rejection of provider dummy blank tiles.
 * - Precision Micro Pin Markers: compact, translucent, numbered needle markers anchored exactly
 *   to destination coordinates to prevent obscuring rooftop satellite imagery.
 * - Dual-state markers (unselected translucent 58%, selected prominent 90% with bright border).
 * - High-contrast military reticle crosshairs with center opening in picker mode.
 * - Strictly isolated Google Street View 360° panorama integration.
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
    selectedMarkerId: Long? = null,
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

    // Stable reference for coordinate callback so pointerInput coroutines never cancel or drop listeners
    val onCoordinatesChangedState = rememberUpdatedState(onCoordinatesChanged)

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
            val halfW = wPx / 2f
            val halfH = hPx / 2f

            // Multi-touch gestures: Drag pan + Pinch zoom + Double tap zoom
            // Recovered from original smooth implementation in 01b841b
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = (zoom + 1.0).coerceAtMost(MapTileMath.MAX_ZOOM)
                                onCoordinatesChangedState.value?.invoke(currentLat, currentLng)
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
                                onCoordinatesChangedState.value?.invoke(newLat, newLng)
                            }
                        }
                    }
            ) {
                // Layer 1: Stylized vector backdrop canvas (smooth background while tiles load)
                MapBackdropCanvas(
                    isSatellite = (currentMapType == DropwayMapType.SATELLITE),
                    modifier = Modifier.fillMaxSize()
                )

                // Layer 2: Native Slippy Raster Map Tiles with Adaptive Deep Zoom
                val sourceTileZoom = min(zoom.toInt(), currentMapType.maxNativeZoom).coerceIn(MapTileMath.MIN_ZOOM.toInt(), currentMapType.maxNativeZoom)
                val overzoomFactor = 2.0.pow(zoom - sourceTileZoom)
                val scale = overzoomFactor.toFloat()
                val scaledTilePx = (MapTileMath.TILE_SIZE * scale).toFloat()
                val numTiles = 1 shl sourceTileZoom

                val (centerMercX, centerMercY) = MapTileMath.latLngToMercator(currentLat, currentLng, sourceTileZoom.toDouble())

                val minTileX = floor((centerMercX - halfW / scale) / MapTileMath.TILE_SIZE).toInt()
                val maxTileX = ceil((centerMercX + halfW / scale) / MapTileMath.TILE_SIZE).toInt()
                val minTileY = floor((centerMercY - halfH / scale) / MapTileMath.TILE_SIZE).toInt().coerceIn(0, numTiles - 1)
                val maxTileY = ceil((centerMercY + halfH / scale) / MapTileMath.TILE_SIZE).toInt().coerceIn(0, numTiles - 1)

                for (tx in minTileX..maxTileX) {
                    for (ty in minTileY..maxTileY) {
                        val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
                        val tileScreenX = halfW + ((tx * MapTileMath.TILE_SIZE - centerMercX) * scale).toFloat()
                        val tileScreenY = halfH + ((ty * MapTileMath.TILE_SIZE - centerMercY) * scale).toFloat()

                        key("$sourceTileZoom-$wrappedTx-$ty-$currentMapType") {
                            NativeTileItem(
                                mapType = currentMapType,
                                x = wrappedTx,
                                y = ty,
                                z = sourceTileZoom,
                                modifier = Modifier
                                    .offset { IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt()) }
                                    .size((scaledTilePx / density).dp)
                            )
                        }
                    }
                }

                // Layer 3: Precision Micro House Markers (Viewer Mode only)
                if (!isPickerMode) {
                    val sortedMarkers = remember(markers, selectedMarkerId) {
                        markers.mapIndexed { idx, m ->
                            val isSel = m.isSelected || (selectedMarkerId != null && m.id == selectedMarkerId)
                            Triple(idx, m, isSel)
                        }.sortedBy { it.third } // Draw selected marker on top
                    }

                    sortedMarkers.forEach { (index, marker, isSelected) ->
                        val (mX, mY) = MapTileMath.latLngToMercator(marker.latitude, marker.longitude, sourceTileZoom.toDouble())
                        val screenX = halfW + ((mX - centerMercX) * scale).toFloat()
                        val screenY = halfH + ((mY - centerMercY) * scale).toFloat()

                        if (screenX >= -50f && screenX <= wPx + 50f && screenY >= -50f && screenY <= hPx + 50f) {
                            key("marker-${marker.id}") {
                                MicroPinMarker(
                                    marker = marker,
                                    displayNumber = marker.sequenceNumber ?: (index + 1),
                                    isSelected = isSelected,
                                    screenX = screenX,
                                    screenY = screenY,
                                    onClick = { onMarkerClick?.invoke(marker.id) }
                                )
                            }
                        }
                    }
                }

                // Layer 4: Precision Reticle Crosshairs with Center Opening (Picker Mode only)
                if (isPickerMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val crossLen = 16.dp.toPx()
                        val gap = 4.dp.toPx()
                        val strokeColor = if (currentMapType == DropwayMapType.SATELLITE)
                            Color.White.copy(alpha = 0.75f)
                        else
                            Color.Black.copy(alpha = 0.45f)

                        val shadowColor = if (currentMapType == DropwayMapType.SATELLITE)
                            Color.Black.copy(alpha = 0.40f)
                        else
                            Color.White.copy(alpha = 0.50f)

                        // Outer contrast shadow strokes for visibility over light or dark roofs
                        drawLine(color = shadowColor, start = Offset(cx - crossLen, cy + 1f), end = Offset(cx - gap, cy + 1f), strokeWidth = 2.5f)
                        drawLine(color = shadowColor, start = Offset(cx + gap, cy + 1f), end = Offset(cx + crossLen, cy + 1f), strokeWidth = 2.5f)
                        drawLine(color = shadowColor, start = Offset(cx + 1f, cy - crossLen), end = Offset(cx + 1f, cy - gap), strokeWidth = 2.5f)
                        drawLine(color = shadowColor, start = Offset(cx + 1f, cy + gap), end = Offset(cx + 1f, cy + crossLen), strokeWidth = 2.5f)

                        // Main reticle crosshairs
                        drawLine(color = strokeColor, start = Offset(cx - crossLen, cy), end = Offset(cx - gap, cy), strokeWidth = 1.5f)
                        drawLine(color = strokeColor, start = Offset(cx + gap, cy), end = Offset(cx + crossLen, cy), strokeWidth = 1.5f)
                        drawLine(color = strokeColor, start = Offset(cx, cy - crossLen), end = Offset(cx, cy - gap), strokeWidth = 1.5f)
                        drawLine(color = strokeColor, start = Offset(cx, cy + gap), end = Offset(cx, cy + crossLen), strokeWidth = 1.5f)

                        // Center reticle ring: permits looking through the center at individual roof pixels
                        drawCircle(color = strokeColor, radius = gap, style = Stroke(width = 1.2f))
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

                // Live Coordinates & Deep Zoom Status HUD Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DropwayNavy.copy(alpha = 0.92f),
                    shadowElevation = 4.dp
                ) {
                    val currentOverzoom = 2.0.pow(max(0.0, zoom - currentMapType.maxNativeZoom))
                    val hudText = if (currentOverzoom > 1.05) {
                        String.format(
                            Locale.US,
                            "พิกัด: %.6f, %.6f • ซูม %.1fx (ขยาย %.1fx)",
                            currentLat,
                            currentLng,
                            zoom,
                            currentOverzoom
                        )
                    } else {
                        String.format(
                            Locale.US,
                            "พิกัด: %.6f, %.6f • ซูม %.1fx",
                            currentLat,
                            currentLng,
                            zoom
                        )
                    }
                    Text(
                        text = hudText,
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
 * Precision Micro Pin Marker:
 * Compact, translucent, numbered needle marker anchored directly to the destination coordinates.
 * Prevents covering satellite rooftops while remaining clearly identifiable against dark/light roofs.
 */
@Composable
private fun MicroPinMarker(
    marker: MapMarkerItem,
    displayNumber: Int,
    isSelected: Boolean,
    screenX: Float,
    screenY: Float,
    onClick: () -> Unit
) {
    val statusColor = when (marker.status) {
        "DELIVERED" -> Color(0xFF10B981) // Emerald Green
        "COD" -> Color(0xFFF59E0B)       // Warm Amber
        else -> Color(0xFF0284C7)        // Dropway Sky Blue
    }

    val bodySize = if (isSelected) 24.dp else 20.dp
    val needleHeight = if (isSelected) 5.dp else 4.dp
    val needleWidth = if (isSelected) 8.dp else 6.dp
    val totalHeight = bodySize + needleHeight

    val bodyAlpha = if (isSelected) 0.90f else 0.58f
    val needleAlpha = if (isSelected) 0.95f else 0.70f
    val borderWidth = if (isSelected) 1.8.dp else 1.2.dp
    val borderColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.92f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset {
                val wPx = bodySize.toPx()
                val hPx = totalHeight.toPx()
                IntOffset((screenX - wPx / 2f).roundToInt(), (screenY - hPx).roundToInt())
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Translucent Circular Pin Body with Destination Sequence Number
        Box(
            modifier = Modifier
                .size(bodySize)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = bodyAlpha))
                .border(borderWidth, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$displayNumber",
                color = Color.White,
                fontSize = if (isSelected) 11.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }

        // Precision Needle Tip
        Canvas(
            modifier = Modifier
                .size(width = needleWidth, height = needleHeight)
                .offset(y = (-1).dp)
        ) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(
                path = path,
                color = statusColor.copy(alpha = needleAlpha)
            )
            drawLine(
                color = borderColor,
                start = Offset(0f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = borderColor,
                start = Offset(size.width, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Micro ground contact point (centered on target coordinate)
        Canvas(
            modifier = Modifier
                .size(3.dp)
                .offset(y = (-1).dp)
        ) {
            drawCircle(
                color = borderColor,
                radius = size.width / 2f
            )
            drawCircle(
                color = statusColor,
                radius = size.width / 3.5f
            )
        }
    }
}

/**
 * Individual native raster tile composable with multi-level Quadtree progressive replacement.
 * Synchronously checks in-memory LruCache to eliminate any draw delay,
 * and asynchronously loads from disk cache or network via MapTileLoader on Dispatchers.IO.
 */
@Composable
private fun NativeTileItem(
    mapType: DropwayMapType,
    x: Int,
    y: Int,
    z: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileUrl = remember(mapType, x, y, z) { mapType.getTileUrl(x, y, z) }

    var cachedItem by remember(tileUrl) {
        mutableStateOf(
            MapTileLoader.getMemoryBitmapOrParent(x, y, z) { tx, ty, tz ->
                mapType.getTileUrl(tx, ty, tz)
            }
        )
    }

    LaunchedEffect(tileUrl) {
        if (cachedItem?.srcRect != null || cachedItem == null) {
            val loaded = MapTileLoader.loadTileWithFallback(context, x, y, z) { tx, ty, tz ->
                mapType.getTileUrl(tx, ty, tz)
            }
            if (loaded != null) {
                cachedItem = loaded
            }
        }
    }

    val item = cachedItem
    if (item != null) {
        val src = item.srcRect
        if (src == null) {
            Image(
                bitmap = item.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = modifier
            )
        } else {
            Canvas(modifier = modifier) {
                drawIntoCanvas { canvas ->
                    val dst = Rect(0, 0, size.width.toInt(), size.height.toInt())
                    canvas.nativeCanvas.drawBitmap(item.bitmap, src, dst, null)
                }
            }
        }
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
