# Agent Directives & Workflows — Dropway Project

## 1. Git Push Protocol (คำสั่งบังคับ)
**ทุกครั้งที่ทำงานเสร็จสิ้นและบิลด์แอปพลิเคชันผ่านเรียบร้อยแล้ว ให้ทำการ Commit และ Push ไปที่ GitHub Repository เสมอ:**
- **Repository URL:** `https://github.com/EnJirad/dropway-app.git`
- **Main Branch:** `main`
- **Authentication:** ใช้ GitHub Personal Access Token (PAT) ในการ Push และรีเซ็ต Remote URL กลับเป็น URL ปกติหลังการ Push เพื่อความปลอดภัย
- คำสั่ง:
  ```bash
  git add .
  git commit -m "feat/fix: <รายละเอียดงาน>"
  git remote set-url origin https://<TOKEN>@github.com/EnJirad/dropway-app.git
  git push origin main
  git remote set-url origin https://github.com/EnJirad/dropway-app.git
  ```

## 2. Non-Negotiable UI & Engineering Rules
1. **ห้ามใช้อีโมจิในแอปพลิเคชันโดยเด็ดขาด (Strict: NO EMOJIS):**
   - ห้ามใช้ตัวอักษรอีโมจิ (เช่น 🏡, 📦, 📷, 🗺️, 📍, 📞, 🚗, 💵, ✏️, 🗑️ ฯลฯ) ใน UI
   - ให้ใช้ Vector Icons หรือ Material Icons (`androidx.compose.material.icons.Icons` หรือ Vector Drawables ใน `res/drawable`) เสมอ
2. **ระบบพิกัดแผนที่ (Location & Coordinates):**
   - ในฟอร์มสร้าง/แก้ไขข้อมูลบ้าน ให้ใช้ช่องกรอกพิกัดช่องเดียวในรูปแบบ `ละติจูด,ลองจิจูด`
   - มีไอคอนแผนที่ (Map Icon) อยู่ด้านหลังช่องกรอกพิกัด เมื่อกดแล้วจะเปิดหน้าต่างแผนที่
   - หน้าต่างแผนที่ต้องมีหมุดอยู่กึ่งกลางหน้าจอเสมอ (Fixed center pin) ให้ผู้ใช้เลื่อนแผนที่ด้านล่างเพื่อชี้หมุดลงบนบ้านเป้าหมาย แล้วกดยืนยันเพื่อนำพิกัดกลับมากรอกในฟอร์ม
3. **Offline-First:**
   - ข้อมูลและฟังก์ชันหลักทุกอย่างต้องใช้งานได้ 100% แม้ไม่มีสัญญาณอินเทอร์เน็ต
4. **ความปลอดภัยทางการเงิน:**
   - คำนวณเงินด้วย `BigDecimal` และหน่วยสตางค์ (Minor units) เท่านั้น ห้ามใช้ Float/Double
