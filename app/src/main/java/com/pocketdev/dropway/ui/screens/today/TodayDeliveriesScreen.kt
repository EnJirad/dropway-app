package com.pocketdev.dropway.ui.screens.today

import android.content.Intent
import com.pocketdev.dropway.R
import androidx.compose.ui.res.painterResource
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
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.core.model.DailyDeliverySummary
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.ui.components.DeliveryStatusBadge
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.components.PaymentStatusBadge
import com.pocketdev.dropway.ui.screens.cod.RecordPaymentDialog
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TodayDeliveriesScreen(
    onNavigateBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenMap: () -> Unit
) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val paymentRepo = DropwayApplication.instance.paymentRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var activeDayId by remember { mutableStateOf<Long?>(null) }
    var parcels by remember { mutableStateOf<List<Parcel>>(emptyList()) }
    var summary by remember { mutableStateOf(DailyDeliverySummary()) }

    var selectedFilter by remember { mutableStateOf<DeliveryStatus?>(null) }
    var codOnlyFilter by remember { mutableStateOf(false) }
    var groupByHousehold by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedParcelForDetail by remember { mutableStateOf<Parcel?>(null) }
    var parcelForPayment by remember { mutableStateOf<Parcel?>(null) }

    LaunchedEffect(Unit) {
        val today = deliveryRepo.getOrCreateToday()
        activeDayId = today.id
        deliveryRepo.observeParcelsForDay(today.id).collect {
            parcels = it
        }
    }

    LaunchedEffect(activeDayId) {
        activeDayId?.let { dayId ->
            deliveryRepo.observeSummaryForDay(dayId).collect {
                summary = it
            }
        }
    }

    // Filter parcels
    val filteredParcels = remember(parcels, selectedFilter, codOnlyFilter, searchQuery) {
        parcels.filter { p ->
            val matchStatus = selectedFilter == null || p.deliveryStatus == selectedFilter
            val matchCod = !codOnlyFilter || (p.codAmountSatang > 0 && p.paymentStatus != PaymentStatus.CASH_RECEIVED && p.paymentStatus != PaymentStatus.TRANSFER_RECEIVED)
            val matchQuery = searchQuery.isBlank() ||
                    p.trackingNumber.contains(searchQuery, ignoreCase = true) ||
                    p.recipientName.contains(searchQuery, ignoreCase = true) ||
                    p.recipientPhone.contains(searchQuery) ||
                    p.destinationAddress.contains(searchQuery, ignoreCase = true)
            matchStatus && matchCod && matchQuery
        }
    }

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
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "กลับ",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "งานวันนี้ (Today's Parcels)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "ทั้งหมด ${parcels.size} ชิ้น • สำเร็จ ${summary.deliveredCount} • คงเหลือ ${summary.pendingCount}",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = onOpenMap) {
                            Icon(painter = painterResource(id = R.drawable.ic_map), contentDescription = "แผนที่", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onOpenScanner) {
                            Icon(painter = painterResource(id = R.drawable.ic_add), contentDescription = "เพิ่มพัสดุ", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
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
            // Overview KPI Cards
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Row 1: Parcels Count KPI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiTile(
                            title = "ทั้งหมด",
                            value = "${summary.totalParcels}",
                            subtitle = "${summary.uniqueLocations} จุดส่ง",
                            bgColor = DropwayPrimary.copy(alpha = 0.1f),
                            textColor = DropwayPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiTile(
                            title = "ส่งแล้ว",
                            value = "${summary.deliveredCount}",
                            subtitle = "สำเร็จ",
                            bgColor = StatusDeliveredBg,
                            textColor = StatusDelivered,
                            modifier = Modifier.weight(1f)
                        )
                        KpiTile(
                            title = "คงเหลือ",
                            value = "${summary.pendingCount}",
                            subtitle = "รอนำส่ง",
                            bgColor = StatusPendingBg,
                            textColor = StatusPending,
                            modifier = Modifier.weight(1f)
                        )
                        KpiTile(
                            title = "ไม่สำเร็จ",
                            value = "${summary.failedCount}",
                            subtitle = "มีปัญหา",
                            bgColor = StatusFailedBg,
                            textColor = StatusFailed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: COD Money KPI
                    if (summary.totalCodSatang > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF8FAFC))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ยอด COD ทั้งหมด", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = MoneyUtils.formatBaht(summary.totalCodSatang),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("เก็บแล้ว", fontSize = 11.sp, color = StatusDelivered)
                                Text(
                                    text = MoneyUtils.formatBaht(summary.collectedCodSatang),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = StatusDelivered
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("ค้างเก็บ", fontSize = 11.sp, color = StatusFailed)
                                Text(
                                    text = MoneyUtils.formatBaht(summary.outstandingCodSatang),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = StatusFailed
                                )
                            }
                        }
                    }

                    // Search input with high-contrast text
                    Spacer(modifier = Modifier.height(10.dp))
                    DropwayOutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ค้นหาเลขพัสดุ, ชื่อ, เบอร์, ที่อยู่...") },
                        singleLine = true,
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_search), contentDescription = null, tint = DropwayPrimary, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Icon(painter = painterResource(id = R.drawable.ic_close), contentDescription = "ล้าง", tint = Color(0xFF475569), modifier = Modifier.size(16.dp).clickable { searchQuery = "" })
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Filter chips & Grouping toggle
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            FilterChip(
                                selected = selectedFilter == null && !codOnlyFilter,
                                onClick = { selectedFilter = null; codOnlyFilter = false },
                                label = { Text("ทั้งหมด", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedFilter == DeliveryStatus.PENDING,
                                onClick = {
                                    selectedFilter = if (selectedFilter == DeliveryStatus.PENDING) null else DeliveryStatus.PENDING
                                },
                                label = { Text("รอนำส่ง", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = codOnlyFilter,
                                onClick = { codOnlyFilter = !codOnlyFilter },
                                label = { Text("ค้าง COD", fontSize = 11.sp) }
                            )
                        }

                        // Group by Household toggle
                        TextButton(
                            onClick = { groupByHousehold = !groupByHousehold },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (groupByHousehold) "รวมตามบ้าน" else "แยกรายชิ้น",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DropwayPrimary
                            )
                        }
                    }
                }
            }

            // Parcel List
            if (filteredParcels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painter = painterResource(id = R.drawable.ic_package), contentDescription = null, tint = DropwayPrimary.copy(alpha = 0.35f), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (parcels.isEmpty()) "ยังไม่มีพัสดุสำหรับวันนี้" else "ไม่พบพัสดุตามเงื่อนไข",
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "กดปุ่ม '+' ด้านบนเพื่อสแกนหรือนำเข้าพัสดุ",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (groupByHousehold) {
                        val grouped = filteredParcels.groupBy {
                            if (it.householdId != null) "H_${it.householdId}" else it.destinationAddress.ifBlank { "NO_ADDR" }
                        }
                        grouped.forEach { (_, groupParcels) ->
                            item {
                                HouseholdParcelGroupCard(
                                    parcels = groupParcels,
                                    onParcelClick = { selectedParcelForDetail = it },
                                    onMarkDelivered = { p ->
                                        scope.launch {
                                            deliveryRepo.updateDeliveryStatus(p.id, DeliveryStatus.DELIVERED)
                                        }
                                    },
                                    onRecordCod = { parcelForPayment = it },
                                    onCall = { phone ->
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        context.startActivity(dialIntent)
                                    },
                                    onNavigate = { lat, lng ->
                                        LocationUtils.openNavigation(context, lat, lng)
                                    }
                                )
                            }
                        }
                    } else {
                        items(filteredParcels, key = { it.id }) { parcel ->
                            ParcelCard(
                                parcel = parcel,
                                onClick = { selectedParcelForDetail = parcel },
                                onMarkDelivered = {
                                    scope.launch {
                                        deliveryRepo.updateDeliveryStatus(parcel.id, DeliveryStatus.DELIVERED)
                                    }
                                },
                                onRecordCod = { parcelForPayment = parcel },
                                onCall = {
                                    if (parcel.recipientPhone.isNotBlank()) {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parcel.recipientPhone}"))
                                        context.startActivity(dialIntent)
                                    }
                                },
                                onNavigate = {
                                    if (parcel.latitude != null && parcel.longitude != null) {
                                        LocationUtils.openNavigation(context, parcel.latitude, parcel.longitude, parcel.recipientName)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedParcelForDetail != null) {
        ParcelDetailDialog(
            parcel = selectedParcelForDetail!!,
            onDismiss = { selectedParcelForDetail = null },
            onUpdateParcel = { updated ->
                scope.launch {
                    deliveryRepo.saveParcel(updated)
                    selectedParcelForDetail = null
                }
            },
            onDeleteParcel = { id ->
                scope.launch {
                    deliveryRepo.deleteParcel(id)
                    selectedParcelForDetail = null
                }
            }
        )
    }

    if (parcelForPayment != null) {
        RecordPaymentDialog(
            parcel = parcelForPayment!!,
            onDismiss = { parcelForPayment = null },
            onPaymentRecorded = {
                parcelForPayment = null
            }
        )
    }
}

@Composable
fun KpiTile(
    title: String,
    value: String,
    subtitle: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 18.sp, color = textColor, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 10.sp, color = textColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun ParcelCard(
    parcel: Parcel,
    onClick: () -> Unit,
    onMarkDelivered: () -> Unit,
    onRecordCod: () -> Unit,
    onCall: () -> Unit,
    onNavigate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Tracking Number + Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parcel.trackingNumber,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = DropwayPrimary
                    )
                }
                DeliveryStatusBadge(status = parcel.deliveryStatus)
            }

            Spacer(modifier = Modifier.height(6.dp))
            // Recipient & Phone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parcel.recipientName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                if (parcel.recipientPhone.isNotBlank()) {
                    Text(
                        text = PhoneUtils.format(parcel.recipientPhone),
                        fontSize = 13.sp,
                        color = DropwayPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onCall() }
                    )
                }
            }

            // Address
            if (parcel.destinationAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = parcel.destinationAddress,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }

            // COD Row
            if (parcel.codAmountSatang > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.ic_money), contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("COD: ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFB45309))
                        Text(
                            text = MoneyUtils.formatBaht(parcel.codAmountSatang),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = Color(0xFFB45309)
                        )
                    }
                    PaymentStatusBadge(status = parcel.paymentStatus)
                }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (parcel.deliveryStatus != DeliveryStatus.DELIVERED) {
                    Button(
                        onClick = onMarkDelivered,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("ส่งแล้ว", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (parcel.codAmountSatang > 0 && parcel.paymentStatus != PaymentStatus.CASH_RECEIVED && parcel.paymentStatus != PaymentStatus.TRANSFER_RECEIVED) {
                    Button(
                        onClick = onRecordCod,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_money), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("เก็บเงิน", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (parcel.recipientPhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCall,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_phone), contentDescription = "โทร", tint = DropwayPrimary, modifier = Modifier.size(16.dp))
                    }
                }

                if (parcel.latitude != null && parcel.longitude != null) {
                    OutlinedButton(
                        onClick = onNavigate,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_navigation), contentDescription = "นำทาง", tint = DropwayPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HouseholdParcelGroupCard(
    parcels: List<Parcel>,
    onParcelClick: (Parcel) -> Unit,
    onMarkDelivered: (Parcel) -> Unit,
    onRecordCod: (Parcel) -> Unit,
    onCall: (String) -> Unit,
    onNavigate: (Double, Double) -> Unit
) {
    val first = parcels.first()
    val totalGroupCod = parcels.sumOf { it.codAmountSatang }
    val deliveredCount = parcels.count { it.deliveryStatus == DeliveryStatus.DELIVERED }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DropwayNavy)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(painter = painterResource(id = R.drawable.ic_home), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("จุดส่งเดียว", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${parcels.size} ชิ้น (ส่งแล้ว $deliveredCount)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }

                if (first.latitude != null && first.longitude != null) {
                    IconButton(
                        onClick = { onNavigate(first.latitude, first.longitude) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_navigation), contentDescription = "นำทาง", tint = DropwayPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = first.destinationAddress.ifBlank { "ที่อยู่ผู้รับ" },
                fontSize = 13.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            if (totalGroupCod > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "รวม COD ทั้งหมดของบ้านนี้: ${MoneyUtils.formatBaht(totalGroupCod)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFB45309)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp))

            // Sub-items
            parcels.forEach { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onParcelClick(p) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.trackingNumber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = DropwayPrimary
                        )
                        Text(
                            text = "${p.recipientName} ${if (p.recipientPhone.isNotBlank()) "(${p.recipientPhone})" else ""}",
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        if (p.codAmountSatang > 0) {
                            Text(
                                text = "COD: ${MoneyUtils.formatBaht(p.codAmountSatang)} • ${p.paymentStatus.label}",
                                fontSize = 11.sp,
                                color = Color(0xFFD97706)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeliveryStatusBadge(status = p.deliveryStatus)
                        Spacer(modifier = Modifier.width(6.dp))
                        if (p.deliveryStatus != DeliveryStatus.DELIVERED) {
                            Button(
                                onClick = { onMarkDelivered(p) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}