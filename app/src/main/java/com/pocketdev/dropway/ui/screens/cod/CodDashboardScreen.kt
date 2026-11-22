package com.pocketdev.dropway.ui.screens.cod

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.core.model.CodSummary
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.ui.components.PaymentStatusBadge
import com.pocketdev.dropway.ui.theme.*

@Composable
fun CodDashboardScreen() {
    val deliveryRepo = DropwayApplication.instance.deliveryRepository
    val paymentRepo = DropwayApplication.instance.paymentRepository

    var codSummary by remember { mutableStateOf(CodSummary()) }
    var outstandingParcels by remember { mutableStateOf<List<Parcel>>(emptyList()) }
    var selectedParcelForPayment by remember { mutableStateOf<Parcel?>(null) }

    LaunchedEffect(Unit) {
        val today = deliveryRepo.getOrCreateToday()
        paymentRepo.observeCodSummary(today.id).collect {
            codSummary = it
        }
    }

    LaunchedEffect(Unit) {
        outstandingParcels = deliveryRepo.getOutstandingParcels()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DropwayBackground)
    ) {
        // COD Header
        Surface(color = DropwayNavy, shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "💰 บัญชีเงินเก็บปลายทาง (COD Tracker)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "คำนวณด้วยระบบทศนิยมแม่นยำ ป้องกันเงินตกหล่น แม้ไม่มีสัญญาณเน็ต",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                // Big Summary Tile
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("ยอด COD รวมวันนี้", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = MoneyUtils.formatBaht(codSummary.totalCodSatang),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("ค้างเก็บทั้งหมด", color = Color(0xFFEF4444), fontSize = 11.sp)
                                Text(
                                    text = MoneyUtils.formatBaht(codSummary.outstandingSatang),
                                    color = Color(0xFFEF4444),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("💵 เงินสดที่ได้รับ: ${MoneyUtils.formatBaht(codSummary.cashCollectedSatang)}", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("📲 โอนเงินแล้ว: ${MoneyUtils.formatBaht(codSummary.transferCollectedSatang)}", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (codSummary.previousDayOutstandingSatang > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "⚠️ ยอดยกมาจากวันก่อนหน้า: ${MoneyUtils.formatBaht(codSummary.previousDayOutstandingSatang)}",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Outstanding List
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📋 รายการที่ต้องติดตามเงิน (${outstandingParcels.size} ชิ้น)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (outstandingParcels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ไม่มีพัสดุค้างชำระ COD", fontWeight = FontWeight.Bold, color = StatusDelivered)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(outstandingParcels, key = { it.id }) { parcel ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = parcel.trackingNumber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = DropwayPrimary
                                    )
                                    Text(
                                        text = "${parcel.recipientName} (${parcel.recipientPhone})",
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "ยอดค้างชำระ: ${MoneyUtils.formatBaht(parcel.codAmountSatang)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = Color(0xFFD97706)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    PaymentStatusBadge(status = parcel.paymentStatus)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = { selectedParcelForPayment = parcel },
                                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("รับเงิน", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedParcelForPayment != null) {
        RecordPaymentDialog(
            parcel = selectedParcelForPayment!!,
            onDismiss = { selectedParcelForPayment = null },
            onPaymentRecorded = {
                selectedParcelForPayment = null
            }
        )
    }
}
