package com.pocketdev.dropway.ui.components

import android.os.Bundle
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.NetworkUtils
import com.pocketdev.dropway.ui.theme.*
import java.util.Locale

enum class DropwayMapType(val label: String, val mapType: Int) {
    ROAD("ถนน", GoogleMap.MAP_TYPE_NORMAL),
    HYBRID("ไฮบริด", GoogleMap.MAP_TYPE_HYBRID),
    SATELLITE("ดาวเทียม", GoogleMap.MAP_TYPE_SATELLITE),
    TERRAIN("ภูมิประเทศ", GoogleMap.MAP_TYPE_TERRAIN)
}

data class MapMarkerItem(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val status: String, // "DELIVERED", "PENDING", "COD"
    val subtitle: String = ""
)

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
    return mapView
}

/**
 * Native Google Maps Android SDK Composable MapView supporting:
 * - Real Google Satellite, Hybrid (Satellite + Road Labels), Normal Road, and Terrain map layers
 * - Camera state preservation when toggling layers
 * - Fixed center pin with live target coordinate updates in picker mode
 * - Custom status markers (Delivered = Green, COD = Amber, Pending = Blue) in delivery mode
 * - Integrated Google Street View 360° Panorama launcher
 * - Offline detection and warning banner
 * - Strict zero-emoji compliance
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
    var currentMapType by remember { mutableStateOf(DropwayMapType.ROAD) }
    var currentLat by remember { mutableStateOf(initialLat) }
    var currentLng by remember { mutableStateOf(initialLng) }
    var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var showStreetViewDialog by remember { mutableStateOf(false) }

    val mapView = rememberMapViewWithLifecycle()

    fun updateMarkers(map: GoogleMap?) {
        if (map == null || isPickerMode) return
        map.clear()
        markers.forEach { item ->
            val hue = when (item.status) {
                "DELIVERED" -> BitmapDescriptorFactory.HUE_GREEN
                "COD" -> BitmapDescriptorFactory.HUE_ORANGE
                else -> BitmapDescriptorFactory.HUE_AZURE
            }
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(item.latitude, item.longitude))
                    .title(item.title)
                    .snippet(item.subtitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
            )
            marker?.tag = item.id
        }
    }

    LaunchedEffect(markers, isMapReady) {
        if (isMapReady && !isPickerMode) {
            updateMarkers(googleMapRef)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            .background(Color(0xFFF1F5F9))
    ) {
        // Native Google MapView
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { googleMap ->
                        googleMapRef = googleMap
                        googleMap.mapType = currentMapType.mapType
                        googleMap.uiSettings.apply {
                            isZoomControlsEnabled = false // Provided by custom buttons
                            isCompassEnabled = true
                            isMyLocationButtonEnabled = false
                            isMapToolbarEnabled = false
                        }

                        val target = LatLng(initialLat, initialLng)
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, initialZoom))

                        googleMap.setOnCameraIdleListener {
                            val pos = googleMap.cameraPosition.target
                            currentLat = pos.latitude
                            currentLng = pos.longitude
                            if (isPickerMode) {
                                onCoordinatesChanged?.invoke(pos.latitude, pos.longitude)
                            }
                        }

                        googleMap.setOnMarkerClickListener { marker ->
                            val id = marker.tag as? Long
                            if (id != null) {
                                onMarkerClick?.invoke(id)
                                true
                            } else false
                        }

                        isMapReady = true
                        updateMarkers(googleMap)
                    }
                }
            },
            update = {
                googleMapRef?.let { map ->
                    if (map.mapType != currentMapType.mapType) {
                        map.mapType = currentMapType.mapType
                    }
                }
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
                        text = "กำลังโหลดแผนที่ Google Maps...",
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

        // Top Modern Controls: Segmented Map Switcher [ ถนน | ไฮบริด | ดาวเทียม | ภูมิประเทศ ]
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
                            googleMapRef?.mapType = type.mapType
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
                onClick = { googleMapRef?.animateCamera(CameraUpdateFactory.zoomIn()) }
            )

            // Zoom Out
            FloatingRoundButton(
                iconRes = R.drawable.ic_minus,
                contentDescription = "ซูมออก",
                onClick = { googleMapRef?.animateCamera(CameraUpdateFactory.zoomOut()) }
            )

            // GPS Current Location / Reset
            FloatingRoundButton(
                iconRes = R.drawable.ic_my_location,
                contentDescription = "ตำแหน่งเริ่มต้น",
                onClick = {
                    googleMapRef?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(initialLat, initialLng), initialZoom)
                    )
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
