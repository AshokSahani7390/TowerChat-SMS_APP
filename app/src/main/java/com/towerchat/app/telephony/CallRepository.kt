package com.towerchat.app.telephony

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.telephony.SmsManager
import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * CallRepository maintains in-memory Telecom Call states, Call.Callback lifecycles,
 * audio routing, proximity sensor wake locks, and RAM-only session call history.
 *
 * Strict RAM-only architecture: zero disk, zero database, zero SharedPreferences.
 * All calls are immediately purged from system CallLog.Calls via CallCleanupHelper,
 * and session call history in RAM is wiped on Back press.
 */
@Suppress("DEPRECATION")
object CallRepository {

    private val _currentCalls = MutableStateFlow<List<Call>>(emptyList())
    val currentCalls: StateFlow<List<Call>> = _currentCalls.asStateFlow()

    private val _activeCall = MutableStateFlow<Call?>(null)
    val activeCall: StateFlow<Call?> = _activeCall.asStateFlow()

    private val _ringingCall = MutableStateFlow<Call?>(null)
    val ringingCall: StateFlow<Call?> = _ringingCall.asStateFlow()

    private val _holdingCall = MutableStateFlow<Call?>(null)
    val holdingCall: StateFlow<Call?> = _holdingCall.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    // Volatile in-memory call events for the active session (Zero disk, wiped on Back press)
    private val _sessionCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val sessionCalls: StateFlow<List<CallLogEntry>> = _sessionCalls.asStateFlow()

    private var inCallServiceRef: WeakReference<InCallService>? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var appContext: Context? = null

    private val callCallbacks = mutableMapOf<Call, Call.Callback>()

    fun setInCallService(service: InCallService) {
        inCallServiceRef = WeakReference(service)
        appContext = service.applicationContext
    }

    fun onCallAdded(call: Call) {
        val current = _currentCalls.value.toMutableList()
        if (!current.contains(call)) {
            current.add(call)
            _currentCalls.value = current

            val callback = object : Call.Callback() {
                override fun onStateChanged(c: Call, state: Int) {
                    updateCallStates()
                    if (state == Call.STATE_DISCONNECTED) {
                        handleCallDisconnected(c)
                    }
                }

                override fun onDetailsChanged(c: Call, details: Call.Details) {
                    updateCallStates()
                }

                override fun onConferenceableCallsChanged(c: Call, conferenceableCalls: List<Call>) {
                    updateCallStates()
                }
            }

            callCallbacks[call] = callback
            call.registerCallback(callback)
            updateCallStates()

            // If incoming call, trigger notification
            if (call.state == Call.STATE_RINGING) {
                appContext?.let { ctx ->
                    val number = getPhoneNumber(call)
                    val name = getCallerDisplayName(call)
                    InCallNotificationManager.showIncomingCallNotification(ctx, number, name)
                }
            }
        }
    }

    fun onCallRemoved(call: Call) {
        val current = _currentCalls.value.toMutableList()
        current.remove(call)
        _currentCalls.value = current

        callCallbacks.remove(call)?.let { callback ->
            try {
                call.unregisterCallback(callback)
            } catch (_: Exception) {}
        }

        updateCallStates()

        // Clean up system call log immediately to ensure zero trace and no cloud backup
        appContext?.let { ctx ->
            val number = getPhoneNumber(call)
            CallCleanupHelper.deleteFromSystemCallLog(ctx, number)
        }

        if (current.isEmpty()) {
            releaseProximityWakeLock()
            appContext?.let { ctx ->
                InCallNotificationManager.cancelIncomingNotification(ctx)
            }
        }
    }

    private fun handleCallDisconnected(call: Call) {
        val number = getPhoneNumber(call)
        val name = getCallerDisplayName(call)
        val disconnectCause = call.details?.disconnectCause

        if (disconnectCause?.code == DisconnectCause.MISSED) {
            appContext?.let { ctx ->
                InCallNotificationManager.showMissedCallNotification(ctx, number, name)
            }
        }

        // Record in volatile session RAM log
        if (number.isNotBlank()) {
            val connectTime = call.details?.connectTimeMillis ?: 0L
            val duration = if (connectTime > 0L) (System.currentTimeMillis() - connectTime) / 1000L else 0L
            val type = when {
                disconnectCause?.code == DisconnectCause.MISSED -> CallType.MISSED
                disconnectCause?.code == DisconnectCause.REJECTED -> CallType.REJECTED
                call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true -> CallType.OUTGOING
                else -> if (connectTime > 0L) CallType.INCOMING else CallType.OUTGOING
            }

            val entry = CallLogEntry(
                id = System.currentTimeMillis(),
                number = number,
                cachedName = name,
                type = type,
                timestamp = System.currentTimeMillis(),
                durationSeconds = duration,
                phoneAccountId = null
            )
            _sessionCalls.value = listOf(entry) + _sessionCalls.value
        }

        // Clean up system call log immediately so nothing is ever saved on disk or backed up
        appContext?.let { ctx ->
            CallCleanupHelper.deleteFromSystemCallLog(ctx, number)
        }
    }

