package com.pocketdev.dropway.ui.components

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewPanoramaCamera
import com.google.android.gms.maps.model.StreetViewPanoramaLocation
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.NetworkUtils
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun rememberStreetViewPanoramaViewWithLifecycle(): StreetViewPanoramaView {
    val context = LocalContext.current
    val streetView = remember { StreetViewPanoramaView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, streetView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> streetView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> streetView.onStart()
                Lifecycle.Event.ON_RESUME -> streetView.onResume()
                Lifecycle.Event.ON_PAUSE -> streetView.onPause()
                Lifecycle.Event.ON_STOP -> streetView.onStop()
                Lifecycle.Event.ON_DESTROY -> streetView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
    return streetView
}

/**
 * Fullscreen Native Google Street View Panorama viewer with rotation, zoom,
 * connected street navigation, coverage checking, and graceful offline fallback.
 * Strictly adheres to the zero-emoji policy (vector drawables only).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DropwayStreetViewDialog(
    latitude: Double,
    longitude: Double,
    title: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isOnline = remember { NetworkUtils.isNetworkAvailable(context) }
    var isLoading by remember { mutableStateOf(true) }
    var isCoverageAvailable by remember { mutableStateOf<Boolean?>(null) }
    var panoramaRef by remember { mutableStateOf<StreetViewPanorama?>(null) }
    var connectedLinksCount by remember { mutableStateOf(0) }
    var currentRadius by remember { mutableStateOf(50) }

    // Fallback timeout in case no Street View imagery exists for this coordinate
    LaunchedEffect(currentRadius, latitude, longitude) {
        isLoading = true
        delay(6000L)
        if (isLoading && isCoverageAvailable != true) {
            isLoading = false
            isCoverageAvailable = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DropwayNavy
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isOnline) {
                    // Offline Fallback Card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xE60F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEF3C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_warning),
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "โหมดออฟไลน์ (Offline Mode)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "การโหลดภาพพาโนรามา 360° ของ Google Street View จำเป็นต้องเชื่อมต่ออินเทอร์เน็ต พิกัด GPS และข้อมูลพัสดุในเครื่องยังคงทำงานได้ตามปกติ",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = DropwayNavy),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("กลับสู่แผนที่", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Native StreetViewPanoramaView wrapped in Compose
                    val streetView = rememberStreetViewPanoramaViewWithLifecycle()
                    AndroidView(
                        factory = {
                            streetView.apply {
                                getStreetViewPanoramaAsync { panorama ->
                                    panoramaRef = panorama
                                    panorama.isPanningGesturesEnabled = true
                                    panorama.isZoomGesturesEnabled = true
                                    panorama.isUserNavigationEnabled = true
                                    panorama.isStreetNamesEnabled = true

                                    panorama.setOnStreetViewPanoramaChangeListener { location: StreetViewPanoramaLocation? ->
                                        if (location == null || location.position == null) {
                                            isCoverageAvailable = false
                                            isLoading = false
                                        } else {
                                            isCoverageAvailable = true
                                            isLoading = false
                                            connectedLinksCount = location.links?.size ?: 0
                                        }
                                    }

                                    panorama.setPosition(LatLng(latitude, longitude), currentRadius)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Loading Indicator
                    AnimatedVisibility(
                        visible = isLoading,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DropwayNavy.copy(alpha = 0.92f),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "กำลังโหลดภาพมุมมองถนนจริง 360°...",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Coverage Unavailable Fallback View
                    if (isCoverageAvailable == false && !isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xE60F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 12.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFEF3C7)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_warning),
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "ไม่มีภาพ Street View ในบริเวณนี้",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Google ยังไม่มีข้อมูลภาพถ่ายระดับถนน 360° ในพิกัดนี้ (อาจเป็นทางส่วนบุคคล ซอยแคบ หรือนอกพื้นที่สำรวจ)",
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    if (currentRadius <= 100) {
                                        OutlinedButton(
                                            onClick = {
                                                currentRadius = 150
                                                isLoading = true
                                                isCoverageAvailable = null
                                                panoramaRef?.setPosition(LatLng(latitude, longitude), 150)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_search),
                                                contentDescription = null,
                                                tint = DropwayPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("ค้นหาถนนใกล้เคียง (รัศมี 150 ม.)", fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            LocationUtils.openStreetView(context, latitude, longitude)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_export),
                                            contentDescription = null,
                                            tint = DropwayPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("เปิดใน Google Maps แอป", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = onDismiss,
                                        colors = ButtonDefaults.buttonColors(containerColor = DropwayNavy),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("กลับสู่แผนที่", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Floating 360 Rotation & Zoom Controls (Right Side)
                    if (isCoverageAvailable == true && !isLoading) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Rotate Left 45°
                            FloatingPanoramaButton(
                                iconRes = R.drawable.ic_arrow_back,
                                contentDescription = "หมุนมุมมองซ้าย 45 องศา",
                                onClick = {
                                    panoramaRef?.let { p ->
                                        val cam = p.panoramaCamera
                                        val newBearing = (cam.bearing - 45f + 360f) % 360f
                                        p.animateTo(
                                            StreetViewPanoramaCamera.Builder().bearing(newBearing).tilt(cam.tilt).zoom(cam.zoom).build(),
                                            300
                                        )
                                    }
                                }
                            )

                            // Rotate Right 45°
                            FloatingPanoramaButton(
                                iconRes = R.drawable.ic_arrow_forward,
                                contentDescription = "หมุนมุมมองขวา 45 องศา",
                                onClick = {
                                    panoramaRef?.let { p ->
                                        val cam = p.panoramaCamera
                                        val newBearing = (cam.bearing + 45f) % 360f
                                        p.animateTo(
                                            StreetViewPanoramaCamera.Builder().bearing(newBearing).tilt(cam.tilt).zoom(cam.zoom).build(),
                                            300
                                        )
                                    }
                                }
                            )

                            // Reset North
                            FloatingPanoramaButton(
                                iconRes = R.drawable.ic_navigation,
                                contentDescription = "ทิศเหนือ",
                                tint = DropwayPrimary,
                                onClick = {
                                    panoramaRef?.let { p ->
                                        val cam = p.panoramaCamera
                                        p.animateTo(
                                            StreetViewPanoramaCamera.Builder().bearing(0f).tilt(0f).zoom(cam.zoom).build(),
                                            300
                                        )
                                    }
                                }
                            )

                            // Zoom In
                            FloatingPanoramaButton(
                                iconRes = R.drawable.ic_add,
                                contentDescription = "ซูมภาพเข้า",
                                onClick = {
                                    panoramaRef?.let { p ->
                                        val cam = p.panoramaCamera
                                        val newZoom = (cam.zoom + 0.5f).coerceAtMost(5f)
                                        p.animateTo(
                                            StreetViewPanoramaCamera.Builder().bearing(cam.bearing).tilt(cam.tilt).zoom(newZoom).build(),
                                            300
                                        )
                                    }
                                }
                            )

                            // Zoom Out
                            FloatingPanoramaButton(
                                iconRes = R.drawable.ic_minus,
                                contentDescription = "ซูมภาพออก",
                                onClick = {
                                    panoramaRef?.let { p ->
                                        val cam = p.panoramaCamera
                                        val newZoom = (cam.zoom - 0.5f).coerceAtLeast(0f)
                                        p.animateTo(
                                            StreetViewPanoramaCamera.Builder().bearing(cam.bearing).tilt(cam.tilt).zoom(newZoom).build(),
                                            300
                                        )
                                    }
                                }
                            )
                        }

                        // Bottom Navigation Guide Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DropwayNavy.copy(alpha = 0.92f),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_street_view),
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (connectedLinksCount > 0)
                                        "แตะลูกศรบนพื้นถนนเพื่อก้าวไปข้างหน้า/หลัง (${connectedLinksCount} เส้นทาง) • หมุน 360°"
                                    else
                                        "ลากนิ้วเพื่อหมุนดูรอบทิศ 360° • หนีบเข้าออกเพื่อซูม",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Top Header Bar
                Surface(
                    color = DropwayNavy.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "ปิด",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_street_view),
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title ?: "ภาพถนนจริง Google Street View 360°",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "พิกัด: %.6f, %.6f", latitude, longitude),
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingPanoramaButton(
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
        modifier = Modifier.size(40.dp)
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
