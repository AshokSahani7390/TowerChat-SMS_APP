package com.towerchat.app

import com.towerchat.app.telephony.QuotaCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaCalculatorTest {

    @Test
    fun testGsm7BitDetection() {
        assertTrue(QuotaCalculator.isGsm7Bit("Hello world, this is Towerchat!"))
        // Hindi unicode text
        assertFalse(QuotaCalculator.isGsm7Bit("नमस्ते"))
        // Emoji
        assertFalse(QuotaCalculator.isGsm7Bit("Hello 😊"))
    }

    @Test
    fun testSingleSegmentGsmMessage() {
        val shortMessage = "Quick SMS message"
        assertEquals(1, QuotaCalculator.calculateSmsSegments(shortMessage))

        val exact160Chars = "A".repeat(160)
        assertEquals(1, QuotaCalculator.calculateSmsSegments(exact160Chars))
    }

    @Test
    fun testMultipartGsmMessage() {
        val message161Chars = "A".repeat(161)
        // 161 characters should split into 2 parts (153 chars per part)
        assertEquals(2, QuotaCalculator.calculateSmsSegments(message161Chars))

        val message306Chars = "A".repeat(306)
        assertEquals(2, QuotaCalculator.calculateSmsSegments(message306Chars))

        val message307Chars = "A".repeat(307)
        assertEquals(3, QuotaCalculator.calculateSmsSegments(message307Chars))
    }

    @Test
    fun testUnicodeMessageSegments() {
        val shortUnicode = "नमस्ते दोस्त"
        assertEquals(1, QuotaCalculator.calculateSmsSegments(shortUnicode))

        val longUnicode = "नमस्ते ".repeat(15) // > 70 characters
        assertTrue(QuotaCalculator.calculateSmsSegments(longUnicode) > 1)
    }

    @Test
    fun testCharacterCountLabel() {
        val label = QuotaCalculator.getCharacterCountLabel("Hello")
        assertEquals("5/160 · 1 SMS", label)
    }
}
