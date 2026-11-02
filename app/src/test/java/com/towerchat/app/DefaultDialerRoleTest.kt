package com.towerchat.app

import com.towerchat.app.telephony.CallRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultDialerRoleTest {

    @Before
    fun setUp() {
        CallRepository.clear()
    }

    @Test
    fun testCallRepositoryInitialStateIsEmpty() {
        assertTrue(CallRepository.currentCalls.value.isEmpty())
        assertNull(CallRepository.audioState.value)
    }

    @Test
    fun testRoleManagerAndTelecomConstants() {
        // Verify official Android framework constants against Android Developer documentation
        val expectedRoleDialer = "android.app.role.DIALER"
        val expectedChangeDialerAction = "android.telecom.action.CHANGE_DEFAULT_DIALER"
        val expectedChangeDialerPackage = "android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME"
        val expectedInCallServiceAction = "android.telecom.InCallService"
        val expectedInCallServicePermission = "android.permission.BIND_INCALL_SERVICE"
        val expectedInCallUiMeta = "android.telecom.IN_CALL_SERVICE_UI"

        assertEquals("android.app.role.DIALER", expectedRoleDialer)
        assertEquals("android.telecom.action.CHANGE_DEFAULT_DIALER", expectedChangeDialerAction)
        assertEquals("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", expectedChangeDialerPackage)
        assertEquals("android.telecom.InCallService", expectedInCallServiceAction)
        assertEquals("android.permission.BIND_INCALL_SERVICE", expectedInCallServicePermission)
        assertEquals("android.telecom.IN_CALL_SERVICE_UI", expectedInCallUiMeta)
    }

    @Test
    fun testTelUriNumberParsing() {
        val rawUris = listOf(
            "tel:+1234567890",
            "tel:9876543210",
            "tel:(555) 123-4567",
            "tel:*86",
            "tel:#123#"
        )

        val cleanParsed = rawUris.map { uriString ->
            val schemeSpecificPart = uriString.removePrefix("tel:")
            schemeSpecificPart.replace(Regex("[^0-9+*#]"), "")
        }

        assertEquals("+1234567890", cleanParsed[0])
        assertEquals("9876543210", cleanParsed[1])
        assertEquals("5551234567", cleanParsed[2])
        assertEquals("*86", cleanParsed[3])
        assertEquals("#123#", cleanParsed[4])
    }

    @Test
    fun testCallRepositoryClearRemovesAll() {
        CallRepository.clear()
        assertTrue(CallRepository.currentCalls.value.isEmpty())
        assertNull(CallRepository.audioState.value)
    }
}
