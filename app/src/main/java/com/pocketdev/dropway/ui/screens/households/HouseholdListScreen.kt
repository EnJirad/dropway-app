package com.pocketdev.dropway.ui.screens.households

import android.widget.Toast
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHousehold by remember { mutableStateOf<Household?>(null) }
    var quickAddHousehold by remember { mutableStateOf<Household?>(null) }

    LaunchedEffect(searchQuery, selectedVillageFilter) {
        households = repository.searchHouseholds(searchQuery, selectedVillageFilter)
    }

    val villages = remember(households) {
        households.map { it.village.trim() }.filter { it.isNotBlank() }.distinct().sorted()
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
            // Search Input with High-Contrast Bold Text
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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

                    // Village Filter Chips
                    if (villages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedVillageFilter.isEmpty(),
                                onClick = { selectedVillageFilter = "" },
                                label = { Text("ทั้งหมด (${households.size})") }
                            )
                            villages.take(5).forEach { v ->
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

            // Household List
            if (households.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            text = "กดปุ่ม '+ เพิ่มบ้าน' เพื่อบันทึกข้อมูล",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
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

    // Add / Edit Household Dialog
    if (showAddDialog) {
        AddEditHouseholdDialog(
            household = editingHousehold,
            onDismiss = { showAddDialog = false },
            onSave = { saved ->
                scope.launch {
                    repository.saveHousehold(saved)
                    households = repository.searchHouseholds(searchQuery, selectedVillageFilter)
                    showAddDialog = false
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
