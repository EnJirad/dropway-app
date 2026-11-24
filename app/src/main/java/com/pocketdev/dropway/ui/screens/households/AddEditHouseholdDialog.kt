package com.pocketdev.dropway.ui.screens.households

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketdev.dropway.R
import com.pocketdev.dropway.core.model.Household
import com.pocketdev.dropway.ui.components.DropwayOutlinedTextField
import com.pocketdev.dropway.ui.screens.navigation.LocationPickerScreen
import com.pocketdev.dropway.ui.theme.DropwayPrimary
import com.pocketdev.dropway.ui.theme.TextPrimary
import com.pocketdev.dropway.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AddEditHouseholdDialog(
    household: Household? = null,
    onDismiss: () -> Unit,
    onSave: (Household) -> Unit = {},
    onSaveWithResident: ((Household, String, String) -> Unit)? = null
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

    // Resident fields for quick simultaneous creation
    var residentName by remember { mutableStateOf("") }
    var residentPhone by remember { mutableStateOf("") }

    // Single coordinate string state (latitude,longitude)
    var coordinateText by remember {
        val initial = if (household?.latitude != null && household.longitude != null) {
            String.format(Locale.US, "%.6f, %.6f", household.latitude, household.longitude)
        } else ""
        mutableStateOf(initial)
    }

    var showLocationPicker by remember { mutableStateOf(false) }
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
                // Header with Vector Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = if (household == null) R.drawable.ic_home else R.drawable.ic_edit),
                        contentDescription = null,
                        tint = DropwayPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (household == null) "เพิ่มข้อมูลบ้าน" else "แก้ไขข้อมูลบ้าน",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // House number & Village row (High contrast bold text)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropwayOutlinedTextField(
                        value = houseNumber,
                        onValueChange = { houseNumber = it; errorMessage = null },
                        label = { Text("บ้านเลขที่ *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DropwayOutlinedTextField(
                        value = village,
                        onValueChange = { village = it },
                        label = { Text("หมู่ (ม.)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = villageName,
                    onValueChange = { villageName = it },
                    label = { Text("ชื่อหมู่บ้าน / ชุมชน") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropwayOutlinedTextField(
                        value = subdistrict,
                        onValueChange = { subdistrict = it },
                        label = { Text("ตำบล") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DropwayOutlinedTextField(
                        value = district,
                        onValueChange = { district = it },
                        label = { Text("อำเภอ") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DropwayOutlinedTextField(
                        value = province,
                        onValueChange = { province = it },
                        label = { Text("จังหวัด") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    DropwayOutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("รหัสไปรษณีย์") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = addressDescription,
                    onValueChange = { addressDescription = it },
                    label = { Text("จุดสังเกต / คำอธิบายที่อยู่") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น ประตูรั้วสีเขียว ตรงข้ามร้านขายของชำ") }
                )

                Spacer(modifier = Modifier.height(10.dp))
                DropwayOutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("โน้ตสำคัญของบ้านนี้") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น หมาดุ, ฝากข้างบ้านได้") }
                )

                // Resident Section (Especially for new household creation)
                if (household == null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "ข้อมูลผู้อยู่อาศัย / ผู้รับพัสดุ (ไม่บังคับ)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DropwayPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DropwayOutlinedTextField(
                        value = residentName,
                        onValueChange = { residentName = it },
                        label = { Text("ชื่อ-นามสกุล ผู้รับ / ลูกบ้าน") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("เช่น นายสมชาย ใจดี") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DropwayOutlinedTextField(
                        value = residentPhone,
                        onValueChange = { residentPhone = it },
                        label = { Text("เบอร์โทรศัพท์ติดต่อ") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("เช่น 0812345678") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Single Coordinate Field (ละติจูด,ลองจิจูด) with Trailing Map Icon Button
                Text("พิกัดแผนที่ GPS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                DropwayOutlinedTextField(
                    value = coordinateText,
                    onValueChange = { coordinateText = it },
                    label = { Text("พิกัด (ละติจูด,ลองจิจูด)") },
                    placeholder = { Text("เช่น 18.788300, 98.985300") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showLocationPicker = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_map),
                                contentDescription = "เลือกจากแผนที่",
                                tint = DropwayPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )
                Text(
                    text = "กดไอคอนแผนที่เพื่อเลือกพิกัดโดยเลื่อนแผนที่ใต้หมุด",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("ยกเลิก", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (houseNumber.trim().isBlank()) {
                                errorMessage = "กรุณากรอกบ้านเลขที่"
                                return@Button
                            }

                            var lat: Double? = null
                            var lng: Double? = null
                            if (coordinateText.isNotBlank()) {
                                val parts = coordinateText.split(",").map { it.trim() }
                                if (parts.size >= 2) {
                                    lat = parts[0].toDoubleOrNull()
                                    lng = parts[1].toDoubleOrNull()
                                    if (lat == null || lng == null) {
                                        errorMessage = "รูปแบบพิกัดไม่ถูกต้อง (ตัวอย่าง: 18.788300, 98.985300)"
                                        return@Button
                                    }
                                } else {
                                    errorMessage = "กรุณาระบุพิกัดในรูปแบบ ละติจูด,ลองจิจูด"
                                    return@Button
                                }
                            }

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
                            if (onSaveWithResident != null) {
                                onSaveWithResident(result, residentName.trim(), residentPhone.trim())
                            } else {
                                onSave(result)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary)
                    ) {
                        Text("บันทึก", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Interactive Map Picker Dialog
    if (showLocationPicker) {
        val latParsed = coordinateText.split(",").getOrNull(0)?.trim()?.toDoubleOrNull() ?: household?.latitude
        val lngParsed = coordinateText.split(",").getOrNull(1)?.trim()?.toDoubleOrNull() ?: household?.longitude
        LocationPickerScreen(
            initialLat = latParsed,
            initialLng = lngParsed,
            onDismiss = { showLocationPicker = false },
            onCoordinatesConfirmed = { newLat, newLng ->
                coordinateText = String.format(Locale.US, "%.6f, %.6f", newLat, newLng)
                showLocationPicker = false
            }
        )
    }
}
