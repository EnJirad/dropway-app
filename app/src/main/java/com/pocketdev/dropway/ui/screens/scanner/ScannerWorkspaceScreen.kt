package com.pocketdev.dropway.ui.screens.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.Resident
import com.pocketdev.dropway.core.scanner.BarcodeDecoder
import com.pocketdev.dropway.core.scanner.BarcodeResult
import com.pocketdev.dropway.core.scanner.ParcelOcrEngine
import com.pocketdev.dropway.core.util.HouseholdMatcher
import com.pocketdev.dropway.core.util.MatchResult
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.SmartTextParser
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
    val recipientName: String = "",
    val recipientPhone: String = "",
    val address: String = "",
    val codAmountSatang: Long = 0L,
    val matchedHousehold: Household? = null,
    val matchedResident: Resident? = null,
    val matchConfidence: Int = 0,
    val matchReason: String = "",
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
    var statusFeedbackMessage by remember { mutableStateOf<String?>(null) }

    // Single mode confirmation dialog state
    var singleItemToConfirm by remember { mutableStateOf<BarcodeResult?>(null) }
    var singleRecipientName by remember { mutableStateOf("") }
    var singleRecipientPhone by remember { mutableStateOf("") }
    var singleAddress by remember { mutableStateOf("") }
    var singleCodStr by remember { mutableStateOf("") }
    var singleDeliveryNotes by remember { mutableStateOf("") }
    var singleMatchedHousehold by remember { mutableStateOf<Household?>(null) }
    var singleMatchedResident by remember { mutableStateOf<Resident?>(null) }
    var singleMatchConfidence by remember { mutableStateOf(0) }
    var singleMatchReason by remember { mutableStateOf("") }

    // Household search dialog
    var showHouseholdSelector by remember { mutableStateOf(false) }

    // Quick text paste dialog
    var showQuickPasteDialog by remember { mutableStateOf(false) }
    var quickPasteText by remember { mutableStateOf("") }

    // Manual input toggle
    var showManualInput by remember { mutableStateOf(false) }
    var manualTrackingText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val householdRepo = DropwayApplication.instance.householdRepository

    // Intelligent parcel resolution against database
    fun resolveBarcodeAndMatch(result: BarcodeResult) {
        val tracking = result.normalizedTracking
        if (tracking.isBlank()) return

        scope.launch {
            // Check tracking duplicate in today's deliveries
            val isDup = deliveryRepo.isTrackingDuplicate(tracking)
            if (isDup) {
                duplicateWarning = "เลขพัสดุ $tracking เคยบันทึกในระบบแล้ว"
                return@launch
            }

            // Extract initial fields from parsed barcode or text
            var name = result.recipientName
            var phone = result.recipientPhone
            var address = result.address
            var houseNum = result.houseNumber
            var village = result.village
            var codSatang = result.codAmountSatang

            // Tier 1: Check if recipient was previously recorded with this phone or name in past parcels
            if (phone.isNotBlank()) {
                val pastParcel = deliveryRepo.findRecentParcelByPhone(phone)
                if (pastParcel != null && pastParcel.householdId != null) {
                    val pastHouse = householdRepo.getHouseholdById(pastParcel.householdId)
                    if (pastHouse != null) {
                        houseNum = pastHouse.houseNumber
                        village = pastHouse.village
                        if (address.isBlank()) address = pastHouse.fullAddress()
                    }
                }
            }

            // Tier 2: Match against Household Database
            val match = householdRepo.findMatchingHousehold(
                houseNumber = houseNum,
                village = village,
                phone = phone,
                name = name,
                address = address
            )

            val matchedHouse = match?.household
            val matchedRes = match?.matchedResident
            val confidence = match?.confidenceScore ?: 0
            val reason = match?.matchReason ?: ""

            if (matchedHouse != null) {
                if (address.isBlank()) address = matchedHouse.fullAddress()
                if (name.isBlank() && matchedRes != null) name = matchedRes.fullName
                if (phone.isBlank() && matchedRes != null) phone = matchedRes.phoneNumber
            }

            if (subMode == BarcodeSubMode.CONTINUOUS) {
                if (scannedQueue.any { it.trackingNumber == tracking }) {
                    duplicateWarning = "เลข $tracking อยู่ในคิวแล้ว"
                    return@launch
                }
                val item = ScannedQueueItem(
                    trackingNumber = tracking,
                    carrier = result.carrierName,
                    recipientName = name,
                    recipientPhone = phone,
                    address = address,
                    codAmountSatang = codSatang,
                    matchedHousehold = matchedHouse,
                    matchedResident = matchedRes,
                    matchConfidence = confidence,
                    matchReason = reason
                )
                scannedQueue = scannedQueue + item
                duplicateWarning = null
                statusFeedbackMessage = if (matchedHouse != null) {
                    "สแกน $tracking -> เชื่อมกับบ้านเลขที่ ${matchedHouse.houseNumber}"
                } else {
                    "สแกน $tracking เข้ารายการแล้ว"
                }
            } else {
                // Single Mode: Open full review sheet
                singleItemToConfirm = result
                singleRecipientName = name
                singleRecipientPhone = phone
                singleAddress = address
                singleCodStr = if (codSatang > 0L) (codSatang / 100.0).toString() else ""
                singleDeliveryNotes = if (matchedHouse != null && matchedHouse.notes.isNotBlank()) {
                    matchedHouse.notes
                } else ""
                singleMatchedHousehold = matchedHouse
                singleMatchedResident = matchedRes
                singleMatchConfidence = confidence
                singleMatchReason = reason
                duplicateWarning = null
            }
        }
    }

    // Auto-match when fields change in Single Mode dialog
    LaunchedEffect(singleRecipientPhone, singleRecipientName, singleAddress) {
        if (singleItemToConfirm != null) {
            val houseNum = SmartTextParser.extractHouseNumber(singleAddress)
            val village = SmartTextParser.extractVillage(singleAddress)
            val match = householdRepo.findMatchingHousehold(
                houseNumber = houseNum,
                village = village,
                phone = singleRecipientPhone,
                name = singleRecipientName,
                address = singleAddress
            )
            if (match != null && match.confidenceScore >= 40) {
                singleMatchedHousehold = match.household
                singleMatchedResident = match.matchedResident
                singleMatchConfidence = match.confidenceScore
                singleMatchReason = match.matchReason
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // SubMode Selector & Control Buttons
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Selector Pill
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

                    // Action buttons: Paste Text & Manual Type
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showQuickPasteDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_document), contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("วางฉลาก", fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = { showManualInput = !showManualInput },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (showManualInput) "ซ่อน" else "พิมพ์เลข", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Collapsible Manual Barcode Input
            if (showManualInput) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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
                                    resolveBarcodeAndMatch(
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
                            Text("ค้นหา", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Camera Viewfinder (Flex)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (subMode == BarcodeSubMode.CONTINUOUS && scannedQueue.isNotEmpty()) 1.1f else 1.8f)
            ) {
                Camera2ScannerView(
                    isContinuousMode = subMode == BarcodeSubMode.CONTINUOUS,
                    overlayGuideText = if (subMode == BarcodeSubMode.CONTINUOUS) {
                        "ส่องบาร์โค้ดหรือ QR พัสดุ ระบบจะจับคู่ฐานข้อมูลอัตโนมัติ"
                    } else {
                        "จัดให้บาร์โค้ดหรือฉลากอยู่ในกรอบเพื่อตรวจสอบและจับคู่บ้าน"
                    },
                    onBarcodeDetected = { barcode ->
                        resolveBarcodeAndMatch(barcode)
                    },
                    onPhotoCaptured = { bitmap ->
                        // Run full label recognition pipeline on captured photo
                        val recognized = ParcelOcrEngine.processLabel(bitmap)
                        if (recognized.trackingNumber.isNotBlank() || recognized.recipientName.isNotBlank()) {
                            resolveBarcodeAndMatch(
                                BarcodeResult(
                                    rawText = recognized.rawOcrText,
                                    normalizedTracking = recognized.trackingNumber,
                                    format = com.google.zxing.BarcodeFormat.QR_CODE,
                                    carrierName = recognized.carrierName,
                                    confidence = recognized.confidenceScore,
                                    recipientName = recognized.recipientName,
                                    recipientPhone = recognized.recipientPhone,
                                    address = recognized.fullAddress,
                                    houseNumber = recognized.houseNumber,
                                    village = recognized.villageMoo,
                                    codAmountSatang = recognized.codAmountSatang
                                )
                            )
                        } else {
                            duplicateWarning = "ไม่พบบาร์โค้ดหรือข้อมูลฉลากในภาพ กรุณาถ่ายใหม่อีกครั้ง"
                        }
                    }
                )

                // Feedback Banner
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
                } else if (statusFeedbackMessage != null) {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.88f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = statusFeedbackMessage!!, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Continuous Scanned Queue Drawer
            if (subMode == BarcodeSubMode.CONTINUOUS) {
                val matchedCount = scannedQueue.count { it.matchedHousehold != null }

                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                                if (matchedCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = StatusDeliveredBg,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "พบบ้าน $matchedCount",
                                            color = StatusDelivered,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
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
                                    text = "ส่องกล้องไปที่บาร์โค้ดหรือฉลาก ระบบจะจับคู่ฐานข้อมูลและเก็บเข้าคิว",
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
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(scannedQueue.size) { idx ->
                                    val item = scannedQueue[scannedQueue.size - 1 - idx]
                                    ScannedQueueRowItem(
                                        item = item,
                                        index = scannedQueue.size - idx,
                                        onDelete = {
                                            scannedQueue = scannedQueue.filter { it.trackingNumber != item.trackingNumber }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val today = deliveryRepo.getOrCreateToday()
                                        val newParcels = scannedQueue.map { item ->
                                            val house = item.matchedHousehold
                                            val resident = item.matchedResident
                                            Parcel(
                                                deliveryDayId = today.id,
                                                trackingNumber = item.trackingNumber,
                                                recipientName = item.recipientName.ifBlank { resident?.fullName ?: "รอระบุชื่อผู้รับ" },
                                                recipientPhone = item.recipientPhone.ifBlank { resident?.phoneNumber ?: "" },
                                                householdId = house?.id,
                                                destinationAddress = item.address.ifBlank { house?.fullAddress() ?: "" },
                                                latitude = house?.latitude,
                                                longitude = house?.longitude,
                                                codAmountSatang = item.codAmountSatang,
                                                source = "SCAN",
                                                deliveryNotes = "ขนส่ง: ${item.carrier}" + (if (house != null && house.notes.isNotBlank()) " • ${house.notes}" else "")
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
                        Text("ยืนยันพัสดุและจับคู่บ้าน", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "เลขพัสดุ: ${item.normalizedTracking}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DropwayPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Database Match Card
                        if (singleMatchedHousehold != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF0FDF4),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "ตรงกับฐานข้อมูล (${singleMatchConfidence}%)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF166534)
                                            )
                                        }
                                        TextButton(
                                            onClick = { showHouseholdSelector = true },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("เปลี่ยนบ้าน", fontSize = 11.sp, color = DropwayPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "บ้านเลขที่ ${singleMatchedHousehold!!.houseNumber} หมู่ ${singleMatchedHousehold!!.village} ${singleMatchedHousehold!!.villageName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (singleMatchedResident != null) {
                                        Text(
                                            text = "ผู้อยู่อาศัย: ${singleMatchedResident!!.fullName} • ${singleMatchedResident!!.phoneNumber}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF15803D)
                                        )
                                    }
                                    if (singleMatchedHousehold!!.notes.isNotBlank()) {
                                        Text(
                                            text = "หมายเหตุบ้าน: ${singleMatchedHousehold!!.notes}",
                                            fontSize = 11.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    if (singleMatchedHousehold!!.latitude != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "มีพิกัดแผนที่แล้ว พร้อมนำทาง",
                                            fontSize = 10.sp,
                                            color = Color(0xFF0369A1),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ยังไม่พบบ้านในฐานข้อมูล",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "ค้นหาเพื่อเชื่อมโยงกับฐานข้อมูล",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Button(
                                        onClick = { showHouseholdSelector = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("เลือกบ้าน", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

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
                        Spacer(modifier = Modifier.height(8.dp))
                        DropwayOutlinedTextField(
                            value = singleDeliveryNotes,
                            onValueChange = { singleDeliveryNotes = it },
                            label = { Text("หมายเหตุการส่ง") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val today = deliveryRepo.getOrCreateToday()
                                val house = singleMatchedHousehold
                                val parcel = Parcel(
                                    deliveryDayId = today.id,
                                    trackingNumber = item.normalizedTracking,
                                    recipientName = singleRecipientName.trim().ifBlank { "รอระบุชื่อผู้รับ" },
                                    recipientPhone = PhoneUtils.normalize(singleRecipientPhone),
                                    householdId = house?.id,
                                    destinationAddress = singleAddress.trim().ifBlank { house?.fullAddress() ?: "" },
                                    latitude = house?.latitude,
                                    longitude = house?.longitude,
                                    codAmountSatang = MoneyUtils.parseToSatang(singleCodStr),
                                    source = "SCAN",
                                    deliveryNotes = if (singleDeliveryNotes.isNotBlank()) singleDeliveryNotes else "ขนส่ง: ${item.carrierName}"
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

        // Quick Paste Dialog
        if (showQuickPasteDialog) {
            AlertDialog(
                onDismissRequest = { showQuickPasteDialog = false },
                title = { Text("วางข้อความฉลากพัสดุ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "วางข้อความฉลากที่คัดลอกมา ระบบจะแยกเลขพัสดุ ชื่อ เบอร์โทร ที่อยู่ และยอด COD ให้ทันที",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DropwayOutlinedTextField(
                            value = quickPasteText,
                            onValueChange = { quickPasteText = it },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            placeholder = { Text("วางข้อความที่นี่...") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (quickPasteText.isNotBlank()) {
                                val parsed = SmartTextParser.parseFreeText(quickPasteText)
                                val track = parsed.trackingNumber.ifBlank { TrackingUtils.generateTracking("PASTE") }
                                resolveBarcodeAndMatch(
                                    BarcodeResult(
                                        rawText = quickPasteText,
                                        normalizedTracking = track,
                                        format = com.google.zxing.BarcodeFormat.QR_CODE,
                                        carrierName = parsed.carrier.ifBlank { "พัสดุทั่วไป" },
                                        confidence = 90,
                                        recipientName = parsed.recipientName,
                                        recipientPhone = parsed.recipientPhone,
                                        address = parsed.address,
                                        houseNumber = parsed.houseNumber,
                                        village = parsed.village,
                                        codAmountSatang = parsed.codAmountSatang
                                    )
                                )
                                quickPasteText = ""
                                showQuickPasteDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary)
                    ) {
                        Text("วิเคราะห์และจับคู่", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuickPasteDialog = false }) {
                        Text("ยกเลิก")
                    }
                }
            )
        }

        // Household Selector Modal Dialog
        if (showHouseholdSelector) {
            HouseholdSelectorDialog(
                onHouseholdSelected = { selectedHouse, selectedRes ->
                    singleMatchedHousehold = selectedHouse
                    singleMatchedResident = selectedRes
                    if (singleAddress.isBlank() || singleAddress == singleMatchedHousehold?.fullAddress()) {
                        singleAddress = selectedHouse.fullAddress()
                    }
                    if (selectedRes != null) {
                        if (singleRecipientName.isBlank()) singleRecipientName = selectedRes.fullName
                        if (singleRecipientPhone.isBlank()) singleRecipientPhone = selectedRes.phoneNumber
                    }
                    if (selectedHouse.notes.isNotBlank() && singleDeliveryNotes.isBlank()) {
                        singleDeliveryNotes = selectedHouse.notes
                    }
                    singleMatchConfidence = 100
                    singleMatchReason = "เลือกด้วยตนเองจากฐานข้อมูล"
                    showHouseholdSelector = false
                },
                onDismiss = { showHouseholdSelector = false }
            )
        }
    }
}

@Composable
fun ScannedQueueRowItem(
    item: ScannedQueueItem,
    index: Int,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    color = Color(0xFFE2E8F0),
                    shape = CircleShape,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$index", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.trackingNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = DropwayPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = item.carrier,
                                fontSize = 9.sp,
                                color = DropwayPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (item.matchedHousehold != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painter = painterResource(id = R.drawable.ic_home), contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "บ้านเลขที่ ${item.matchedHousehold.houseNumber} หมู่ ${item.matchedHousehold.village}" + (if (item.matchedResident != null) " (${item.matchedResident.fullName})" else ""),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF15803D)
                            )
                        }
                    } else if (item.recipientName.isNotBlank() || item.address.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.recipientName} • ${item.address}".trim().trim('•').trim(),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("ยังไม่พบบ้านในระบบ (รอนำเข้า)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.codAmountSatang > 0L) {
                    Text(
                        text = "COD ${MoneyUtils.formatBaht(item.codAmountSatang)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
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
}

@Composable
fun HouseholdSelectorDialog(
    onHouseholdSelected: (Household, Resident?) -> Unit,
    onDismiss: () -> Unit
) {
    val householdRepo = DropwayApplication.instance.householdRepository
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var householdsList by remember { mutableStateOf<List<Household>>(emptyList()) }
    var residentsMap by remember { mutableStateOf<Map<Long, List<Resident>>>(emptyMap()) }

    LaunchedEffect(searchQuery) {
        scope.launch {
            val list = if (searchQuery.isBlank()) {
                householdRepo.getAllHouseholds()
            } else {
                householdRepo.searchHouseholds(searchQuery)
            }
            householdsList = list

            val map = mutableMapOf<Long, List<Resident>>()
            for (h in list.take(20)) {
                map[h.id] = householdRepo.getResidentsByHousehold(h.id)
            }
            residentsMap = map
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("เลือกบ้านจากฐานข้อมูล", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(painter = painterResource(id = R.drawable.ic_close), contentDescription = "ปิด", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("พิมพ์บ้านเลขที่, ชื่อลูกบ้าน, หรือหมู่...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                if (householdsList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ไม่พบบ้านที่ตรงกับคำค้นหา", color = TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(householdsList) { h ->
                            val residents = residentsMap[h.id] ?: emptyList()
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onHouseholdSelected(h, residents.firstOrNull())
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "บ้านเลขที่ ${h.houseNumber} หมู่ ${h.village}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = DropwayPrimary
                                        )
                                        if (h.latitude != null) {
                                            Surface(
                                                color = Color(0xFFE0F2FE),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "GPS",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF0369A1),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (h.villageName.isNotBlank() || h.subdistrict.isNotBlank()) {
                                        Text(
                                            text = listOf(h.villageName, h.subdistrict).filter { it.isNotBlank() }.joinToString(" "),
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    if (residents.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            residents.forEach { res ->
                                                Surface(
                                                    color = Color(0xFFEDE9FE),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(end = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "${res.fullName} (${res.phoneNumber})",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF6D28D9),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
