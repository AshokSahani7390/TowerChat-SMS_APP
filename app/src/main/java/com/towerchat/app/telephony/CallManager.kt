package com.towerchat.app.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.towerchat.app.data.model.SimAccountInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val STANDARD_EMERGENCY_NUMBERS = setOf("112", "911", "100", "101", "102", "108")
    }

    private val telecomManager: TelecomManager?
        get() = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private val telephonyManager: TelephonyManager?
        get() = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val subscriptionManager: SubscriptionManager?
        get() = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    /**
     * Checks if the given number is a certified emergency number.
     * Android 10+ (API 29+) uses PhoneNumberUtils.isEmergencyNumber.
     * Older SDKs check PhoneNumberUtils.isPotentialEmergencyNumber and standard emergency codes.
     */
    fun isEmergencyNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) return false

        if (STANDARD_EMERGENCY_NUMBERS.contains(cleanNumber)) {
            return true
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager?.isEmergencyNumber(cleanNumber) == true
            } else {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.isEmergencyNumber(cleanNumber)
            }
        } catch (_: Exception) {
            STANDARD_EMERGENCY_NUMBERS.contains(cleanNumber)
        }
    }

    /**
     * Detects whether Airplane Mode is currently enabled.
     */
    fun isAirplaneMode(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether the device has an active, ready cellular SIM card.
     */
    fun hasSim(): Boolean {
        val tm = telephonyManager ?: return false
        val simState = tm.simState
        if (simState == TelephonyManager.SIM_STATE_READY) return true

        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val activeCount = subscriptionManager?.activeSubscriptionInfoCount ?: 0
                activeCount > 0
            } else {
                simState != TelephonyManager.SIM_STATE_ABSENT
            }
        } catch (_: Exception) {
            simState != TelephonyManager.SIM_STATE_ABSENT
        }
    }

    /**
     * Retrieves carrier voicemail number if available.
     */
    fun getVoicemailNumber(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            telephonyManager?.voiceMailNumber
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves all available call-capable SIM phone accounts for dual-SIM selection.
     */
    fun getSimAccounts(): List<SimAccountInfo> {
        val telecom = telecomManager ?: return emptyList()
        val accounts = mutableListOf<SimAccountInfo>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        try {
            val handles: List<PhoneAccountHandle> = telecom.getCallCapablePhoneAccounts() ?: emptyList()
            val subList = try {
                subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            for ((index, handle) in handles.withIndex()) {
                val phoneAccount = telecom.getPhoneAccount(handle)
                val label = phoneAccount?.label?.toString() ?: "SIM ${index + 1}"
                val matchingSub = subList.firstOrNull { it.subscriptionId.toString() == handle.id }

                val carrierName = matchingSub?.carrierName?.toString()
                    ?: matchingSub?.displayName?.toString()
                    ?: label

                val slotIndex = matchingSub?.simSlotIndex ?: index

                accounts.add(
                    SimAccountInfo(
                        id = handle.id,
                        label = label,
                        carrierName = carrierName,
                        slotIndex = slotIndex,
                        number = matchingSub?.number
                    )
                )
            }
        } catch (_: Exception) {
            // Failsafe in case of OEM security restriction
        }

        return accounts
    }

    /**
     * Resolves a PhoneAccountHandle from a slot index or account ID.
     */
    fun getPhoneAccountHandle(accountId: String?): PhoneAccountHandle? {
        if (accountId == null) return null
        val telecom = telecomManager ?: return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            telecom.getCallCapablePhoneAccounts()?.firstOrNull { it.id == accountId }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Places a cellular voice call following the mandatory architectural rules:
     * 1. Emergency numbers (112, etc.) go straight to TelecomManager with zero delay, no custom logic, and no confirmation dialog.
     * 2. If CALL_PHONE permission is granted, initiates direct call via TelecomManager.placeCall with specified SIM (if provided).
     * 3. If CALL_PHONE permission is denied, gracefully falls back to Intent.ACTION_DIAL (never crashes).
     */
    fun placeCall(phoneNumber: String, phoneAccountHandle: PhoneAccountHandle? = null): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) return false

        val uri = Uri.fromParts("tel", cleanNumber, null)

        // Emergency Call zero-bypass: send straight to telecom stack immediately
        if (isEmergencyNumber(cleanNumber)) {
            return try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    val extras = Bundle().apply {
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                    }
                    telecomManager?.placeCall(uri, extras)
                    true
                } else {
                    val emergencyIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(emergencyIntent)
                    true
                }
            } catch (_: Exception) {
                // Secondary fallback
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                true
            }
        }

        // Standard Call Placement
        val hasCallPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        if (hasCallPhone && telecomManager != null) {
            return try {
                val extras = Bundle().apply {
                    if (phoneAccountHandle != null) {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                    }
                }
                telecomManager?.placeCall(uri, extras)
                true
            } catch (_: Exception) {
                // Fallback to ACTION_DIAL if TelecomManager.placeCall encounters OEM rejection
                fallbackToDialer(uri)
            }
        } else {
            return fallbackToDialer(uri)
        }
    }

    private fun fallbackToDialer(uri: Uri): Boolean {
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Backward-compatible alias matching existing callers.
     */
    fun makeVoiceCall(phoneNumber: String) {
        placeCall(phoneNumber, null)
    }
}
