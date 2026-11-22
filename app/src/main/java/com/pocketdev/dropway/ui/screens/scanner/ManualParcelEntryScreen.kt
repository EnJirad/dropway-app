package com.pocketdev.dropway.ui.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun ManualParcelEntryScreen(
    onSaved: () -> Unit
) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val householdRepo = DropwayApplication.instance.householdRepository
    val scope = rememberCoroutineScope()

    var trackingNumber by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var houseNumber by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var destinationAddress by remember { mutableStateOf("") }
    var codAmountStr by remember { mutableStateOf("") }
    var deliveryNotes by remember { mutableStateOf("") }

    var matchedHouseholdInfo by remember { mutableStateOf<String?>(null) }
    var matchedHouseholdId by remember { mutableStateOf<Long?>(null) }
    var duplicateError by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Auto-match household when house number or phone changes
    LaunchedEffect(houseNumber, village, recipientPhone, recipientName) {
        if (houseNumber.isNotBlank() || recipientPhone.isNotBlank() || recipientName.isNotBlank()) {
            val match = householdRepo.findMatchingHousehold(
                houseNumber = houseNumber,
                village = village,
                phone = recipientPhone,
                name = recipientName
            )
            if (match != null && match.confidenceScore >= 50) {
                matchedHouseholdId = match.household.id
                matchedHouseholdInfo = "ตรงกับบ้านเลขที่ ${match.household.houseNumber} (${match.matchReason})"
            } else {
                matchedHouseholdId = null
                matchedHouseholdInfo = null
            }
        }
    }

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
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "ป้อนข้อมูลพัสดุ (Manual Entry)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Tracking
                DropwayOutlinedTextField(
                    value = trackingNumber,
                    onValueChange = {
                        trackingNumber = it
                        duplicateError = null
                        validationError = null
                    },
                    label = { Text("เลขพัสดุ (Tracking Number) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (duplicateError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(duplicateError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropwayOutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it; validationError = null },
                        label = { Text("ชื่อผู้รับ *") },
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

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropwayOutlinedTextField(
                        value = houseNumber,
                        onValueChange = { houseNumber = it },
                        label = { Text("บ้านเลขที่") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DropwayOutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("หมู่ (ม.)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = destinationAddress,
                    onValueChange = { destinationAddress = it },
                    label = { Text("ที่อยู่จัดส่งเพิ่มเติม (ตำบล อำเภอ จังหวัด)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Household Auto-match alert
                if (matchedHouseholdInfo != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFD1FAE5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = matchedHouseholdInfo!!,
                            color = Color(0xFF065F46),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = codAmountStr,
                    onValueChange = { codAmountStr = it },
                    label = { Text("ยอดเงิน COD (บาท) - ถ้าไม่มีให้เว้นว่าง") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("0.00") }
                )

                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = deliveryNotes,
                    onValueChange = { deliveryNotes = it },
                    label = { Text("หมายเหตุการจัดส่ง") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น โทรแจ้งก่อนเข้าส่ง") }
                )

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(validationError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val track = TrackingUtils.normalize(trackingNumber)
                            if (track.isBlank()) {
                                validationError = "กรุณากรอกเลขพัสดุ"
                                return@OutlinedButton
                            }
                            scope.launch {
                                if (deliveryRepo.isTrackingDuplicate(track)) {
                                    duplicateError = "เลขพัสดุนี้เคยถูกบันทึกแล้ว"
                                    return@launch
                                }
                                val today = deliveryRepo.getOrCreateToday()
                                val fullAddr = listOfNotNull(
                                    if (houseNumber.isNotBlank()) "บ้านเลขที่ $houseNumber" else null,
                                    if (village.isNotBlank()) "หมู่ $village" else null,
                                    destinationAddress.ifBlank { null }
                                ).joinToString(" ")

                                val parcel = Parcel(
                                    deliveryDayId = today.id,
                                    trackingNumber = track,
                                    recipientName = recipientName.trim().ifBlank { "ไม่ระบุชื่อ" },
                                    recipientPhone = PhoneUtils.normalize(recipientPhone),
                                    householdId = matchedHouseholdId,
                                    destinationAddress = fullAddr,
                                    codAmountSatang = MoneyUtils.parseToSatang(codAmountStr),
                                    deliveryNotes = deliveryNotes.trim(),
                                    source = "MANUAL"
                                )
                                deliveryRepo.saveParcel(parcel)

                                // Reset form for next
                                trackingNumber = ""
                                recipientName = ""
                                recipientPhone = ""
                                houseNumber = ""
                                village = ""
                                destinationAddress = ""
                                codAmountStr = ""
                                deliveryNotes = ""
                                matchedHouseholdId = null
                                matchedHouseholdInfo = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("บันทึก & ป้อนต่อ", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val track = TrackingUtils.normalize(trackingNumber)
                            if (track.isBlank()) {
                                validationError = "กรุณากรอกเลขพัสดุ"
                                return@Button
                            }
                            scope.launch {
                                if (deliveryRepo.isTrackingDuplicate(track)) {
                                    duplicateError = "เลขพัสดุนี้เคยถูกบันทึกแล้ว"
                                    return@launch
                                }
                                val today = deliveryRepo.getOrCreateToday()
                                val fullAddr = listOfNotNull(
                                    if (houseNumber.isNotBlank()) "บ้านเลขที่ $houseNumber" else null,
                                    if (village.isNotBlank()) "หมู่ $village" else null,
                                    destinationAddress.ifBlank { null }
                                ).joinToString(" ")

                                val parcel = Parcel(
                                    deliveryDayId = today.id,
                                    trackingNumber = track,
                                    recipientName = recipientName.trim().ifBlank { "ไม่ระบุชื่อ" },
                                    recipientPhone = PhoneUtils.normalize(recipientPhone),
                                    householdId = matchedHouseholdId,
                                    destinationAddress = fullAddr,
                                    codAmountSatang = MoneyUtils.parseToSatang(codAmountStr),
                                    deliveryNotes = deliveryNotes.trim(),
                                    source = "MANUAL"
                                )
                                deliveryRepo.saveParcel(parcel)
                                onSaved()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("บันทึกเสร็จสิ้น", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
