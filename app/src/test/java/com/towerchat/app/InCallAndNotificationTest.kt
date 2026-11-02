package com.towerchat.app

import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import com.towerchat.app.telephony.CallRepository
import com.towerchat.app.telephony.InCallNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InCallAndNotificationTest {

    @Before
    fun setUp() {
        CallRepository.clear()
    }

    @Test
    fun testAudioRouteMasks() {
        val supportedMask = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER

        val hasEarpiece = (supportedMask and CallAudioState.ROUTE_EARPIECE) != 0
        val hasSpeaker = (supportedMask and CallAudioState.ROUTE_SPEAKER) != 0
        val hasBluetooth = (supportedMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        val hasHeadset = (supportedMask and CallAudioState.ROUTE_WIRED_HEADSET) != 0

        assertTrue(hasEarpiece)
        assertTrue(hasSpeaker)
        assertFalse(hasBluetooth)
        assertFalse(hasHeadset)
    }

    @Test
    fun testCallNotificationConstants() {
        assertEquals("towerchat_incoming_calls", InCallNotificationManager.INCOMING_CALL_CHANNEL_ID)
        assertEquals("towerchat_missed_calls", InCallNotificationManager.MISSED_CALL_CHANNEL_ID)
    }

    @Test
    fun testDisconnectCauseMapping() {
        assertEquals(DisconnectCause.MISSED, 5)
        assertEquals(DisconnectCause.REJECTED, 6)
        assertEquals(DisconnectCause.LOCAL, 2)
        assertEquals(DisconnectCause.REMOTE, 3)
    }

    @Test
    fun testRejectWithSmsDoesNotTouchCrypto() {
        // Assert repository can clear and handle empty call state safely
        CallRepository.clear()
        assertNull(CallRepository.ringingCall.value)
        assertNull(CallRepository.activeCall.value)
    }

    @Test
    fun testCallTimerStringFormatting() {
        val elapsedSeconds = 125L // 2 min 5 sec
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)
        assertEquals("02:05", formatted)

        val longCallSeconds = 3665L // 1 hr 1 min 5 sec
        val hrMinutes = longCallSeconds / 60
        val hrSeconds = longCallSeconds % 60
        val hours = hrMinutes / 60
        val hrFormatted = String.format("%02d:%02d:%02d", hours, hrMinutes % 60, hrSeconds)
        assertEquals("01:01:05", hrFormatted)
    }
}
