package com.towerchat.app.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallLogGroup
import com.towerchat.app.data.model.CallLogGrouper
import com.towerchat.app.data.model.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CallLogRepository reads call history strictly on demand from the Android system call log.
 * No Room, no SQLite, no SharedPreferences, no file caching.
 */
@Singleton
class CallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val projection = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.PHONE_ACCOUNT_ID
    )

    /**
     * Checks if READ_CALL_LOG permission is granted.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries a paged list of call log entries from CallLog.Calls, grouped consecutively by day.
     * @param limit Number of raw records to fetch per page (default: 100)
     * @param offset Number of raw records to skip (default: 0)
     */
    suspend fun getGroupedCallLogs(limit: Int = 100, offset: Int = 0): List<CallLogGroup> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext emptyList()
        }

        val rawEntries = mutableListOf<CallLogEntry>()
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                    putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                    putStringArray(
                        android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(CallLog.Calls.DATE)
                    )
                    putInt(
                        android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                }
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    queryArgs,
                    null
                )
            } else {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "$sortOrder LIMIT $limit OFFSET $offset"
                )
            }

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                val accountIdIdx = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                    val number = if (numberIdx != -1) c.getString(numberIdx) ?: "" else ""
                    val cachedName = if (nameIdx != -1) c.getString(nameIdx) else null
                    val typeInt = if (typeIdx != -1) c.getInt(typeIdx) else 0
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    val duration = if (durationIdx != -1) c.getLong(durationIdx) else 0L
                    val accountId = if (accountIdIdx != -1) c.getString(accountIdIdx) else null

                    if (number.isNotBlank()) {
                        rawEntries.add(
                            CallLogEntry(
                                id = id,
                                number = number,
                                cachedName = cachedName,
                                type = CallType.fromSystemType(typeInt),
                                timestamp = date,
                                durationSeconds = duration,
                                phoneAccountId = accountId
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        CallLogGrouper.groupCalls(rawEntries)
    }

    /**
     * Retrieves all call records for a specific phone number.
     */
    suspend fun getCallsForNumber(phoneNumber: String): List<CallLogEntry> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val entries = mutableListOf<CallLogEntry>()
        val selection = "${CallLog.Calls.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$cleanNumber%")

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC LIMIT 50"
            )?.use { c ->
                val idIdx = c.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                val accountIdIdx = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                    val number = if (numberIdx != -1) c.getString(numberIdx) ?: "" else ""
                    val cachedName = if (nameIdx != -1) c.getString(nameIdx) else null
                    val typeInt = if (typeIdx != -1) c.getInt(typeIdx) else 0
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                    val duration = if (durationIdx != -1) c.getLong(durationIdx) else 0L
                    val accountId = if (accountIdIdx != -1) c.getString(accountIdIdx) else null

                    entries.add(
                        CallLogEntry(
                            id = id,
                            number = number,
                            cachedName = cachedName,
                            type = CallType.fromSystemType(typeInt),
                            timestamp = date,
                            durationSeconds = duration,
                            phoneAccountId = accountId
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore failure
        }
        entries
    }
}
