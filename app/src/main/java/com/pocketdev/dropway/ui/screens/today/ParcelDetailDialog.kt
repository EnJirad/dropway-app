package com.pocketdev.dropway.ui.screens.today

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.core.model.DeliveryStatus
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.core.util.PhoneUtils
import com.pocketdev.dropway.core.util.TrackingUtils
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.TextPrimary

@Composable
fun ParcelDetailDialog(
    parcel: Parcel,
    onDismiss: () -> Unit,
    onUpdateParcel: (Parcel) -> Unit,
    onDeleteParcel: (Long) -> Unit
) {
    var trackingNumber by remember { mutableStateOf(parcel.trackingNumber) }
    var recipientName by remember { mutableStateOf(parcel.recipientName) }
    var recipientPhone by remember { mutableStateOf(parcel.recipientPhone) }
    var destinationAddress by remember { mutableStateOf(parcel.destinationAddress) }
    var codAmountStr by remember { mutableStateOf((parcel.codAmountSatang / 100.0).toString()) }
    var deliveryNotes by remember { mutableStateOf(parcel.deliveryNotes) }
    var selectedDeliveryStatus by remember { mutableStateOf(parcel.deliveryStatus) }
    var selectedPaymentStatus by remember { mutableStateOf(parcel.paymentStatus) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "📦 รายละเอียดพัสดุ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    label = { Text("เลขพัสดุ (Tracking Number) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it },
                        label = { Text("ชื่อผู้รับ *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = recipientPhone,
                        onValueChange = { recipientPhone = it },
                        label = { Text("เบอร์โทร") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = destinationAddress,
                    onValueChange = { destinationAddress = it },
                    label = { Text("ที่อยู่จัดส่ง") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = codAmountStr,
                    onValueChange = { codAmountStr = it },
                    label = { Text("ยอดเงิน COD (บาท)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = deliveryNotes,
                    onValueChange = { deliveryNotes = it },
                    label = { Text("หมายเหตุการจัดส่ง") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("สถานะการจัดส่ง", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DeliveryStatus.values().take(3).forEach { st ->
                        FilterChip(
                            selected = selectedDeliveryStatus == st,
                            onClick = { selectedDeliveryStatus = st },
                            label = { Text(st.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("สถานะการชำระเงิน COD", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(PaymentStatus.UNPAID, PaymentStatus.CASH_RECEIVED, PaymentStatus.TRANSFER_RECEIVED).forEach { pst ->
                        FilterChip(
                            selected = selectedPaymentStatus == pst,
                            onClick = { selectedPaymentStatus = pst },
                            label = { Text(pst.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("ลบพัสดุ")
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("ยกเลิก")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val codSatang = MoneyUtils.parseToSatang(codAmountStr)
                                val updated = parcel.copy(
                                    trackingNumber = TrackingUtils.normalize(trackingNumber),
                                    recipientName = recipientName.trim(),
                                    recipientPhone = PhoneUtils.normalize(recipientPhone),
                                    destinationAddress = destinationAddress.trim(),
                                    codAmountSatang = codSatang,
                                    deliveryNotes = deliveryNotes.trim(),
                                    deliveryStatus = selectedDeliveryStatus,
                                    paymentStatus = selectedPaymentStatus
                                )
                                onUpdateParcel(updated)
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("ยืนยันการลบพัสดุ") },
            text = { Text("คุณต้องการลบพัสดุเลขที่ ${parcel.trackingNumber} ใช่หรือไม่?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteParcel(parcel.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ลบ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}
