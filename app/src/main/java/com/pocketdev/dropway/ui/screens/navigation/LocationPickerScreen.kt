package com.pocketdev.dropway.ui.screens.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketdev.dropway.ui.theme.DropwayNavy
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.TextPrimary

@Composable
fun LocationPickerScreen(
    initialLat: Double?,
    initialLng: Double?,
    onDismiss: () -> Unit,
    onCoordinatesConfirmed: (Double, Double) -> Unit
) {
    var latStr by remember { mutableStateOf(initialLat?.toString() ?: "18.7883") }
    var lngStr by remember { mutableStateOf(initialLng?.toString() ?: "98.9853") }
    var manualMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "📍 เลือกและระบุพิกัด GPS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "หมุดจะชี้ตำแหน่งจัดส่ง สามารถระบุพิกัดตรงนี้หรือใช้ GPS จากอุปกรณ์ได้",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(14.dp))
                // Visual Map Viewport Representation
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📍", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "พิกัด: $latStr, $lngStr",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DropwayNavy
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latStr,
                        onValueChange = { latStr = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lngStr,
                        onValueChange = { lngStr = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("ยกเลิก")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val lat = latStr.toDoubleOrNull()
                            val lng = lngStr.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                onCoordinatesConfirmed(lat, lng)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ยืนยันพิกัดนี้")
                    }
                }
            }
        }
    }
}
