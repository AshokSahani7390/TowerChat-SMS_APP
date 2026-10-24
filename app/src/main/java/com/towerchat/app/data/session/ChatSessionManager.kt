package com.towerchat.app.data.session

import com.towerchat.app.data.model.Contact
import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.data.model.QuotaState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChatSessionManager manages all active conversations, message histories,
 * and cellular quota in RAM ONLY.
 * No SQLite, no Room, no SharedPreferences. When process exits, all state is lost.
 */
@Singleton
class ChatSessionManager @Inject constructor() {

    private val _userPhoneNumber = MutableStateFlow<String?>(null)
    val userPhoneNumber: StateFlow<String?> = _userPhoneNumber.asStateFlow()

    private val _activeChatRecipient = MutableStateFlow<String?>(null)
    val activeChatRecipient: StateFlow<String?> = _activeChatRecipient.asStateFlow()

    // Map of normalized phone number -> list of messages in RAM
    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    // Activity timestamp for contacts (to sort conversation list properly even when messages are cleared)
    private val _contactActivity = MutableStateFlow<Map<String, Long>>(emptyMap())
    val contactActivity: StateFlow<Map<String, Long>> = _contactActivity.asStateFlow()

    // Quota State (100 SMS/day estimate)
    private val _quotaState = MutableStateFlow(QuotaState())
    val quotaState: StateFlow<QuotaState> = _quotaState.asStateFlow()

    fun setUserPhoneNumber(number: String) {
        _userPhoneNumber.value = Contact.normalizePhoneNumber(number)
    }

    fun setActiveChat(recipientNumber: String?) {
        _activeChatRecipient.value = recipientNumber?.let { Contact.normalizePhoneNumber(it) }
    }

    fun isActiveChat(senderNumber: String): Boolean {
        val currentActive = _activeChatRecipient.value ?: return false
        val normalized = Contact.normalizePhoneNumber(senderNumber)
        return currentActive == normalized
    }

    fun ensureContactExists(phoneNumber: String) {
        val key = Contact.normalizePhoneNumber(phoneNumber)
        _messages.update { currentMap ->
            if (!currentMap.containsKey(key)) {
                currentMap + (key to emptyList())
            } else {
                currentMap
            }
        }
        _contactActivity.update { currentMap ->
            if (!currentMap.containsKey(key)) {
                currentMap + (key to System.currentTimeMillis())
            } else {
                currentMap
            }
        }
    }

    fun addMessage(message: Message) {
        val contactKey = if (message.isOutgoing) {
            Contact.normalizePhoneNumber(message.recipientNumber)
        } else {
            Contact.normalizePhoneNumber(message.senderNumber)
        }

        _messages.update { currentMap ->
            val list = currentMap[contactKey]?.toMutableList() ?: mutableListOf()
            list.add(message)
            currentMap + (contactKey to list)
        }

        _contactActivity.update { currentMap ->
            currentMap + (contactKey to message.timestamp)
        }

        if (message.isOutgoing) {
            consumeQuota(message.partsCount)
        }
    }

    fun updateMessageStatus(messageId: String, newStatus: MessageStatus, errorMessage: String? = null) {
        _messages.update { currentMap ->
            currentMap.mapValues { (_, messageList) ->
                messageList.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(status = newStatus, errorMessage = errorMessage ?: msg.errorMessage)
                    } else {
                        msg
                    }
                }
            }
        }
    }

    private fun consumeQuota(partsCount: Int) {
        _quotaState.update { current ->
            current.copy(usedSmsCount = current.usedSmsCount + partsCount)
        }
    }

    fun getMessagesForContact(phoneNumber: String): List<Message> {
        val key = Contact.normalizePhoneNumber(phoneNumber)
        return _messages.value[key] ?: emptyList()
    }

    // Contact -> is typing
    private val _typingStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingStatus: StateFlow<Map<String, Boolean>> = _typingStatus.asStateFlow()

    fun setTyping(contactNumber: String, isTyping: Boolean) {
        val key = Contact.normalizePhoneNumber(contactNumber)
        _typingStatus.update { it + (key to isTyping) }
    }

    fun markOutgoingMessagesAsRead(contactNumber: String, upToTimestamp: Long) {
        val key = Contact.normalizePhoneNumber(contactNumber)
        _messages.update { currentMap ->
            val list = currentMap[key] ?: return@update currentMap
            val updated = list.map { msg ->
                if (msg.isOutgoing && msg.timestamp <= upToTimestamp && msg.status != MessageStatus.READ) {
                    msg.copy(status = MessageStatus.READ)
                } else {
                    msg
                }
            }
            currentMap + (key to updated)
        }
    }

    fun addReaction(contactNumber: String, targetTimestamp: Long, emoji: String) {
        val key = Contact.normalizePhoneNumber(contactNumber)
        _messages.update { currentMap ->
            val list = currentMap[key] ?: return@update currentMap
            // Find closest message by timestamp (within 2 seconds tolerance)
            val updated = list.map { msg ->
                if (kotlin.math.abs(msg.timestamp - targetTimestamp) < 3000) {
                    msg.copy(reaction = emoji)
                } else {
                    msg
                }
            }
            currentMap + (key to updated)
        }
    }

    fun toggleSaveMessage(phoneNumber: String, messageId: String) {
        val key = Contact.normalizePhoneNumber(phoneNumber)
        _messages.update { currentMap ->
            val list = currentMap[key] ?: return@update currentMap
            val updated = list.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(isSaved = !msg.isSaved)
                } else {
                    msg
                }
            }
            currentMap + (key to updated)
        }
    }

    /**
     * Clears all chat messages for a contact.
     * When [keepContact] is true (default on Back press):
     * All messages in that conversation are erased, but the contact entry is preserved
     * in the conversations list with an empty message list.
     * When [keepContact] is false (Long press delete):
     * The contact and all its messages are completely removed from RAM.
     */
    fun clearMessagesForContact(phoneNumber: String, keepContact: Boolean = true) {
        val key = Contact.normalizePhoneNumber(phoneNumber)
        _messages.update { currentMap ->
            if (keepContact) {
                currentMap + (key to emptyList())
            } else {
                currentMap - key
            }
        }
        if (!keepContact) {
            _contactActivity.update { currentMap ->
                currentMap - key
            }
            _typingStatus.update { currentMap ->
                currentMap - key
            }
        }
    }

    fun purgeUnsavedMessagesForContact(phoneNumber: String) {
        clearMessagesForContact(phoneNumber, keepContact = true)
    }

    fun deleteMessage(phoneNumber: String, messageId: String) {
        val key = Contact.normalizePhoneNumber(phoneNumber)
        _messages.update { currentMap ->
            val list = currentMap[key] ?: return@update currentMap
            val remaining = list.filterNot { it.id == messageId }
            currentMap + (key to remaining)
        }
    }

    fun clearSession() {
        _messages.value = emptyMap()
        _contactActivity.value = emptyMap()
        _typingStatus.value = emptyMap()
        _activeChatRecipient.value = null
    }
}
