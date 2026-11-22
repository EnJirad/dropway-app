package com.pocketdev.dropway.ui.screens.households

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.TextPrimary

@Composable
fun AddEditHouseholdDialog(
    household: Household? = null,
    onDismiss: () -> Unit,
    onSave: (Household) -> Unit
) {
    var houseNumber by remember { mutableStateOf(household?.houseNumber ?: "") }
    var village by remember { mutableStateOf(household?.village ?: "") }
    var villageName by remember { mutableStateOf(household?.villageName ?: "") }
    var subdistrict by remember { mutableStateOf(household?.subdistrict ?: "") }
    var district by remember { mutableStateOf(household?.district ?: "") }
    var province by remember { mutableStateOf(household?.province ?: "") }
    var postalCode by remember { mutableStateOf(household?.postalCode ?: "") }
    var addressDescription by remember { mutableStateOf(household?.addressDescription ?: "") }
    var notes by remember { mutableStateOf(household?.notes ?: "") }
    var latitudeStr by remember { mutableStateOf(household?.latitude?.toString() ?: "") }
    var longitudeStr by remember { mutableStateOf(household?.longitude?.toString() ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    text = if (household == null) "🏡 เพิ่มข้อมูลบ้าน" else "✏️ แก้ไขข้อมูลบ้าน",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // House number & Village row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = houseNumber,
                        onValueChange = { houseNumber = it; errorMessage = null },
                        label = { Text("บ้านเลขที่ *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("หมู่ (ม.)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = villageName,
                    onValueChange = { villageName = it },
                    label = { Text("ชื่อหมู่บ้าน / ชุมชน") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subdistrict,
                        onValueChange = { subdistrict = it },
                        label = { Text("ตำบล") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = district,
                        onValueChange = { district = it },
                        label = { Text("อำเภอ") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = province,
                        onValueChange = { province = it },
                        label = { Text("จังหวัด") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("รหัสไปรษณีย์") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = addressDescription,
                    onValueChange = { addressDescription = it },
                    label = { Text("จุดสังเกต / คำอธิบายที่อยู่") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น ประตูรั้วสีเขียว ตรงข้ามร้านขายของชำ") }
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("โน้ตสำคัญของบ้านนี้") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น หมาดุ, ฝากข้างบ้านได้") }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("พิกัด GPS (หากมี)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = latitudeStr,
                        onValueChange = { latitudeStr = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = longitudeStr,
                        onValueChange = { longitudeStr = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("ยกเลิก")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (houseNumber.trim().isBlank()) {
                                errorMessage = "กรุณากรอกบ้านเลขที่"
                                return@Button
                            }
                            val lat = latitudeStr.trim().toDoubleOrNull()
                            val lng = longitudeStr.trim().toDoubleOrNull()

                            val result = (household ?: Household(houseNumber = "")).copy(
                                houseNumber = houseNumber.trim(),
                                village = village.trim(),
                                villageName = villageName.trim(),
                                subdistrict = subdistrict.trim(),
                                district = district.trim(),
                                province = province.trim(),
                                postalCode = postalCode.trim(),
                                addressDescription = addressDescription.trim(),
                                notes = notes.trim(),
                                latitude = lat,
                                longitude = lng
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
