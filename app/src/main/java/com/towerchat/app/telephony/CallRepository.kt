package com.towerchat.app.telephony

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CallRepository maintains active in-memory Telecom Call states received from TowerInCallService.
 * RAM-only: zero disk, zero database, zero SharedPreferences.
 */
object CallRepository {

    private val _currentCalls = MutableStateFlow<List<Call>>(emptyList())
    val currentCalls: StateFlow<List<Call>> = _currentCalls.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    fun onCallAdded(call: Call) {
        val current = _currentCalls.value.toMutableList()
        if (!current.contains(call)) {
            current.add(call)
            _currentCalls.value = current
        }
    }

    fun onCallRemoved(call: Call) {
        val current = _currentCalls.value.toMutableList()
        current.remove(call)
        _currentCalls.value = current
    }

    fun onAudioStateChanged(audioState: CallAudioState) {
        _audioState.value = audioState
    }

    fun clear() {
        _currentCalls.value = emptyList()
        _audioState.value = null
    }
}
