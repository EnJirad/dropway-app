package com.pocketdev.dropway

import com.google.zxing.BarcodeFormat
import com.pocketdev.dropway.core.scanner.*
import org.junit.Assert.*
import org.junit.Test

class LabelRecognitionTest {

    @Test
    fun testJtExpressTikTokShopLabelRecognition() {
        // Reference fixture: J&T Express / TikTok Shop label
        val barcode1D = DecodedBarcodeItem(
            rawText = "828123456789",
            format = BarcodeFormat.CODE_128,
            normalizedTracking = "828123456789",
            carrierName = "J&T Express"
        )
        val qr2D = DecodedBarcodeItem(
            rawText = "https://www.jtexpress.co.th/track?billCode=828123456789",
            format = BarcodeFormat.QR_CODE,
            normalizedTracking = "828123456789",
            carrierName = "J&T Express"
        )

        val ocrText = """
            J&T Express (TikTok Shop)
            Order ID: TT261124998TH
            คัดแยก: 2 BKK-01
            ผู้รับ: สมชาย ใจดี
            โทร: 0812345678
            ที่อยู่: 123/45 หมู่ 2 ต.บางกร่าง อ.เมือง จ.นนทบุรี 11000
            เก็บเงินปลายทาง COD: ฿350
        """.trimIndent()

        val ocrResult = LabelOcrEngine.OcrEngineResult(ocrText, emptyList(), 0.95f)
        val result = StructuredFieldExtractor.extract(listOf(barcode1D, qr2D), ocrResult)

        assertEquals("J&T Express", result.carrierName)
        assertEquals("828123456789", result.trackingNumber)
        assertEquals("1D_BARCODE", result.trackingSource)
        assertEquals("TT261124998TH", result.orderId)
        assertTrue(result.sortingCode.contains("BKK-01"))
        assertEquals("TikTok Shop", result.sellerPlatform)
        assertTrue(result.recipientName.contains("สมชาย ใจดี"))
        assertEquals("0812345678", result.recipientPhone)
        assertEquals("123/45", result.houseNumber)
        assertEquals("2", result.villageMoo)
        assertEquals("บางกร่าง", result.subdistrict)
        assertEquals("เมือง", result.district)
        assertTrue(result.province.contains("นนทบุรี"))
        assertEquals("11000", result.postalCode)
        assertTrue(result.isCod)
        assertEquals(35000L, result.codAmountSatang)
        assertEquals(350.0, result.codAmountBaht(), 0.01)
        assertTrue(result.confidenceScore >= 90)
    }

    @Test
    fun testFlashExpressLabelRecognition() {
        val barcode = DecodedBarcodeItem(
            rawText = "TH0123456789",
            format = BarcodeFormat.CODE_128,
            normalizedTracking = "TH0123456789",
            carrierName = "Flash Express"
        )
        val ocrText = """
            Flash Express
            BDC BKK01
            ผู้รับ: น.ส.วิภา สมบูรณ์
            โทร: 0898765432
            55 หมู่ 4 ต.เสาธงหิน อ.บางใหญ่ จ.นนทบุรี 11140
        """.trimIndent()

        val ocrResult = LabelOcrEngine.OcrEngineResult(ocrText, emptyList(), 0.92f)
        val result = StructuredFieldExtractor.extract(listOf(barcode), ocrResult)

        assertEquals("Flash Express", result.carrierName)
        assertEquals("TH0123456789", result.trackingNumber)
        assertTrue(result.recipientName.contains("วิภา สมบูรณ์"))
        assertEquals("0898765432", result.recipientPhone)
        assertEquals("55", result.houseNumber)
        assertEquals("4", result.villageMoo)
        assertEquals("เสาธงหิน", result.subdistrict)
        assertEquals("บางใหญ่", result.district)
        assertEquals("11140", result.postalCode)
        assertFalse(result.isCod)
        assertEquals(0L, result.codAmountSatang)
    }

