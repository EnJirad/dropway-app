package com.pocketdev.dropway

import com.pocketdev.dropway.core.scanner.BarcodeDecoder
import org.junit.Assert.*
import org.junit.Test

class BarcodeDecoderTest {

    @Test
    fun testFlashCarrierDetection() {
        assertEquals("Flash Express", BarcodeDecoder.detectCarrier("TH123456789012"))
        assertEquals("Flash Express", BarcodeDecoder.detectCarrier("FLA098765432"))
    }

    @Test
    fun testKerryCarrierDetection() {
        assertEquals("Kerry Express (KEX)", BarcodeDecoder.detectCarrier("KEX12345678"))
        assertEquals("Kerry Express (KEX)", BarcodeDecoder.detectCarrier("KER12345678"))
        assertEquals("Kerry Express (KEX)", BarcodeDecoder.detectCarrier("SHP12345678"))
    }

    @Test
    fun testSpxCarrierDetection() {
        assertEquals("SPX Express", BarcodeDecoder.detectCarrier("SPXTH123456789"))
    }

    @Test
    fun testThailandPostDetection() {
        assertEquals("ไปรษณีย์ไทย (Thailand Post)", BarcodeDecoder.detectCarrier("ED123456789TH"))
        assertEquals("ไปรษณีย์ไทย (Thailand Post)", BarcodeDecoder.detectCarrier("EF987654321TH"))
    }

    @Test
    fun testJtCarrierDetection() {
        assertEquals("J&T Express", BarcodeDecoder.detectCarrier("821234567890"))
        assertEquals("J&T Express", BarcodeDecoder.detectCarrier("JT12345678"))
    }
}
