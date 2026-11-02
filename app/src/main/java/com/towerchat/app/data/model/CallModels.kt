package com.towerchat.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standard Call Types matching Android system CallLog.Calls types.
 */
enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    VOICEMAIL,
    UNKNOWN;

    companion object {
        fun fromSystemType(typeInt: Int): CallType {
            return when (typeInt) {
                1 -> INCOMING // CallLog.Calls.INCOMING_TYPE
                2 -> OUTGOING // CallLog.Calls.OUTGOING_TYPE
                3 -> MISSED // CallLog.Calls.MISSED_TYPE
                4 -> VOICEMAIL // CallLog.Calls.VOICEMAIL_TYPE
                5 -> REJECTED // CallLog.Calls.REJECTED_TYPE
                6 -> BLOCKED // CallLog.Calls.BLOCKED_TYPE
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Single raw call entry read from Android CallLog.Calls.
 */
data class CallLogEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: CallType,
    val timestamp: Long,
    val durationSeconds: Long,
    val phoneAccountId: String?
)

/**
 * Grouped call log item representing consecutive calls with the same contact on the same date.
 */
data class CallLogGroup(
    val id: Long,
    val number: String,
    val displayName: String,
    val latestType: CallType,
    val latestTimestamp: Long,
    val callCount: Int,
    val totalDurationSeconds: Long,
    val entries: List<CallLogEntry>,
    val phoneAccountId: String?
) {
    val formattedDuration: String
        get() {
            if (totalDurationSeconds <= 0) return "0s"
            val mins = totalDurationSeconds / 60
            val secs = totalDurationSeconds % 60
            return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        }
}

/**
 * Contact retrieved from ContactsContract.
 */
data class PhoneContact(
    val id: String,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val isStarred: Boolean = false,
    val lookupKey: String? = null
)

/**
 * SIM slot and carrier information for dual-SIM management.
 */
data class SimAccountInfo(
    val id: String,
    val label: String,
    val carrierName: String,
    val slotIndex: Int,
    val number: String? = null
)

/**
 * CallLogGrouper groups raw CallLog.Calls records consecutively by contact and calendar day.
 * Pure Kotlin logic for 100% JVM unit testability.
 */
object CallLogGrouper {

    private fun getDayKey(timestamp: Long): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))
    }

    /**
     * Groups consecutive calls with the same normalized number occurring on the same day.
     */
    fun groupCalls(entries: List<CallLogEntry>): List<CallLogGroup> {
        if (entries.isEmpty()) return emptyList()

        val result = mutableListOf<CallLogGroup>()
        var currentEntries = mutableListOf<CallLogEntry>()
        var currentNumberKey: String? = null
        var currentDayKey: String? = null

        fun flushGroup() {
            if (currentEntries.isNotEmpty()) {
                val first = currentEntries.first()
                val normalizedNum = first.number
                val displayName = first.cachedName?.takeIf { it.isNotBlank() } ?: normalizedNum
                val totalDuration = currentEntries.sumOf { it.durationSeconds }

                result.add(
                    CallLogGroup(
                        id = first.id,
                        number = normalizedNum,
                        displayName = displayName,
                        latestType = first.type,
                        latestTimestamp = first.timestamp,
                        callCount = currentEntries.size,
                        totalDurationSeconds = totalDuration,
                        entries = currentEntries.toList(),
                        phoneAccountId = first.phoneAccountId
                    )
                )
                currentEntries = mutableListOf()
            }
        }

        for (entry in entries) {
            val normalizedNum = Contact.normalizePhoneNumber(entry.number)
            val dayKey = getDayKey(entry.timestamp)

            if (currentNumberKey == normalizedNum && currentDayKey == dayKey) {
                currentEntries.add(entry)
            } else {
                flushGroup()
                currentNumberKey = normalizedNum
                currentDayKey = dayKey
                currentEntries.add(entry)
            }
        }
        flushGroup()

        return result
    }
}
