package com.pocketdev.dropway.ui.components

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
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
import com.pocketdev.dropway.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class DropwayMapType {
    STREET,     // Road & street map (Google Road / OpenStreetMap / CartoDB)
    SATELLITE   // High-resolution satellite imagery (Google Hybrid / Esri)
}

data class MapMarkerItem(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val status: String, // "DELIVERED", "PENDING", "COD"
    val subtitle: String = ""
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DropwayMapView(
    modifier: Modifier = Modifier,
    initialLat: Double = 18.788300,
    initialLng: Double = 98.985300,
    initialZoom: Int = 16,
    isPickerMode: Boolean = true, // If true: shows fixed center pin and updates coordinates on drag
    markers: List<MapMarkerItem> = emptyList(),
    onCoordinatesChanged: ((Double, Double) -> Unit)? = null,
    onMarkerClick: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    var currentMapType by remember { mutableStateOf(DropwayMapType.STREET) }
    var currentLat by remember { mutableStateOf(initialLat) }
    var currentLng by remember { mutableStateOf(initialLng) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

    // Pre-load self-contained map html with inlined leaflet assets
    val mapHtml = remember {
        try {
            context.assets.open("map.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
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
        fun onMapLoaded() {
            mainHandler.post {
                isMapReady = true
                onLoaded()
            }
        }
    }

    // Update markers when list changes
    LaunchedEffect(markers, isMapReady) {
        if (isMapReady && !isPickerMode && webViewRef != null) {
            val jsonArray = JSONArray()
            markers.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("latitude", item.latitude)
                    put("longitude", item.longitude)
                    put("title", item.title)
                    put("status", item.status)
                }
                jsonArray.put(obj)
            }
            webViewRef?.evaluateJavascript("updateMarkers(${jsonArray.toString()})", null)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            .background(Color(0xFFF1F5F9))
    ) {
        // Map WebView with HTTPS origin for tile access
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            android.util.Log.d(
                                "DropwayMap",
                                "${consoleMessage?.messageLevel()}: ${consoleMessage?.message()} (${consoleMessage?.lineNumber()})"
                            )
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.proceed() // Allow tile servers across all network types
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val layerStr = if (currentMapType == DropwayMapType.SATELLITE) "satellite" else "street"
                            view?.evaluateJavascript(
                                "setupMap($initialLat, $initialLng, $initialZoom, $isPickerMode, '$layerStr')",
                                null
                            )
                        }
                    }
                    addJavascriptInterface(
                        AndroidMapBridge(
                            onMoved = { lat, lng ->
                                currentLat = lat
                                currentLng = lng
                                onCoordinatesChanged?.invoke(lat, lng)
                            },
                            onMarkerSelected = onMarkerClick,
                            onLoaded = {
                                isMapReady = true
                                if (!isPickerMode && markers.isNotEmpty()) {
                                    val jsonArray = JSONArray()
                                    markers.forEach { item ->
                                        val obj = JSONObject().apply {
                                            put("id", item.id)
                                            put("latitude", item.latitude)
                                            put("longitude", item.longitude)
                                            put("title", item.title)
                                            put("status", item.status)
                                        }
                                        jsonArray.put(obj)
                                    }
                                    evaluateJavascript("updateMarkers(${jsonArray.toString()})", null)
                                }
                            }
                        ),
                        "AndroidBridge"
                    )

                    // Load self-contained HTML with https:// base URL for unrestricted HTTPS tile loading
                    loadDataWithBaseURL("https://dropway.app", mapHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { webView ->
                if (isMapReady) {
                    webView.evaluateJavascript("setMapView($initialLat, $initialLng, $initialZoom)", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading overlay until map engine signals loaded
        AnimatedVisibility(
            visible = !isMapReady,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DropwayNavy.copy(alpha = 0.88f),
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
                        text = "กำลังเปิดแผนที่...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // FIXED CENTER PIN (Only in Picker Mode)
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

        // Top Modern Controls: Segmented Map Switcher [ ถนน (Street) | ดาวเทียม (Satellite) ]
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Street Mode Tab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (currentMapType == DropwayMapType.STREET) DropwayPrimary else Color.Transparent,
                    modifier = Modifier.clickable {
                        currentMapType = DropwayMapType.STREET
                        webViewRef?.evaluateJavascript("switchLayer('street')", null)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_map),
                            contentDescription = null,
                            tint = if (currentMapType == DropwayMapType.STREET) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ถนน",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMapType == DropwayMapType.STREET) Color.White else Color(0xFF0F172A)
                        )
                    }
                }

                // Satellite Mode Tab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (currentMapType == DropwayMapType.SATELLITE) DropwayNavy else Color.Transparent,
                    modifier = Modifier.clickable {
                        currentMapType = DropwayMapType.SATELLITE
                        webViewRef?.evaluateJavascript("switchLayer('satellite')", null)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_satellite),
                            contentDescription = null,
                            tint = if (currentMapType == DropwayMapType.SATELLITE) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ดาวเทียม",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMapType == DropwayMapType.SATELLITE) Color.White else Color(0xFF0F172A)
                        )
                    }
                }
            }
        }

        // Live Coordinates HUD Pill (Top Left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 12.dp),
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

        // Floating Action Buttons (Right Side: Zoom +, Zoom -, My Location, Google Maps, Street View 360)
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
                onClick = { webViewRef?.evaluateJavascript("zoomIn()", null) }
            )

            // Zoom Out
            FloatingRoundButton(
                iconRes = R.drawable.ic_minus,
                contentDescription = "ซูมออก",
                onClick = { webViewRef?.evaluateJavascript("zoomOut()", null) }
            )

            // GPS Current Location
            FloatingRoundButton(
                iconRes = R.drawable.ic_my_location,
                contentDescription = "ตำแหน่งปัจจุบัน",
                onClick = {
                    webViewRef?.evaluateJavascript("setMapView(18.788300, 98.985300, 16)", null)
                }
            )

            // Open in Google Maps App
            FloatingRoundButton(
                iconRes = R.drawable.ic_navigation,
                contentDescription = "เปิดใน Google Maps",
                tint = DropwayPrimary,
                onClick = {
                    LocationUtils.openNavigation(context, currentLat, currentLng, "พิกัดที่เลือก")
                }
            )

            // Real Google Street View 360° Panorama Launcher
            FloatingRoundButton(
                iconRes = R.drawable.ic_street_view,
                contentDescription = "ดูภาพถนนจริง Street View",
                tint = DropwayPrimaryDark,
                onClick = {
                    LocationUtils.openStreetView(context, currentLat, currentLng)
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
