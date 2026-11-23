package com.pocketdev.dropway.ui.components

import android.annotation.SuppressLint
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
 * Interactive map viewer rendering real Google Satellite Hybrid, Normal Road,
 * Satellite, and Terrain map tiles with zero external API key requirements.
 * Connects directly to native Google Street View 360° panorama viewer.
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
    var currentMapType by remember { mutableStateOf(DropwayMapType.HYBRID) } // Default to Satellite Hybrid for roof & street view
    var currentLat by remember { mutableStateOf(initialLat) }
    var currentLng by remember { mutableStateOf(initialLng) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var showStreetViewDialog by remember { mutableStateOf(false) }

    // Pre-load self-contained map html with inlined leaflet assets
    val mapHtml = remember {
        try {
            context.assets.open("map.html").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    // Bridge for JS to Android communication (Dispatched to Main Thread)
    class AndroidMapBridge(
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
        fun onMapReady() {
            mainHandler.post {
                isMapReady = true
                onLoaded()
            }
        }
    }

    fun updateMarkers(wv: WebView?) {
        if (wv == null || isPickerMode) return
        try {
            val arr = JSONArray()
            markers.forEach { m ->
                val obj = JSONObject()
                obj.put("id", m.id.toString())
                obj.put("lat", m.latitude)
                obj.put("lng", m.longitude)
                obj.put("title", m.title)
                obj.put("status", m.status)
                arr.put(obj)
            }
            val js = "setMarkers(${arr.toString()});"
            wv.evaluateJavascript(js, null)
        } catch (_: Exception) {}
    }

    LaunchedEffect(markers, isMapReady) {
        if (isMapReady && !isPickerMode) {
            updateMarkers(webViewRef)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            .background(Color(0xFFF1F5F9))
    ) {
        // High-resolution Satellite & Road Leaflet WebView
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

                    addJavascriptInterface(
                        AndroidMapBridge(
                            onMoved = { lat, lng ->
                                if (isPickerMode) {
                                    onCoordinatesChanged?.invoke(lat, lng)
                                }
                            },
                            onMarkerSelected = onMarkerClick,
                            onLoaded = {
                                evaluateJavascript("initMap($initialLat, $initialLng, ${initialZoom.toInt()}, '${currentMapType.layerKey}');", null)
                                if (!isPickerMode) {
                                    updateMarkers(this)
                                }
                            }
                        ),
                        "AndroidMap"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript("initMap($initialLat, $initialLng, ${initialZoom.toInt()}, '${currentMapType.layerKey}');", null)
                            if (!isPickerMode) {
                                updateMarkers(view)
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

        // Loading Overlay until Map is initialized
        AnimatedVisibility(
            visible = !isMapReady,
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
                        text = "กำลังโหลดแผนที่ดาวเทียม...",
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
                    // Drop shadow under pin tip
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
                            webViewRef?.evaluateJavascript("switchLayer('${type.layerKey}');", null)
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

        // Offline Warning Banner (if no internet)
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

        // Floating Action Buttons (Right Side: Zoom +, Zoom -, My Location, Google Maps App, Native Street View)
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
                onClick = { webViewRef?.evaluateJavascript("zoomIn();", null) }
            )

            // Zoom Out
            FloatingRoundButton(
                iconRes = R.drawable.ic_minus,
                contentDescription = "ซูมออก",
                onClick = { webViewRef?.evaluateJavascript("zoomOut();", null) }
            )

            // GPS Current Location / Reset
            FloatingRoundButton(
                iconRes = R.drawable.ic_my_location,
                contentDescription = "ตำแหน่งเริ่มต้น",
                onClick = {
                    webViewRef?.evaluateJavascript("setCenter($initialLat, $initialLng);", null)
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

            // Real Google Street View 360° Panorama Launcher (In-App Native Viewer)
            FloatingRoundButton(
                iconRes = R.drawable.ic_street_view,
                contentDescription = "ดูภาพถนนจริง Street View",
                tint = DropwayPrimaryDark,
                onClick = {
                    showStreetViewDialog = true
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

    // In-App Native Google Street View 360° Panorama Dialog (Untouched from 3d7172f)
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
