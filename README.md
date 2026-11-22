# Dropway (ดรอปเวย์) 📦🚀
### Offline-First Parcel Delivery Workspace for Professional Couriers
> **"จำทุกบ้าน จัดการทุกพัสดุ ชัดเจนทุกบาท แม้ไม่มีเน็ต"**
> (Remember every home. Manage every parcel. Track every baht. Work even without Internet.)

---

## 1. วิสัยทัศน์ผลิตภัณฑ์ (Product Vision)

Dropway เป็นแอปพลิเคชัน Android สำหรับผู้ปฏิบัติงานจัดส่งพัสดุรายวัน (200–300 ชิ้นต่อวัน) โดยเฉพาะในพื้นที่ต่างจังหวัดหรือพื้นที่ชนบทที่มีสัญญาณอินเทอร์เน็ตไม่เสถียรหรือไม่มีสัญญาณเลย แอปออกแบบให้ทำงานแบบ **Offline-First 100%** โดยไม่ต้องพึ่งพาคลาวด์ ไม่ต้องล็อกอิน ไม่สูญเสียข้อมูล และรองรับการทำงานด้วยมือเดียวอย่างรวดเร็วกลางแจ้ง

---

## 2. โครงสร้างและการนำทาง (App Structure)

Dropway ใช้แถบนำทางหลัก 4 หมวด (Bottom Navigation) พร้อมหน้างานวันนี้ (Today's Workspace) ที่เข้าถึงได้ทันทีจาก Header ด้านบน:

1. **🏡 คลังบ้าน (Household Directory)**
   - บันทึกประวัติบ้านเลขที่, หมู่, ตำบล, อำเภอ, จังหวัด, รหัสไปรษณีย์
   - ผู้อยู่อาศัยในบ้านแต่ละหลัง (ชื่อ, เบอร์โทร, โน้ต) รองรับหลายคนต่อหนึ่งบ้าน
   - บันทึกพิกัด GPS ประจำบ้าน พร้อมปุ่มโทรหาผู้อยู่อาศัย และปุ่มเปิดแอปนำทาง
   - ระบบค้นหาอัจฉริยะ (Local Search) รวดเร็วผ่าน SQLite: ค้นหาด้วยบ้านเลขที่, ชื่อคน, เบอร์โทรศัพท์, หรือจุดสังเกต
   - ตัวกรองแยกตามหมู่บ้าน/ชุมชน

2. **📦 งานวันนี้ (Today's Deliveries Workspace)**
   - เข้าถึงได้ด้วยปุ่มลัดด้านบนพร้อมแสดง Badge จำนวนพัสดุคงเหลือ
   - สรุปตัวชี้วัดประจำวัน (KPI Overview): จำนวนพัสดุทั้งหมด, จุดส่งทั้งหมด, ส่งสำเร็จ, รอนำส่ง, มีปัญหา, ยอด COD รวม, ยอดที่เก็บได้แล้ว, ยอดค้างเก็บ
   - โหมดสลับมุมมอง: "แยกรายชิ้น" หรือ "🏡 รวมตามบ้าน" (เมื่อบ้านเดียวมีหลายชิ้น)
   - แถบสถานะด่วน: ปุ่มกดครั้งเดียว "✓ ส่งแล้ว", "📞 โทร", "🚗 นำทาง", "💵 เก็บเงิน"

3. **📷 สแกนและนำเข้าพัสดุ (Scanner & Smart Import Workspace)**
   - **สแกนบาร์โค้ดต่อเนื่อง (Continuous Scan):** เก็บเลขพัสดุเข้าคิวสแกนต่อเนื่อง พร้อมระบบตรวจจับเลขพัสดุซ้ำ (Duplicate Detection)
   - **ป้อนเอง (Manual Entry):** กรอกข้อมูลรวดเร็ว พร้อมระบบค้นหาบ้านที่ตรงกันอัตโนมัติ (Household Auto-match) และปุ่ม "บันทึก & ป้อนต่อ"
   - **วางข้อความ (Smart Import / Bulk Paste):** คัดลอกข้อความแชท LINE, สเปรดชีต Excel, CSV/TSV วางแล้ววิเคราะห์แยกเลขพัสดุ, ชื่อ, เบอร์โทร, ที่อยู่, และยอด COD อัตโนมัติ พร้อมตารางตรวจสอบก่อนบันทึกแบบ Batch Transaction
   - **อ่านฉลาก OCR (Smart Label OCR):** แยกข้อความจากฉลากพัสดุบนเครื่อง (On-Device) พร้อมให้แก้ไขก่อนบันทึก
   - **Android Share Intent:** รองรับการแชร์ข้อความจากแอปอื่นเข้าสู่ Dropway โดยตรง

4. **🗺️ นำทางและพิกัด (Map & Navigation)**
   - แสดงรายการและพิกัดจุดส่งของวันนี้
   - ตัวกรองสถานะ: ทั้งหมด, รอดำเนินการ, ค้างจ่าย COD, ส่งสำเร็จ
   - ปุ่มเปิด Google Maps / แอปนำทางแบบ Turn-by-Turn ทันทีด้วยพิกัด GPS
   - เครื่องมือเลือกพิกัด (Location Picker) พร้อมหมุดเล็งกึ่งกลางหน้าจอ

5. **💰 บัญชีเงินเก็บปลายทาง (COD Tracker & Payment Ledger)**
   - บันทึกการรับเงินแบบ Append-Only Audit Ledger
   - คำนวณยอดเงินอย่างแม่นยำด้วยหน่วยย่อย (Satang) และ `BigDecimal` ป้องกันปัญหาเลขทศนิยมผิดพลาด
   - สรุปยอดเงินสดที่ได้รับ, ยอดโอนเงิน, ยอดค้างชำระ, และยอดยกมาจากวันก่อนหน้า (Carryover)
   - หน้าต่างบันทึกเงินด่วน: ระบุเงินสด, โอนเงิน/สลิป, ลูกค้าจะโอน, ชำระบางส่วน

6. **👤 โปรไฟล์และการจัดการข้อมูล (Profile & Data Safety)**
   - ระบบส่งออกข้อมูลสำรอง (Export JSON Backup) ลงในเครื่องเพื่อความปลอดภัยของข้อมูล
   - อัตลักษณ์และแบรนด์ Dropway เต็มรูปแบบจากแบรนด์แพ็กเกจ

---

## 3. สถาปัตยกรรมและเทคโนโลยี (Architecture & Technology Stack)

- **ภาษา:** Kotlin 1.9.22
- **UI Framework:** Jetpack Compose + Material 3 (Material Theme, Dark/Light Mode, Custom Palette)
- **สถาปัตยกรรม:** MVVM + Clean Architecture + Repository Pattern
- **ฐานข้อมูล:** Offline-First SQLite Native Database (`SQLiteOpenHelper`) พร้อมระบบแจ้งเตือนแบบ Reactive Flow (`DatabaseChangeNotifier` + Kotlin Coroutines Flow)
- **การเงิน:** `MoneyUtils` รองรับคำนวณเงินระดับสตางค์ (Minor units) และ `BigDecimal` 100%
- **Assets:** ผสานโลโก้และไอคอนเวกเตอร์จาก Dropway Brand Pack (`.ai/dropway-brand-pack`) ครบถ้วนทุกขนาด

---

## 4. โครงสร้างฐานข้อมูล (Database Schema)

| ตาราง | คำอธิบาย | ดัชนี (Indexes) |
|---|---|---|
| `households` | ข้อมูลบ้านเลขที่, หมู่, ตำบล, อำเภอ, จังหวัด, พิกัด GPS, โน้ต | `house_number`, `village` |
| `residents` | ผู้อยู่อาศัยในแต่ละบ้าน (Foreign Key -> `households.id`) | `household_id`, `phone_number`, `full_name` |
| `delivery_days` | รอบการทำงานในแต่ละวัน (วันที่, สถานะ ACTIVE/CLOSED) | `date` |
| `parcels` | ข้อมูลพัสดุ (Tracking, ผู้รับ, เบอร์โทร, COD, สถานะการส่ง, สถานะเงิน) | `tracking_number`, `delivery_day_id`, `household_id`, `delivery_status`, `payment_status` |
| `payment_records` | บันทึกประวัติการรับเงิน COD (Append-only Ledger) | `parcel_id` |
| `delivery_events` | ประวัติการเปลี่ยนแปลงและ Audit Trail | `parcel_id`, `timestamp` |
| `app_settings` | การตั้งค่าแอปพลิเคชัน | `key` |

---

## 5. คำแนะนำการบิลด์ (Build Instructions)

ในสภาพแวดล้อม PocketDev Android:

```bash
# บิลด์ Debug APK แบบ Fast Offline (เมื่อมี APK เดิมอยู่แล้ว):
timeout 5m gradle -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 --offline --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 assembleDebug --console=plain --stacktrace

# ผลลัพธ์ APK:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. ตารางความคืบหน้าของฟีเจอร์ (Feature Checklist)

- [x] **Phase A — Foundation & App Architecture**
  - [x] ตรวจสอบสภาพแวดล้อมและตั้งค่าบิลด์
  - [x] ธีม Dropway Brand Pack (สีน้ำเงิน Navy / Sky Blue / Status Colors)
  - [x] แถบนำทางด้านล่าง 4 หมวด (คลังบ้าน, สแกน, นำทาง, โปรไฟล์)
  - [x] แถบด้านบนแสดงสถานะและปุ่มลัด "งานวันนี้" พร้อมตัวเลขนับพัสดุ
- [x] **Phase B — Household Directory (คลังบ้าน)**
  - [x] ระบบบันทึก/แก้ไข/ลบข้อมูลบ้าน
  - [x] ระบบจัดการผู้อยู่อาศัยหลายคนต่อหนึ่งบ้าน
  - [x] ระบบค้นหาอัจฉริยะ (บ้านเลขที่, ชื่อ, เบอร์โทร, หมู่, โน้ต)
  - [x] ตัวกรองแยกตามหมู่บ้าน
  - [x] Household Matching สำหรับจับคู่บ้านกับพัสดุที่นำเข้า
- [x] **Phase C — GPS & Location Picker**
  - [x] จัดเก็บพิกัด Latitude / Longitude
  - [x] หน้าต่างเลือกและปรับแก้พิกัด (Location Picker)
  - [x] การเรียก Intent นำทางภายนอก (Google Maps Navigation)
- [x] **Phase D — Today's Deliveries (งานวันนี้)**
  - [x] รอบการจัดส่งประจำวัน (DeliveryDay)
  - [x] แดชบอร์ดสรุปยอด KPI (ทั้งหมด, ส่งแล้ว, รอนำส่ง, มีปัญหา, ยอด COD)
  - [x] สลับโหมดแสดงผล: แยกรายชิ้น หรือ รวมตามบ้าน (Household Grouping)
  - [x] ปุ่มลัดกดส่งสำเร็จ, โทรหาผู้รับ, นำทาง, เก็บเงิน
- [x] **Phase E — COD Tracker & Payment Ledger**
  - [x] ระบบ Ledger บันทึกการรับเงินแบบปลอดภัย
  - [x] คำนวณเงินระดับสตางค์และ BigDecimal
  - [x] แยกยอดเงินสด และ ยอดโอนเงิน
  - [x] บันทึกยอดยกมาจากวันก่อนหน้า (Carryover)
  - [x] รายการติดตามเงินที่ยังค้างชำระ
- [x] **Phase F — Barcode Scanner**
  - [x] ระบบสแกนและเก็บเข้าคิวต่อเนื่อง (Continuous Scan Queue)
  - [x] ระบบตรวจสอบเลขพัสดุซ้ำ (Duplicate Detection)
- [x] **Phase G — Smart Label OCR & Text Extraction**
  - [x] ตัวแยกข้อมูลฉลากพัสดุอัจฉริยะ (On-Device Parsing)
  - [x] แยกเลขพัสดุ, ชื่อ, เบอร์โทร, ที่อยู่, COD อัตโนมัติ
  - [x] หน้าต่างตรวจสอบและแก้ไขก่อนบันทึก
- [x] **Phase H — Smart Import (Bulk Paste & Share Intent)**
  - [x] วางข้อความหลายบรรทัด, TSV, CSV จากแชทหรือไฟล์
  - [x] ตารางพรีวิวและตรวจสอบความถูกต้องก่อนนำเข้า
  - [x] บันทึกเข้าฐานข้อมูลแบบปลอดภัยใน Transaction เดียว
  - [x] รองรับ Intent แชร์ข้อความจากภายนอก (`Intent.ACTION_SEND`)
- [x] **Phase I — Delivery Map**
  - [x] แสดงจุดหมายปลายทางของวันนี้
  - [x] กรองตามสถานะส่ง และสถานะ COD
  - [x] ลิงก์นำทาง GPS ทันที
- [x] **Phase J — Data Safety & Export**
  - [x] ส่งออกข้อมูลสำรอง (Export JSON Backup)
  - [x] ระบบ Audit Trail บันทึก Delivery Events
