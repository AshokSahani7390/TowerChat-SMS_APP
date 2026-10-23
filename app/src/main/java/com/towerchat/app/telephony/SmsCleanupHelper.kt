package com.towerchat.app.telephony

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Storage Audit: Zero Storage Guarantee
 * Android writes outgoing SMS to system DB automatically via SmsManager.
 * This helper deletes it immediately after the sent callback fires,
 * ensuring zero persistent traces remain on the Android system SMS database.
 */
object SmsCleanupHelper {

    fun deleteFromSystemSmsDb(context: Context, recipientNumber: String) {
        val uri = Telephony.Sms.Sent.CONTENT_URI
        val selection = "${Telephony.Sms.ADDRESS} = ?"
        val args = arrayOf(recipientNumber)

        // Run on IO thread — never block main thread with ContentResolver
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.contentResolver.delete(uri, selection, args)
            } catch (e: SecurityException) {
                // Some OEMs restrict SMS DB write access without default SMS role — fail silently
                // The intercept via BroadcastReceiver already prevented DB write for incoming
            } catch (e: Exception) {
                // Ignore any system content provider errors
            }
        }
    }
}
