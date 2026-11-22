package com.pocketdev.dropway.ui.screens.navigation

import android.content.Context
import com.pocketdev.dropway.R
import androidx.compose.ui.res.painterResource
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
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.ui.components.DeliveryStatusBadge
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DeliveryMapScreen(
    onParcelClick: (Parcel) -> Unit
) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var parcels by remember { mutableStateOf<List<Parcel>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf<DeliveryStatus?>(DeliveryStatus.PENDING) }
    var codOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val today = deliveryRepo.getOrCreateToday()
        deliveryRepo.observeParcelsForDay(today.id).collect {
            parcels = it
        }
    }

    val destinations = remember(parcels, selectedFilter, codOnly) {
        parcels.filter { p ->
            val matchStatus = selectedFilter == null || p.deliveryStatus == selectedFilter
            val matchCod = !codOnly || (p.codAmountSatang > 0 && p.paymentStatus != PaymentStatus.CASH_RECEIVED && p.paymentStatus != PaymentStatus.TRANSFER_RECEIVED)
            matchStatus && matchCod
        }
    }

    val stopsWithGps = destinations.count { it.latitude != null && it.longitude != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DropwayBackground)
    ) {
        // Map Workspace Header
        Surface(color = DropwayNavy, shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "นำทาง & จุดส่งวันนี้ (Map & Navigation)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "จุดส่งทั้งหมด ${destinations.size} จุด • มีพิกัด GPS ${stopsWithGps} จุด",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                // Filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null && !codOnly,
                        onClick = { selectedFilter = null; codOnly = false },
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
                        selected = codOnly,
                        onClick = { codOnly = !codOnly },
                        label = { Text("ค้าง COD", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == DeliveryStatus.DELIVERED,
                        onClick = {
                            selectedFilter = if (selectedFilter == DeliveryStatus.DELIVERED) null else DeliveryStatus.DELIVERED
                        },
                        label = { Text("ส่งแล้ว", fontSize = 11.sp) }
                    )
                }
            }
        }

        // Destinations List with one-tap Navigation
        if (destinations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(id = R.drawable.ic_map_pin), contentDescription = null, tint = DropwayPrimary.copy(alpha = 0.35f), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ไม่มีจุดส่งในเงื่อนไขที่เลือก", fontWeight = FontWeight.Bold, color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(destinations, key = { it.id }) { parcel ->
                    DeliveryDestinationCard(
                        parcel = parcel,
                        onCardClick = { onParcelClick(parcel) },
                        onNavigate = {
                            if (parcel.latitude != null && parcel.longitude != null) {
                                LocationUtils.openNavigation(
                                    context = context,
                                    latitude = parcel.latitude,
                                    longitude = parcel.longitude,
                                    label = "${parcel.recipientName} - ${parcel.destinationAddress}"
                                )
                            }
                        },
                        onMarkDelivered = {
                            scope.launch {
                                deliveryRepo.updateDeliveryStatus(parcel.id, DeliveryStatus.DELIVERED)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeliveryDestinationCard(
    parcel: Parcel,
    onCardClick: () -> Unit,
    onNavigate: () -> Unit,
    onMarkDelivered: () -> Unit
) {
    val hasGps = parcel.latitude != null && parcel.longitude != null

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
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
                            .background(if (hasGps) StatusDeliveredBg else Color(0xFFF1F5F9))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (hasGps) "มีพิกัด GPS" else "รอพิกัด",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (hasGps) StatusDelivered else TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parcel.trackingNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DropwayPrimary
                    )
                }
                DeliveryStatusBadge(status = parcel.deliveryStatus)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${parcel.recipientName} ${if (parcel.recipientPhone.isNotBlank()) "(${parcel.recipientPhone})" else ""}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )

            if (parcel.destinationAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = parcel.destinationAddress,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }

            if (parcel.codAmountSatang > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ยอด COD: ${MoneyUtils.formatBaht(parcel.codAmountSatang)} (${parcel.paymentStatus.label})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB45309)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasGps) {
                    Button(
                        onClick = onNavigate,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(painter = painterResource(id = R.drawable.ic_navigation), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("เริ่มนำทาง (GPS)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }
                }

                if (parcel.deliveryStatus != DeliveryStatus.DELIVERED) {
                    Button(
                        onClick = onMarkDelivered,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("ส่งสำเร็จ", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}