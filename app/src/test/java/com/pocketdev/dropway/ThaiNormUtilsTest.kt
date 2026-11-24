package com.pocketdev.dropway

import com.pocketdev.dropway.core.scanner.ThaiNormUtils
import org.junit.Assert.*
import org.junit.Test

class ThaiNormUtilsTest {

    @Test
    fun testSaraAmComposition() {
        // Nikhahit (U+0E4D) + Tone Mai Tho (U+0E49) + Sara Aa (U+0E32) -> Tone + Sara Am
        val decomposed = "น\u0E4D\u0E49\u0E32"
        val canonical = ThaiNormUtils.canonical(decomposed)
        assertEquals("น้ำ", canonical)
    }

    @Test
    fun testDuplicateSaraEToSaraAe() {
        // Two Sara E -> Sara Ae
        val doubleE = "เเดด"
        val canonical = ThaiNormUtils.canonical(doubleE)
        assertEquals("แดด", canonical)
    }

    @Test
    fun testDropZeroWidthAndJoiners() {
        val withZeroWidth = "สม\u200Bชาย"
        val canonical = ThaiNormUtils.canonical(withZeroWidth)
        assertEquals("สมชาย", canonical)
    }

    @Test
    fun testLegacyPuaMapping() {
        // 0xF705 is legacy shifted Mai Ek -> 0x0E48 (Mai Ek)
        val legacy = "บ\uF705าน"
        val canonical = ThaiNormUtils.canonical(legacy)
        assertEquals("บ่าน", canonical)
    }
}
