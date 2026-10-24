package com.towerchat.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.towerchat.app.crypto.KeySessionRepository
import com.towerchat.app.data.SecureConfigStorage
import com.towerchat.app.data.model.Contact
import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.data.model.QuotaState
import com.towerchat.app.data.session.ChatSessionManager
import com.towerchat.app.telephony.CallManager
import com.towerchat.app.telephony.IdentityManager
import com.towerchat.app.telephony.SmsDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationSummary(
    val recipientNumber: String,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val lastTimestamp: Long = 0L
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionManager: ChatSessionManager,
    private val identityManager: IdentityManager,
    private val smsDispatcher: SmsDispatcher,
    private val callManager: CallManager,
    private val keySessionRepository: KeySessionRepository,
    private val secureConfigStorage: SecureConfigStorage
) : ViewModel() {

    val userPhoneNumber: StateFlow<String?> = sessionManager.userPhoneNumber
    val activeRecipient: StateFlow<String?> = sessionManager.activeChatRecipient
    val quotaState: StateFlow<QuotaState> = sessionManager.quotaState
    val flaggedKeyChanges: StateFlow<Set<String>> = keySessionRepository.flaggedKeyChanges

    private val _uiErrorEvents = MutableSharedFlow<String>()
    val uiErrorEvents: SharedFlow<String> = _uiErrorEvents.asSharedFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // App-to-App WhatsApp style mode (Port 8901) vs Standard SMS mode
    private val _isAppOnlyMode = MutableStateFlow(true)
    val isAppOnlyMode: StateFlow<Boolean> = _isAppOnlyMode.asStateFlow()

    private val _priorityContacts = MutableStateFlow<Set<String>>(secureConfigStorage.getPriorityContacts())
    val priorityContacts: StateFlow<Set<String>> = _priorityContacts.asStateFlow()

    // Typing status for currently active recipient
    val isRecipientTyping: StateFlow<Boolean> = combine(
        sessionManager.typingStatus,
        sessionManager.activeChatRecipient
    ) { typingMap, recipient ->
        if (recipient == null) false
        else typingMap[Contact.normalizePhoneNumber(recipient)] == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Active conversation messages
    val activeMessages: StateFlow<List<Message>> = combine(
        sessionManager.messages,
        sessionManager.activeChatRecipient
    ) { messagesMap, activeRecipient ->
        if (activeRecipient == null) emptyList()
        else messagesMap[Contact.normalizePhoneNumber(activeRecipient)] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All active conversations in RAM (retains contact number even when chats are deleted)
    val conversations: StateFlow<List<ConversationSummary>> = combine(
        sessionManager.messages,
        sessionManager.contactActivity
    ) { messagesMap, activityMap ->
        messagesMap.keys.map { recipient ->
            val messageList = messagesMap[recipient] ?: emptyList()
            val lastMsg = messageList.lastOrNull()
            val timestamp = lastMsg?.timestamp ?: (activityMap[recipient] ?: 0L)
            ConversationSummary(
                recipientNumber = recipient,
                lastMessage = lastMsg,
                unreadCount = messageList.count { !it.isOutgoing && it.status != MessageStatus.READ },
                lastTimestamp = timestamp
            )
        }.sortedByDescending { it.lastTimestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateInputText(newText: String) {
        _inputText.value = newText
        val recipient = activeRecipient.value
        if (recipient != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // Save draft (zero cellular SMS sent for typing)
                secureConfigStorage.saveDraft(recipient, newText)
            }
        }
    }

    fun initSimIdentity() {
        identityManager.detectSimPhoneNumber()
    }

    fun setManualPhoneNumber(number: String) {
        identityManager.setManualPhoneNumber(number)
    }

    fun openChat(recipientNumber: String) {
        sessionManager.ensureContactExists(recipientNumber)
        sessionManager.setActiveChat(recipientNumber)
        // Load draft for this recipient
        val draft = secureConfigStorage.getDraft(recipientNumber)
        _inputText.value = draft
    }

    fun toggleAppOnlyMode(recipient: String) {
        // App-to-App mode is permanently active (Plain SMS removed)
    }

    fun isBurnOnExit(recipient: String): Boolean = secureConfigStorage.isBurnOnExitForContact(recipient)

    fun toggleBurnOnExit(recipient: String) {
        val current = secureConfigStorage.isBurnOnExitForContact(recipient)
        secureConfigStorage.setBurnOnExitForContact(recipient, !current)
    }

    fun toggleSaveMessage(recipient: String, messageId: String) {
        sessionManager.toggleSaveMessage(recipient, messageId)
    }

    fun wipeChatNow(recipient: String) {
        sessionManager.purgeUnsavedMessagesForContact(recipient)
    }

    fun deleteMessage(recipient: String, messageId: String) {
        sessionManager.deleteMessage(recipient, messageId)
    }

    fun deleteConversation(recipient: String) {
        // Complete deletion: removes contact and all messages from RAM
        sessionManager.clearMessagesForContact(recipient, keepContact = false)
        keySessionRepository.clearKeysForContact(recipient)
        secureConfigStorage.clearDraft(recipient)
        if (activeRecipient.value?.let { Contact.normalizePhoneNumber(it) } == Contact.normalizePhoneNumber(recipient)) {
            sessionManager.setActiveChat(null)
        }
    }

    fun closeChatWithBurnOnExit(recipient: String) {
        // Back press behavior: delete all chat messages from RAM, but keep contact number in the list
        sessionManager.clearMessagesForContact(recipient, keepContact = true)
        _inputText.value = ""
        sessionManager.setActiveChat(null)
    }

    fun closeChat() {
        val recipient = activeRecipient.value
        if (recipient != null) {
            closeChatWithBurnOnExit(recipient)
        } else {
            sessionManager.setActiveChat(null)
        }
    }

    fun sendMessage(recipient: String) {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            secureConfigStorage.clearDraft(recipient)
            // Plain SMS removed: always send via App-to-App mode (Port 8901)
            val result = smsDispatcher.sendMessage(recipient, text, isAppOnly = true)
            result.onFailure { ex ->
                _uiErrorEvents.emit(ex.message ?: "Failed to dispatch SMS")
            }
        }
    }

    fun sendReaction(recipient: String, emoji: String, targetTimestamp: Long) {
        smsDispatcher.sendReaction(recipient, emoji, targetTimestamp)
    }

    fun getTemplates(): List<String> = secureConfigStorage.getTemplates()

    fun sendTemplate(recipient: String, templateText: String) {
        val result = smsDispatcher.sendMessage(recipient, templateText)
        result.onFailure { ex ->
            viewModelScope.launch {
                _uiErrorEvents.emit(ex.message ?: "Failed to dispatch template")
            }
        }
    }

    fun sendEmergencyBroadcast(): Int {
        val message = secureConfigStorage.getEmergencyMessage()
        val targets = secureConfigStorage.getEmergencyContacts().ifEmpty {
            secureConfigStorage.getPriorityContacts().toList()
        }
        var count = 0
        targets.forEach { target ->
            val res = smsDispatcher.sendMessage(target, message)
            if (res.isSuccess) count++
        }
        return count
    }

    fun getQuotaResetCountdown(): String = secureConfigStorage.getQuotaResetCountdown()

    fun isPriorityContact(number: String): Boolean = secureConfigStorage.isPriorityContact(number)

    fun togglePriorityContact(number: String) {
        secureConfigStorage.togglePriorityContact(number)
        _priorityContacts.value = secureConfigStorage.getPriorityContacts()
    }

    fun shouldWarnPriority(number: String): Boolean {
        return secureConfigStorage.isPriorityContact(number) && (sessionManager.quotaState.value.remaining < 15)
    }

    fun sendHandshake(recipient: String) {
        smsDispatcher.sendHandshake(recipient)
    }

    fun getSafetyNumber(recipient: String): String {
        return keySessionRepository.getSafetyNumber(recipient) ?: "Establishing..."
    }

    fun isKeyChanged(recipient: String): Boolean {
        return keySessionRepository.isKeyChanged(recipient)
    }

    fun dismissKeyChangeWarning(recipient: String) {
        keySessionRepository.dismissKeyChangeWarning(recipient)
    }

    fun hasSharedSecret(recipient: String): Boolean {
        return keySessionRepository.hasSharedSecret(recipient)
    }

    fun makeCall(recipient: String) {
        callManager.makeVoiceCall(recipient)
    }

    fun panicWipe() {
        keySessionRepository.wipeAllKeys()
        secureConfigStorage.wipeAllConfiguration()
        sessionManager.clearSession()
        _inputText.value = ""
    }

    fun clearSession() {
        sessionManager.clearSession()
        _inputText.value = ""
    }
}
