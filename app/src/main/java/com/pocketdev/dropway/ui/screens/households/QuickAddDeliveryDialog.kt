package com.pocketdev.dropway.ui.screens.households

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.Resident
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun QuickAddDeliveryDialog(
    household: Household,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val householdRepo = DropwayApplication.instance.householdRepository
    val deliveryRepo = DropwayApplication.instance.deliveryRepository

    var residents by remember { mutableStateOf<List<Resident>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddNewResident by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newNotes by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(household.id) {
        residents = householdRepo.getResidentsByHousehold(household.id)
        isLoading = false
    }

    fun addParcelForResident(recipientName: String, recipientPhone: String) {
        if (isSubmitting) return
        isSubmitting = true
        scope.launch {
            try {
                val today = deliveryRepo.getOrCreateToday()
                val tracking = TrackingUtils.generateTracking("DROP")
                val addressText = buildString {
                    append("บ้านเลขที่ ").append(household.houseNumber)
                    if (household.village.isNotBlank()) append(" หมู่ ").append(household.village)
                    if (household.villageName.isNotBlank()) append(" ").append(household.villageName)
                    if (household.subdistrict.isNotBlank()) append(" ต.").append(household.subdistrict)
                    if (household.district.isNotBlank()) append(" อ.").append(household.district)
                    if (household.province.isNotBlank()) append(" จ.").append(household.province)
                }

                val parcel = Parcel(
                    deliveryDayId = today.id,
                    trackingNumber = tracking,
                    recipientName = recipientName.trim(),
                    recipientPhone = recipientPhone.trim(),
                    householdId = household.id,
                    destinationAddress = addressText,
                    latitude = household.latitude,
                    longitude = household.longitude,
                    deliveryStatus = DeliveryStatus.PENDING,
                    deliveryNotes = household.notes,
                    source = "HOUSEHOLD_QUICK"
                )

                deliveryRepo.saveParcel(parcel)
                onSuccess("เพิ่มพัสดุสำหรับ $recipientName เข้างานวันนี้เรียบร้อยแล้ว")
                onDismiss()
            } catch (e: Exception) {
                isSubmitting = false
                Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DropwayPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_package),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "เพิ่มพัสดุเข้างานวันนี้",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "บ้านเลขที่ ${household.houseNumber} ${if (household.village.isNotBlank()) "หมู่ " + household.village else ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DropwayPrimaryDark
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

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "แตะปุ่ม '+ ส่งคนนี้' เพื่อเพิ่มเข้างานวันนี้ทันที",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Residents List
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DropwayPrimary, modifier = Modifier.size(28.dp))
                    }
                } else if (residents.isEmpty()) {
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ยังไม่มีรายชื่อผู้รับในบ้านนี้",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "พิมพ์ชื่อผู้รับด้านล่างเพื่อเพิ่มและส่งได้ทันที",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(residents, key = { it.id }) { res ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(DropwayPrimary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_person),
                                                contentDescription = null,
                                                tint = DropwayPrimaryDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = res.fullName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = TextPrimary
                                            )
                                            if (res.phoneNumber.isNotBlank()) {
                                                Text(
                                                    text = PhoneUtils.format(res.phoneNumber),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextMuted
                                                )
                                            }
                                            if (res.notes.isNotBlank()) {
                                                Text(
                                                    text = res.notes,
                                                    fontSize = 11.sp,
                                                    color = TextSubtle
                                                )
                                            }
                                        }
                                    }

                                    // Quick-add button: "ส่งคนนี้"
                                    Button(
                                        onClick = { addParcelForResident(res.fullName, res.phoneNumber) },
                                        enabled = !isSubmitting,
                                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_add),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ส่งคนนี้",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Add New Resident Form
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (showAddNewResident) Color(0xFFF1F5F9) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddNewResident = !showAddNewResident }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = if (showAddNewResident) R.drawable.ic_close else R.drawable.ic_add),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showAddNewResident) "ซ่อนแบบฟอร์มเพิ่มผู้รับ" else "+ เพิ่มชื่อผู้รับใหม่ของบ้านนี้",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DropwayPrimaryDark
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showAddNewResident) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        DropwayOutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it; inputError = null },
                            label = { Text("ชื่อ-นามสกุล ผู้รับใหม่ *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        DropwayOutlinedTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it },
                            label = { Text("เบอร์โทรศัพท์ (ถ้ามี)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        DropwayOutlinedTextField(
                            value = newNotes,
                            onValueChange = { newNotes = it },
                            label = { Text("หมายเหตุ (เช่น ลูกสาว, ญาติ)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (inputError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inputError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (newName.isBlank()) {
                                    inputError = "กรุณาระบุชื่อผู้รับ"
                                    return@Button
                                }
                                scope.launch {
                                    // Save new resident to household
                                    householdRepo.saveResident(
                                        Resident(
                                            householdId = household.id,
                                            fullName = newName.trim(),
                                            phoneNumber = newPhone.trim(),
                                            notes = newNotes.trim()
                                        )
                                    )
                                    // Add parcel and close
                                    addParcelForResident(newName.trim(), newPhone.trim())
                                }
                            },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("บันทึกและส่งเข้างานวันนี้ทันที", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
