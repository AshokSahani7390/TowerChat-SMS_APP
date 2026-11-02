package com.towerchat.app.telephony

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * TowerInCallService provides the Telecom InCallService implementation required for the default dialer role.
 * Bound by Android Telecom subsystem when active calls are placed or received while TowerChat is the default dialer.
 *
 * Manifest declarations:
 * - permission: android.permission.BIND_INCALL_SERVICE
 * - intent-filter: android.telecom.InCallService
 * - meta-data: android.telecom.IN_CALL_SERVICE_UI = true
 */
class TowerInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallRepository.setInCallService(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallRepository.setInCallService(this)
        CallRepository.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallRepository.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("onCallEndpointChanged"))
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState?.let { CallRepository.onAudioStateChanged(it) }
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
    }

    override fun onDestroy() {
        super.onDestroy()
        CallRepository.clear()
    }
}
