package com.pocketdev.dropway.ui.screens.scanner

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.model.Resident
import com.pocketdev.dropway.core.util.MatchLevel
import com.pocketdev.dropway.core.util.MatchResult
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

data class ScannedLabelDraft(
    val recipientName: String = "",
    val recipientPhone: String = "",
    val houseNumber: String = "",
    val address: String = "",
    val postalCode: String = "",
    val trackingNumber: String = "",
    val carrierName: String = "",
    val codAmountSatang: Long = 0L,
    val deliveryNotes: String = "",
    val rawText: String = "",
    val orderId: String = "",
    val sortingCode: String = "",
    val sellerPlatform: String = "",
    val confidenceScore: Int = 90
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ScanResultBottomSheet(
    draft: ScannedLabelDraft,
    primaryMatch: MatchResult?,
    candidates: List<MatchResult>,
    isDuplicateInToday: Boolean,
    onDismiss: () -> Unit,
    onAddToNavigation: (ScannedLabelDraft, Household?, Resident?) -> Unit,
    onSaveAsNewHouse: (ScannedLabelDraft) -> Unit,
    onScanNext: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val householdRepo = DropwayApplication.instance.householdRepository

    var selectedHousehold by remember { mutableStateOf(primaryMatch?.household) }
    var selectedResident by remember { mutableStateOf(primaryMatch?.matchedResident) }
    var currentMatch by remember { mutableStateOf(primaryMatch) }

    // Form editing states
    var name by remember { mutableStateOf(draft.recipientName) }
    var phone by remember { mutableStateOf(draft.recipientPhone) }
    var houseNo by remember { mutableStateOf(draft.houseNumber) }
    var address by remember { mutableStateOf(draft.address) }
    var postalCode by remember { mutableStateOf(draft.postalCode) }
    var tracking by remember { mutableStateOf(draft.trackingNumber) }
    var codStr by remember {
        mutableStateOf(if (draft.codAmountSatang > 0L) (draft.codAmountSatang / 100.0).toString() else "")
    }
    var deliveryNotes by remember {
        mutableStateOf(
            draft.deliveryNotes.ifBlank {
                primaryMatch?.household?.notes ?: ""
            }
        )
    }

    var isEditingForm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Household>>(emptyList()) }

    // When primaryMatch changes, populate fields
    LaunchedEffect(primaryMatch) {
        selectedHousehold = primaryMatch?.household
        selectedResident = primaryMatch?.matchedResident
        currentMatch = primaryMatch
        if (primaryMatch?.household != null) {
            val h = primaryMatch.household
            if (houseNo.isBlank()) houseNo = h.houseNumber
            if (address.isBlank()) address = h.fullAddress()
            if (deliveryNotes.isBlank()) deliveryNotes = h.notes
        }
        if (primaryMatch?.matchedResident != null) {
            val r = primaryMatch.matchedResident
            if (name.isBlank()) name = r.fullName
            if (phone.isBlank()) phone = r.phoneNumber
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            scope.launch {
                searchResults = householdRepo.searchHouseholds(searchQuery.trim())
            }
        } else {
            searchResults = emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Drag Handle Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(44.dp)
                                .height(4.dp),
                            shape = RoundedCornerShape(2.dp),
                            color = Color(0xFFCBD5E1)
                        ) {}
                    }
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_document),
                        contentDescription = null,
                        tint = DropwayPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ผลการสแกนฉลากพัสดุ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (draft.carrierName.isNotBlank()) {
                        Surface(
                            color = DropwayBlueSurface,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = draft.carrierName,
                                color = DropwayPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (draft.sortingCode.isNotBlank()) {
                        Surface(
                            color = Color(0xFFF0FDF4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = draft.sortingCode,
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Duplicate Parcel Warning Banner
            if (isDuplicateInToday) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF2F2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = StatusFailed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "มีเลขพัสดุนี้ในคิวงานวันนี้แล้ว (พัสดุชิ้นเดียวกัน)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = StatusFailed
                        )
                    }
                }
            }

            // Primary Match / Household Card
            if (selectedHousehold != null) {
                val house = selectedHousehold!!
                val match = currentMatch
                val level = match?.level ?: MatchLevel.HIGH

                val levelColor = when (level) {
                    MatchLevel.EXACT -> StatusDelivered
                    MatchLevel.HIGH -> DropwayPrimary
                    MatchLevel.MEDIUM -> StatusPending
                }
                val levelBg = when (level) {
                    MatchLevel.EXACT -> StatusDeliveredBg
                    MatchLevel.HIGH -> DropwayBlueSurface
                    MatchLevel.MEDIUM -> StatusPendingBg
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DropwayBlueSubtle,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, DropwayBlueBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = levelBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = level.label,
                                        color = levelColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                if (match != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${match.confidenceScore}% ความมั่นใจ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // GPS / Street View indicator
                            if (house.latitude != null && house.longitude != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_map_pin),
                                        contentDescription = null,
                                        tint = DropwayPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "มีพิกัด GPS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DropwayPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "บ้านเลขที่ ${house.houseNumber}" + (if (house.village.isNotBlank()) " หมู่ ${house.village}" else "") + (if (house.villageName.isNotBlank()) " (${house.villageName})" else ""),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )

                        if (selectedResident != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_person),
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${selectedResident!!.fullName} • ${selectedResident!!.phoneNumber}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                                if (selectedResident!!.phoneNumber.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${selectedResident!!.phoneNumber}"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_phone),
                                            contentDescription = "โทร",
                                            tint = DropwayPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Reasons tags
                        if (match != null && match.reasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                match.reasons.forEach { r ->
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Text(
                                            text = r,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Notes
                        if (house.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "หมายเหตุบ้าน: ${house.notes}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = StatusPending
                            )
                        }
                    }
                }
            } else {
                // No household match card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_home),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ยังไม่พบบ้านที่ตรงกันในคลังหลัก",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "คุณสามารถตรวจสอบข้อมูลด้านล่าง แล้วกดเพิ่มเข้าคิว หรือบันทึกเป็นบ้านใหม่เข้าคลังได้ทันที",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Candidate Switches (if other houses matched)
            if (candidates.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "บ้านแนะนำอื่น ๆ ที่ใกล้เคียง:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    candidates.take(5).forEach { c ->
                        val isCurr = c.household.id == selectedHousehold?.id
                        Surface(
                            color = if (isCurr) DropwayPrimary else Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurr) DropwayPrimary else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.clickable {
                                selectedHousehold = c.household
                                selectedResident = c.matchedResident
                                currentMatch = c
                                houseNo = c.household.houseNumber
                                address = c.household.fullAddress()
                                if (c.household.notes.isNotBlank()) deliveryNotes = c.household.notes
                            }
                        ) {
                            Text(
                                text = "เลขที่ ${c.household.houseNumber} (${c.confidenceScore}%)",
                                color = if (isCurr) Color.White else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Editable Fields Section Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditingForm = !isEditingForm },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ข้อมูลที่อ่านได้จากฉลาก (ตรวจสอบ / แก้ไข)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = DropwayPrimary
                )
                Text(
                    text = if (isEditingForm) "ซ่อนแบบฟอร์ม" else "แตะเพื่อแก้ไข",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DropwayBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Core Fields always visible or expanded
            DropwayOutlinedTextField(
                value = tracking,
                onValueChange = { tracking = it },
                label = { Text("เลขพัสดุ (Tracking No.)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropwayOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ชื่อผู้รับ") },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true
                )
                DropwayOutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("เบอร์โทร") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            if (isEditingForm) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DropwayOutlinedTextField(
                        value = houseNo,
                        onValueChange = { houseNo = it },
                        label = { Text("บ้านเลขที่") },
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
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ที่อยู่จัดส่ง") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                DropwayOutlinedTextField(
                    value = codStr,
                    onValueChange = { codStr = it },
                    label = { Text("ยอดเงิน COD (บาท)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                DropwayOutlinedTextField(
                    value = deliveryNotes,
                    onValueChange = { deliveryNotes = it },
                    label = { Text("หมายเหตุการส่ง") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น ฝากป้อมยาม, โทรแจ้งก่อนส่ง") }
                )

                // Search box to link any house in database
                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("ค้นหาบ้านในคลังหลักเพื่อเชื่อมโยง") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("พิมพ์บ้านเลขที่ หรือชื่อลูกบ้าน...") },
                    singleLine = true
                )

                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DropwayBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DropwayBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            searchResults.take(4).forEach { sh ->
                                Text(
                                    text = "บ้านเลขที่ ${sh.houseNumber} หมู่ ${sh.village} (${sh.fullAddress()})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DropwayPrimary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedHousehold = sh
                                            houseNo = sh.houseNumber
                                            address = sh.fullAddress()
                                            if (sh.notes.isNotBlank()) deliveryNotes = sh.notes
                                            searchQuery = ""
                                            searchResults = emptyList()
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            val updatedDraft = draft.copy(
                recipientName = name.trim(),
                recipientPhone = PhoneUtils.normalize(phone),
                houseNumber = houseNo.trim(),
                address = address.trim(),
                postalCode = postalCode.trim(),
                trackingNumber = tracking.trim(),
                codAmountSatang = MoneyUtils.parseToSatang(codStr),
                deliveryNotes = deliveryNotes.trim()
            )

            // Primary: Add to Today's Navigation Queue
            Button(
                onClick = {
                    onAddToNavigation(updatedDraft, selectedHousehold, selectedResident)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "เพิ่มเข้าคิวส่งวันนี้",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary: Save as new household (if not already matched or want new)
            if (selectedHousehold == null) {
                OutlinedButton(
                    onClick = {
                        onSaveAsNewHouse(updatedDraft)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_home),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("บันทึกเป็นบ้านใหม่เข้าคลัง", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tertiary: Scan Next Label immediately
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onScanNext,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("สแกนใบถัดไป", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Text("ปิด / ถ่ายใหม่", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            }
        }
    }
}
}
}
