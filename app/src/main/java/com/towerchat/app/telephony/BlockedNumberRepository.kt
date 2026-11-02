package com.towerchat.app.telephony

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class BlockedNumberItem(
    val id: Long,
    val originalNumber: String,
    val e164Number: String? = null
)

@Singleton
class BlockedNumberRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Verifies if the current user and TowerChat have permission to manage blocked numbers.
     * BlockedNumberContract requires the app to be the system default dialer or default SMS provider.
     */
    fun canBlockNumbers(): Boolean {
        return try {
            BlockedNumberContract.canCurrentUserBlockNumbers(context)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isBlocked(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+*#]"), "")
        if (cleanNumber.isBlank()) return@withContext false
        try {
            BlockedNumberContract.isBlocked(context, cleanNumber)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getBlockedNumbers(): List<BlockedNumberItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BlockedNumberItem>()
        try {
            val cursor = context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                    BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER
                ),
                null,
                null,
                "${BlockedNumberContract.BlockedNumbers.COLUMN_ID} DESC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
                val origIdx = it.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                val e164Idx = it.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER)

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getLong(idIdx) else 0L
                    val orig = if (origIdx != -1) it.getString(origIdx) else ""
                    val e164 = if (e164Idx != -1) it.getString(e164Idx) else null
                    if (orig.isNotBlank()) {
                        list.add(BlockedNumberItem(id, orig, e164))
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun blockNumber(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+*#]"), "")
        if (cleanNumber.isBlank()) return@withContext false
        try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, cleanNumber)
            }
            val uri = context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
            uri != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun unblockNumber(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+*#]"), "")
        if (cleanNumber.isBlank()) return@withContext false
        try {
            val rows = BlockedNumberContract.unblock(context, cleanNumber)
            rows > 0
        } catch (_: Exception) {
            false
        }
    }
}
