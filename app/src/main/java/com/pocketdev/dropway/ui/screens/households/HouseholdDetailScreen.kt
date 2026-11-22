package com.pocketdev.dropway.ui.screens.households

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.model.Resident
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HouseholdDetailScreen(
    householdId: Long,
    onBack: () -> Unit
) {
    val repository = DropwayApplication.instance.householdRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var household by remember { mutableStateOf<Household?>(null) }
    var residents by remember { mutableStateOf<List<Resident>>(emptyList()) }
    var showEditHouseholdDialog by remember { mutableStateOf(false) }
    var showAddResidentDialog by remember { mutableStateOf(false) }
    var editingResident by remember { mutableStateOf<Resident?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(householdId) {
        household = repository.getHouseholdById(householdId)
        residents = repository.getResidentsByHousehold(householdId)
    }

    if (household == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DropwayPrimary)
        }
        return
    }

    val h = household!!

    Scaffold(
        topBar = {
            Surface(color = DropwayNavy, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "←",
                            fontSize = 22.sp,
                            color = Color.White,
                            modifier = Modifier
                                .clickable { onBack() }
                                .padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "บ้านเลขที่ ${h.houseNumber}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            if (h.village.isNotBlank()) {
                                Text(
                                    text = "หมู่ ${h.village} ${h.villageName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = { showEditHouseholdDialog = true }) {
                            Text("✏️", fontSize = 16.sp)
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Text("🗑️", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DropwayBackground),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Address & GPS card
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📍 ที่อยู่และพิกัดนำทาง", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = h.fullAddress(), fontSize = 14.sp, color = TextPrimary)

                        if (h.addressDescription.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "จุดสังเกต: ${h.addressDescription}", fontSize = 13.sp, color = TextSecondary)
                        }

                        if (h.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "โน้ต: ${h.notes}", fontSize = 13.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        if (h.latitude != null && h.longitude != null) {
                            Button(
                                onClick = {
                                    LocationUtils.openNavigation(context, h.latitude, h.longitude, "บ้านเลขที่ ${h.houseNumber}")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("🚗 เริ่มนำทางไปบ้านนี้", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showEditHouseholdDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("📍 เพิ่มพิกัด GPS", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Residents section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👥 ผู้อยู่อาศัย (${residents.size} คน)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Button(
                        onClick = {
                            editingResident = null
                            showAddResidentDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("+ เพิ่มคน", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (residents.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("ยังไม่มีรายชื่อผู้อยู่อาศัยในบ้านนี้", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(residents, key = { it.id }) { resident ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = resident.fullName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                if (resident.phoneNumber.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = PhoneUtils.format(resident.phoneNumber),
                                        fontSize = 13.sp,
                                        color = DropwayPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (resident.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = resident.notes,
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (resident.phoneNumber.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${resident.phoneNumber}"))
                                            context.startActivity(dialIntent)
                                        }
                                    ) {
                                        Text("📞", fontSize = 18.sp)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        editingResident = resident
                                        showAddResidentDialog = true
                                    }
                                ) {
                                    Text("✏️", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditHouseholdDialog) {
        AddEditHouseholdDialog(
            household = h,
            onDismiss = { showEditHouseholdDialog = false },
            onSave = { updated ->
                scope.launch {
                    repository.saveHousehold(updated)
                    household = repository.getHouseholdById(householdId)
                    showEditHouseholdDialog = false
                }
            }
        )
    }

    if (showAddResidentDialog) {
        AddEditResidentDialog(
            householdId = householdId,
            resident = editingResident,
            onDismiss = { showAddResidentDialog = false },
            onSave = { saved ->
                scope.launch {
                    repository.saveResident(saved)
                    residents = repository.getResidentsByHousehold(householdId)
                    showAddResidentDialog = false
                }
            },
            onDelete = { resId ->
                scope.launch {
                    repository.deleteResident(resId, householdId)
                    residents = repository.getResidentsByHousehold(householdId)
                    showAddResidentDialog = false
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("ยืนยันการลบข้อมูลบ้าน") },
            text = { Text("คุณต้องการลบบ้านเลขที่ ${h.houseNumber} ใช่หรือไม่? รายชื่อผู้อยู่อาศัยทั้งหมดของบ้านนี้จะถูกลบด้วย") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteHousehold(householdId)
                            showDeleteConfirmDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ลบข้อมูล")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}

@Composable
fun AddEditResidentDialog(
    householdId: Long,
    resident: Resident? = null,
    onDismiss: () -> Unit,
    onSave: (Resident) -> Unit,
    onDelete: ((Long) -> Unit)? = null
) {
    var fullName by remember { mutableStateOf(resident?.fullName ?: "") }
    var phoneNumber by remember { mutableStateOf(resident?.phoneNumber ?: "") }
    var notes by remember { mutableStateOf(resident?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (resident == null) "👤 เพิ่มผู้อยู่อาศัย" else "✏️ แก้ไขผู้อยู่อาศัย",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; error = null },
                    label = { Text("ชื่อ-นามสกุล *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("เบอร์โทรศัพท์") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("0812345678") }
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("หมายเหตุ (เช่น แม่, ลูกสาว, คุณตา)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (resident != null && onDelete != null) {
                        TextButton(
                            onClick = { onDelete(resident.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("ลบ")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("ยกเลิก")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (fullName.trim().isBlank()) {
                                    error = "กรุณากรอกชื่อผู้อยู่อาศัย"
                                    return@Button
                                }
                                val result = (resident ?: Resident(householdId = householdId, fullName = "")).copy(
                                    fullName = fullName.trim(),
                                    phoneNumber = PhoneUtils.normalize(phoneNumber),
                                    notes = notes.trim()
                                )
                                onSave(result)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary)
                        ) {
                            Text("บันทึก")
                        }
                    }
                }
            }
        }
    }
}
