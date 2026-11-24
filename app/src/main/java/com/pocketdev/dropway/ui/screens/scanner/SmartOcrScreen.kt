package com.pocketdev.dropway.ui.screens.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.pocketdev.dropway.core.scanner.FieldConflict
import com.pocketdev.dropway.core.scanner.LabelOcrEngine
import com.pocketdev.dropway.core.scanner.LabelRecognitionResult
import com.pocketdev.dropway.core.scanner.ParcelOcrEngine
import com.pocketdev.dropway.core.scanner.StructuredFieldExtractor
import com.pocketdev.dropway.core.util.MatchResult
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.SmartTextParser
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Recognition Result Data State
    var recognitionResult by remember { mutableStateOf<LabelRecognitionResult?>(null) }

    // Form fields
    var trackingNumber by remember { mutableStateOf("") }
    var carrierName by remember { mutableStateOf("") }
    var orderId by remember { mutableStateOf("") }
    var sortingCode by remember { mutableStateOf("") }
    var sellerPlatform by remember { mutableStateOf("") }
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
    var rawOcrText by remember { mutableStateOf("") }

    var isParsed by remember { mutableStateOf(false) }
    var matchedResult by remember { mutableStateOf<MatchResult?>(null) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    var showHouseholdSelector by remember { mutableStateOf(false) }

    // Auto-match household when address / phone / name updates
    LaunchedEffect(houseNumber, villageMoo, recipientPhone, recipientName, address) {
        if (houseNumber.isNotBlank() || recipientPhone.isNotBlank() || recipientName.isNotBlank() || address.isNotBlank()) {
            val match = householdRepo.findMatchingHousehold(
                houseNumber = houseNumber,
                village = villageMoo,
                phone = recipientPhone,
                name = recipientName,
                address = address
            )
            matchedResult = match
        } else {
            matchedResult = null
        }
    }

    // Executes full label recognition pipeline in background
    fun executeRecognition(bitmap: Bitmap) {
        isProcessing = true
        capturedBitmap = bitmap
        scope.launch(Dispatchers.Default) {
            val result: LabelRecognitionResult = ParcelOcrEngine.processLabel(bitmap)
            withContext(Dispatchers.Main) {
                recognitionResult = result
                trackingNumber = result.trackingNumber
                carrierName = result.carrierName
                orderId = result.orderId
                sortingCode = result.sortingCode
                sellerPlatform = result.sellerPlatform
                recipientName = result.recipientName
                recipientPhone = result.recipientPhone
                address = result.fullAddress
                houseNumber = result.houseNumber
                villageMoo = result.villageMoo
                subdistrict = result.subdistrict
                district = result.district
                province = result.province
                postalCode = result.postalCode
                codAmountStr = if (result.codAmountSatang > 0L) (result.codAmountSatang / 100.0).toString() else ""
                rawOcrText = result.rawOcrText
                isParsed = true
                isProcessing = false

                if (trackingNumber.isNotBlank()) {
                    scope.launch {
                        val isDup = deliveryRepo.isTrackingDuplicate(trackingNumber)
                        duplicateWarning = if (isDup) "เลขพัสดุนี้เคยถูกบันทึกในระบบแล้ว" else null
                    }
                }
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
                    overlayGuideText = "จัดกรอบให้เห็นฉลากพัสดุทั้งใบเพื่ออ่านข้อมูลอัตโนมัติ",
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
                            .background(Color.Black.copy(alpha = 0.65f)),
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
                                    text = "กำลังวิเคราะห์ฉลากพัสดุด้วย AI On-Device...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "อ่านบาร์โค้ด QR และข้อความทั้งใบแบบออฟไลน์",
                                    fontSize = 11.sp,
                                    color = TextSecondary
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
                // Photo Preview Card with Retake Button
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
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "ภาพถ่ายฉลากพัสดุต้นฉบับ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    val confScore = recognitionResult?.confidenceScore ?: 90
                                    Text(
                                        text = "วิเคราะห์สำเร็จ (ความมั่นใจ $confScore%)",
                                        fontSize = 11.sp,
                                        color = if (confScore >= 80) StatusDelivered else DropwayPrimary,
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

                // Text Input Area (if in TEXT_INPUT mode)
                if (inputMode == OcrInputMode.TEXT_INPUT && !isParsed) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "ข้อความจากฉลากพัสดุ (Text Input)",
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
                                onValueChange = { rawOcrText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                placeholder = {
                                    Text("วางข้อความฉลากพัสดุที่นี่...\n(เช่น J&T Express 828123456789 Order ID: TT123456789TH ผู้รับ: สมชาย ใจดี โทร: 0812345678 ที่อยู่: 123/45 หมู่ 2 ต.บางกร่าง อ.เมือง จ.นนทบุรี 11000 COD: 350 บาท)")
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
                                        rawOcrText = "J&T Express เลขพัสดุ: 828123456789 Order ID: TT261124998TH คัดแยก: 2 BKK-01 ผู้รับ: สมชาย ใจดี โทร: 0812345678 ที่อยู่: 123/45 หมู่ 2 ต.บางกร่าง อ.เมือง จ.นนทบุรี 11000 เก็บเงินปลายทาง: 350 บาท"
                                    }
                                ) {
                                    Text("ใส่ตัวอย่าง J&T/TikTok", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        if (rawOcrText.isNotBlank()) {
                                            val ocrResult = LabelOcrEngine.OcrEngineResult(rawOcrText, emptyList(), 0.9f)
                                            val parsed = StructuredFieldExtractor.extract(emptyList(), ocrResult, rawOcrText)
                                            recognitionResult = parsed
                                            trackingNumber = parsed.trackingNumber
                                            carrierName = parsed.carrierName
                                            orderId = parsed.orderId
                                            sortingCode = parsed.sortingCode
                                            sellerPlatform = parsed.sellerPlatform
                                            recipientName = parsed.recipientName
                                            recipientPhone = parsed.recipientPhone
                                            address = parsed.fullAddress
                                            houseNumber = parsed.houseNumber
                                            villageMoo = parsed.villageMoo
                                            subdistrict = parsed.subdistrict
                                            district = parsed.district
                                            province = parsed.province
                                            postalCode = parsed.postalCode
                                            codAmountStr = if (parsed.codAmountSatang > 0L) (parsed.codAmountSatang / 100.0).toString() else ""
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

                // Extracted Editable Form
                if (isParsed) {
                    // Conflicts Banner (if any)
                    val conflicts = recognitionResult?.conflicts ?: emptyList()
                    if (conflicts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(id = R.drawable.ic_warning), contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("พบข้อมูลขัดแย้งกันระหว่างแหล่งข้อมูล (แตะเพื่อเลือกค่าที่ถูกต้อง):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF991B1B))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                conflicts.forEach { c ->
                                    Text("${c.fieldLabel}:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF991B1B))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                                        FilterChip(
                                            selected = (trackingNumber == c.valueA),
                                            onClick = { trackingNumber = c.valueA },
                                            label = { Text("${c.sourceA}: ${c.valueA}", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = (trackingNumber == c.valueB),
                                            onClick = { trackingNumber = c.valueB },
                                            label = { Text("${c.sourceB}: ${c.valueB}", fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Missing fields warning
                    val missing = recognitionResult?.missingFields ?: emptyList()
                    if (missing.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFFBEB),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(id = R.drawable.ic_warning), contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                val missingNames = missing.map {
                                    when (it) {
                                        "recipientPhone" -> "เบอร์โทร"
                                        "recipientName" -> "ชื่อผู้รับ"
                                        "fullAddress" -> "ที่อยู่"
                                        "trackingNumber" -> "เลขพัสดุ"
                                        else -> it
                                    }
                                }.joinToString(", ")
                                Text("ยังไม่พบข้อมูล: $missingNames (โปรดตรวจสอบหรือกรอกเพิ่มเติม)", fontSize = 11.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header with Carrier & Sorting / Order info chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ข้อมูลพัสดุที่แยกได้",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (carrierName.isNotBlank()) {
                                        Surface(
                                            color = DropwayPrimary.copy(alpha = 0.12f),
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
                                    if (sellerPlatform.isNotBlank()) {
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = sellerPlatform,
                                                color = Color(0xFF475569),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Secondary metadata row: Sorting Code & Order ID
                            if (sortingCode.isNotBlank() || orderId.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (sortingCode.isNotBlank()) {
                                        Surface(
                                            color = Color(0xFFF0FDF4),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "คัดแยก: $sortingCode",
                                                color = Color(0xFF166534),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (orderId.isNotBlank()) {
                                        Surface(
                                            color = Color(0xFFF8FAFC),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Order: $orderId",
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
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
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DropwayOutlinedTextField(
                                    value = subdistrict,
                                    onValueChange = { subdistrict = it },
                                    label = { Text("ตำบล/แขวง") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                DropwayOutlinedTextField(
                                    value = district,
                                    onValueChange = { district = it },
                                    label = { Text("อำเภอ/เขต") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DropwayOutlinedTextField(
                                    value = province,
                                    onValueChange = { province = it },
                                    label = { Text("จังหวัด") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                DropwayOutlinedTextField(
                                    value = postalCode,
                                    onValueChange = { postalCode = it },
                                    label = { Text("รหัสไปรษณีย์") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            DropwayOutlinedTextField(
                                value = codAmountStr,
                                onValueChange = { codAmountStr = it },
                                label = { Text("ยอดเงิน COD (บาท) - ปล่อยว่างถ้าจ่ายแล้ว") },
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                Spacer(modifier = Modifier.width(4.dp))
                                                TextButton(
                                                    onClick = { showHouseholdSelector = true },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("เปลี่ยน", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DropwayPrimary)
                                                }
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
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(painter = painterResource(id = R.drawable.ic_home), contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "ยังไม่พบบ้านที่ตรงกันในฐานข้อมูล",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                        Button(
                                            onClick = { showHouseholdSelector = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("เลือกบ้าน", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val track = TrackingUtils.normalize(trackingNumber)
                                    scope.launch {
                                        val today = deliveryRepo.getOrCreateToday()
                                        val house = matchedResult?.household
                                        val parcel = Parcel(
                                            deliveryDayId = today.id,
                                            trackingNumber = track.ifBlank { TrackingUtils.generateTracking("OCR") },
                                            recipientName = recipientName.trim().ifBlank { "ไม่ระบุชื่อ" },
                                            recipientPhone = PhoneUtils.normalize(recipientPhone),
                                            householdId = house?.id,
                                            destinationAddress = address.trim().ifBlank { house?.fullAddress() ?: "" },
                                            latitude = house?.latitude,
                                            longitude = house?.longitude,
                                            codAmountSatang = MoneyUtils.parseToSatang(codAmountStr),
                                            source = "OCR",
                                            deliveryNotes = listOfNotNull(
                                                if (carrierName.isNotBlank()) "ขนส่ง: $carrierName" else null,
                                                if (sortingCode.isNotBlank()) "โซน: $sortingCode" else null,
                                                if (orderId.isNotBlank()) "Order: $orderId" else null
                                            ).joinToString(" | ")
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

        if (showHouseholdSelector) {
            HouseholdSelectorDialog(
                onHouseholdSelected = { selHouse, selRes ->
                    matchedResult = MatchResult(
                        household = selHouse,
                        matchedResident = selRes,
                        confidenceScore = 100,
                        matchReason = "เลือกด้วยตนเองจากฐานข้อมูล"
                    )
                    houseNumber = selHouse.houseNumber
                    villageMoo = selHouse.village
                    if (address.isBlank()) address = selHouse.fullAddress()
                    if (selRes != null) {
                        if (recipientName.isBlank()) recipientName = selRes.fullName
                        if (recipientPhone.isBlank()) recipientPhone = selRes.phoneNumber
                    }
                    showHouseholdSelector = false
                },
                onDismiss = { showHouseholdSelector = false }
            )
        }
    }
}
