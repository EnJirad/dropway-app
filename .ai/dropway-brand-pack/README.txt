DROPWAY – BRAND & APP ICON PACK
================================
กล่องพัสดุมุมมนพร้อมหมุดนำทางตรงกลาง พื้นน้ำเงินเข้ม (ไม่มีสีเหลือง/ส้ม)
Rounded parcel box with a centered navigation pin, deep-blue app tile.

สี / COLORS
  Navy (พื้นหลังไอคอน)  #0A2A52
  Box top               #3F8EE0
  Box left              #2A74CC
  Box right / pin dot   #1A55A6
  Pin                   #FFFFFF
ฟอนต์ชื่อ: Poppins Bold (SIL OFL) – ในไฟล์ SVG แปลงเป็นเส้น path แล้ว ไม่ต้องติดตั้งฟอนต์

โครงสร้างโฟลเดอร์ / FOLDERS
  preview.png                      ภาพรวมทั้งชุด
  svg/icon/                        ไอคอนแอป: rounded (มุมมน), square (เต็มจัตุรัส ไม่มีมุมมน), circle
  svg/mark/                        เฉพาะสัญลักษณ์ พื้นโปร่งใส: สีเต็ม, mono-white, mono-navy
  svg/logo-with-name/              โลโก้ + ชื่อ Dropway: horizontal / stacked, on-light / on-dark
  svg/android/                     foreground, background, monochrome ของ Adaptive Icon
  png/icon-only/                   สัญลักษณ์อย่างเดียว (128–2048px) + app-tile/ (ไอคอนพื้นน้ำเงิน 16–1024px)
  png/logo-with-name/              โลโก้ + ชื่อ (กว้าง 512 / 1024 / 2048px, พื้นโปร่งใส)
  app/ios/AppIcon.appiconset/      ลากไปวางใน Xcode Assets.xcassets ได้เลย (iPhone, iPad, App Store 1024)
  app/android/res/                 mipmap-*, adaptive icon (xml), monochrome (Android 13), ไอคอน notification
  app/android/play-store/          ไอคอน Play Store 512px
  app/expo/                        icon.png, adaptive-icon.png, splash-icon.png, favicon.png
  web/                             favicon.ico/svg/png, apple-touch-icon, PWA icons (รวม maskable), site.webmanifest
  social/                          avatar 400/800 และ og-image 1200x630

วิธีใช้ / USAGE
  iOS      : วางโฟลเดอร์ AppIcon.appiconset ทับของเดิมใน Assets.xcassets (ไฟล์ไม่มี alpha ตามที่ App Store กำหนด)
  Android  : คัดลอกโฟลเดอร์ app/android/res/* ไปรวมกับ app/src/main/res/
             (ชื่อ ic_launcher, ic_launcher_round, ic_stat_dropway ใช้ใน AndroidManifest / notification)
  Expo/RN  : app.json -> "icon": "./assets/icon.png",
             "android": {"adaptiveIcon": {"foregroundImage": "./assets/adaptive-icon.png", "backgroundColor": "#0A2A52"}},
             "web": {"favicon": "./assets/favicon.png"}
  Web      : คัดลอกไฟล์ใน web/ ไปไว้ root ของเว็บ แล้วใส่ head-snippet.html

หมายเหตุ
  - ไอคอน iOS / Play Store / maskable เป็นจัตุรัสเต็ม ไม่ตัดมุมเอง ระบบจะตัดมุมให้
  - ตัวสัญลักษณ์อยู่ใน safe zone ของ Android Adaptive Icon แล้ว
  - ไอคอน notification (ic_stat_dropway) เป็นสีขาวโปร่งใส ตามข้อกำหนดของ Android
