package com.pocketdev.dropway.ui.screens.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.ui.components.DropwayMapView
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.components.DropwayStreetViewDialog
import com.pocketdev.dropway.ui.components.GoogleMapsHelper
import com.pocketdev.dropway.ui.theme.*
import java.util.Locale

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

    // Synchronized text for manual editing
    var latText by remember(centerLat) {
        mutableStateOf(String.format(Locale.US, "%.6f", centerLat))
    }
    var lngText by remember(centerLng) {
        mutableStateOf(String.format(Locale.US, "%.6f", centerLng))
    }
    var showStreetViewDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
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
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DropwayPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_map),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(22.dp)
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
                                text = "สลับแผนที่ถนน / ดาวเทียม เพื่อหาหลังคาบ้าน",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
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
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Modern Map (Street & Satellite Toggle + Fixed Center Pin)
                DropwayMapView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    initialLat = centerLat,
                    initialLng = centerLng,
                    initialZoom = 16f,
                    isPickerMode = true,
                    onCoordinatesChanged = { newLat, newLng ->
                        centerLat = newLat
                        centerLng = newLng
                        latText = String.format(Locale.US, "%.6f", newLat)
                        lngText = String.format(Locale.US, "%.6f", newLng)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick City & Device GPS Presets
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
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("เชียงใหม่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            centerLat = 13.756300
                            centerLng = 100.501800
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("กรุงเทพฯ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_my_location),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GPS ปัจจุบัน", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Coordinate Input Display & External Street View Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropwayOutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            it.toDoubleOrNull()?.let { v -> centerLat = v }
                        },
                        label = { Text("ละติจูด", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DropwayOutlinedTextField(
                        value = lngText,
                        onValueChange = {
                            lngText = it
                            it.toDoubleOrNull()?.let { v -> centerLng = v }
                        },
                        label = { Text("ลองจิจูด", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Street View 360 Open Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (GoogleMapsHelper.isGoogleMapsSdkUsable(context)) {
                                showStreetViewDialog = true
                            } else {
                                LocationUtils.openStreetView(context, centerLat, centerLng)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_street_view),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "เปิดภาพถนนจริง (Google Street View 360°)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            painter = painterResource(id = R.drawable.ic_export),
                            contentDescription = null,
                            tint = DropwayPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Confirmation Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("ยกเลิก", fontWeight = FontWeight.Bold, color = TextSecondary)
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

    if (showStreetViewDialog) {
        DropwayStreetViewDialog(
            latitude = centerLat,
            longitude = centerLng,
            title = "พิกัดที่เลือก",
            onDismiss = { showStreetViewDialog = false }
        )
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
        val loc = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
        if (loc != null) {
            return Pair(loc.latitude, loc.longitude)
        }
    }
    return null
}
