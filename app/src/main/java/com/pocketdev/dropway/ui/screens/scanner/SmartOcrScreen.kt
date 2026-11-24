package com.pocketdev.dropway.ui.screens.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.scanner.LabelRecognitionResult
import com.pocketdev.dropway.core.scanner.ParcelOcrEngine
import com.pocketdev.dropway.core.util.MatchResult
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.SmartTextParser
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

enum class OcrInputMode(val title: String) {
    CAMERA("กล้องถ่ายรูปฉลาก"),
    TEXT_INPUT("พิมพ์หรือวางข้อความ")
}

@Composable
fun SmartOcrScreen(
    onParcelSaved: () -> Unit
) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val householdRepo = DropwayApplication.instance.householdRepository
    val scope = rememberCoroutineScope()

    var inputMode by remember { mutableStateOf(OcrInputMode.CAMERA) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Form fields
    var rawOcrText by remember { mutableStateOf("") }
    var trackingNumber by remember { mutableStateOf("") }
    var carrierName by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var houseNumber by remember { mutableStateOf("") }
    var villageMoo by remember { mutableStateOf("") }
    var subdistrict by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var codAmountStr by remember { mutableStateOf("") }
    var detectionSource by remember { mutableStateOf("OCR") }

    var isParsed by remember { mutableStateOf(false) }
    var matchedResult by remember { mutableStateOf<MatchResult?>(null) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }

    // Auto-match household when address / phone / name updates
    LaunchedEffect(houseNumber, villageMoo, recipientPhone, recipientName) {
        if (houseNumber.isNotBlank() || recipientPhone.isNotBlank() || recipientName.isNotBlank()) {
            val match = householdRepo.findMatchingHousehold(
                houseNumber = houseNumber,
                village = villageMoo,
                phone = recipientPhone,
                name = recipientName
            )
            matchedResult = match
        } else {
            matchedResult = null
        }
    }

    // Function to run the full modular label recognition pipeline
    fun executeRecognition(bitmap: Bitmap) {
        isProcessing = true
        capturedBitmap = bitmap
        val result: LabelRecognitionResult = ParcelOcrEngine.processLabel(bitmap)

        trackingNumber = result.trackingNumber
        carrierName = result.carrierName
        recipientName = result.recipientName
        recipientPhone = result.recipientPhone
        address = result.fullAddress
        houseNumber = result.houseNumber
        villageMoo = result.villageMoo
        subdistrict = result.subdistrict
        district = result.district
        province = result.province
        postalCode = result.postalCode
        codAmountStr = if (result.codAmountSatang > 0) (result.codAmountSatang / 100.0).toString() else ""
        rawOcrText = result.rawOcrText
        detectionSource = result.detectionSource
        isParsed = true
        isProcessing = false

        if (trackingNumber.isNotBlank()) {
            scope.launch {
                val isDup = deliveryRepo.isTrackingDuplicate(trackingNumber)
                duplicateWarning = if (isDup) "เลขพัสดุนี้เคยถูกบันทึกในระบบแล้ว" else null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DropwayBackground)
    ) {
        // Mode Selector: Camera vs Text
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OcrInputMode.values().forEach { mode ->
                    val isSelected = mode == inputMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) DropwayPrimary else Color.Transparent)
                            .clickable {
                                inputMode = mode
                                if (mode == OcrInputMode.CAMERA) {
                                    isParsed = false
                                }
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        if (inputMode == OcrInputMode.CAMERA && !isParsed) {
            // Live Camera Viewfinder with Capture & Review
            Box(modifier = Modifier.fillMaxSize()) {
                Camera2ScannerView(
                    isContinuousMode = false,
                    overlayGuideText = "จัดกรอบให้เห็นฉลากพัสดุและบาร์โค้ดชัดเจน",
                    onBarcodeDetected = { barcode ->
                        trackingNumber = barcode.normalizedTracking
                        carrierName = barcode.carrierName
                    },
                    onPhotoCaptured = { bitmap ->
                        executeRecognition(bitmap)
                    }
                )

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = DropwayPrimary)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "กำลังประมวลผลฉลากพัสดุ On-Device...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Extracted Review Form & Manual Text OCR Input
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // If photo was captured, show photo preview with Retake button
                if (capturedBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = capturedBitmap!!.asImageBitmap(),
                                    contentDescription = "ภาพฉลากที่ถ่าย",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "ภาพถ่ายฉลากพัสดุ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "ประมวลผลสำเร็จ (${detectionSource})",
                                        fontSize = 11.sp,
                                        color = StatusDelivered,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    capturedBitmap = null
                                    isParsed = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(id = R.drawable.ic_camera), contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ถ่ายใหม่", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Text Input Area (if in TEXT_INPUT mode or wanting to re-analyze)
                if (inputMode == OcrInputMode.TEXT_INPUT && !isParsed) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ข้อความจากฉลากพัสดุ (Text Parser)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "วางข้อความฉลากที่คัดลอกมา หรือพิมพ์ข้อความจากใบปะหน้าพัสดุ",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            DropwayOutlinedTextField(
                                value = rawOcrText,
                                onValueChange = {
                                    rawOcrText = it
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                placeholder = {
                                    Text("วางข้อความฉลากพัสดุที่นี่...\n(เช่น Flash Express TH123456789 ผู้รับ: วิภาดา วงศ์ใหญ่ โทร: 089-988-7766 ที่อยู่: 55/4 หมู่ 5 ต.หนองหอย อ.เมือง จ.เชียงใหม่ 50000 COD: 280 บาท)")
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        rawOcrText = "Flash Express พัสดุ: TH1234598765 ผู้รับ: น.ส.วิภาดา วงศ์ใหญ่ โทร: 089-988-7766 ที่อยู่: 55/4 หมู่ 5 ต.หนองหอย อ.เมือง จ.เชียงใหม่ 50000 เก็บเงินปลายทาง: 280 บาท"
                                    }
                                ) {
                                    Text("ใส่ตัวอย่างฉลาก", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        if (rawOcrText.isNotBlank()) {
                                            val parsed = SmartTextParser.parseFreeText(rawOcrText)
                                            trackingNumber = parsed.trackingNumber
                                            carrierName = parsed.carrier.ifBlank { "พัสดุทั่วไป" }
                                            recipientName = parsed.recipientName
                                            recipientPhone = parsed.recipientPhone
                                            address = parsed.address
                                            houseNumber = parsed.houseNumber
                                            villageMoo = parsed.village
                                            subdistrict = parsed.subdistrict
                                            district = parsed.district
                                            province = parsed.province
                                            postalCode = parsed.postalCode
                                            codAmountStr = if (parsed.codAmountSatang > 0) (parsed.codAmountSatang / 100.0).toString() else ""
                                            detectionSource = "TEXT_OCR"
                                            isParsed = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("แยกข้อมูลฉลาก", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Extracted Editable Form & Household Match Card
                if (isParsed) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ข้อมูลพัสดุที่แยกได้ (ตรวจสอบและแก้ไข)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (carrierName.isNotBlank()) {
                                    Surface(
                                        color = DropwayPrimary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = carrierName,
                                            color = DropwayPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (duplicateWarning != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFFEF2F2),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_warning), contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = duplicateWarning!!, color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            DropwayOutlinedTextField(
                                value = trackingNumber,
                                onValueChange = {
                                    trackingNumber = it
                                    duplicateWarning = null
                                },
                                label = { Text("เลขพัสดุ (Tracking Number)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DropwayOutlinedTextField(
                                    value = recipientName,
                                    onValueChange = { recipientName = it },
                                    label = { Text("ชื่อผู้รับ") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                DropwayOutlinedTextField(
                                    value = recipientPhone,
                                    onValueChange = { recipientPhone = it },
                                    label = { Text("เบอร์โทร") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DropwayOutlinedTextField(
                                    value = houseNumber,
                                    onValueChange = { houseNumber = it },
                                    label = { Text("บ้านเลขที่") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                DropwayOutlinedTextField(
                                    value = villageMoo,
                                    onValueChange = { villageMoo = it },
                                    label = { Text("หมู่") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            DropwayOutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("ที่อยู่จัดส่งเต็ม") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            DropwayOutlinedTextField(
                                value = codAmountStr,
                                onValueChange = { codAmountStr = it },
                                label = { Text("ยอดเงิน COD (บาท)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Household Match Result Banner
                            Spacer(modifier = Modifier.height(12.dp))
                            if (matchedResult != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(painter = painterResource(id = R.drawable.ic_home), contentDescription = null, tint = StatusDelivered, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "พบข้อมูลบ้านในระบบ (${matchedResult!!.household.houseNumber})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF166534)
                                                )
                                            }
                                            Surface(
                                                color = StatusDelivered.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "ตรงกัน ${matchedResult!!.confidenceScore}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF166534),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = matchedResult!!.matchReason,
                                            fontSize = 11.sp,
                                            color = Color(0xFF15803D)
                                        )
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
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ic_home), contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "ยังไม่พบบ้านที่ตรงกันในฐานข้อมูล (จะบันทึกเป็นพัสดุใหม่)",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val track = TrackingUtils.normalize(trackingNumber)
                                    scope.launch {
                                        val today = deliveryRepo.getOrCreateToday()
                                        val parcel = Parcel(
                                            deliveryDayId = today.id,
                                            trackingNumber = track.ifBlank { TrackingUtils.generateTracking("OCR") },
                                            recipientName = recipientName.trim().ifBlank { "ไม่ระบุชื่อ" },
                                            recipientPhone = PhoneUtils.normalize(recipientPhone),
                                            householdId = matchedResult?.household?.id,
                                            destinationAddress = address.trim(),
                                            codAmountSatang = MoneyUtils.parseToSatang(codAmountStr),
                                            source = "OCR",
                                            deliveryNotes = if (carrierName.isNotBlank()) "ขนส่ง: $carrierName" else ""
                                        )
                                        deliveryRepo.saveParcel(parcel)
                                        onParcelSaved()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("บันทึกพัสดุนี้เข้างานวันนี้", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
