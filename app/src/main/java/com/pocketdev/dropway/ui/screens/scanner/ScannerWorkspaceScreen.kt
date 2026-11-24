package com.pocketdev.dropway.ui.screens.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.background
import com.pocketdev.dropway.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.pocketdev.dropway.core.scanner.BarcodeDecoder
import com.pocketdev.dropway.core.scanner.BarcodeResult
import com.pocketdev.dropway.core.util.MatchResult
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

enum class ScanMode(val title: String, val iconRes: Int) {
    BARCODE("สแกนพัสดุ", R.drawable.ic_scan),
    LABEL_OCR("อ่านฉลาก OCR", R.drawable.ic_camera),
    BULK_IMPORT("วางข้อความ", R.drawable.ic_document),
    MANUAL("ป้อนเอง", R.drawable.ic_edit)
}

enum class BarcodeSubMode(val title: String) {
    CONTINUOUS("สแกนต่อเนื่อง (Continuous)"),
    SINGLE("ตรวจทีละชิ้น (Single)")
}

data class ScannedQueueItem(
    val trackingNumber: String,
    val carrier: String,
    val scannedAt: Long = System.currentTimeMillis()
)

@Composable
fun ScannerWorkspaceScreen(
    initialSharedText: String? = null,
    onParcelSaved: () -> Unit,
    onBack: () -> Unit
) {
    var selectedMode by remember {
        mutableStateOf(if (initialSharedText != null) ScanMode.BULK_IMPORT else ScanMode.BARCODE)
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
                                    tint = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = mode.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
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
                ScanMode.BARCODE -> RealCameraBarcodeWorkspace(onParcelSaved = onParcelSaved)
                ScanMode.LABEL_OCR -> SmartOcrScreen(onParcelSaved = onParcelSaved)
                ScanMode.MANUAL -> ManualParcelEntryScreen(onSaved = onParcelSaved)
                ScanMode.BULK_IMPORT -> SmartImportScreen(
                    initialText = initialSharedText,
                    onImportCompleted = onParcelSaved
                )
            }
        }
    }
}

