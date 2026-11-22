package com.pocketdev.dropway.ui.screens.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.LocationUtils
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.ui.components.DeliveryStatusBadge
import com.pocketdev.dropway.ui.components.DropwayMapView
import com.pocketdev.dropway.ui.components.MapMarkerItem
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DeliveryMapScreen(
    onParcelClick: (Parcel) -> Unit
) {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var parcels by remember { mutableStateOf<List<Parcel>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf<DeliveryStatus?>(DeliveryStatus.PENDING) }
    var codOnly by remember { mutableStateOf(false) }
    var selectedParcelId by remember { mutableStateOf<Long?>(null) }

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

    val mapMarkers = remember(destinations) {
        destinations.mapNotNull { p ->
            if (p.latitude != null && p.longitude != null) {
                val statusStr = if (p.deliveryStatus == DeliveryStatus.DELIVERED) {
                    "DELIVERED"
                } else if (p.codAmountSatang > 0) {
                    "COD"
                } else {
                    "PENDING"
                }
                MapMarkerItem(
                    id = p.id,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    title = p.recipientName,
                    status = statusStr,
                    subtitle = p.destinationAddress
                )
            } else null
        }
    }

    val firstGps = destinations.firstOrNull { it.latitude != null && it.longitude != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DropwayBackground)
    ) {
        // Map Workspace Header (High Contrast)
        Surface(color = DropwayNavy, shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "นำทาง & จุดส่งวันนี้ (Map & Navigation)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "จุดส่งทั้งหมด ${destinations.size} จุด • มีพิกัด GPS ${stopsWithGps} จุด",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
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
                        label = { Text("ทั้งหมด (${destinations.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = selectedFilter == DeliveryStatus.PENDING,
                        onClick = {
                            selectedFilter = if (selectedFilter == DeliveryStatus.PENDING) null else DeliveryStatus.PENDING
                        },
                        label = { Text("รอนำส่ง", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = codOnly,
                        onClick = { codOnly = !codOnly },
                        label = { Text("ค้าง COD", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = selectedFilter == DeliveryStatus.DELIVERED,
                        onClick = {
                            selectedFilter = if (selectedFilter == DeliveryStatus.DELIVERED) null else DeliveryStatus.DELIVERED
                        },
                        label = { Text("ส่งแล้ว", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // Interactive Map Section (Street & Satellite Toggle + Pins)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            DropwayMapView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                initialLat = firstGps?.latitude ?: 18.788300,
                initialLng = firstGps?.longitude ?: 98.985300,
                initialZoom = 14,
                isPickerMode = false,
                markers = mapMarkers,
                onMarkerClick = { clickedId ->
                    selectedParcelId = clickedId
                    val index = destinations.indexOfFirst { it.id == clickedId }
                    if (index >= 0) {
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                }
            )
        }

        // Destinations List with one-tap Navigation
        if (destinations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map_pin),
                        contentDescription = null,
                        tint = DropwayPrimary.copy(alpha = 0.35f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ไม่มีจุดส่งในเงื่อนไขที่เลือก",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(destinations, key = { it.id }) { parcel ->
                    val isSelected = parcel.id == selectedParcelId
                    DeliveryDestinationCard(
                        parcel = parcel,
                        isSelected = isSelected,
                        onCardClick = {
                            selectedParcelId = parcel.id
                            onParcelClick(parcel)
                        },
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
                        onOpenStreetView = {
                            if (parcel.latitude != null && parcel.longitude != null) {
                                LocationUtils.openStreetView(context, parcel.latitude, parcel.longitude)
                            }
                        },
                        onCall = {
                            if (parcel.recipientPhone.isNotBlank()) {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${parcel.recipientPhone}"))
                                context.startActivity(dialIntent)
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
    isSelected: Boolean = false,
    onCardClick: () -> Unit,
    onNavigate: () -> Unit,
    onOpenStreetView: () -> Unit,
    onCall: () -> Unit,
    onMarkDelivered: () -> Unit
) {
    val hasGps = parcel.latitude != null && parcel.longitude != null

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = if (isSelected) 6.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) DropwayPrimary else Color(0xFFE2E8F0)
        ),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_map_pin),
                                contentDescription = null,
                                tint = if (hasGps) StatusDelivered else TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (hasGps) "มีพิกัด GPS" else "รอพิกัด",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (hasGps) StatusDelivered else TextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = parcel.trackingNumber,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = DropwayPrimary
                    )
                }
                DeliveryStatusBadge(status = parcel.deliveryStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parcel.recipientName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                if (parcel.recipientPhone.isNotBlank()) {
                    Text(
                        text = PhoneUtils.format(parcel.recipientPhone),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DropwayPrimary,
                        modifier = Modifier.clickable { onCall() }
                    )
                }
            }

            if (parcel.destinationAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = parcel.destinationAddress,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    maxLines = 2
                )
            }

            if (parcel.codAmountSatang > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_money),
                                contentDescription = null,
                                tint = Color(0xFF78350F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ยอด COD: ${MoneyUtils.formatBaht(parcel.codAmountSatang)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                        }
                        Text(
                            text = parcel.paymentStatus.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F)
                        )
                    }
                }
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
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_navigation),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("นำทาง GPS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenStreetView,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_street_view),
                            contentDescription = "Street View",
                            tint = DropwayPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (parcel.recipientPhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCall,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_phone),
                            contentDescription = "โทร",
                            tint = DropwayPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (parcel.deliveryStatus != DeliveryStatus.DELIVERED) {
                    Button(
                        onClick = onMarkDelivered,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ส่งแล้ว", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