    fun clearSessionCalls() {
        _sessionCalls.value = emptyList()
    }

    private fun updateCallStates() {
        val calls = _currentCalls.value
        _ringingCall.value = calls.firstOrNull { it.state == Call.STATE_RINGING }
        _activeCall.value = calls.firstOrNull {
            it.state == Call.STATE_ACTIVE ||
                    it.state == Call.STATE_DIALING ||
                    it.state == Call.STATE_CONNECTING
        }
        _holdingCall.value = calls.firstOrNull { it.state == Call.STATE_HOLDING }

        // Update proximity lock based on active call and audio route
        updateProximitySensor()
    }

    fun onAudioStateChanged(audioState: CallAudioState) {
        _audioState.value = audioState
        updateProximitySensor()
    }

    private fun updateProximitySensor() {
        val hasActiveOrDialing = _activeCall.value != null
        val route = _audioState.value?.route ?: CallAudioState.ROUTE_EARPIECE

        if (hasActiveOrDialing && route == CallAudioState.ROUTE_EARPIECE) {
            acquireProximityWakeLock()
        } else {
            releaseProximityWakeLock()
        }
    }

    private fun acquireProximityWakeLock() {
        val context = appContext ?: return
        if (proximityWakeLock == null) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "towerchat:incall_proximity"
                ).apply {
                    setReferenceCounted(false)
                }
            }
        }
        try {
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire(3 * 60 * 60 * 1000L) // 3-hour max safeguard timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseProximityWakeLock() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    // Call Actions
    fun answerIncomingCall() {
        _ringingCall.value?.let { call ->
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
            appContext?.let { InCallNotificationManager.cancelIncomingNotification(it) }
        }
    }

    fun declineIncomingCall() {
        _ringingCall.value?.let { call ->
            call.reject(Call.REJECT_REASON_DECLINED)
            appContext?.let { InCallNotificationManager.cancelIncomingNotification(it) }
        }
    }

    /**
     * Rejects an incoming call with a quick SMS sent via plain SmsManager.
     * CRITICAL: Strictly uses Android platform SmsManager and NEVER touches the E2EE crypto path.
     */
    fun rejectWithSms(message: String) {
        val call = _ringingCall.value ?: return
        val targetNumber = getPhoneNumber(call)
        val context = appContext

        if (context != null && targetNumber.isNotBlank()) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(targetNumber, null, message, null, null)
            } catch (_: Exception) {}
        }

        call.reject(Call.REJECT_REASON_DECLINED)
        context?.let { InCallNotificationManager.cancelIncomingNotification(it) }
    }

    fun disconnectActiveCall() {
        val target = _activeCall.value ?: _holdingCall.value ?: _ringingCall.value
        target?.disconnect()
    }

    fun toggleHold() {
        val active = _activeCall.value
        val holding = _holdingCall.value

        if (active != null && active.state == Call.STATE_ACTIVE) {
            active.hold()
        } else if (holding != null) {
            holding.unhold()
        }
    }

    fun toggleMute() {
        val service = inCallServiceRef?.get() ?: return
        val currentMuted = _audioState.value?.isMuted ?: false
        service.setMuted(!currentMuted)
    }

    fun setAudioRoute(route: Int) {
        val service = inCallServiceRef?.get() ?: return
        service.setAudioRoute(route)
    }

    fun playDtmf(digit: Char) {
        _activeCall.value?.playDtmfTone(digit)
    }

    fun stopDtmf() {
        _activeCall.value?.stopDtmfTone()
    }

    fun mergeCalls() {
        val active = _activeCall.value ?: return
        val holding = _holdingCall.value ?: return
        active.conference(holding)
    }

    fun swapCalls() {
        val active = _activeCall.value
        val holding = _holdingCall.value
        if (active != null && holding != null) {
            active.hold()
            holding.unhold()
        }
    }

    // Call Waiting Handlers
    fun answerWaitingHoldActive() {
        _activeCall.value?.hold()
        answerIncomingCall()
    }

    fun answerWaitingEndActive() {
        _activeCall.value?.disconnect()
        answerIncomingCall()
    }

    fun declineWaiting() {
        declineIncomingCall()
    }

    fun getPhoneNumber(call: Call): String {
        val uri: Uri? = call.details?.handle
        return uri?.schemeSpecificPart?.replace(Regex("[^0-9+*#]"), "") ?: ""
    }

    fun getCallerDisplayName(call: Call): String? {
        val details = call.details ?: return null
        return details.callerDisplayName?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        releaseProximityWakeLock()
        for ((call, callback) in callCallbacks) {
            try {
                call.unregisterCallback(callback)
            } catch (_: Exception) {}
        }
        callCallbacks.clear()
        _currentCalls.value = emptyList()
        _activeCall.value = null
        _ringingCall.value = null
        _holdingCall.value = null
        _audioState.value = null
        inCallServiceRef = null
    }
}
