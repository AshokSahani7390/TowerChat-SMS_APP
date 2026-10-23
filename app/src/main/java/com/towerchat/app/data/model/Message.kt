package com.towerchat.app.data.model

import java.util.UUID

/**
 * Message represents a single chat message exchanged via cellular SMS.
 * Stored strictly in-memory (RAM) for the duration of the app session.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val senderNumber: String,
    val recipientNumber: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean,
    val status: MessageStatus = if (isOutgoing) MessageStatus.SENDING else MessageStatus.DELIVERED,
    val partsCount: Int = 1,
    val reaction: String? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)
