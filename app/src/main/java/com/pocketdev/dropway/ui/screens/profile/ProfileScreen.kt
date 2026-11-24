package com.pocketdev.dropway.ui.screens.profile

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.google.gson.GsonBuilder
import com.pocketdev.dropway.DropwayApplication
import com.pocketdev.dropway.R
import com.pocketdev.dropway.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProfileScreen(
    onOpenCodDashboard: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val householdRepo = DropwayApplication.instance.householdRepository
    val deliveryRepo = DropwayApplication.instance.deliveryRepository

    var exportMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DropwayBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // Profile Header
        Surface(color = DropwayNavy, shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dropway_logo_light),
                    contentDescription = "Dropway Logo",
                    modifier = Modifier.height(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "พี่คนขับเดลิเวอรี่ (Driver Workspace)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Offline-First Parcel Delivery Workspace",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Quick Access to COD Ledger
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ยอดเงิน & บัญชี COD", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "ตรวจสอบยอดเงินสดที่ได้รับ โอนเงินแล้ว และรายการที่ต้องติดตาม",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenCodDashboard,
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("เปิดแดชบอร์ด COD Ledger", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Master Household Database Card
            var householdCount by remember { mutableStateOf(0) }
            var isSyncingCsv by remember { mutableStateOf(false) }

            val csvUploadLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        isSyncingCsv = true
                        try {
                            val res = householdRepo.importFromUri(context, uri, clearExisting = false)
                            householdCount = householdRepo.getCount()
                            Toast.makeText(context, "นำเข้าสำเร็จ ${res.householdsCreated} หลังคาเรือน (${res.residentsCreated} รายชื่อ)", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSyncingCsv = false
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                householdCount = householdRepo.getCount()
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ฐานข้อมูลบ้านหลัก (Master Households)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Surface(
                            color = DropwayPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "$householdCount หลัง",
                                color = DropwayPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "รองรับการอัพโหลดไฟล์ CSV ของคุณเพื่อนำเข้าข้อมูลบ้าน พิกัด GPS และโน้ตการนำทางเข้าสู่ระบบทันที",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            csvUploadLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DropwayPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_document), contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("อัพโหลดไฟล์ CSV จากเครื่อง", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isSyncingCsv = true
                                val res = householdRepo.importFromCsv(context, clearExisting = true)
                                isSyncingCsv = false
                                householdCount = householdRepo.getCount()
                                Toast.makeText(context, "ซิงค์สำเร็จ ${res.householdsCreated} หลังคาเรือน (${res.residentsCreated} รายชื่อ)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncingCsv) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DropwayPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("กำลังนำเข้าข้อมูล...")
                        } else {
                            Text("ซิงค์ข้อมูล Master เริ่มต้น (4,500+ รายการ)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Data Safety & Export
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ความปลอดภัยข้อมูล & สำรองข้อมูล", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "ข้อมูลทั้งหมดเก็บอยู่บนเครื่องของคุณแบบออฟไลน์ 100% สามารถส่งออก (Export) เป็นไฟล์สำรองได้ตลอดเวลา",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val households = householdRepo.getAllHouseholds()
                                    val today = deliveryRepo.getOrCreateToday()
                                    val parcels = deliveryRepo.getParcelsForDay(today.id)

                                    val gson = GsonBuilder().setPrettyPrinting().create()
                                    val exportData = mapOf(
                                        "exportedAt" to System.currentTimeMillis(),
                                        "households" to households,
                                        "todayParcels" to parcels
                                    )
                                    val json = gson.toJson(exportData)
                                    val exportFile = File(context.filesDir, "dropway_backup_${System.currentTimeMillis()}.json")
                                    exportFile.writeText(json)
                                    exportMessage = "สำรองข้อมูลสำเร็จที่: ${exportFile.name}"
                                    Toast.makeText(context, "ส่งออกข้อมูลสำรองเรียบร้อย", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    exportMessage = "เกิดข้อผิดพลาด: ${e.message}"
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ส่งออกข้อมูลสำรอง (Export JSON Backup)")
                    }

                    if (exportMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = exportMessage!!,
                            color = StatusDelivered,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // About Application
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("เกี่ยวกับ Dropway", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dropway — จำทุกบ้าน จัดการทุกพัสดุ ชัดเจนทุกบาท แม้ไม่มีเน็ต\nเวอร์ชัน 1.0.0 (Production Build)\nออกแบบมาสำหรับการจัดส่งพัสดุ 200–300 ชิ้นต่อวัน",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
