package com.towerchat.app.telephony

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Initiates a cellular voice call using Intent.ACTION_DIAL.
     * Opens the device dialer with the recipient number pre-populated.
     * Complies with Google Play policy by avoiding the high-risk CALL_PHONE permission.
     */
    fun makeVoiceCall(phoneNumber: String) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(dialIntent)
        } catch (_: Exception) {
            // Failsafe in case device has no dialer activity registered
        }
    }
}
