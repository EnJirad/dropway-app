package com.pocketdev.dropway.ui.screens.scanner

import androidx.compose.foundation.background
import com.pocketdev.dropway.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
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
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

enum class ScanMode(val title: String, val iconRes: Int) {
    BARCODE("สแกนบาร์โค้ด", R.drawable.ic_scan),
    MANUAL("ป้อนเอง", R.drawable.ic_edit),
    BULK_IMPORT("วางข้อความ", R.drawable.ic_document),
    LABEL_OCR("อ่านฉลาก OCR", R.drawable.ic_camera)
}

@Composable
fun ScannerWorkspaceScreen(
    initialSharedText: String? = null,
    onParcelSaved: () -> Unit,
    onBack: () -> Unit
) {
    var selectedMode by remember {
        mutableStateOf(if (initialSharedText != null) ScanMode.BULK_IMPORT else ScanMode.MANUAL)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DropwayBackground)
    ) {
        // Top Header
        Surface(color = DropwayNavy, shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "เพิ่มพัสดุเข้างานวันนี้",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = R.drawable.ic_close), contentDescription = "ปิด", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                // Mode Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ScanMode.values().forEach { mode ->
                        val isSelected = mode == selectedMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) DropwayPrimary else Color.Transparent)
                                .clickable { selectedMode = mode }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = mode.iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mode.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Workspace View
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedMode) {
                ScanMode.BARCODE -> BarcodeScanWorkspace(onParcelSaved = onParcelSaved)
                ScanMode.MANUAL -> ManualParcelEntryScreen(onSaved = onParcelSaved)
                ScanMode.BULK_IMPORT -> SmartImportScreen(
                    initialText = initialSharedText,
                    onImportCompleted = onParcelSaved
                )
                ScanMode.LABEL_OCR -> SmartOcrScreen(onParcelSaved = onParcelSaved)
            }
        }
    }
}

@Composable
fun BarcodeScanWorkspace(onParcelSaved: () -> Unit) {
    var barcodeInput by remember { mutableStateOf("") }
    var scannedQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val deliveryRepo = DropwayApplication.instance.deliveryRepository

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "สแกนบาร์โค้ดต่อเนื่อง (Continuous Scan)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "สแกนด้วยเครื่องอ่านบาร์โค้ด หรือพิมพ์เลขพัสดุเพื่อเก็บเข้าคิว",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = barcodeInput,
                    onValueChange = {
                        barcodeInput = it
                        duplicateWarning = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("สแกนหรือกรอกเลขพัสดุ (เช่น TH123456789)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                if (duplicateWarning != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = duplicateWarning!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val norm = TrackingUtils.normalize(barcodeInput)
                        if (norm.isBlank()) return@Button

                        if (scannedQueue.contains(norm)) {
                            duplicateWarning = "เลขพัสดุนี้อยู่ในคิวที่สแกนแล้ว"
                            return@Button
                        }

                        scope.launch {
                            val isDup = deliveryRepo.isTrackingDuplicate(norm)
                            if (isDup) {
                                duplicateWarning = "เลขพัสดุนี้เคยถูกบันทึกในระบบแล้ว"
                            } else {
                                scannedQueue = scannedQueue + norm
                                barcodeInput = ""
                                duplicateWarning = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("เพิ่มเข้าคิว (+)", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scanned Queue List
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "รายการในคิว (${scannedQueue.size} ชิ้น)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (scannedQueue.isNotEmpty()) {
                        TextButton(onClick = { scannedQueue = emptyList() }) {
                            Text("ล้างคิว", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                if (scannedQueue.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("ยังไม่มีพัสดุในคิวสแกน", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(scannedQueue.size) { idx ->
                            val track = scannedQueue[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${idx + 1}. $track", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "ลบ",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp).clickable {
                                        scannedQueue = scannedQueue.filter { it != track }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val today = deliveryRepo.getOrCreateToday()
                                val newParcels = scannedQueue.map { track ->
                                    Parcel(
                                        deliveryDayId = today.id,
                                        trackingNumber = track,
                                        recipientName = "รอระบุชื่อผู้รับ",
                                        source = "SCAN"
                                    )
                                }
                                deliveryRepo.saveParcelsBatch(newParcels)
                                scannedQueue = emptyList()
                                onParcelSaved()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("บันทึกพัสดุทั้งหมดเข้างานวันนี้ (${scannedQueue.size} ชิ้น)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}