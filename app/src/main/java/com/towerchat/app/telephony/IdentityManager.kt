package com.towerchat.app.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.towerchat.app.data.SecureConfigStorage
import com.towerchat.app.data.model.Contact
import com.towerchat.app.data.session.ChatSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: ChatSessionManager,
    private val secureConfigStorage: SecureConfigStorage
) {

    init {
        val saved = secureConfigStorage.getOwnPhoneNumber()
        if (!saved.isNullOrBlank()) {
            sessionManager.setUserPhoneNumber(saved)
        }
    }

    /**
     * Attempts to automatically read user's phone number from SIM.
     * Returns normalized phone number if detected, null otherwise.
     */
    @SuppressLint("HardwareIds")
    fun detectSimPhoneNumber(): String? {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            var detectedNumber: String? = null

            // Try SubscriptionManager on API 33+ first for multi-SIM support
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val defaultSubId = SubscriptionManager.getDefaultSubscriptionId()
                if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    try {
                        val subNumber = subscriptionManager?.getPhoneNumber(defaultSubId)
                        if (!subNumber.isNullOrBlank()) {
                            detectedNumber = subNumber
                        }
                    } catch (_: SecurityException) {}
                }
            }

            // Fallback to TelephonyManager.getLine1Number()
            if (detectedNumber.isNullOrBlank()) {
                val line1 = telephonyManager?.line1Number
                if (!line1.isNullOrBlank()) {
                    detectedNumber = line1
                }
            }

            if (!detectedNumber.isNullOrBlank()) {
                val normalized = Contact.normalizePhoneNumber(detectedNumber)
                secureConfigStorage.saveOwnPhoneNumber(normalized)
                sessionManager.setUserPhoneNumber(normalized)
                return normalized
            }
        } catch (_: Exception) {
            // Permission denied or carrier SIM did not store MSISDN
        }
        return null
    }

    fun setManualPhoneNumber(number: String) {
        val normalized = Contact.normalizePhoneNumber(number)
        secureConfigStorage.saveOwnPhoneNumber(normalized)
        sessionManager.setUserPhoneNumber(normalized)
    }

    fun getStoredPhoneNumber(): String? {
        return sessionManager.userPhoneNumber.value
    }
}
