package com.towerchat.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallLogGroup
import com.towerchat.app.data.model.PhoneContact
import com.towerchat.app.data.model.SimAccountInfo
import com.towerchat.app.telephony.CallLogRepository
import com.towerchat.app.telephony.CallManager
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
    private val callLogRepository: CallLogRepository,
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val _currentSubTab = MutableStateFlow(CallsSubTab.RECENTS)
    val currentSubTab: StateFlow<CallsSubTab> = _currentSubTab.asStateFlow()

    private val _requestedMainTab = MutableStateFlow<String?>(null)
    val requestedMainTab: StateFlow<String?> = _requestedMainTab.asStateFlow()

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

    private var recentsOffset = 0
    private var isLoadingRecents = false

    init {
        refreshState()
    }

    fun refreshState() {
        _hasReadCallLogPermission.value = callLogRepository.hasPermission()
        _hasReadContactsPermission.value = contactsRepository.hasPermission()
        _isAirplaneMode.value = callManager.isAirplaneMode()
        _hasSim.value = callManager.hasSim()
        _simAccounts.value = callManager.getSimAccounts()
        _isDefaultDialer.value = callManager.isDefaultDialer()

        loadRecents(reset = true)
        loadContacts()
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

    fun loadRecents(reset: Boolean = false) {
        if (!callLogRepository.hasPermission()) {
            _recents.value = emptyList()
            return
        }

        if (isLoadingRecents) return
        isLoadingRecents = true

        if (reset) {
            recentsOffset = 0
        }

        viewModelScope.launch {
            val paged = callLogRepository.getGroupedCallLogs(limit = 60, offset = recentsOffset)
            if (reset) {
                _recents.value = paged
            } else {
                _recents.value = _recents.value + paged
            }
            recentsOffset += paged.size
            isLoadingRecents = false
        }
    }

    fun loadMoreRecents() {
        loadRecents(reset = false)
    }

    fun loadContacts() {
        if (!contactsRepository.hasPermission()) {
            _contacts.value = emptyList()
            _favorites.value = emptyList()
            return
        }

        viewModelScope.launch {
            val list = contactsRepository.getContacts()
            _contacts.value = list
            _favorites.value = list.filter { it.isStarred }
        }
    }

    fun openCallDetails(group: CallLogGroup) {
        _selectedCallDetail.value = group
        viewModelScope.launch {
            _callDetailHistory.value = callLogRepository.getCallsForNumber(group.number)
        }
    }

    fun closeCallDetails() {
        _selectedCallDetail.value = null
        _callDetailHistory.value = emptyList()
    }

    fun initiateCall(phoneNumber: String) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) return

        // 1. Emergency zero-bypass
        if (callManager.isEmergencyNumber(cleanNumber)) {
            callManager.placeCall(cleanNumber, null)
            return
        }

        // 2. Dual-SIM check
        val accounts = callManager.getSimAccounts()
        if (accounts.size > 1) {
            _pendingSimCall.value = Pair(cleanNumber, accounts)
            viewModelScope.launch {
                _uiEvents.emit(CallsUiEvent.ShowSimSelector(cleanNumber, accounts))
            }
            return
        }

        // 3. Single SIM or standard placement
        val singleHandle = accounts.firstOrNull()?.let { callManager.getPhoneAccountHandle(it.id) }
        callManager.placeCall(cleanNumber, singleHandle)
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
        val vmNumber = callManager.getVoicemailNumber()
        if (!vmNumber.isNullOrBlank()) {
            initiateCall(vmNumber)
        } else {
            // Standard carrier fallback
            initiateCall("*86")
        }
    }

    fun openMessageChat(phoneNumber: String) {
        viewModelScope.launch {
            _uiEvents.emit(CallsUiEvent.OpenChat(phoneNumber))
        }
    }
}
