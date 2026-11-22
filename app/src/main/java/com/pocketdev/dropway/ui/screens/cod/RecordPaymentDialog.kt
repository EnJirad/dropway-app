package com.pocketdev.dropway.ui.screens.cod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pocketdev.dropway.core.model.Parcel
import com.pocketdev.dropway.core.model.PaymentMethod
import com.pocketdev.dropway.core.model.PaymentStatus
import com.pocketdev.dropway.core.util.MoneyUtils
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.StatusDelivered
import com.pocketdev.dropway.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun RecordPaymentDialog(
    parcel: Parcel,
    onDismiss: () -> Unit,
    onPaymentRecorded: () -> Unit
) {
    val paymentRepo = DropwayApplication.instance.paymentRepository
    val scope = rememberCoroutineScope()

    var amountStr by remember {
        mutableStateOf((parcel.codAmountSatang / 100.0).toString())
    }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var note by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "บันทึกการรับเงิน COD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "พัสดุ: ${parcel.trackingNumber} (${parcel.recipientName})",
                    fontSize = 13.sp,
                    color = DropwayPrimary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(14.dp))
                // Amount Field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; error = null },
                    label = { Text("จำนวนเงินที่ได้รับ (บาท) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("ช่องทางการชำระเงิน", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethod.values().forEach { method ->
                        FilterChip(
                            selected = selectedMethod == method,
                            onClick = { selectedMethod = method },
                            label = { Text(method.label, fontSize = 11.sp) }
                        )
                    }
                }

                if (selectedMethod == PaymentMethod.TRANSFER || selectedMethod == PaymentMethod.QR) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        label = { Text("เลขอ้างอิงสลิป / สลิปโอนเงิน (หากมี)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("บันทึกช่วยจำ (Note)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น ลูกค้าโอนเข้าบัญชีบริษัท") }
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Quick Action Chips: Customer will transfer / Full Cash / etc.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                paymentRepo.updatePaymentStatusOnly(parcel.id, PaymentStatus.AWAITING_TRANSFER)
                                onPaymentRecorded()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("ลูกค้าจะโอน", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val satang = MoneyUtils.parseToSatang(amountStr)
                            if (satang <= 0) {
                                error = "กรุณากรอกจำนวนเงินที่ถูกต้อง"
                                return@Button
                            }
                            scope.launch {
                                paymentRepo.recordPayment(
                                    parcelId = parcel.id,
                                    amountSatang = satang,
                                    method = selectedMethod,
                                    note = note,
                                    reference = reference.ifBlank { null }
                                )
                                onPaymentRecorded()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDelivered),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("รับเงินแล้ว", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
