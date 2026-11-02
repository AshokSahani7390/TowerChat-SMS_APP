package com.towerchat.app

import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallLogGroup
import com.towerchat.app.data.model.CallType
import com.towerchat.app.data.model.PhoneContact
import com.towerchat.app.data.model.SimAccountInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPermissionAndEmergencyTest {

    private val standardEmergencyNumbers = setOf("112", "911", "100", "101", "102", "108")

    private fun isEmergencyNumberSimulated(number: String): Boolean {
        val clean = number.replace(Regex("[^0-9+]"), "")
        return standardEmergencyNumbers.contains(clean)
    }

    @Test
    fun testEmergencyNumbersRecognition() {
        assertTrue(isEmergencyNumberSimulated("112")) // Standard India / Europe Emergency
        assertTrue(isEmergencyNumberSimulated("911")) // US Emergency
        assertTrue(isEmergencyNumberSimulated("100")) // Police India
        assertTrue(isEmergencyNumberSimulated("101")) // Fire India
        assertTrue(isEmergencyNumberSimulated("102")) // Ambulance India
        assertTrue(isEmergencyNumberSimulated("108")) // Emergency Disaster Management India

        assertFalse(isEmergencyNumberSimulated("+919876543210"))
        assertFalse(isEmergencyNumberSimulated("12345"))
        assertFalse(isEmergencyNumberSimulated("0000000000"))
    }

    @Test
    fun testCallTypeFromSystemInteger() {
        assertEquals(CallType.INCOMING, CallType.fromSystemType(1))
        assertEquals(CallType.OUTGOING, CallType.fromSystemType(2))
        assertEquals(CallType.MISSED, CallType.fromSystemType(3))
        assertEquals(CallType.VOICEMAIL, CallType.fromSystemType(4))
        assertEquals(CallType.REJECTED, CallType.fromSystemType(5))
        assertEquals(CallType.BLOCKED, CallType.fromSystemType(6))
        assertEquals(CallType.UNKNOWN, CallType.fromSystemType(99))
    }

    @Test
    fun testCallLogGroupDurationFormatting() {
        val groupSecondsOnly = CallLogGroup(
            id = 1L,
            number = "123",
            displayName = "Test",
            latestType = CallType.OUTGOING,
            latestTimestamp = 0L,
            callCount = 1,
            totalDurationSeconds = 45L,
            entries = emptyList(),
            phoneAccountId = null
        )
        assertEquals("45s", groupSecondsOnly.formattedDuration)

        val groupMinutesAndSeconds = CallLogGroup(
            id = 2L,
            number = "123",
            displayName = "Test",
            latestType = CallType.OUTGOING,
            latestTimestamp = 0L,
            callCount = 1,
            totalDurationSeconds = 125L, // 2 mins 5 secs
            entries = emptyList(),
            phoneAccountId = null
        )
        assertEquals("2m 5s", groupMinutesAndSeconds.formattedDuration)

        val groupZeroSeconds = CallLogGroup(
            id = 3L,
            number = "123",
            displayName = "Test",
            latestType = CallType.MISSED,
            latestTimestamp = 0L,
            callCount = 1,
            totalDurationSeconds = 0L,
            entries = emptyList(),
            phoneAccountId = null
        )
        assertEquals("0s", groupZeroSeconds.formattedDuration)
    }

    @Test
    fun testPhoneContactStarredFiltering() {
        val contacts = listOf(
            PhoneContact(id = "1", name = "Alice", number = "+911111111111", isStarred = true),
            PhoneContact(id = "2", name = "Bob", number = "+912222222222", isStarred = false),
            PhoneContact(id = "3", name = "Charlie", number = "+913333333333", isStarred = true)
        )

        val favorites = contacts.filter { it.isStarred }
        assertEquals(2, favorites.size)
        assertEquals("Alice", favorites[0].name)
        assertEquals("Charlie", favorites[1].name)
    }

    @Test
    fun testSimAccountInfoProperties() {
        val sim = SimAccountInfo(
            id = "sim_0",
            label = "Jio 4G",
            carrierName = "Jio",
            slotIndex = 0,
            number = "+919876543210"
        )
        assertEquals("sim_0", sim.id)
        assertEquals("Jio 4G", sim.label)
        assertEquals("Jio", sim.carrierName)
        assertEquals(0, sim.slotIndex)
        assertEquals("+919876543210", sim.number)
    }
}
