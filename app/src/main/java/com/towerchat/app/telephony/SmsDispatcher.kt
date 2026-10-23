package com.towerchat.app.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.towerchat.app.crypto.CryptoManager
import com.towerchat.app.crypto.HandshakeProtocol
import com.towerchat.app.crypto.KeySessionRepository
import com.towerchat.app.data.model.Contact
import com.towerchat.app.data.model.Message
import com.towerchat.app.data.model.MessageStatus
import com.towerchat.app.data.session.ChatSessionManager
import com.towerchat.app.crypto.TowerchatProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: ChatSessionManager,
    private val keySessionRepository: KeySessionRepository
) {

    // Throttled typing indicator: max once per 60s per contact
    private val lastTypingSentMap = mutableMapOf<String, Long>()

    companion object {
        const val ACTION_SMS_SENT = "com.towerchat.app.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.towerchat.app.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_RECIPIENT_NUMBER = "extra_recipient_number"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"

        // Dedicated port for TowerChat App-to-App messaging (bypasses Google Messages & default SMS inbox)
        const val TOWERCHAT_DATA_PORT: Short = 8901
    }

    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * Sends an X25519/ECDH public key exchange handshake over SMS.
     * When isAppOnly is true, dispatches over port 8901 so other SMS apps never see it.
     */
    fun sendHandshake(recipientNumber: String, isAppOnly: Boolean = true): Result<Unit> {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val handshakeBody = HandshakeProtocol.buildHandshakeMessage(keySessionRepository.myPublicKeyBase64)
        return try {
            val smsManager = getSmsManager()
            if (isAppOnly) {
                val bytes = handshakeBody.toByteArray(Charsets.UTF_8)
                smsManager.sendDataMessage(normalizedRecipient, null, TOWERCHAT_DATA_PORT, bytes, null, null)
            } else {
                // In standard SMS mode, omit handshake to keep message inbox completely clean
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Dispatches an SMS to the cellular network.
     * @param isAppOnly When true (default), sends via port-directed Data SMS (Port 8901).
     *                  This ensures the message is received ONLY inside TowerChat (like WhatsApp),
     *                  completely bypassing Google Messages, Samsung Messages, or standard SMS inboxes.
     *                  When false, sends as standard SMS with ONLY the clean user text.
     */
    fun sendMessage(recipientNumber: String, text: String, isAppOnly: Boolean = true): Result<Message> {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val sharedSecret = keySessionRepository.getSharedSecret(normalizedRecipient)

        val wirePayload: String
        if (isAppOnly) {
            // App-to-App mode: If no key exchange yet, send silent handshake over Port 8901
            if (sharedSecret == null) {
                sendHandshake(normalizedRecipient, isAppOnly = true)
            }

            wirePayload = if (sharedSecret != null) {
                val ciphertext = CryptoManager.encrypt(text, sharedSecret)
                HandshakeProtocol.buildEncryptedMessage(ciphertext)
            } else {
                text
            }
        } else {
            // Standard SMS mode: Send EXCLUSIVELY the raw user text (e.g. "hii").
            // Zero handshake, zero typing indicator, zero metadata prefixes!
            wirePayload = text
        }

        val quota = sessionManager.quotaState.value
        val segments = QuotaCalculator.calculateSmsSegments(wirePayload)

        if (quota.remaining < segments) {
            return Result.failure(
                IllegalStateException("Daily SMS quota exceeded. Remaining: ${quota.remaining}, Required: $segments")
            )
        }

        val myNumber = sessionManager.userPhoneNumber.value ?: "Self"

        // In-memory message stores plaintext for user readability
        val message = Message(
            senderNumber = myNumber,
            recipientNumber = normalizedRecipient,
            body = text,
            isOutgoing = true,
            status = MessageStatus.SENDING,
            partsCount = segments
        )

        sessionManager.addMessage(message)

        try {
            val smsManager = getSmsManager()
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntent = PendingIntent.getBroadcast(
                context,
                message.id.hashCode(),
                Intent(ACTION_SMS_SENT).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_MESSAGE_ID, message.id)
                    putExtra(EXTRA_RECIPIENT_NUMBER, normalizedRecipient)
                    putExtra(EXTRA_PART_INDEX, 0)
                    putExtra(EXTRA_TOTAL_PARTS, 1)
                },
                flags
            )

            val deliveredIntent = PendingIntent.getBroadcast(
                context,
                message.id.hashCode() + 1,
                Intent(ACTION_SMS_DELIVERED).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_MESSAGE_ID, message.id)
                },
                flags
            )

            if (isAppOnly) {
                // Send via Port 8901 Data SMS (Received exclusively by TowerChat app)
                val payloadBytes = wirePayload.toByteArray(Charsets.UTF_8)
                sendDataPayload(smsManager, normalizedRecipient, TOWERCHAT_DATA_PORT, payloadBytes, sentIntent, deliveredIntent)
            } else {
                // Send via Standard SMS (Received by normal SMS apps)
                if (segments <= 1) {
                    smsManager.sendTextMessage(
                        normalizedRecipient,
                        null,
                        wirePayload,
                        sentIntent,
                        deliveredIntent
                    )
                } else {
                    val parts = smsManager.divideMessage(wirePayload)
                    val sentIntents = ArrayList<PendingIntent>()
                    val deliveryIntents = ArrayList<PendingIntent>()

                    parts.forEachIndexed { index, _ ->
                        val partSentIntent = PendingIntent.getBroadcast(
                            context,
                            message.id.hashCode() + index,
                            Intent(ACTION_SMS_SENT).apply {
                                setPackage(context.packageName)
                                putExtra(EXTRA_MESSAGE_ID, message.id)
                                putExtra(EXTRA_RECIPIENT_NUMBER, normalizedRecipient)
                                putExtra(EXTRA_PART_INDEX, index)
                                putExtra(EXTRA_TOTAL_PARTS, parts.size)
                            },
                            flags
                        )
                        sentIntents.add(partSentIntent)
                        deliveryIntents.add(deliveredIntent)
                    }

                    smsManager.sendMultipartTextMessage(
                        normalizedRecipient,
                        null,
                        parts,
                        sentIntents,
                        deliveryIntents
                    )
                }
            }

            return Result.success(message)
        } catch (e: Exception) {
            sessionManager.updateMessageStatus(
                message.id,
                MessageStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Failed to send SMS"
            )
            return Result.failure(e)
        }
    }

    private fun sendDataPayload(
        smsManager: SmsManager,
        recipientNumber: String,
        port: Short,
        data: ByteArray,
        sentIntent: PendingIntent?,
        deliveredIntent: PendingIntent?
    ) {
        val maxSingleSize = 130
        if (data.size <= maxSingleSize) {
            smsManager.sendDataMessage(recipientNumber, null, port, data, sentIntent, deliveredIntent)
        } else {
            // Slices for larger payloads
            val chunkSize = 120
            val totalChunks = (data.size + chunkSize - 1) / chunkSize
            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = (start + chunkSize).coerceAtMost(data.size)
                val slice = data.copyOfRange(start, end)
                val header = "[${i + 1}/$totalChunks]".toByteArray(Charsets.UTF_8)
                val chunkWithHeader = header + slice
                val isLast = (i == totalChunks - 1)
                smsManager.sendDataMessage(
                    recipientNumber,
                    null,
                    port,
                    chunkWithHeader,
                    if (isLast) sentIntent else null,
                    if (isLast) deliveredIntent else null
                )
            }
        }
    }

    /**
     * Typing indicators over cellular SMS are intentionally disabled to prevent quota waste
     * and spamming standard SMS inboxes with TC_TYPING:.
     */
    fun sendTypingIndicator(recipientNumber: String) {
        // No-op: Zero cellular SMS messages sent for typing events
    }

    /**
     * Cellular protocol: read receipt for active open conversation.
     * Dispatched over Port 8901 so other apps never see it.
     */
    fun sendReadReceipt(recipientNumber: String, timestamp: Long) {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val payload = TowerchatProtocol.buildReadReceipt(timestamp)
        dispatchRawProtocolPayload(normalizedRecipient, payload)
    }

    /**
     * Cellular protocol: emoji message reaction attached to message timestamp.
     * Dispatched over Port 8901 so other apps never see it.
     */
    fun sendReaction(recipientNumber: String, emoji: String, targetTimestamp: Long) {
        val normalizedRecipient = Contact.normalizePhoneNumber(recipientNumber)
        val payload = TowerchatProtocol.buildReaction(emoji, targetTimestamp)
        dispatchRawProtocolPayload(normalizedRecipient, payload)
        // Also apply reaction to message in local RAM
        sessionManager.addReaction(normalizedRecipient, targetTimestamp, emoji)
    }

    private fun dispatchRawProtocolPayload(recipientNumber: String, plaintext: String) {
        try {
            val sharedSecret = keySessionRepository.getSharedSecret(recipientNumber)
            val wirePayload = if (sharedSecret != null) {
                val ciphertext = CryptoManager.encrypt(plaintext, sharedSecret)
                HandshakeProtocol.buildEncryptedMessage(ciphertext)
            } else {
                plaintext
            }
            val smsManager = getSmsManager()
            val bytes = wirePayload.toByteArray(Charsets.UTF_8)
            // Send exclusively on Port 8901 so normal SMS apps never see protocol packets
            smsManager.sendDataMessage(recipientNumber, null, TOWERCHAT_DATA_PORT, bytes, null, null)
        } catch (_: Exception) {}
    }
}