@Composable
fun RealCameraBarcodeWorkspace(onParcelSaved: () -> Unit) {
    var subMode by remember { mutableStateOf(BarcodeSubMode.CONTINUOUS) }
    var scannedQueue by remember { mutableStateOf<List<ScannedQueueItem>>(emptyList()) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    var lastScannedItem by remember { mutableStateOf<ScannedQueueItem?>(null) }

    // Single mode confirmation dialog state
    var singleItemToConfirm by remember { mutableStateOf<BarcodeResult?>(null) }
    var singleRecipientName by remember { mutableStateOf("") }
    var singleRecipientPhone by remember { mutableStateOf("") }
    var singleAddress by remember { mutableStateOf("") }
    var singleCodStr by remember { mutableStateOf("") }
    var singleMatchedHousehold by remember { mutableStateOf<MatchResult?>(null) }

    // Manual input toggle
    var showManualInput by remember { mutableStateOf(false) }
    var manualTrackingText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val deliveryRepo = DropwayApplication.instance.deliveryRepository

    // Handle barcode detected from Camera2
    fun handleBarcodeScanned(result: BarcodeResult) {
        val tracking = result.normalizedTracking
        if (tracking.isBlank()) return

        if (subMode == BarcodeSubMode.CONTINUOUS) {
            // Check if already in current queue
            if (scannedQueue.any { it.trackingNumber == tracking }) {
                duplicateWarning = "เลข $tracking อยู่ในคิวที่สแกนแล้ว"
                return
            }

            scope.launch {
                val isDup = deliveryRepo.isTrackingDuplicate(tracking)
                if (isDup) {
                    duplicateWarning = "เลข $tracking เคยบันทึกในระบบแล้ว"
                } else {
                    val item = ScannedQueueItem(tracking, result.carrierName)
                    scannedQueue = scannedQueue + item
                    lastScannedItem = item
                    duplicateWarning = null
                }
            }
        } else {
            // Single mode: Open review card
            singleItemToConfirm = result
            singleRecipientName = ""
            singleRecipientPhone = ""
            singleAddress = ""
            singleCodStr = ""
            singleMatchedHousehold = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // SubMode Selector & Quick Manual Toggle
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(2.dp)
                    ) {
                        BarcodeSubMode.values().forEach { mode ->
                            val isSel = mode == subMode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) DropwayPrimary else Color.Transparent)
                                    .clickable { subMode = mode }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else TextSecondary
                                )
                            }
                        }
                    }

                    TextButton(onClick = { showManualInput = !showManualInput }) {
                        Icon(painter = painterResource(id = R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showManualInput) "ซ่อนพิมพ์" else "พิมพ์เลข", fontSize = 11.sp)
                    }
                }
            }

            // Manual Barcode Input Card (Collapsible)
            if (showManualInput) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DropwayOutlinedTextField(
                            value = manualTrackingText,
                            onValueChange = {
                                manualTrackingText = it
                                duplicateWarning = null
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("กรอกเลขพัสดุ (เช่น TH123456789)") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val norm = TrackingUtils.normalize(manualTrackingText)
                                if (norm.isNotBlank()) {
                                    handleBarcodeScanned(
                                        BarcodeResult(
                                            rawText = norm,
                                            normalizedTracking = norm,
                                            format = com.google.zxing.BarcodeFormat.CODE_128,
                                            carrierName = BarcodeDecoder.detectCarrier(norm)
                                        )
                                    )
                                    manualTrackingText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("เพิ่ม", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Camera Viewfinder (Top Half / Flex)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (subMode == BarcodeSubMode.CONTINUOUS && scannedQueue.isNotEmpty()) 1.1f else 1.8f)
            ) {
                Camera2ScannerView(
                    isContinuousMode = subMode == BarcodeSubMode.CONTINUOUS,
                    overlayGuideText = if (subMode == BarcodeSubMode.CONTINUOUS) "สแกนบาร์โค้ดต่อเนื่องเพื่อเก็บเข้าคิว" else "จัดบาร์โค้ดให้อยู่ในกรอบเพื่อตรวจทีละชิ้น",
                    onBarcodeDetected = { barcode ->
                        handleBarcodeScanned(barcode)
                    },
                    onPhotoCaptured = { bitmap ->
                        val result = BarcodeDecoder.decodeBitmap(bitmap)
                        if (result != null) {
                            handleBarcodeScanned(result)
                        } else {
                            duplicateWarning = "ไม่พบบาร์โค้ดในรูปถ่าย กรุณาจัดระยะแล้วถ่ายใหม่"
                        }
                    }
                )

                // Duplicate Warning Banner
                if (duplicateWarning != null) {
                    Surface(
                        color = Color(0xFFDC2626),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_warning), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = duplicateWarning!!, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Continuous Scanned Queue Drawer (Bottom Half)
            if (subMode == BarcodeSubMode.CONTINUOUS) {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "คิวพัสดุที่สแกนแล้ว",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (scannedQueue.isNotEmpty()) DropwayPrimary else Color(0xFF94A3B8),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${scannedQueue.size} ชิ้น",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (scannedQueue.isNotEmpty()) {
                                TextButton(onClick = { scannedQueue = emptyList() }) {
                                    Text("ล้างคิว", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }

                        if (scannedQueue.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "ส่องกล้องไปที่บาร์โค้ดพัสดุ ระบบจะเก็บเข้าคิวอัตโนมัติ",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(scannedQueue.size) { idx ->
                                    val item = scannedQueue[scannedQueue.size - 1 - idx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = Color(0xFFE2E8F0),
                                                shape = CircleShape,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("${scannedQueue.size - idx}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(item.trackingNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(item.carrier, fontSize = 10.sp, color = TextSecondary)
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                scannedQueue = scannedQueue.filter { it.trackingNumber != item.trackingNumber }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_close),
                                                contentDescription = "ลบ",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val today = deliveryRepo.getOrCreateToday()
                                        val newParcels = scannedQueue.map { item ->
                                            Parcel(
                                                deliveryDayId = today.id,
                                                trackingNumber = item.trackingNumber,
                                                recipientName = "รอระบุชื่อผู้รับ",
                                                source = "SCAN",
                                                deliveryNotes = "ขนส่ง: ${item.carrier}"
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

        // Single Mode Confirmation Card Dialog
        if (singleItemToConfirm != null) {
            val item = singleItemToConfirm!!
            AlertDialog(
                onDismissRequest = { singleItemToConfirm = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ยืนยันพัสดุใหม่", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Surface(
                            color = DropwayPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = item.carrierName,
                                color = DropwayPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "เลขพัสดุ: ${item.normalizedTracking}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DropwayPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        DropwayOutlinedTextField(
                            value = singleRecipientName,
                            onValueChange = { singleRecipientName = it },
                            label = { Text("ชื่อผู้รับ") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DropwayOutlinedTextField(
                            value = singleRecipientPhone,
                            onValueChange = { singleRecipientPhone = it },
                            label = { Text("เบอร์โทร") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DropwayOutlinedTextField(
                            value = singleAddress,
                            onValueChange = { singleAddress = it },
                            label = { Text("ที่อยู่จัดส่ง") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DropwayOutlinedTextField(
                            value = singleCodStr,
                            onValueChange = { singleCodStr = it },
                            label = { Text("ยอดเงิน COD (บาท)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val today = deliveryRepo.getOrCreateToday()
                                val parcel = Parcel(
                                    deliveryDayId = today.id,
                                    trackingNumber = item.normalizedTracking,
                                    recipientName = singleRecipientName.trim().ifBlank { "รอระบุชื่อผู้รับ" },
                                    recipientPhone = PhoneUtils.normalize(singleRecipientPhone),
                                    destinationAddress = singleAddress.trim(),
                                    codAmountSatang = MoneyUtils.parseToSatang(singleCodStr),
                                    source = "SCAN",
                                    deliveryNotes = "ขนส่ง: ${item.carrierName}"
                                )
                                deliveryRepo.saveParcel(parcel)
                                singleItemToConfirm = null
                                onParcelSaved()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered)
                    ) {
                        Text("บันทึกเข้างานวันนี้", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { singleItemToConfirm = null }) {
                        Text("ยกเลิก")
                    }
                }
            )
        }
    }
}
