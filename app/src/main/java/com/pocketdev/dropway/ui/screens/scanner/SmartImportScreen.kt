package com.pocketdev.dropway.ui.screens.scanner

import androidx.compose.foundation.background
import com.pocketdev.dropway.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.ParsedParcelRow
import com.pocketdev.dropway.core.util.SmartTextParser
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SmartImportScreen(
    initialText: String? = null,
    onImportCompleted: () -> Unit
) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val householdRepo = DropwayApplication.instance.householdRepository
    val scope = rememberCoroutineScope()

    var rawInputText by remember { mutableStateOf(initialText ?: "") }
    var parsedRows by remember { mutableStateOf<List<ParsedParcelRow>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) {
            parsedRows = SmartTextParser.parseBulkText(initialText)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (parsedRows.isEmpty()) {
            // Text Input Form
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "นำเข้าข้อมูลแบบรวดเร็ว (Smart Import / Bulk Paste)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "คัดลอกข้อความจาก LINE, Excel, แชท หรือใบสรุปพัสดุ แล้ววางที่นี่ ระบบจะแยกเลขพัสดุ, ชื่อผู้รับ, เบอร์โทร, ที่อยู่, และยอด COD ให้อัตโนมัติ",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    DropwayOutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = {
                            Text("ตัวอย่าง:\nTH123456789 สมชาย ใจดี 0812345678 123/4 ม.2 ต.ป่าแดด COD 250\nKEX987654321 สมศรี บุญมี 0897654321 45/1 ม.1 ต.ริมปิง 150 บาท")
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                rawInputText = """
                                    TH012345678 สมชาย ชัยชนะ 0812345678 บ้านเลขที่ 12/3 หมู่ 2 ต.ช้างเผือก อ.เมือง เชียงใหม่ COD 350
                                    KEX88776655 กานดา รัตนพร 0898765432 45 หมู่ 4 ต.สุเทพ อ.เมือง เชียงใหม่ 0 บาท
                                    SPX99112233 นิวัฒน์ สุขสวัสดิ์ 0612348899 99/1 หมู่ 2 ต.ป่าแดด ยอด 500 บาท
                                """.trimIndent()
                            }
                        ) {
                            Text("ใส่ข้อความตัวอย่าง", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (rawInputText.isNotBlank()) {
                                    isAnalyzing = true
                                    parsedRows = SmartTextParser.parseBulkText(rawInputText)
                                    isAnalyzing = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("วิเคราะห์ข้อความ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Preview Table & Confirmation View
            val validCount = parsedRows.count { it.isValid }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ตรวจสอบรายการนำเข้า (${parsedRows.size} ชิ้น)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "พร้อมนำเข้า $validCount รายการ",
                                fontSize = 12.sp,
                                color = StatusDelivered,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(onClick = { parsedRows = emptyList() }) {
                            Text("แก้ไขข้อความใหม่", fontSize = 12.sp)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(parsedRows) { index, row ->
                            ParsedRowCard(
                                index = index + 1,
                                row = row,
                                onDelete = {
                                    parsedRows = parsedRows.filterIndexed { i, _ -> i != index }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val today = deliveryRepo.getOrCreateToday()
                                val validRows = parsedRows.filter { it.isValid }
                                val newParcels = validRows.map { r ->
                                    val match = householdRepo.findMatchingHousehold(
                                        houseNumber = r.houseNumber,
                                        village = r.village,
                                        phone = r.recipientPhone,
                                        name = r.recipientName,
                                        address = r.address
                                    )
                                    val house = match?.household
                                    val resident = match?.matchedResident
                                    Parcel(
                                        deliveryDayId = today.id,
                                        trackingNumber = r.trackingNumber.ifBlank { "TEMP_${System.currentTimeMillis()}" },
                                        recipientName = r.recipientName.ifBlank { resident?.fullName ?: "ไม่ระบุชื่อ" },
                                        recipientPhone = r.recipientPhone.ifBlank { resident?.phoneNumber ?: "" },
                                        householdId = house?.id,
                                        destinationAddress = r.address.ifBlank { house?.fullAddress() ?: "" },
                                        latitude = house?.latitude,
                                        longitude = house?.longitude,
                                        codAmountSatang = r.codAmountSatang,
                                        source = "IMPORT_PASTE",
                                        deliveryNotes = if (r.carrier.isNotBlank()) "ขนส่ง: ${r.carrier}" else ""
                                    )
                                }
                                deliveryRepo.saveParcelsBatch(newParcels)
                                onImportCompleted()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = "ยืนยันบันทึกพัสดุทั้งหมด ($validCount ชิ้น)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ParsedRowCard(
    index: Int,
    row: ParsedParcelRow,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$index. ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = row.trackingNumber.ifBlank { "ไม่มีเลขพัสดุ" },
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (row.trackingNumber.isNotBlank()) DropwayPrimary else Color(0xFFEF4444)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.codAmountSatang > 0) {
                        Text(
                            text = "COD ${MoneyUtils.formatBaht(row.codAmountSatang)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFD97706),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "ลบ",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp).clickable { onDelete() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${row.recipientName.ifBlank { "-" }} • โทร: ${row.recipientPhone.ifBlank { "-" }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            if (row.address.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = row.address,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}