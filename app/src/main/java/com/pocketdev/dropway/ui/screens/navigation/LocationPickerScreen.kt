package com.pocketdev.dropway.ui.screens.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.pocketdev.dropway.R
import com.pocketdev.dropway.ui.theme.DropwayNavy
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.TextPrimary
import com.pocketdev.dropway.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.floor
import kotlin.math.pow

@Composable
fun LocationPickerScreen(
    initialLat: Double?,
    initialLng: Double?,
    onDismiss: () -> Unit,
    onCoordinatesConfirmed: (Double, Double) -> Unit
) {
    val context = LocalContext.current

    // Default to Chiang Mai city coordinates if none provided
    var centerLat by remember { mutableStateOf(initialLat ?: 18.788300) }
    var centerLng by remember { mutableStateOf(initialLng ?: 98.985300) }
    var zoomLevel by remember { mutableStateOf(16f) }

    // Synchronized text for manual editing
    var latText by remember(centerLat) {
        mutableStateOf(String.format(Locale.US, "%.6f", centerLat))
    }
    var lngText by remember(centerLng) {
        mutableStateOf(String.format(Locale.US, "%.6f", centerLng))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Vector Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DropwayPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_map),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "เลือกและระบุพิกัด GPS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "เลื่อนแผนที่เพื่อให้หมุดกึ่งกลางชี้ที่ตำแหน่งบ้าน",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "ปิด",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Map Viewport with FIXED CENTER PIN
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE5ECE7))
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                        .pointerInput(zoomLevel) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomLevel = (zoomLevel * zoom).coerceIn(12f, 19f)
                                val scale = 256.0 * 2.0.pow(zoomLevel.toDouble())
                                val degPerPixelLng = 360.0 / scale
                                val degPerPixelLat = 180.0 / scale

                                centerLng = (centerLng - pan.x * degPerPixelLng).coerceIn(-180.0, 180.0)
                                centerLat = (centerLat + pan.y * degPerPixelLat).coerceIn(-85.0, 85.0)
                            }
                        }
                ) {
                    // Procedural Map Canvas (Street grid, blocks, terrain)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val centerX = canvasWidth / 2f
                        val centerY = canvasHeight / 2f

                        val scale = 256.0 * 2.0.pow(zoomLevel.toDouble())
                        val pixelsPerDegLng = scale / 360.0
                        val pixelsPerDegLat = scale / 180.0

                        // Draw background land tiles
                        drawRect(Color(0xFFE8EDE9))

                        // Draw street grid based on lat/lng modulo
                        val streetSpacingDeg = 0.002 // Approx 200m
                        val minLng = centerLng - (canvasWidth / 2f) / pixelsPerDegLng
                        val maxLng = centerLng + (canvasWidth / 2f) / pixelsPerDegLng
                        val minLat = centerLat - (canvasHeight / 2f) / pixelsPerDegLat
                        val maxLat = centerLat + (canvasHeight / 2f) / pixelsPerDegLat

                        val startLngIndex = floor(minLng / streetSpacingDeg).toInt()
                        val endLngIndex = floor(maxLng / streetSpacingDeg).toInt() + 1
                        val startLatIndex = floor(minLat / streetSpacingDeg).toInt()
                        val endLatIndex = floor(maxLat / streetSpacingDeg).toInt() + 1

                        // Draw block fills (simulated urban parcels)
                        for (i in startLngIndex until endLngIndex) {
                            val lngGrid = i * streetSpacingDeg
                            val screenX = centerX + ((lngGrid - centerLng) * pixelsPerDegLng).toFloat()

                            for (j in startLatIndex until endLatIndex) {
                                val latGrid = j * streetSpacingDeg
                                val screenY = centerY - ((latGrid - centerLat) * pixelsPerDegLat).toFloat()

                                val blockW = (streetSpacingDeg * pixelsPerDegLng).toFloat()
                                val blockH = (streetSpacingDeg * pixelsPerDegLat).toFloat()

                                val hash = (i * 31 + j * 17) % 7
                                val blockColor = when (hash) {
                                    0 -> Color(0xFFD5E8D4) // Park / Greenery
                                    1 -> Color(0xFFF0EBE1) // Residential zone
                                    2 -> Color(0xFFE9EDF4) // Civic zone
                                    else -> Color(0xFFEFEFEF) // General parcel
                                }

                                drawRect(
                                    color = blockColor,
                                    topLeft = Offset(screenX + 5f, screenY - blockH + 5f),
                                    size = Size(blockW - 10f, blockH - 10f)
                                )
                            }
                        }

                        // Draw secondary street lines
                        for (i in startLngIndex..endLngIndex) {
                            val lngGrid = i * streetSpacingDeg
                            val screenX = centerX + ((lngGrid - centerLng) * pixelsPerDegLng).toFloat()
                            drawLine(
                                color = Color.White,
                                start = Offset(screenX, 0f),
                                end = Offset(screenX, canvasHeight),
                                strokeWidth = 12f
                            )
                            drawLine(
                                color = Color(0xFFD1D5DB),
                                start = Offset(screenX, 0f),
                                end = Offset(screenX, canvasHeight),
                                strokeWidth = 1f
                            )
                        }

                        for (j in startLatIndex..endLatIndex) {
                            val latGrid = j * streetSpacingDeg
                            val screenY = centerY - ((latGrid - centerLat) * pixelsPerDegLat).toFloat()
                            drawLine(
                                color = Color.White,
                                start = Offset(0f, screenY),
                                end = Offset(canvasWidth, screenY),
                                strokeWidth = 12f
                            )
                            drawLine(
                                color = Color(0xFFD1D5DB),
                                start = Offset(0f, screenY),
                                end = Offset(canvasWidth, screenY),
                                strokeWidth = 1f
                            )
                        }

                        // Draw main thoroughfare avenue
                        val mainSpacingDeg = streetSpacingDeg * 4
                        val mainLngIndex = floor(minLng / mainSpacingDeg).toInt()
                        for (m in mainLngIndex..(floor(maxLng / mainSpacingDeg).toInt() + 1)) {
                            val avenueLng = m * mainSpacingDeg
                            val screenX = centerX + ((avenueLng - centerLng) * pixelsPerDegLng).toFloat()
                            drawLine(
                                color = Color(0xFFFDE68A),
                                start = Offset(screenX, 0f),
                                end = Offset(screenX, canvasHeight),
                                strokeWidth = 20f
                            )
                        }

                        // Subtle center crosshair guide
                        drawLine(
                            color = Color(0x330284C7),
                            start = Offset(centerX - 30f, centerY),
                            end = Offset(centerX + 30f, centerY),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color(0x330284C7),
                            start = Offset(centerX, centerY - 30f),
                            end = Offset(centerX, centerY + 30f),
                            strokeWidth = 2f
                        )
                    }

                    // FIXED CENTER PIN (Permanently Centered in Viewport)
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
                                contentDescription = "หมุดกึ่งกลาง",
                                tint = DropwayPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                            // Shadow under pin tip
                            Canvas(
                                modifier = Modifier
                                    .size(14.dp, 6.dp)
                                    .offset(y = (-2).dp)
                            ) {
                                drawOval(Color(0x55000000))
                            }
                        }
                    }

                    // Floating Controls Overlay (Top Right: Zoom +, Zoom -, Current GPS)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Zoom in button
                        FloatingMapButton(
                            iconRes = R.drawable.ic_add,
                            contentDescription = "ซูมเข้า",
                            onClick = { zoomLevel = (zoomLevel + 0.7f).coerceAtMost(19f) }
                        )

                        // Zoom out button
                        FloatingMapButton(
                            iconRes = R.drawable.ic_minus,
                            contentDescription = "ซูมออก",
                            onClick = { zoomLevel = (zoomLevel - 0.7f).coerceAtLeast(12f) }
                        )

                        // My Location / GPS Button
                        FloatingMapButton(
                            iconRes = R.drawable.ic_my_location,
                            contentDescription = "ตำแหน่งปัจจุบัน",
                            onClick = {
                                val userLoc = getDeviceLocation(context)
                                if (userLoc != null) {
                                    centerLat = userLoc.first
                                    centerLng = userLoc.second
                                } else {
                                    // Default back to Chiang Mai center
                                    centerLat = 18.788300
                                    centerLng = 98.985300
                                }
                            }
                        )
                    }

                    // Live Center Coordinates Badge (Floating at Top Center)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = DropwayNavy.copy(alpha = 0.9f),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.6f, %.6f", centerLat, centerLng),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Hint at the bottom of the map
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "ลากแผนที่เพื่อเลื่อนหมุดไปยังบ้านเป้าหมาย",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            centerLat = 18.788300
                            centerLng = 98.985300
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("เชียงใหม่", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            centerLat = 13.756300
                            centerLng = 100.501800
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("กรุงเทพฯ", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val loc = getDeviceLocation(context)
                            if (loc != null) {
                                centerLat = loc.first
                                centerLng = loc.second
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("GPS ปัจจุบัน", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fine-tune Coordinate Input Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            it.toDoubleOrNull()?.let { v -> centerLat = v }
                        },
                        label = { Text("ละติจูด") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = {
                            lngText = it
                            it.toDoubleOrNull()?.let { v -> centerLng = v }
                        },
                        label = { Text("ลองจิจูด") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Confirmation Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("ยกเลิก")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onCoordinatesConfirmed(centerLat, centerLng)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ยืนยันพิกัดนี้", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingMapButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier.size(36.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = DropwayNavy,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun getDeviceLocation(context: Context): Pair<Double, Double>? {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasFine && !hasCoarse) return null

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)

    for (p in providers) {
        val loc = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
        if (loc != null) {
            return Pair(loc.latitude, loc.longitude)
        }
    }
    return null
}