    @Test
    fun testSpxExpressShopeeLabelRecognition() {
        val barcode = DecodedBarcodeItem(
            rawText = "SPXTH04567890123",
            format = BarcodeFormat.CODE_128,
            normalizedTracking = "SPXTH04567890123",
            carrierName = "SPX Express"
        )
        val ocrText = """
            SPX Express (Shopee)
            Order SN: 240101SPX99TH
            SPX-CENTRAL
            ผู้รับ: กิตติพงษ์ ยิ้มแย้ม
            0612345678
            78/9 หมู่ 1 ต.บางรักน้อย อ.เมือง จ.นนทบุรี 11000
            COD ฿520.00
        """.trimIndent()

        val ocrResult = LabelOcrEngine.OcrEngineResult(ocrText, emptyList(), 0.94f)
        val result = StructuredFieldExtractor.extract(listOf(barcode), ocrResult)

        assertEquals("SPX Express", result.carrierName)
        assertEquals("SPXTH04567890123", result.trackingNumber)
        assertEquals("Shopee", result.sellerPlatform)
        assertTrue(result.recipientName.contains("กิตติพงษ์"))
        assertEquals("0612345678", result.recipientPhone)
        assertEquals("78/9", result.houseNumber)
        assertEquals("1", result.villageMoo)
        assertEquals(52000L, result.codAmountSatang)
    }

    @Test
    fun testThailandPostLabelRecognition() {
        val barcode = DecodedBarcodeItem(
            rawText = "ED987654321TH",
            format = BarcodeFormat.CODE_128,
            normalizedTracking = "ED987654321TH",
            carrierName = "ไปรษณีย์ไทย (Thailand Post)"
        )
        val ocrText = """
            ไปรษณีย์ไทย EMS
            ผู้รับ: สุรชัย รักดี
            0823456789
            12 หมู่ 3 ต.บางศรีเมือง อ.เมือง จ.นนทบุรี 11000
        """.trimIndent()

        val ocrResult = LabelOcrEngine.OcrEngineResult(ocrText, emptyList(), 0.90f)
        val result = StructuredFieldExtractor.extract(listOf(barcode), ocrResult)

        assertEquals("ไปรษณีย์ไทย (Thailand Post)", result.carrierName)
        assertEquals("ED987654321TH", result.trackingNumber)
        assertTrue(result.recipientName.contains("สุรชัย"))
        assertEquals("0823456789", result.recipientPhone)
        assertEquals("12", result.houseNumber)
        assertEquals("3", result.villageMoo)
    }

    @Test
    fun testConflictingBarcodeAndOcrTrackingFlagsConflict() {
        val barcode = DecodedBarcodeItem(
            rawText = "828123456789",
            format = BarcodeFormat.CODE_128,
            normalizedTracking = "828123456789",
            carrierName = "J&T Express"
        )
        // OCR misreads or has an alternate tracking printed
        val ocrText = """
            J&T Express
            Tracking No: 828999999999
            ผู้รับ: สมใจ มั่นคง
            โทร: 0891112233
            10 หมู่ 1 นนทบุรี 11000
        """.trimIndent()

        val ocrResult = LabelOcrEngine.OcrEngineResult(ocrText, emptyList(), 0.88f)
        val result = StructuredFieldExtractor.extract(listOf(barcode), ocrResult)

        // Prioritizes 1D barcode for primary, but flags conflict
        assertEquals("828123456789", result.trackingNumber)
        assertTrue("Conflict should be flagged", result.hasCriticalConflicts())
        val trackingConflict = result.conflicts.firstOrNull { it.fieldName == "trackingNumber" }
        assertNotNull(trackingConflict)
        assertEquals("828123456789", trackingConflict!!.valueA)
        assertEquals("828999999999", trackingConflict.valueB)
    }

    @Test
    fun testNoCodPrepaidLabelDoesNotInferCodFromPhoneOrOrder() {
        val barcode = DecodedBarcodeItem(
            rawText = "TH12345678",
            format = BarcodeFormat.CODE_128,
            normalizedTracking = "TH12345678",
            carrierName = "Flash Express"
        )
        val ocrText = """
            Flash Express
            Order ID: 9988776655
            ผู้รับ: มานี มีตา
            โทร: 0855554433
            100 หมู่ 5 นนทบุรี 11000
        """.trimIndent()

        val ocrResult = LabelOcrEngine.OcrEngineResult(ocrText, emptyList(), 0.90f)
        val result = StructuredFieldExtractor.extract(listOf(barcode), ocrResult)

        // Verifies COD is strictly 0 and NOT confused with phone, order ID, or house number
        assertFalse(result.isCod)
        assertEquals(0L, result.codAmountSatang)
    }

