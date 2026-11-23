package com.pocketdev.dropway.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.pocketdev.dropway.BuildConfig
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.NetworkUtils
import com.pocketdev.dropway.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class DropwayMapType(val label: String, val layerKey: String) {
    HYBRID("ไฮบริด", "hybrid"),
    ROAD("ถนน", "road"),
    SATELLITE("ดาวเทียม", "satellite"),
    TERRAIN("ภูมิประเทศ", "terrain")
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
 * Dual-Engine Map Viewer:
 * 1. Primary: Official Google Maps Android SDK (when configured with valid key and Google Play Services).
 * 2. Compliant Fallback: Leaflet WebView using OpenStreetMap, Esri World Imagery, and OpenTopoMap
 *    with proper attributions and zero Google tile scraping.
 *
 * Provides real Google Street View 360° launcher, layer switching, HUD pill,
 * fixed center pin in picker mode, and colored markers in viewer mode.
 * Strictly adheres to the zero-emoji policy.
 */
@SuppressLint("SetJavaScriptEnabled")
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
    val useGoogleMaps = remember { GoogleMapsHelper.isGoogleMapsSdkUsable(context) }

    var currentMapType by remember { mutableStateOf(DropwayMapType.HYBRID) }
    var currentLat by remember { mutableStateOf(initialLat) }
    var currentLng by remember { mutableStateOf(initialLng) }

    // Google Maps SDK state
    var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }
    var isGoogleMapReady by remember { mutableStateOf(false) }

    // WebView Fallback state
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }

    var showStreetViewDialog by remember { mutableStateOf(false) }

    // Pre-load map html
    val mapHtml = remember {
        try {
            context.assets.open("map.html").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    // Sync external coordinate changes (e.g. city presets)
    LaunchedEffect(initialLat, initialLng) {
        currentLat = initialLat
        currentLng = initialLng
        if (useGoogleMaps) {
            googleMapRef?.let { gMap ->
                val curPos = gMap.cameraPosition.target
                if (Math.abs(curPos.latitude - initialLat) > 0.0001 || Math.abs(curPos.longitude - initialLng) > 0.0001) {
                    gMap.animateCamera(CameraUpdateFactory.newLatLng(LatLng(initialLat, initialLng)))
                }
            }
        } else {
            webViewRef?.evaluateJavascript("setMapView($initialLat, $initialLng, ${initialZoom.toInt()});", null)
        }
    }

    // Sync layer switches
    LaunchedEffect(currentMapType) {
        if (useGoogleMaps) {
            googleMapRef?.let { gMap ->
                gMap.mapType = when (currentMapType) {
                    DropwayMapType.HYBRID -> GoogleMap.MAP_TYPE_HYBRID
                    DropwayMapType.ROAD -> GoogleMap.MAP_TYPE_NORMAL
                    DropwayMapType.SATELLITE -> GoogleMap.MAP_TYPE_SATELLITE
                    DropwayMapType.TERRAIN -> GoogleMap.MAP_TYPE_TERRAIN
                }
            }
        } else {
            webViewRef?.evaluateJavascript("switchLayer('${currentMapType.layerKey}');", null)
        }
    }

    // Sync markers in Google Map
    fun syncGoogleMapMarkers(gMap: GoogleMap) {
        if (isPickerMode) return
        gMap.clear()
        markers.forEach { m ->
            val hue = when (m.status) {
                "DELIVERED" -> BitmapDescriptorFactory.HUE_GREEN
                "COD" -> BitmapDescriptorFactory.HUE_ORANGE
                else -> BitmapDescriptorFactory.HUE_AZURE
            }
            val marker = gMap.addMarker(
                MarkerOptions()
                    .position(LatLng(m.latitude, m.longitude))
                    .title(m.title)
                    .snippet(m.subtitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
            )
            marker?.tag = m.id
        }
        gMap.setOnMarkerClickListener { marker ->
            val id = marker.tag as? Long
            if (id != null) {
                onMarkerClick?.invoke(id)
            }
            false
        }
    }

    LaunchedEffect(markers, isGoogleMapReady) {
        if (useGoogleMaps && isGoogleMapReady && !isPickerMode) {
            googleMapRef?.let { syncGoogleMapMarkers(it) }
        }
    }

    // Sync markers in WebView Fallback Map
    fun updateMarkersInWebView(wv: WebView?) {
        if (wv == null || isPickerMode) return
        try {
            val arr = JSONArray()
            markers.forEach { m ->
                val obj = JSONObject()
                obj.put("id", m.id.toString())
                obj.put("latitude", m.latitude)
                obj.put("longitude", m.longitude)
                obj.put("title", m.title)
                obj.put("status", m.status)
                obj.put("subtitle", m.subtitle)
                arr.put(obj)
            }
            val js = "updateMarkers(${arr.toString()});"
            wv.evaluateJavascript(js, null)
        } catch (_: Exception) {}
    }

    LaunchedEffect(markers, isWebViewReady) {
        if (!useGoogleMaps && isWebViewReady && !isPickerMode) {
            updateMarkersInWebView(webViewRef)
        }
    }

    // Bridge for JS to Android communication (Dispatched to Main Thread)
    class AndroidBridge(
        private val onMoved: ((Double, Double) -> Unit)?,
        private val onMarkerSelected: ((Long) -> Unit)?,
        private val onLoaded: () -> Unit
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun onCenterMoved(lat: Double, lng: Double) {
            mainHandler.post {
                currentLat = lat
                currentLng = lng
                onMoved?.invoke(lat, lng)
            }
        }

        @JavascriptInterface
        fun onMarkerSelected(idStr: String) {
            val id = idStr.toLongOrNull() ?: return
            mainHandler.post {
                onMarkerSelected?.invoke(id)
            }
        }

        @JavascriptInterface
        fun onMapLoaded() {
            mainHandler.post {
                isWebViewReady = true
                onLoaded()
            }
        }

        @JavascriptInterface
        fun onMapReady() {
            onMapLoaded()
        }

        @JavascriptInterface
        fun onMapError(@Suppress("UNUSED_PARAMETER") error: String) {}
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            .background(Color(0xFFF1F5F9))
    ) {
        if (useGoogleMaps) {
            // Engine 1: Native Google Maps SDK MapView
            val mapView = rememberMapViewWithLifecycle()
            AndroidView(
                factory = {
                    mapView.apply {
                        getMapAsync { gMap ->
                            googleMapRef = gMap
                            gMap.mapType = when (currentMapType) {
                                DropwayMapType.HYBRID -> GoogleMap.MAP_TYPE_HYBRID
                                DropwayMapType.ROAD -> GoogleMap.MAP_TYPE_NORMAL
                                DropwayMapType.SATELLITE -> GoogleMap.MAP_TYPE_SATELLITE
                                DropwayMapType.TERRAIN -> GoogleMap.MAP_TYPE_TERRAIN
                            }
                            gMap.uiSettings.apply {
                                isZoomControlsEnabled = false
                                isCompassEnabled = false
                                isMyLocationButtonEnabled = false
                                isMapToolbarEnabled = false
                            }
                            val startPos = LatLng(initialLat, initialLng)
                            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPos, initialZoom))

                            if (isPickerMode) {
                                gMap.setOnCameraMoveListener {
                                    val target = gMap.cameraPosition.target
                                    currentLat = target.latitude
                                    currentLng = target.longitude
                                    onCoordinatesChanged?.invoke(target.latitude, target.longitude)
                                }
                            } else {
                                syncGoogleMapMarkers(gMap)
                            }
                            isGoogleMapReady = true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Engine 2: Compliant Leaflet WebView Map (OpenStreetMap / Esri / OpenTopoMap)
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = false
                            displayZoomControls = false
                        }

                        val bridge = AndroidBridge(
                            onMoved = { lat, lng ->
                                if (isPickerMode) {
                                    onCoordinatesChanged?.invoke(lat, lng)
                                }
                            },
                            onMarkerSelected = onMarkerClick,
                            onLoaded = {
                                evaluateJavascript(
                                    "setupMap($initialLat, $initialLng, ${initialZoom.toInt()}, $isPickerMode, '${currentMapType.layerKey}');",
                                    null
                                )
                                if (!isPickerMode) {
                                    updateMarkersInWebView(this)
                                }
                            }
                        )
                        addJavascriptInterface(bridge, "AndroidBridge")
                        addJavascriptInterface(bridge, "AndroidMap")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(
                                    "setupMap($initialLat, $initialLng, ${initialZoom.toInt()}, $isPickerMode, '${currentMapType.layerKey}');",
                                    null
                                )
                                if (!isPickerMode) {
                                    updateMarkersInWebView(view)
                                }
                            }
                        }

                        loadDataWithBaseURL("https://dropway.local", mapHtml, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading Overlay
        val isMapLoaded = if (useGoogleMaps) isGoogleMapReady else isWebViewReady
        AnimatedVisibility(
            visible = !isMapLoaded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DropwayNavy.copy(alpha = 0.90f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (useGoogleMaps) "กำลังโหลด Google Maps..." else "กำลังโหลดแผนที่ดาวเทียม...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // FIXED CENTER PIN (Picker Mode Only)
        if (isPickerMode) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = (-18).dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map_pin),
                        contentDescription = "หมุดตำแหน่ง",
                        tint = DropwayPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                    Canvas(
                        modifier = Modifier
                            .size(14.dp, 6.dp)
                            .offset(y = (-2).dp)
                    ) {
                        drawOval(Color(0x66000000))
                    }
                }
            }
        }

        // Top Controls: Segmented Map Switcher [ ไฮบริด | ถนน | ดาวเทียม | ภูมิประเทศ ]
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
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
                            val iconRes = when (type) {
                                DropwayMapType.ROAD -> R.drawable.ic_map
                                DropwayMapType.HYBRID -> R.drawable.ic_layers
                                DropwayMapType.SATELLITE -> R.drawable.ic_satellite
                                DropwayMapType.TERRAIN -> R.drawable.ic_navigation
                            }
                            Icon(
                                painter = painterResource(id = iconRes),
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

        // Live Coordinates HUD Pill (Top Left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 10.dp),
            shape = RoundedCornerShape(10.dp),
            color = DropwayNavy.copy(alpha = 0.92f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = String.format(Locale.US, "%.5f, %.5f", currentLat, currentLng),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        // Offline Warning Banner
        if (!isOnline) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 46.dp),
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
                        text = "ออฟไลน์: ภาพดาวเทียมและ Street View ต้องใช้เน็ต • ข้อมูลพัสดุทำงานได้ปกติ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                }
            }
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
                    if (useGoogleMaps) {
                        googleMapRef?.animateCamera(CameraUpdateFactory.zoomIn())
                    } else {
                        webViewRef?.evaluateJavascript("zoomIn();", null)
                    }
                }
            )

            // Zoom Out
            FloatingRoundButton(
                iconRes = R.drawable.ic_minus,
                contentDescription = "ซูมออก",
                onClick = {
                    if (useGoogleMaps) {
                        googleMapRef?.animateCamera(CameraUpdateFactory.zoomOut())
                    } else {
                        webViewRef?.evaluateJavascript("zoomOut();", null)
                    }
                }
            )

            // GPS Current / Initial Position Reset
            FloatingRoundButton(
                iconRes = R.drawable.ic_my_location,
                contentDescription = "ตำแหน่งเริ่มต้น",
                onClick = {
                    currentLat = initialLat
                    currentLng = initialLng
                    if (useGoogleMaps) {
                        googleMapRef?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(initialLat, initialLng), initialZoom)
                        )
                    } else {
                        webViewRef?.evaluateJavascript(
                            "setMapView($initialLat, $initialLng, ${initialZoom.toInt()});",
                            null
                        )
                    }
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

            // Real Google Street View 360° Panorama Launcher (Zero-API: Launches native Google Maps app directly)
            FloatingRoundButton(
                iconRes = R.drawable.ic_street_view,
                contentDescription = "ดูภาพถนนจริง Street View",
                tint = DropwayPrimaryDark,
                onClick = {
                    if (useGoogleMaps) {
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
                        text = "เลื่อนแผนที่ให้หมุดชี้ที่หลังคาหรือหน้าบ้าน",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
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
