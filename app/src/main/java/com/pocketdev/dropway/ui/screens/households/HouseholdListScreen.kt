package com.pocketdev.dropway.ui.screens.households

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HouseholdListScreen(
    onHouseholdClick: (Long) -> Unit,
    onOpenLocationPicker: ((Double?, Double?) -> Unit)? = null
) {
    val repository = DropwayApplication.instance.householdRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedVillageFilter by remember { mutableStateOf("") }
    var households by remember { mutableStateOf<List<Household>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var distinctVillages by remember { mutableStateOf<List<String>>(emptyList()) }

    var isImporting by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var importReplaceMode by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingHousehold by remember { mutableStateOf<Household?>(null) }
    var quickAddHousehold by remember { mutableStateOf<Household?>(null) }

    // File picker launcher for CSV import
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                importStatusMessage = "กำลังอ่านและประมวลผลไฟล์ CSV จากเครื่อง..."
                try {
                    val res = repository.importFromUri(context, uri, clearExisting = importReplaceMode)
                    totalCount = repository.getCount()
                    distinctVillages = repository.getDistinctVillages()
                    searchQuery = ""
                    selectedVillageFilter = ""
                    households = repository.searchHouseholds("", "")
                    Toast.makeText(
                        context,
                        "นำเข้าสำเร็จ ${res.householdsCreated} หลังคาเรือน (${res.residentsCreated} รายชื่อ)",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "เกิดข้อผิดพลาดในการนำเข้า: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isImporting = false
                    importStatusMessage = null
                    showImportOptionsDialog = false
                }
            }
        }
    }

    LaunchedEffect(searchQuery, selectedVillageFilter) {
        households = repository.searchHouseholds(searchQuery, selectedVillageFilter)
        totalCount = repository.getCount()
        distinctVillages = repository.getDistinctVillages()
    }

    val villages = remember(distinctVillages) {
        distinctVillages
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingHousehold = null
                    showAddDialog = true
                },
                containerColor = DropwayPrimary,
                contentColor = Color.White
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("เพิ่มบ้าน", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DropwayBackground)
        ) {
            // Top Surface: Header, Action Buttons, Search & Village Filters
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Status & Action Buttons (Upload CSV & Sync Master)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ฐานข้อมูลลูกบ้าน ($totalCount หลังคาเรือน)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DropwayPrimary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Upload CSV Button
                            Button(
                                onClick = { showImportOptionsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_document),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "อัพโหลด CSV",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Sync Master CSV Button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isImporting = true
                                        importStatusMessage = "กำลังซิงค์ Master CSV..."
                                        val res = repository.importFromCsv(context, clearExisting = false)
                                        isImporting = false
                                        importStatusMessage = null
                                        households = repository.searchHouseholds(searchQuery, selectedVillageFilter)
                                        totalCount = repository.getCount()
                                        distinctVillages = repository.getDistinctVillages()
                                        Toast.makeText(
                                            context,
                                            "ซิงค์สำเร็จ ${res.householdsCreated} หลังคาเรือน (${res.residentsCreated} รายชื่อ)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_refresh),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ซิงค์ Master", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    DropwayOutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ค้นหาบ้านเลขที่, ชื่อผู้รับ, เบอร์โทร, หมู่, โน้ต...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                tint = DropwayPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_close),
                                        contentDescription = "ล้างการค้นหา",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Village Filter Chips with Horizontal Scroll
                    if (villages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedVillageFilter.isEmpty(),
                                onClick = { selectedVillageFilter = "" },
                                label = { Text("ทั้งหมด ($totalCount)") }
                            )
                            villages.forEach { v ->
                                FilterChip(
                                    selected = selectedVillageFilter == v,
                                    onClick = {
                                        selectedVillageFilter = if (selectedVillageFilter == v) "" else v
                                    },
                                    label = { Text("หมู่ $v") }
                                )
                            }
                        }
                    }
                }
            }

            // Household List / Empty State
            if (households.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_home),
                            contentDescription = null,
                            tint = DropwayPrimary.copy(alpha = 0.35f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "ยังไม่มีข้อมูลบ้านในคลัง" else "ไม่พบข้อมูลบ้านที่ค้นหา",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) {
                                "สามารถอัพโหลดไฟล์ CSV ของคุณเพื่อลิสต์บ้านทั้งหมด หรือโหลดชุดข้อมูลเริ่มต้นได้ทันที"
                            } else {
                                "ลองค้นหาด้วยคำค้นอื่น"
                            },
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))

                            // Big Upload Button in Empty State
                            Button(
                                onClick = { showImportOptionsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_document),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("อัพโหลดไฟล์ CSV จากเครื่อง", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Master Data Quick Button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isImporting = true
                                        importStatusMessage = "กำลังนำเข้าข้อมูล Master 4,500+ รายการ..."
                                        val res = repository.importFromCsv(context, clearExisting = false)
                                        isImporting = false
                                        importStatusMessage = null
                                        households = repository.searchHouseholds("", "")
                                        totalCount = repository.getCount()
                                        distinctVillages = repository.getDistinctVillages()
                                        Toast.makeText(
                                            context,
                                            "นำเข้าสำเร็จ ${res.householdsCreated} หลังคาเรือน (${res.residentsCreated} รายชื่อ)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_refresh),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("โหลดข้อมูล Master 4,500+ หลังคาเรือน", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(households, key = { it.id }) { household ->
                        HouseholdCard(
                            household = household,
                            onClick = { onHouseholdClick(household.id) },
                            onQuickAdd = { quickAddHousehold = household },
                            onNavigate = {
                                if (household.latitude != null && household.longitude != null) {
                                    LocationUtils.openNavigation(
                                        context = context,
                                        latitude = household.latitude,
                                        longitude = household.longitude,
                                        label = "บ้านเลขที่ ${household.houseNumber}"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Import CSV Options Dialog
    if (showImportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_document),
                        contentDescription = null,
                        tint = DropwayPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("นำเข้าข้อมูลบ้านจาก CSV", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "เลือกวิธีการนำเข้าข้อมูลบ้านเข้าสู่ระบบ Dropway",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("โหมดการบันทึกข้อมูล:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !importReplaceMode,
                            onClick = { importReplaceMode = false },
                            label = { Text("เพิ่มต่อจากเดิม") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = importReplaceMode,
                            onClick = { importReplaceMode = true },
                            label = { Text("ลบเก่าแล้วแทนที่") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Option 1: File Picker from Storage
                    Button(
                        onClick = {
                            csvPickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_document),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("เลือกไฟล์ CSV จากเครื่อง", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Default Master Data
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isImporting = true
                                importStatusMessage = "กำลังนำเข้าข้อมูล Master 4,500+ รายการ..."
                                try {
                                    val res = repository.importFromCsv(context, clearExisting = importReplaceMode)
                                    totalCount = repository.getCount()
                                    distinctVillages = repository.getDistinctVillages()
                                    searchQuery = ""
                                    selectedVillageFilter = ""
                                    households = repository.searchHouseholds("", "")
                                    Toast.makeText(
                                        context,
                                        "นำเข้าสำเร็จ ${res.householdsCreated} หลังคาเรือน (${res.residentsCreated} รายชื่อ)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isImporting = false
                                    importStatusMessage = null
                                    showImportOptionsDialog = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ใช้ชุดข้อมูล Master (4,500+ หลังคาเรือน)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ระบบรองรับไฟล์ CSV ทุกรูปแบบ (ทั้งไฟล์จาก Excel, Google Sheets, หรือ all_home.csv) โดยจะแยกบ้านเลขที่, หมู่บ้าน, ชื่อผู้รับ, เบอร์โทร และพิกัดให้อัตโนมัติ",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showImportOptionsDialog = false },
                    enabled = !isImporting
                ) {
                    Text("ปิด", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal Progress Dialog During Import
    if (isImporting) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = DropwayPrimary, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = importStatusMessage ?: "กำลังนำเข้าข้อมูล...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ระบบกำลังแยกบ้านเลขที่ หมู่ และรายชื่อลูกบ้าน กรุณารอสักครู่",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    // Add / Edit Household Dialog
    if (showAddDialog) {
        AddEditHouseholdDialog(
            household = editingHousehold,
            onDismiss = { showAddDialog = false },
            onSaveWithResident = { savedHouse, residentName, residentPhone ->
                scope.launch {
                    repository.saveHouseholdWithResident(savedHouse, residentName, residentPhone)
                    households = repository.searchHouseholds(searchQuery, selectedVillageFilter)
                    totalCount = repository.getCount()
                    distinctVillages = repository.getDistinctVillages()
                    showAddDialog = false
                    Toast.makeText(
                        context,
                        "บันทึกข้อมูลบ้านเลขที่ ${savedHouse.houseNumber} สำเร็จ",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // Quick-Add Delivery Dialog (ปึ้งคือจบ)
    if (quickAddHousehold != null) {
        QuickAddDeliveryDialog(
            household = quickAddHousehold!!,
            onDismiss = { quickAddHousehold = null },
            onSuccess = { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun HouseholdCard(
    household: Household,
    onClick: () -> Unit,
    onQuickAdd: () -> Unit,
    onNavigate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DropwayPrimary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "เลขที่ ${household.houseNumber}",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = DropwayPrimary
                        )
                    }
                    if (household.village.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "หมู่ ${household.village}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Action Buttons on Right: Quick-Add (+) Button and GPS Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick-Add Button: "+" for instant parcel creation into today's deliveries
                    Button(
                        onClick = onQuickAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_add),
                            contentDescription = "ส่งบ้านนี้",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ส่งวันนี้",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // GPS Indicator / Navigation Trigger
                    if (household.latitude != null && household.longitude != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusDeliveredBg)
                                .clickable { onNavigate() }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_navigation),
                                    contentDescription = "นำทาง",
                                    tint = StatusDelivered,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (household.addressDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = household.addressDescription,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            if (household.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_document),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = household.notes,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
