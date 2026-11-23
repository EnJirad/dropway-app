package com.pocketdev.dropway.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import com.google.android.gms.maps.MapView
import com.pocketdev.dropway.BuildConfig
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.NetworkUtils
import com.pocketdev.dropway.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Standard 2D map providers in Dropway:
 * 1. OpenStreetMap (Standard street map, roads, streets, place names, keyless)
 * 2. OpenFreeMap (Clean modern vector street map style, keyless)
 * 3. Maptoolkit (Official community vector street map, keyless)
 */
enum class DropwayMapType(val label: String, val layerKey: String) {
    OPEN_STREET_MAP("OpenStreetMap", "osm"),
    OPEN_FREE_MAP("OpenFreeMap", "openfreemap"),
    MAPTOOLKIT("Maptoolkit", "maptoolkit")
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
 * Preserved for Google Street View 360° panorama feature.
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
 * Keyless Standard 2D Map Viewer:
 * Uses exclusively OpenStreetMap, OpenFreeMap, and Maptoolkit for 2D map rendering.
 * Eliminates dependency on Google Maps API keys for the 2D map interface.
 * Retains full Google Street View 360° panorama launcher.
 * Strictly adheres to zero-emoji policy.
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

    var currentMapType by remember { mutableStateOf(DropwayMapType.OPEN_STREET_MAP) }
    var currentLat by remember { mutableStateOf(initialLat) }
    var currentLng by remember { mutableStateOf(initialLng) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewReady by remember { mutableStateOf(false) }
    var isTileLoaded by remember { mutableStateOf(false) }
    var mapErrorMessage by remember { mutableStateOf<String?>(null) }

    var showStreetViewDialog by remember { mutableStateOf(false) }

    // Sync external coordinate changes (e.g. city presets)
    LaunchedEffect(initialLat, initialLng) {
        currentLat = initialLat
        currentLng = initialLng
        webViewRef?.evaluateJavascript("setMapView($initialLat, $initialLng, ${initialZoom.toInt()});", null)
    }

    // Sync provider layer switches
    LaunchedEffect(currentMapType) {
        isTileLoaded = false
        mapErrorMessage = null
        webViewRef?.evaluateJavascript("switchProvider('${currentMapType.layerKey}');", null)
    }

    // Sync markers in WebView Map (viewer mode only)
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
        if (isWebViewReady && !isPickerMode) {
            updateMarkersInWebView(webViewRef)
        }
    }

