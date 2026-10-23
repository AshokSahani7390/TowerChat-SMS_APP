package com.towerchat.app.crypto

/**
 * Implements the lightweight cellular protocol extensions from Features v1.1:
 * - SMS Batching: Combining multiple rapid messages into 1 SMS (TC_BATCH:|msg1||msg2|)
 * - Typing Indicator: One SMS per 60s max per contact (TC_TYPING:)
 * - Read Receipt: One SMS per session when active chat is foregrounded (TC_READ:<timestamp>)
 * - Message Reactions: Attaching emojis to bubbles via timestamp (TC_REACT:<emoji>:<timestamp>)
 */
object TowerchatProtocol {

    const val PREFIX_BATCH = "TC_BATCH:|"
    const val DELIMITER_BATCH = "||"
    const val SUFFIX_BATCH = "|"

    const val PREFIX_TYPING = "TC_TYPING:"
    const val PREFIX_READ = "TC_READ:"
    const val PREFIX_REACT = "TC_REACT:"

    // --- Batching ---
    fun isBatch(body: String): Boolean = body.startsWith(PREFIX_BATCH) && body.endsWith(SUFFIX_BATCH)

    fun buildBatch(messages: List<String>): String {
        return "$PREFIX_BATCH${messages.joinToString(DELIMITER_BATCH)}$SUFFIX_BATCH"
    }

    fun extractBatchMessages(body: String): List<String> {
        val trimmed = body.removePrefix(PREFIX_BATCH).removeSuffix(SUFFIX_BATCH)
        return trimmed.split(DELIMITER_BATCH).filter { it.isNotBlank() }
    }

    // --- Typing Indicator ---
    fun isTyping(body: String): Boolean = body.trim() == PREFIX_TYPING

    fun buildTyping(): String = PREFIX_TYPING

    // --- Read Receipt ---
    fun isReadReceipt(body: String): Boolean = body.startsWith(PREFIX_READ)

    fun buildReadReceipt(timestamp: Long): String = "$PREFIX_READ$timestamp"

    fun extractReadReceiptTimestamp(body: String): Long? {
        return body.removePrefix(PREFIX_READ).trim().toLongOrNull()
    }

    // --- Reactions ---
    fun isReaction(body: String): Boolean = body.startsWith(PREFIX_REACT)

    fun buildReaction(emoji: String, timestamp: Long): String = "$PREFIX_REACT$emoji:$timestamp"

    fun extractReaction(body: String): Pair<String, Long>? {
        val parts = body.removePrefix(PREFIX_REACT).split(":")
        if (parts.size >= 2) {
            val emoji = parts[0]
            val timestamp = parts[1].toLongOrNull()
            if (timestamp != null) {
                return Pair(emoji, timestamp)
            }
        }
        return null
    }
}
