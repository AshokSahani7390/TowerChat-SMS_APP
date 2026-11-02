package com.towerchat.app

import com.towerchat.app.telephony.smartdial.T9SmartDialEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9SmartDialTest {

    @Test
    fun testCharToDigitMapping() {
        // ITU E.161 Standard Keypad Mapping
        assertEquals('2', T9SmartDialEngine.charToDigit('a'))
        assertEquals('2', T9SmartDialEngine.charToDigit('B'))
        assertEquals('2', T9SmartDialEngine.charToDigit('c'))

        assertEquals('3', T9SmartDialEngine.charToDigit('D'))
        assertEquals('3', T9SmartDialEngine.charToDigit('e'))
        assertEquals('3', T9SmartDialEngine.charToDigit('f'))

        assertEquals('4', T9SmartDialEngine.charToDigit('g'))
        assertEquals('4', T9SmartDialEngine.charToDigit('H'))
        assertEquals('4', T9SmartDialEngine.charToDigit('i'))

        assertEquals('5', T9SmartDialEngine.charToDigit('J'))
        assertEquals('5', T9SmartDialEngine.charToDigit('k'))
        assertEquals('5', T9SmartDialEngine.charToDigit('L'))

        assertEquals('6', T9SmartDialEngine.charToDigit('m'))
        assertEquals('6', T9SmartDialEngine.charToDigit('N'))
        assertEquals('6', T9SmartDialEngine.charToDigit('o'))

        assertEquals('7', T9SmartDialEngine.charToDigit('P'))
        assertEquals('7', T9SmartDialEngine.charToDigit('q'))
        assertEquals('7', T9SmartDialEngine.charToDigit('r'))
        assertEquals('7', T9SmartDialEngine.charToDigit('S'))

        assertEquals('8', T9SmartDialEngine.charToDigit('t'))
        assertEquals('8', T9SmartDialEngine.charToDigit('U'))
        assertEquals('8', T9SmartDialEngine.charToDigit('v'))

        assertEquals('9', T9SmartDialEngine.charToDigit('w'))
        assertEquals('9', T9SmartDialEngine.charToDigit('X'))
        assertEquals('9', T9SmartDialEngine.charToDigit('y'))
        assertEquals('9', T9SmartDialEngine.charToDigit('Z'))

        assertEquals(null, T9SmartDialEngine.charToDigit('@'))
        assertEquals(null, T9SmartDialEngine.charToDigit('-'))
    }

    @Test
    fun testNameToT9Conversion() {
        assertEquals("27465", T9SmartDialEngine.nameToT9("Ashok"))
        assertEquals("724264", T9SmartDialEngine.nameToT9("Sahani"))
        assertEquals("5646 363", T9SmartDialEngine.nameToT9("John Doe"))
    }

    @Test
    fun testNameToInitialsT9() {
        assertEquals("53", T9SmartDialEngine.nameToInitialsT9("John Doe"))
        assertEquals("27", T9SmartDialEngine.nameToInitialsT9("Ashok Sahani"))
        assertEquals("2", T9SmartDialEngine.nameToInitialsT9("Antigravity"))
    }

    @Test
    fun testSmartDialMatchingByNamePrefix() {
        // "Ashok" -> 27465
        assertTrue(T9SmartDialEngine.matches("Ashok", "+919876543210", "2"))
        assertTrue(T9SmartDialEngine.matches("Ashok", "+919876543210", "27"))
        assertTrue(T9SmartDialEngine.matches("Ashok", "+919876543210", "274"))
        assertTrue(T9SmartDialEngine.matches("Ashok", "+919876543210", "27465"))

        assertFalse(T9SmartDialEngine.matches("Ashok", "+919876543210", "999"))
    }

    @Test
    fun testSmartDialMatchingByWordInitialOrSecondWord() {
        // "John Doe" -> "John" = 5646, "Doe" = 363, Initials = 53
        assertTrue(T9SmartDialEngine.matches("John Doe", "+919876543210", "53")) // Initials
        assertTrue(T9SmartDialEngine.matches("John Doe", "+919876543210", "363")) // Second word "Doe"
        assertTrue(T9SmartDialEngine.matches("John Doe", "+919876543210", "5646")) // First word "John"
    }

    @Test
    fun testSmartDialMatchingByPhoneNumberSubstring() {
        assertTrue(T9SmartDialEngine.matches("Anonymous", "+919876543210", "5432"))
        assertTrue(T9SmartDialEngine.matches("User", "9876543210", "98765"))
        assertFalse(T9SmartDialEngine.matches("User", "9876543210", "1111"))
    }

    @Test
    fun testComputeMatchScoreRanking() {
        val scoreExactNumber = T9SmartDialEngine.computeMatchScore("Bob", "12345", "12345")
        val scoreNameStart = T9SmartDialEngine.computeMatchScore("Charlie", "98765", "242") // "CHA" = 242
        val scoreInitials = T9SmartDialEngine.computeMatchScore("David Miller", "98765", "36") // D=3, M=6 (David starts with 32)
        val scoreNumberSub = T9SmartDialEngine.computeMatchScore("David", "9876543210", "5432")

        assertTrue(scoreExactNumber > scoreNameStart)
        assertTrue(scoreNameStart > scoreInitials)
        assertTrue(scoreInitials > scoreNumberSub)
    }
}
