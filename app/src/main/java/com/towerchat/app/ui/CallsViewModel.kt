package com.towerchat.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallLogGroup
import com.towerchat.app.data.model.CallLogGrouper
import com.towerchat.app.data.model.PhoneContact
import com.towerchat.app.data.model.SimAccountInfo
import com.towerchat.app.data.session.ChatSessionManager
import com.towerchat.app.telephony.BlockedNumberItem
import com.towerchat.app.telephony.BlockedNumberRepository
import com.towerchat.app.telephony.CallLogRepository
import com.towerchat.app.telephony.CallManager
import com.towerchat.app.telephony.CallRepository
import com.towerchat.app.telephony.ContactsRepository
import com.towerchat.app.telephony.smartdial.T9SmartDialEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CallsSubTab {
    FAVORITES,
    RECENTS,
    CONTACTS,
    KEYPAD
}

sealed class CallsUiEvent {
    data class ShowSimSelector(val phoneNumber: String, val simList: List<SimAccountInfo>) : CallsUiEvent()
    data class OpenChat(val phoneNumber: String) : CallsUiEvent()
    data class ShowToast(val message: String) : CallsUiEvent()
    object RequestDefaultDialerRole : CallsUiEvent()
}

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val callManager: CallManager,
    private val sessionManager: ChatSessionManager,
    private val callLogRepository: CallLogRepository,
    private val contactsRepository: ContactsRepository,
    private val blockedNumberRepository: BlockedNumberRepository
) : ViewModel() {

    private val _currentSubTab = MutableStateFlow(CallsSubTab.RECENTS)
    val currentSubTab: StateFlow<CallsSubTab> = _currentSubTab.asStateFlow()

    private val _requestedMainTab = MutableStateFlow<String?>(null)
    val requestedMainTab: StateFlow<String?> = _requestedMainTab.asStateFlow()

    private val _showBlockedNumbersScreen = MutableStateFlow(false)
    val showBlockedNumbersScreen: StateFlow<Boolean> = _showBlockedNumbersScreen.asStateFlow()

    private val _blockedNumbers = MutableStateFlow<List<BlockedNumberItem>>(emptyList())
    val blockedNumbers: StateFlow<List<BlockedNumberItem>> = _blockedNumbers.asStateFlow()

    private val _canBlockNumbers = MutableStateFlow(false)
    val canBlockNumbers: StateFlow<Boolean> = _canBlockNumbers.asStateFlow()

    private val _isCurrentDetailBlocked = MutableStateFlow(false)
    val isCurrentDetailBlocked: StateFlow<Boolean> = _isCurrentDetailBlocked.asStateFlow()

    private val _keypadInput = MutableStateFlow("")
    val keypadInput: StateFlow<String> = _keypadInput.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _recents = MutableStateFlow<List<CallLogGroup>>(emptyList())
    val recents: StateFlow<List<CallLogGroup>> = _recents.asStateFlow()

    private val _contacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val contacts: StateFlow<List<PhoneContact>> = _contacts.asStateFlow()

    private val _favorites = MutableStateFlow<List<PhoneContact>>(emptyList())
    val favorites: StateFlow<List<PhoneContact>> = _favorites.asStateFlow()

    private val _smartDialMatches = MutableStateFlow<List<PhoneContact>>(emptyList())
    val smartDialMatches: StateFlow<List<PhoneContact>> = _smartDialMatches.asStateFlow()

    private val _selectedCallDetail = MutableStateFlow<CallLogGroup?>(null)
    val selectedCallDetail: StateFlow<CallLogGroup?> = _selectedCallDetail.asStateFlow()

    private val _callDetailHistory = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callDetailHistory: StateFlow<List<CallLogEntry>> = _callDetailHistory.asStateFlow()

    private val _simAccounts = MutableStateFlow<List<SimAccountInfo>>(emptyList())
    val simAccounts: StateFlow<List<SimAccountInfo>> = _simAccounts.asStateFlow()

    private val _pendingSimCall = MutableStateFlow<Pair<String, List<SimAccountInfo>>?>(null)
    val pendingSimCall: StateFlow<Pair<String, List<SimAccountInfo>>?> = _pendingSimCall.asStateFlow()

    private val _hasReadCallLogPermission = MutableStateFlow(false)
    val hasReadCallLogPermission: StateFlow<Boolean> = _hasReadCallLogPermission.asStateFlow()

    private val _hasReadContactsPermission = MutableStateFlow(false)
    val hasReadContactsPermission: StateFlow<Boolean> = _hasReadContactsPermission.asStateFlow()

    private val _isAirplaneMode = MutableStateFlow(false)
    val isAirplaneMode: StateFlow<Boolean> = _isAirplaneMode.asStateFlow()

    private val _hasSim = MutableStateFlow(true)
    val hasSim: StateFlow<Boolean> = _hasSim.asStateFlow()

    private val _isDefaultDialer = MutableStateFlow(false)
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    private val _isDefaultDialerBannerDismissed = MutableStateFlow(false)
    val isDefaultDialerBannerDismissed: StateFlow<Boolean> = _isDefaultDialerBannerDismissed.asStateFlow()

    private val _uiEvents = MutableSharedFlow<CallsUiEvent>()
    val uiEvents: SharedFlow<CallsUiEvent> = _uiEvents.asSharedFlow()

    init {
        refreshState()
        // Collect in-memory ephemeral RAM call logs for active session (Zero disk, no backup)
        viewModelScope.launch {
            CallRepository.sessionCalls.collect { entries ->
                _recents.value = CallLogGrouper.groupCalls(entries)
            }
        }
    }

    fun refreshState() {
        _hasReadCallLogPermission.value = callLogRepository.hasPermission()
        _hasReadContactsPermission.value = contactsRepository.hasPermission()
        _isAirplaneMode.value = callManager.isAirplaneMode()
        _hasSim.value = callManager.hasSim()
        _simAccounts.value = callManager.getSimAccounts()
        _isDefaultDialer.value = callManager.isDefaultDialer()
        _canBlockNumbers.value = blockedNumberRepository.canBlockNumbers()

        loadRecents(reset = true)
        loadContacts()
        loadBlockedNumbers()
    }

    fun requestDefaultDialerRole() {
        viewModelScope.launch {
            _uiEvents.emit(CallsUiEvent.RequestDefaultDialerRole)
        }
    }

    fun dismissDefaultDialerBanner() {
        _isDefaultDialerBannerDismissed.value = true
    }

    fun setKeypadInput(digits: String) {
        _keypadInput.value = digits
        updateSmartDialMatches(digits)
        _currentSubTab.value = CallsSubTab.KEYPAD
        _requestedMainTab.value = "CALLS"
    }

    fun requestMainTab(tab: String) {
        _requestedMainTab.value = tab
    }

    fun clearRequestedMainTab() {
        _requestedMainTab.value = null
    }

    fun createDefaultDialerRequestIntent(): android.content.Intent? {
        return callManager.createDefaultDialerRequestIntent()
    }

    fun selectSubTab(tab: CallsSubTab) {
        _currentSubTab.value = tab
    }

    fun onKeypadDigit(digit: Char) {
        val updated = _keypadInput.value + digit
        _keypadInput.value = updated
        updateSmartDialMatches(updated)
    }

    fun onKeypadBackspace() {
        val current = _keypadInput.value
        if (current.isNotEmpty()) {
            val updated = current.dropLast(1)
            _keypadInput.value = updated
            updateSmartDialMatches(updated)
        }
    }

    fun onKeypadClear() {
        _keypadInput.value = ""
        _smartDialMatches.value = emptyList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun updateSmartDialMatches(digits: String) {
        if (digits.isBlank()) {
            _smartDialMatches.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val allContacts = _contacts.value
            val matched = allContacts.filter { contact ->
                T9SmartDialEngine.matches(contact.name, contact.number, digits)
            }.sortedByDescending { contact ->
                T9SmartDialEngine.computeMatchScore(contact.name, contact.number, digits)
            }
            _smartDialMatches.value = matched
        }
    }

    fun loadMoreRecents() {}

    fun loadRecents(reset: Boolean = false) {
        // Zero storage guarantee: Recents are strictly derived from active RAM session calls
        val sessionEntries = CallRepository.sessionCalls.value
        _recents.value = CallLogGrouper.groupCalls(sessionEntries)
    }

    fun loadContacts() {
        viewModelScope.launch {
            val deviceContacts = if (contactsRepository.hasPermission()) {
                contactsRepository.getContacts()
            } else {
                emptyList()
            }

            val tcNumbers = sessionManager.getAllContactNumbers()
            val list = mutableListOf<PhoneContact>()

            for (number in tcNumbers) {
                val cleanTc = number.replace(Regex("[^0-9+]"), "")
                val matching = deviceContacts.firstOrNull {
                    val cleanDev = it.number.replace(Regex("[^0-9+]"), "")
                    cleanDev.endsWith(cleanTc.takeLast(10)) || cleanTc.endsWith(cleanDev.takeLast(10))
                }
                list.add(
                    PhoneContact(
                        id = matching?.id ?: number,
                        name = matching?.name ?: number,
                        number = number,
                        photoUri = matching?.photoUri,
                        isStarred = matching?.isStarred ?: false,
                        lookupKey = matching?.lookupKey
                    )
                )
            }

            list.sortBy { it.name }
            _contacts.value = list
            _favorites.value = list.filter { it.isStarred }
        }
    }

    fun openCallDetails(group: CallLogGroup) {
        _selectedCallDetail.value = group
        viewModelScope.launch {
            val entries = CallRepository.sessionCalls.value.filter {
                it.number == group.number || it.number.contains(group.number)
            }
            _callDetailHistory.value = entries
            _isCurrentDetailBlocked.value = blockedNumberRepository.isBlocked(group.number)
        }
    }

    fun loadBlockedNumbers() {
        viewModelScope.launch {
            _canBlockNumbers.value = blockedNumberRepository.canBlockNumbers()
            _blockedNumbers.value = blockedNumberRepository.getBlockedNumbers()
        }
    }

    fun openBlockedNumbersScreen() {
        _showBlockedNumbersScreen.value = true
        loadBlockedNumbers()
    }

    fun closeBlockedNumbersScreen() {
        _showBlockedNumbersScreen.value = false
    }

    fun blockNumber(phoneNumber: String) {
        viewModelScope.launch {
            val success = blockedNumberRepository.blockNumber(phoneNumber)
            if (success) {
                loadBlockedNumbers()
                if (_selectedCallDetail.value?.number == phoneNumber) {
                    _isCurrentDetailBlocked.value = true
                }
            }
        }
    }

    fun unblockNumber(phoneNumber: String) {
        viewModelScope.launch {
            val success = blockedNumberRepository.unblockNumber(phoneNumber)
            if (success) {
                loadBlockedNumbers()
                if (_selectedCallDetail.value?.number == phoneNumber) {
                    _isCurrentDetailBlocked.value = false
                }
            }
        }
    }

    fun toggleBlockCurrentDetail() {
        val detail = _selectedCallDetail.value ?: return
        if (_isCurrentDetailBlocked.value) {
            unblockNumber(detail.number)
        } else {
            blockNumber(detail.number)
        }
    }

    fun closeCallDetails() {
        _selectedCallDetail.value = null
        _callDetailHistory.value = emptyList()
    }

    /**
     * Clears all session call logs from RAM on Back press.
     * Preserves contacts in the list.
     */
    fun clearCallHistory(keepContacts: Boolean = true) {
        CallRepository.clearSessionCalls()
        _recents.value = emptyList()
        _selectedCallDetail.value = null
        _callDetailHistory.value = emptyList()
    }

    /**
     * Long-press contact deletion: completely removes contact and its messages/calls from RAM.
     */
    fun deleteContact(phoneNumber: String) {
        sessionManager.clearMessagesForContact(phoneNumber, keepContact = false)
        refreshState()
    }

    fun initiateCall(phoneNumber: String) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) return

        // 1. Emergency zero-bypass
        if (callManager.isEmergencyNumber(cleanNumber)) {
            callManager.placeCall(cleanNumber, null)
            return
        }

        // 2. App-to-App restriction: Calling is only allowed for TowerChat contacts
        if (!sessionManager.isKnownContact(cleanNumber)) {
            viewModelScope.launch {
                _uiEvents.emit(CallsUiEvent.ShowToast("Calling is restricted to TowerChat contacts (same app only)"))
            }
            return
        }

        // 3. Must be default dialer so calling stays 100% inside TowerChat (no Google Phone redirect)
        if (!callManager.isDefaultDialer()) {
            viewModelScope.launch {
                _uiEvents.emit(CallsUiEvent.RequestDefaultDialerRole)
                _uiEvents.emit(CallsUiEvent.ShowToast("Set TowerChat as default phone app to stay inside TowerChat"))
            }
            return
        }

        // 4. Dual-SIM check
        val accounts = callManager.getSimAccounts()
        if (accounts.size > 1) {
            _pendingSimCall.value = Pair(cleanNumber, accounts)
            viewModelScope.launch {
                _uiEvents.emit(CallsUiEvent.ShowSimSelector(cleanNumber, accounts))
            }
            return
        }

        // 5. Single SIM or standard placement
        val singleHandle = accounts.firstOrNull()?.let { callManager.getPhoneAccountHandle(it.id) }
        val success = callManager.placeCall(cleanNumber, singleHandle)
        if (!success) {
            viewModelScope.launch {
                _uiEvents.emit(CallsUiEvent.ShowToast("Unable to place in-app call"))
            }
        }
    }

    fun dismissSimSelector() {
        _pendingSimCall.value = null
    }

    fun selectSimForCall(sim: SimAccountInfo) {
        val pending = _pendingSimCall.value
        _pendingSimCall.value = null
        if (pending != null) {
            placeCallWithSim(pending.first, sim.id)
        }
    }

    fun placeCallWithSim(phoneNumber: String, simId: String) {
        val handle = callManager.getPhoneAccountHandle(simId)
        callManager.placeCall(phoneNumber, handle)
    }

    fun dialVoicemail() {
        viewModelScope.launch {
            _uiEvents.emit(CallsUiEvent.ShowToast("Voicemail is unavailable in App-to-App mode"))
        }
    }

    fun openMessageChat(phoneNumber: String) {
        viewModelScope.launch {
            _uiEvents.emit(CallsUiEvent.OpenChat(phoneNumber))
        }
    }
}
