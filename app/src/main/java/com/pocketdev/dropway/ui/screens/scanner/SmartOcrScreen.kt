package com.pocketdev.dropway.ui.screens.scanner

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.SmartTextParser
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.StatusDelivered
import com.pocketdev.dropway.ui.theme.TextPrimary
import com.pocketdev.dropway.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SmartOcrScreen(
    onParcelSaved: () -> Unit
) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val scope = rememberCoroutineScope()

    var rawOcrText by remember { mutableStateOf("") }
    var trackingNumber by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var codAmountStr by remember { mutableStateOf("") }
    var isParsed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "อ่านฉลากพัสดุ (Smart Label OCR)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ประมวลผลข้อความจากฉลากพัสดุบนเครื่องโดยตรง (On-Device) ปลอดภัยและทำงานได้แม้อยู่ในพื้นที่ไร้สัญญาณอินเทอร์เน็ต",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))
                DropwayOutlinedTextField(
                    value = rawOcrText,
                    onValueChange = {
                        rawOcrText = it
                        isParsed = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = {
                        Text("พิมพ์หรือวางข้อความที่สแกนได้จากฉลากพัสดุ...\n(เช่น TH0199887766 ผู้รับ: สมเกียรติ สว่างศรี โทร 086-123-4567 ที่อยู่ 88/2 ม.3 ต.สารภี เชียงใหม่ ยอด COD 420.-)")
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
                        Text("ใส่ตัวอย่างฉลาก", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (rawOcrText.isNotBlank()) {
                                val parsed = SmartTextParser.parseFreeText(rawOcrText)
                                trackingNumber = parsed.trackingNumber
                                recipientName = parsed.recipientName
                                recipientPhone = parsed.recipientPhone
                                address = parsed.address
                                codAmountStr = if (parsed.codAmountSatang > 0) (parsed.codAmountSatang / 100.0).toString() else ""
                                isParsed = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("แยกข้อมูลฉลาก", fontWeight = FontWeight.Bold)
                    }
                }

                // Extracted Editable Fields
                if (isParsed) {
                    Divider(modifier = Modifier.padding(vertical = 14.dp))
                    Text("ผลการแยกข้อมูล (ตรวจสอบและแก้ไขได้)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    DropwayOutlinedTextField(
                        value = trackingNumber,
                        onValueChange = { trackingNumber = it },
                        label = { Text("เลขพัสดุ (Tracking)") },
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
                    DropwayOutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ที่อยู่จัดส่ง") },
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

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val track = TrackingUtils.normalize(trackingNumber)
                            scope.launch {
                                val today = deliveryRepo.getOrCreateToday()
                                val parcel = Parcel(
                                    deliveryDayId = today.id,
                                    trackingNumber = track.ifBlank { "TEMP_${System.currentTimeMillis()}" },
                                    recipientName = recipientName.trim().ifBlank { "ไม่ระบุชื่อ" },
                                    recipientPhone = PhoneUtils.normalize(recipientPhone),
                                    destinationAddress = address.trim(),
                                    codAmountSatang = MoneyUtils.parseToSatang(codAmountStr),
                                    source = "OCR"
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
