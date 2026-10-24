package com.towerchat.app

import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.data.session.ChatSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatSessionManagerTest {

    private lateinit var sessionManager: ChatSessionManager

    @Before
    fun setup() {
        sessionManager = ChatSessionManager()
        sessionManager.setUserPhoneNumber("+919999999999")
    }

    @Test
    fun testAddOutgoingMessageConsumesQuota() {
        val message = Message(
            senderNumber = "+919999999999",
            recipientNumber = "+918888888888",
            body = "Test message",
            isOutgoing = true,
            status = MessageStatus.SENDING,
            partsCount = 2
        )

        sessionManager.addMessage(message)

        val messages = sessionManager.getMessagesForContact("+918888888888")
        assertEquals(1, messages.size)
        assertEquals("Test message", messages[0].body)

        // Quota should have deducted 2 segments (100 - 2 = 98)
        assertEquals(98, sessionManager.quotaState.value.remaining)
        assertFalse(sessionManager.quotaState.value.isExhausted)
    }

    @Test
    fun testUpdateMessageStatus() {
        val message = Message(
            id = "msg-123",
            senderNumber = "+919999999999",
            recipientNumber = "+918888888888",
            body = "Hello",
            isOutgoing = true,
            status = MessageStatus.SENDING,
            partsCount = 1
        )

        sessionManager.addMessage(message)
        sessionManager.updateMessageStatus("msg-123", MessageStatus.SENT)

        val messages = sessionManager.getMessagesForContact("+918888888888")
        assertEquals(MessageStatus.SENT, messages[0].status)
    }

    @Test
    fun testClearSessionPurgesMemory() {
        val message = Message(
            senderNumber = "+919999999999",
            recipientNumber = "+918888888888",
            body = "Secret message",
            isOutgoing = true
        )
        sessionManager.addMessage(message)
        assertEquals(1, sessionManager.getMessagesForContact("+918888888888").size)

        sessionManager.clearSession()
        assertTrue(sessionManager.getMessagesForContact("+918888888888").isEmpty())
    }

    @Test
    fun testActiveChatInterception() {
        sessionManager.setActiveChat("+918888888888")
        assertTrue(sessionManager.isActiveChat("+918888888888"))
        assertFalse(sessionManager.isActiveChat("+917777777777"))
    }

    @Test
    fun testClearMessagesKeepsContactInMapWhenKeepContactIsTrue() {
        val message = Message(
            senderNumber = "+919999999999",
            recipientNumber = "+918888888888",
            body = "Ephemeral message",
            isOutgoing = true
        )
        sessionManager.addMessage(message)
        assertEquals(1, sessionManager.getMessagesForContact("+918888888888").size)

        // Simulate back button press (keepContact = true)
        sessionManager.clearMessagesForContact("+918888888888", keepContact = true)
        assertTrue(sessionManager.getMessagesForContact("+918888888888").isEmpty())
        assertTrue(sessionManager.messages.value.containsKey("+918888888888"))
    }

    @Test
    fun testClearMessagesRemovesContactWhenKeepContactIsFalse() {
        sessionManager.ensureContactExists("+918888888888")
        assertTrue(sessionManager.messages.value.containsKey("+918888888888"))

        // Simulate long-press delete contact (keepContact = false)
        sessionManager.clearMessagesForContact("+918888888888", keepContact = false)
        assertFalse(sessionManager.messages.value.containsKey("+918888888888"))
    }

    @Test
    fun testEnsureContactExistsAddsEmptyList() {
        sessionManager.ensureContactExists("+917777777777")
        assertTrue(sessionManager.messages.value.containsKey("+917777777777"))
        assertTrue(sessionManager.getMessagesForContact("+917777777777").isEmpty())
    }
}