    @Test
    fun testMissingPhoneIsReportedInMissingFields() {
        val barcode = DecodedBarcodeItem(
            rawText = "828111222333",
            format = BarcodeFormat.CODE_128,
            normalizedTracking = "828111222333",
            carrierName = "J&T Express"
        )
        val ocrText = """
            J&T Express
            ผู้รับ: ชูใจ สดใส
            ที่อยู่: 45 หมู่ 1 ต.สวนใหญ่ อ.เมือง จ.นนทบุรี 11000
        """.trimIndent()

        val ocrResult = LabelOcrEngine.OcrEngineResult(ocrText, emptyList(), 0.85f)
        val result = StructuredFieldExtractor.extract(listOf(barcode), ocrResult)

        assertTrue(result.missingFields.contains("recipientPhone"))
        assertEquals("", result.recipientPhone)
    }

    @Test
    fun testMeasurePerFieldAccuracyAcrossTestDataset() {
        val testFixtures = listOf(
            LabelFixture(
                tracking = "828123456789",
                carrier = "J&T Express",
                name = "สมชาย ใจดี",
                phone = "0812345678",
                house = "123/45",
                cod = 35000L,
                ocr = "J&T Express ผู้รับ: สมชาย ใจดี โทร: 0812345678 123/45 หมู่ 2 นนทบุรี 11000 COD 350 บาท"
            ),
            LabelFixture(
                tracking = "TH0123456789",
                carrier = "Flash Express",
                name = "วิภา สมบูรณ์",
                phone = "0898765432",
                house = "55",
                cod = 0L,
                ocr = "Flash Express ผู้รับ: วิภา สมบูรณ์ โทร: 0898765432 55 หมู่ 4 นนทบุรี 11140"
            ),
            LabelFixture(
                tracking = "SPXTH04567890123",
                carrier = "SPX Express",
                name = "กิตติพงษ์ ยิ้มแย้ม",
                phone = "0612345678",
                house = "78/9",
                cod = 52000L,
                ocr = "SPX Express ผู้รับ: กิตติพงษ์ ยิ้มแย้ม 0612345678 78/9 หมู่ 1 นนทบุรี 11000 COD ฿520"
            ),
            LabelFixture(
                tracking = "ED987654321TH",
                carrier = "ไปรษณีย์ไทย (Thailand Post)",
                name = "สุรชัย รักดี",
                phone = "0823456789",
                house = "12",
                cod = 0L,
                ocr = "ไปรษณีย์ไทย ผู้รับ: สุรชัย รักดี 0823456789 12 หมู่ 3 นนทบุรี 11000"
            )
        )

        var trackingCorrect = 0
        var carrierCorrect = 0
        var nameCorrect = 0
        var phoneCorrect = 0
        var houseCorrect = 0
        var codCorrect = 0

        for (fixture in testFixtures) {
            val barcode = DecodedBarcodeItem(
                rawText = fixture.tracking,
                format = BarcodeFormat.CODE_128,
                normalizedTracking = fixture.tracking,
                carrierName = fixture.carrier
            )
            val ocr = LabelOcrEngine.OcrEngineResult(fixture.ocr, emptyList(), 0.95f)
            val res = StructuredFieldExtractor.extract(listOf(barcode), ocr)

            if (res.trackingNumber == fixture.tracking) trackingCorrect++
            if (res.carrierName == fixture.carrier) carrierCorrect++
            if (res.recipientName.contains(fixture.name)) nameCorrect++
            if (res.recipientPhone == fixture.phone) phoneCorrect++
            if (res.houseNumber == fixture.house) houseCorrect++
            if (res.codAmountSatang == fixture.cod) codCorrect++
        }

        val total = testFixtures.size
        assertEquals("Tracking accuracy must be 100%", total, trackingCorrect)
        assertEquals("Carrier accuracy must be 100%", total, carrierCorrect)
        assertEquals("Name accuracy must be 100%", total, nameCorrect)
        assertEquals("Phone accuracy must be 100%", total, phoneCorrect)
        assertEquals("House number accuracy must be 100%", total, houseCorrect)
        assertEquals("COD accuracy must be 100%", total, codCorrect)
    }

    private data class LabelFixture(
        val tracking: String,
        val carrier: String,
        val name: String,
        val phone: String,
        val house: String,
        val cod: Long,
        val ocr: String
    )
}
