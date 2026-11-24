package com.pocketdev.dropway

import com.pocketdev.dropway.core.database.HouseholdCsvImporter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class HouseholdCsvImporterTest {

    @Test
    fun testParseCsvSimple() {
        val csv = """
            id,full_name,phone,address,lat,lng,note,quantity,created_at,updated_at
            1,สมชาย ใจดี,0812345678,638 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก,16.66059,98.58588,ผาทอง ซ.หลังสุสาน,1,2026-11-24,2026-11-24
        """.trimIndent()

        val rows = HouseholdCsvImporter.parseCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(2, rows.size)
        assertEquals("สมชาย ใจดี", rows[1][1])
        assertEquals("0812345678", rows[1][2])
        assertEquals("638 ม.3 บ.แม่ตาวแพะ ต.แม่ตาว อ.แม่สอด จ.ตาก", rows[1][3])
        assertEquals("16.66059", rows[1][4])
    }

    @Test
    fun testParseCsvWithQuotesAndCommas() {
        val csv = """
            id,full_name,phone,address,lat,lng,note,quantity,created_at,updated_at
            2,สมศรี,"089-999-8888","1464 ม.3 บ.แม่ตาวแพะ, แม่สอด",16.67528,98.58064,"บ้านหลังที่ 2, ซ้ายมือ",1,2026-11-24,2026-11-24
        """.trimIndent()

        val rows = HouseholdCsvImporter.parseCsv(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(2, rows.size)
        assertEquals("1464 ม.3 บ.แม่ตาวแพะ, แม่สอด", rows[1][3])
        assertEquals("บ้านหลังที่ 2, ซ้ายมือ", rows[1][6])
    }
}
