package com.pocketdev.dropway.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class DropwayMapType {
    STREET,     // Road & street map (CartoDB / OpenStreetMap)
    SATELLITE   // High-resolution satellite imagery (Esri World Imagery)
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

    // Bridge for JS to Android communication
    class AndroidMapBridge {
        @JavascriptInterface
        fun onCenterMoved(lat: Double, lng: Double) {
            currentLat = lat
            currentLng = lng
            onCoordinatesChanged?.invoke(lat, lng)
        }

        @JavascriptInterface
        fun onMarkerSelected(idStr: String) {
            val id = idStr.toLongOrNull() ?: return
            onMarkerClick?.invoke(id)
        }

        @JavascriptInterface
        fun onMapLoaded() {
            isMapReady = true
        }
    }

    val htmlTemplate = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <style>
                html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #e5ece7; }
                .leaflet-control-attribution, .leaflet-control-zoom { display: none !important; }
                .custom-pin {
                    width: 28px; height: 28px; border-radius: 50%; border: 3px solid #ffffff;
                    box-shadow: 0 3px 6px rgba(0,0,0,0.35); text-align: center; font-weight: bold;
                    display: flex; align-items: center; justify-content: center; color: white; font-size: 11px;
                }
                .pin-delivered { background: #059669; }
                .pin-cod { background: #d97706; }
                .pin-pending { background: #0284c7; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
                var map;
                var streetLayer, satelliteLayer, labelLayer;
                var currentMarkers = [];
                var isPicker = $isPickerMode;

                function initMap() {
                    // Street Layer: CartoDB Voyager (clean streets with Thai labels)
                    streetLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                        maxZoom: 19,
                        subdomains: 'abcd'
                    });

                    // Satellite Layer: Esri World Imagery (high-res true satellite photo)
                    satelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                        maxZoom: 19
                    });

                    // Transparent street/place labels overlay for satellite
                    labelLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', {
                        maxZoom: 19
                    });

                    map = L.map('map', {
                        center: [$initialLat, $initialLng],
                        zoom: $initialZoom,
                        zoomControl: false,
                        layers: [streetLayer]
                    });

                    if (isPicker) {
                        map.on('move', function() {
                            var center = map.getCenter();
                            if (window.AndroidBridge) {
                                AndroidBridge.onCenterMoved(center.lat, center.lng);
                            }
                        });
                        map.on('moveend', function() {
                            var center = map.getCenter();
                            if (window.AndroidBridge) {
                                AndroidBridge.onCenterMoved(center.lat, center.lng);
                            }
                        });
                    }

                    if (window.AndroidBridge) {
                        AndroidBridge.onMapLoaded();
                    }
                }

                function switchLayer(type) {
                    if (!map) return;
                    if (type === 'satellite') {
                        map.removeLayer(streetLayer);
                        map.addLayer(satelliteLayer);
                        map.addLayer(labelLayer);
                    } else {
                        map.removeLayer(satelliteLayer);
                        map.removeLayer(labelLayer);
                        map.addLayer(streetLayer);
                    }
                }

                function setMapView(lat, lng, zoom) {
                    if (!map) return;
                    map.setView([lat, lng], zoom || map.getZoom(), { animate: true });
                }

                function zoomIn() { if (map) map.zoomIn(); }
                function zoomOut() { if (map) map.zoomOut(); }

                function updateMarkers(markersJson) {
                    if (!map) return;
                    // Clear existing markers
                    for (var i = 0; i < currentMarkers.length; i++) {
                        map.removeLayer(currentMarkers[i]);
                    }
                    currentMarkers = [];

                    var data = JSON.parse(markersJson);
                    for (var j = 0; j < data.length; j++) {
                        var m = data[j];
                        var cls = m.status === 'DELIVERED' ? 'pin-delivered' : (m.status === 'COD' ? 'pin-cod' : 'pin-pending');
                        var icon = L.divIcon({
                            className: '',
                            html: '<div class="custom-pin ' + cls + '">' + (j + 1) + '</div>',
                            iconSize: [28, 28],
                            iconAnchor: [14, 14]
                        });
                        var marker = L.marker([m.latitude, m.longitude], { icon: icon, id: m.id }).addTo(map);
                        marker.on('click', (function(id) {
                            return function() {
                                if (window.AndroidBridge) {
                                    AndroidBridge.onMarkerSelected(String(id));
                                }
                            };
                        })(m.id));
                        currentMarkers.push(marker);
                    }
                }

                document.addEventListener('DOMContentLoaded', initMap);
            </script>
        </body>
        </html>
        """.trimIndent()
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
            webViewRef?.evaluateJavascript("updateMarkers('${jsonArray.toString().replace("'", "\\'")}')", null)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            .background(Color(0xFFE2E8F0))
    ) {
        // Map WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(false)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {}
                    addJavascriptInterface(AndroidMapBridge(), "AndroidBridge")
                    loadDataWithBaseURL("https://unpkg.com", htmlTemplate, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

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
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                            tint = if (currentMapType == DropwayMapType.STREET) Color.White else Color(0xFF1E293B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ถนน",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMapType == DropwayMapType.STREET) Color.White else Color(0xFF1E293B)
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
                            tint = if (currentMapType == DropwayMapType.SATELLITE) Color.White else Color(0xFF1E293B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ดาวเทียม",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMapType == DropwayMapType.SATELLITE) Color.White else Color(0xFF1E293B)
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

        // Floating Action Buttons (Right Side: Zoom +, Zoom -, My Location, Street View)
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
                    val loc = LocationUtils.calculateDistanceMeters(0.0, 0.0, 0.0, 0.0) // ping
                    // Center to current location or default
                    webViewRef?.evaluateJavascript("setMapView(18.788300, 98.985300, 16)", null)
                }
            )

            // Real Google Street View 360° Panorama Launcher
            FloatingRoundButton(
                iconRes = R.drawable.ic_street_view,
                contentDescription = "ดูภาพถนนจริง Street View",
                tint = DropwayPrimary,
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
                        color = Color(0xFF1E293B)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