    // Bridge for JS to Android communication (Dispatched to Main Thread)
    class AndroidBridge(
        private val onMoved: ((Double, Double) -> Unit)?,
        private val onMarkerSelected: ((Long) -> Unit)?,
        private val onLoaded: () -> Unit,
        private val onTileSuccess: (String) -> Unit,
        private val onError: (String, String) -> Unit
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
        fun onTileLoaded(provider: String) {
            mainHandler.post {
                isTileLoaded = true
                mapErrorMessage = null
                onTileSuccess(provider)
            }
        }

        @JavascriptInterface
        fun onMapError(provider: String, errorMsg: String) {
            mainHandler.post {
                Log.e("DropwayMapWV", "Map Error ($provider): $errorMsg")
                onError(provider, errorMsg)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            .background(Color(0xFFF1F5F9))
    ) {
        // 2D Map Engine: Keyless MapLibre GL JS WebView Map (OpenStreetMap / OpenFreeMap / Maptoolkit)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        loadsImagesAutomatically = true
                        blockNetworkImage = false
                        blockNetworkLoads = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 DropwayDeliveryApp/1.0 (contact: support@dropway.app)"
                    }

                    val bridge = AndroidBridge(
                        onMoved = { lat, lng ->
                            if (isPickerMode) {
                                onCoordinatesChanged?.invoke(lat, lng)
                            }
                        },
                        onMarkerSelected = onMarkerClick,
                        onLoaded = {
                            if (!isPickerMode) {
                                updateMarkersInWebView(this)
                            }
                        },
                        onTileSuccess = {
                            isTileLoaded = true
                            mapErrorMessage = null
                        },
                        onError = { provider, msg ->
                            val cleanMsg = when {
                                msg.contains("Failed to fetch", ignoreCase = true) -> "เชื่อมต่ออินเทอร์เน็ตไม่ได้"
                                msg.contains("WebGL", ignoreCase = true) -> "อุปกรณ์ไม่รองรับ WebGL"
                                else -> "โหลดไม่สำเร็จ กรุณาสลับผู้ให้บริการ"
                            }
                            mapErrorMessage = "$provider: $cleanMsg"
                        }
                    )
                    addJavascriptInterface(bridge, "AndroidBridge")
                    addJavascriptInterface(bridge, "AndroidMap")

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.proceed()
                        }

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            Log.e("DropwayMapWV", "WebView Error ($errorCode): $description at $failingUrl")
                            mapErrorMessage = "ไม่สามารถโหลดแผนที่ได้ ($description)"
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url ?: return null
                            val host = url.host ?: return null

                            // Intercept OpenStreetMap tile requests to ensure compliant User-Agent and Referer headers
                            if (host == "tile.openstreetmap.org" || host.endsWith(".tile.openstreetmap.org")) {
                                try {
                                    val conn = java.net.URL(url.toString()).openConnection() as java.net.HttpURLConnection
                                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 DropwayDeliveryApp/1.0 (contact: support@dropway.app)")
                                    conn.setRequestProperty("Referer", "https://dropway.app/")
                                    conn.connectTimeout = 8000
                                    conn.readTimeout = 8000
                                    conn.instanceFollowRedirects = true
                                    val code = conn.responseCode
                                    if (code == 200) {
                                        val mimeType = conn.contentType?.substringBefore(';') ?: "image/png"
                                        return WebResourceResponse(mimeType, "UTF-8", conn.inputStream)
                                    } else {
                                        Log.w("DropwayMapWV", "OSM tile returned HTTP $code for $url")
                                    }
                                } catch (e: Exception) {
                                    Log.w("DropwayMapWV", "OSM tile intercept error: ${e.message}")
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

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

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("DropwayMapJS", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                            return true
                        }
                    }

                    loadUrl("file:///android_asset/map.html")
                    webViewRef = this
                }
            },
            update = { wv ->
                webViewRef = wv
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Overlay (smoothly dismissed when first tile renders)
        AnimatedVisibility(
            visible = (!isWebViewReady || !isTileLoaded) && mapErrorMessage == null,
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
                        text = "กำลังโหลดแผนที่...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Error Diagnostic Banner (when a provider fails to load)
        if (mapErrorMessage != null && !isTileLoaded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFEF2F2),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ไม่สามารถแสดงผล ${currentMapType.label}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF991B1B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = mapErrorMessage ?: "กรุณาแตะเลือกผู้ให้บริการอื่นที่เมนูด้านบน",
                        fontSize = 11.sp,
                        color = Color(0xFFB91C1C)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                mapErrorMessage = null
                                isTileLoaded = false
                                webViewRef?.evaluateJavascript("switchProvider('${currentMapType.layerKey}');", null)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("ลองใหม่", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // FIXED CENTER PIN (Picker Mode Only - Single Fixed Center Pin)
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

        // Top Controls: Segmented Provider Switcher + Live Coordinates HUD (Unified Column, Zero Overlap)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Segmented Map Switcher [ OpenStreetMap | OpenFreeMap | Maptoolkit ]
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
                                isTileLoaded = false
                                mapErrorMessage = null
                                webViewRef?.evaluateJavascript("switchProvider('${type.layerKey}');", null)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val iconRes = when (type) {
                                    DropwayMapType.OPEN_STREET_MAP -> R.drawable.ic_map
                                    DropwayMapType.OPEN_FREE_MAP -> R.drawable.ic_layers
                                    DropwayMapType.MAPTOOLKIT -> R.drawable.ic_navigation
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

            Spacer(modifier = Modifier.height(6.dp))

            // Live Coordinates HUD Pill (Centered beneath switcher)
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
                    webViewRef?.evaluateJavascript("zoomIn();", null)
                }
            )

            // Zoom Out
            FloatingRoundButton(
                iconRes = R.drawable.ic_minus,
                contentDescription = "ซูมออก",
                onClick = {
                    webViewRef?.evaluateJavascript("zoomOut();", null)
                }
            )

            // GPS Current / Initial Position Reset
            FloatingRoundButton(
                iconRes = R.drawable.ic_my_location,
                contentDescription = "ตำแหน่งเริ่มต้น",
                onClick = {
                    currentLat = initialLat
                    currentLng = initialLng
                    webViewRef?.evaluateJavascript(
                        "setMapView($initialLat, $initialLng, ${initialZoom.toInt()});",
                        null
                    )
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
