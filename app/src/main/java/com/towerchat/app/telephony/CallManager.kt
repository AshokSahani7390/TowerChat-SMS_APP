package com.towerchat.app.telephony

import android.Manifest
import android.app.role.RoleManager
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
import com.towerchat.app.data.session.ChatSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: ChatSessionManager
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
     * Checks if the device currently has an active SIM card inserted.
     */
    fun hasSim(): Boolean {
        val tm = telephonyManager ?: return false
        return tm.simState != TelephonyManager.SIM_STATE_ABSENT &&
                tm.simState != TelephonyManager.SIM_STATE_UNKNOWN
    }

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
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves the configured voicemail number for the active subscription.
     */
    fun getVoicemailNumber(): String? {
        val tm = telephonyManager ?: return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            tm.voiceMailNumber
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Queries available SIM subscriptions on the device for dual-SIM management.
     */
    fun getSimAccounts(): List<SimAccountInfo> {
        val accounts = mutableListOf<SimAccountInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return accounts
        }

        try {
            val subManager = subscriptionManager ?: return accounts
            val subList = subManager.activeSubscriptionInfoList
            if (subList != null) {
                for (info in subList) {
                    val carrier = info.carrierName?.toString() ?: "SIM ${info.simSlotIndex + 1}"
                    val displayName = info.displayName?.toString() ?: carrier
                    val number = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            subManager.getPhoneNumber(info.subscriptionId)
                        } else {
                            @Suppress("DEPRECATION")
                            info.number
                        }
                    } catch (_: Exception) {
                        null
                    }

                    accounts.add(
                        SimAccountInfo(
                            id = info.subscriptionId.toString(),
                            label = displayName,
                            carrierName = carrier,
                            slotIndex = info.simSlotIndex,
                            number = number
                        )
                    )
                }
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
     * Checks if a contact exists in TowerChat (App-to-App mode).
     */
    fun isTowerChatContact(phoneNumber: String): Boolean {
        return sessionManager.isKnownContact(phoneNumber)
    }

    /**
     * Places a cellular voice call following the strict App-to-App and In-App UI guidelines:
     * 1. Emergency numbers (112, etc.) go straight to TelecomManager with zero delay.
     * 2. Non-emergency calls are RESTRICTED to TowerChat contacts (same app only).
     * 3. Calls STAY inside TowerChat - NEVER redirects to Google Phone app (no ACTION_DIAL).
     *    Requires TowerChat to be the default dialer.
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
                    false
                }
            } catch (_: Exception) {
                false
            }
        }

        // App-to-App restriction: Calling is only allowed for TowerChat contacts
        if (!isTowerChatContact(cleanNumber)) {
            return false
        }

        // Must be default dialer to stay 100% inside TowerChat (prevents redirecting to Google Phone app)
        if (!isDefaultDialer()) {
            return false
        }

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
                false
            }
        } else {
            return false
        }
    }

    /**
     * Checks if TowerChat is currently designated as the default dialer (phone app) of the device.
     */
    fun isDefaultDialer(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                telecomManager?.defaultDialerPackage == context.packageName
            }
        } catch (_: Exception) {
            telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    /**
     * Creates the Intent to request the default dialer role.
     * Uses RoleManager on API 29+ (Android 10+), and TelecomManager on API 26-28 (Android 8.0 - 9.0).
     */
    fun createDefaultDialerRequestIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else {
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Backward-compatible alias matching existing callers.
     */
    fun makeVoiceCall(phoneNumber: String) {
        placeCall(phoneNumber, null)
    }
}
