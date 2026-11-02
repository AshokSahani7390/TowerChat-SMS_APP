package com.towerchat.app.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Storage Audit: Zero Storage & No Backup Guarantee
 * Android's telephony stack automatically writes calls to system CallLog.Calls.
 * This helper deletes the call record immediately after call termination,
 * ensuring zero persistent traces remain on the Android system call database,
 * preventing Google Drive or Android Cloud Backup from capturing call records.
 */
object CallCleanupHelper {

    fun deleteFromSystemCallLog(context: Context, phoneNumber: String?) {
        if (phoneNumber.isNullOrBlank()) return

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                    val selection = "${CallLog.Calls.NUMBER} LIKE ?"
                    val args = arrayOf("%$cleanNumber%")
                    context.contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, args)
                }
            } catch (_: SecurityException) {
                // Fail silently if permission is revoked in settings
            } catch (_: Exception) {
                // Ignore any system content provider issues
            }
        }
    }

    fun wipeAllTowerChatCallLogs(context: Context, knownNumbers: Set<String>) {
        if (knownNumbers.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                    for (number in knownNumbers) {
                        val clean = number.replace(Regex("[^0-9+]"), "")
                        if (clean.isNotEmpty()) {
                            val selection = "${CallLog.Calls.NUMBER} LIKE ?"
                            val args = arrayOf("%$clean%")
                            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, args)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
