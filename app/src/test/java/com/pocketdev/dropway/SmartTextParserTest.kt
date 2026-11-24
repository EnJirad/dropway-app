package com.pocketdev.dropway

import com.pocketdev.dropway.core.util.SmartTextParser
import org.junit.Assert.*
import org.junit.Test

class SmartTextParserTest {

    @Test
    fun testParseFreeTextFlashExpress() {
        val input = "Flash Express พัสดุ: TH1234598765 ผู้รับ: น.ส.วิภาดา วงศ์ใหญ่ โทร: 089-988-7766 ที่อยู่: 55/4 หมู่ 5 ต.หนองหอย อ.เมือง จ.เชียงใหม่ 50000 เก็บเงินปลายทาง: 280 บาท"
        val parsed = SmartTextParser.parseFreeText(input)

        assertEquals("TH1234598765", parsed.trackingNumber)
        assertEquals("Flash Express", parsed.carrier)
        assertTrue("Recipient should contain name", parsed.recipientName.contains("วิภาดา วงศ์ใหญ่"))
        assertEquals("0899887766", parsed.recipientPhone)
        assertEquals("55/4", parsed.houseNumber)
        assertEquals("5", parsed.village)
        assertEquals("หนองหอย", parsed.subdistrict)
        assertEquals("เมือง", parsed.district)
        assertTrue(parsed.province.contains("เชียงใหม่"))
        assertEquals("50000", parsed.postalCode)
        assertEquals(28000L, parsed.codAmountSatang)
        assertTrue(parsed.isValid)
    }

    @Test
    fun testParseFreeTextKerryExpress() {
        val input = "KEX88776655 กานดา รัตนพร 0898765432 45 หมู่ 4 ต.สุเทพ อ.เมือง จ.เชียงใหม่ COD 350.00"
        val parsed = SmartTextParser.parseFreeText(input)

        assertEquals("KEX88776655", parsed.trackingNumber)
        assertEquals("Kerry Express (KEX)", parsed.carrier)
        assertTrue("Name should match", parsed.recipientName.contains("กานดา"))
        assertEquals("0898765432", parsed.recipientPhone)
        assertEquals("45", parsed.houseNumber)
        assertEquals("4", parsed.village)
        assertEquals(35000L, parsed.codAmountSatang)
    }

    @Test
    fun testParseThailandPostEms() {
        val input = "ED123456789TH สมชาย เจริญผล 0812345678 12/3 ม.2 ต.ช้างเผือก อ.เมือง 50300 ยอดเก็บ 0 บาท"
        val parsed = SmartTextParser.parseFreeText(input)

        assertEquals("ED123456789TH", parsed.trackingNumber)
        assertEquals("ไปรษณีย์ไทย (Thailand Post)", parsed.carrier)
        assertEquals("0812345678", parsed.recipientPhone)
        assertEquals("12/3", parsed.houseNumber)
        assertEquals("2", parsed.village)
        assertEquals("50300", parsed.postalCode)
        assertEquals(0L, parsed.codAmountSatang)
    }

    @Test
    fun testParseDelimitedTabSeparated() {
        val line = "TH99887766\tสมเกียรติ สว่างศรี\t0861234567\t88/2 ม.3 ต.สารภี เชียงใหม่\t420"
        val parsed = SmartTextParser.parseDelimitedRow(line, "\t")

        assertNotNull(parsed)
        assertEquals("TH99887766", parsed!!.trackingNumber)
        assertEquals("สมเกียรติ สว่างศรี", parsed.recipientName)
        assertEquals("0861234567", parsed.recipientPhone)
        assertEquals("88/2", parsed.houseNumber)
        assertEquals("3", parsed.village)
        assertEquals(42000L, parsed.codAmountSatang)
    }
}
