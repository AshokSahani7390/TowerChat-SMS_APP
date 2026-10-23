package com.towerchat.app

import com.towerchat.app.data.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactNormalizationTest {

    @Test
    fun testIndianTenDigitNormalization() {
        val normalized = Contact.normalizePhoneNumber("9876543210")
        assertEquals("+919876543210", normalized)
    }

    @Test
    fun testIndianZeroPrefixNormalization() {
        val normalized = Contact.normalizePhoneNumber("09876543210")
        assertEquals("+919876543210", normalized)
    }

    @Test
    fun testIndianCountryCodePrefixedNormalization() {
        val normalized1 = Contact.normalizePhoneNumber("+919876543210")
        assertEquals("+919876543210", normalized1)

        val normalized2 = Contact.normalizePhoneNumber("919876543210")
        assertEquals("+919876543210", normalized2)
    }

    @Test
    fun testSpecialCharactersStripped() {
        val normalized = Contact.normalizePhoneNumber("+91 (987) 654-3210")
        assertEquals("+919876543210", normalized)
    }
}
